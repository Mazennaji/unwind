package org.unwind.common.event;

public record StepResult(
        String sagaId,
        String step,
        boolean success,
        String detail
) {}