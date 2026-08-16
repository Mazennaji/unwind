package org.unwind.ledger;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.unwind.common.FailStep;
import org.unwind.common.Messaging;
import org.unwind.common.command.RecordLedgerCommand;
import org.unwind.common.event.StepResult;

import java.time.Instant;
import java.util.UUID;

@Component
public class LedgerListener {

    private final LedgerRepository ledger;
    private final RabbitTemplate rabbit;

    public LedgerListener(LedgerRepository ledger, RabbitTemplate rabbit) {
        this.ledger = ledger;
        this.rabbit = rabbit;
    }

    @RabbitListener(queues = Messaging.Q_LEDGER)
    @Transactional
    public void onMessage(RecordLedgerCommand cmd) {
        if (cmd.failStep() == FailStep.LEDGER) {
            reply(cmd.sagaId(), false, "injected failure");
            return;
        }
        LedgerEntry entry = new LedgerEntry(
                UUID.randomUUID().toString(),
                cmd.sagaId(),
                cmd.fromAccount(),
                cmd.toAccount(),
                cmd.amount(),
                Instant.now()
        );
        ledger.save(entry);
        reply(cmd.sagaId(), true, "recorded " + cmd.amount());
    }

    private void reply(String sagaId, boolean success, String detail) {
        rabbit.convertAndSend(
                Messaging.EXCHANGE,
                Messaging.RK_REPLY,
                new StepResult(sagaId, "LEDGER", success, detail)
        );
    }
}