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

    public SagaOrchestrator(SagaRepository sagas, RabbitTemplate rabbit) {
        this.sagas = sagas;
        this.rabbit = rabbit;
    }

    @Transactional
    public String startTransfer(String from, String to, BigDecimal amount, FailStep failStep) {
        String sagaId = UUID.randomUUID().toString();
        SagaInstance saga = new SagaInstance(sagaId, from, to, amount, failStep);
        sagas.save(saga);

        try {
            rabbit.convertAndSend(Messaging.EXCHANGE, Messaging.RK_DEBIT,
                    new DebitCommand(sagaId, from, amount, failStep));
            System.out.println(">>> published DebitCommand for saga " + sagaId
                    + " to " + Messaging.EXCHANGE + "/" + Messaging.RK_DEBIT);
        } catch (Exception e) {
            System.out.println(">>> PUBLISH FAILED: " + e.getMessage());
            e.printStackTrace();
        }

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
            default -> { /* ignore compensation acks in happy path */ }
        }
        sagas.save(saga);
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
            saga.setState(SagaState.FAILED);
            saga.setDetail("credit failed: " + result.detail());
            return;
        }
        saga.setState(SagaState.CREDITED);
        rabbit.convertAndSend(Messaging.EXCHANGE, Messaging.RK_LEDGER,
                new RecordLedgerCommand(saga.getId(), saga.getFromAccount(),
                        saga.getToAccount(), saga.getAmount(), saga.getFailStep()));
    }

    private void handleLedgerResult(SagaInstance saga, StepResult result) {
        if (!result.success()) {
            saga.setState(SagaState.FAILED);
            saga.setDetail("ledger failed: " + result.detail());
            return;
        }
        saga.setState(SagaState.COMPLETED);
        saga.setDetail("transfer complete");
    }
}