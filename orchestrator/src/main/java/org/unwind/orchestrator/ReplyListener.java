package org.unwind.orchestrator;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.unwind.common.Messaging;
import org.unwind.common.event.StepResult;

@Component
public class ReplyListener {

    private final SagaOrchestrator orchestrator;

    public ReplyListener(SagaOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @RabbitListener(queues = Messaging.Q_REPLY)
    public void onReply(StepResult result) {
        orchestrator.onStepResult(result);
    }
}