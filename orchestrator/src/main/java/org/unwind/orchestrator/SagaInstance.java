package org.unwind.orchestrator;

import jakarta.persistence.*;
import org.unwind.common.FailStep;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "saga_instances", schema = "saga")
public class SagaInstance {

    @Id
    private String id;

    private String fromAccount;
    private String toAccount;
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    private SagaState state;

    @Enumerated(EnumType.STRING)
    private FailStep failStep;

    private String detail;
    private Instant createdAt;
    private Instant updatedAt;

    protected SagaInstance() {}

    public SagaInstance(String id, String fromAccount, String toAccount,
                        BigDecimal amount, FailStep failStep) {
        this.id = id;
        this.fromAccount = fromAccount;
        this.toAccount = toAccount;
        this.amount = amount;
        this.failStep = failStep;
        this.state = SagaState.STARTED;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public String getId() { return id; }
    public String getFromAccount() { return fromAccount; }
    public String getToAccount() { return toAccount; }
    public BigDecimal getAmount() { return amount; }
    public SagaState getState() { return state; }
    public FailStep getFailStep() { return failStep; }
    public String getDetail() { return detail; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setState(SagaState state) {
        this.state = state;
        this.updatedAt = Instant.now();
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }
}