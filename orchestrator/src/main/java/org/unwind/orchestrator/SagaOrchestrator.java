package org.unwind.orchestrator;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.unwind.common.FailStep;
import org.unwind.common.Messaging;
import org.unwind.common.command.*;
import org.unwind.common.event.StepResult;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class SagaOrchestrator {

    private final SagaRepository sagas;
    private final RabbitTemplate rabbit;
    private final SagaWebSocketHandler ws;

    public SagaOrchestrator(SagaRepository sagas, RabbitTemplate rabbit, SagaWebSocketHandler ws) {
        this.sagas = sagas;
        this.rabbit = rabbit;
        this.ws = ws;
    }

    @Transactional
    public String startTransfer(String from, String to, BigDecimal amount, FailStep failStep) {
        String sagaId = UUID.randomUUID().toString();
        SagaInstance saga = new SagaInstance(sagaId, from, to, amount, failStep);
        sagas.save(saga);
        ws.broadcast(saga);

        rabbit.convertAndSend(Messaging.EXCHANGE, Messaging.RK_DEBIT,
                new DebitCommand(sagaId, from, amount, failStep));

        return sagaId;
    }

    @Transactional
    public void onStepResult(StepResult result) {
        SagaInstance saga = sagas.findById(result.sagaId()).orElse(null);
        if (saga == null) return;

        switch (result.step()) {
            case "DEBIT" -> handleDebitResult(saga, result);
            case "CREDIT" -> handleCreditResult(saga, result);
            case "LEDGER" -> handleLedgerResult(saga, result);
            case "REFUND" -> handleRefundResult(saga);
            case "REVERSE_CREDIT" -> handleReverseResult(saga);
            default -> { }
        }
        sagas.save(saga);
        ws.broadcast(saga);
    }

    private void handleDebitResult(SagaInstance saga, StepResult result) {
        if (!result.success()) {
            saga.setState(SagaState.FAILED);
            saga.setDetail("debit failed: " + result.detail());
            return;
        }
        saga.setState(SagaState.DEBITED);
        rabbit.convertAndSend(Messaging.EXCHANGE, Messaging.RK_CREDIT,
                new CreditCommand(saga.getId(), saga.getToAccount(),
                        saga.getAmount(), saga.getFailStep()));
    }

    private void handleCreditResult(SagaInstance saga, StepResult result) {
        if (!result.success()) {
            saga.setState(SagaState.COMPENSATING);
            saga.setDetail("credit failed, refunding debit: " + result.detail());
            rabbit.convertAndSend(Messaging.EXCHANGE, Messaging.RK_REFUND,
                    new RefundCommand(saga.getId(), saga.getFromAccount(), saga.getAmount()));
            return;
        }
        saga.setState(SagaState.CREDITED);
        rabbit.convertAndSend(Messaging.EXCHANGE, Messaging.RK_LEDGER,
                new RecordLedgerCommand(saga.getId(), saga.getFromAccount(),
                        saga.getToAccount(), saga.getAmount(), saga.getFailStep()));
    }

    private void handleLedgerResult(SagaInstance saga, StepResult result) {
        if (!result.success()) {
            saga.setState(SagaState.COMPENSATING);
            saga.setDetail("ledger failed, unwinding: " + result.detail());
            rabbit.convertAndSend(Messaging.EXCHANGE, Messaging.RK_REVERSE_CREDIT,
                    new ReverseCreditCommand(saga.getId(), saga.getToAccount(), saga.getAmount()));
            rabbit.convertAndSend(Messaging.EXCHANGE, Messaging.RK_REFUND,
                    new RefundCommand(saga.getId(), saga.getFromAccount(), saga.getAmount()));
            return;
        }
        saga.setState(SagaState.COMPLETED);
        saga.setDetail("transfer complete");
    }

    private void handleRefundResult(SagaInstance saga) {
        saga.setState(SagaState.FAILED);
        saga.setDetail(saga.getDetail() + " | refund done");
    }

    private void handleReverseResult(SagaInstance saga) {
        saga.setDetail(saga.getDetail() + " | credit reversed");
    }
}