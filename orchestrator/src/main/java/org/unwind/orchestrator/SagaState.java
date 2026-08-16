package org.unwind.orchestrator;

public enum SagaState {
    STARTED,
    DEBITED,
    CREDITED,
    COMPLETED,
    COMPENSATING,
    FAILED
}