package org.unwind.ledger;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "entries", schema = "ledger")
public class LedgerEntry {

    @Id
    private String id;

    private String sagaId;
    private String fromAccount;
    private String toAccount;
    private BigDecimal amount;
    private Instant recordedAt;

    protected LedgerEntry() {}

    public LedgerEntry(String id, String sagaId, String fromAccount,
                       String toAccount, BigDecimal amount, Instant recordedAt) {
        this.id = id;
        this.sagaId = sagaId;
        this.fromAccount = fromAccount;
        this.toAccount = toAccount;
        this.amount = amount;
        this.recordedAt = recordedAt;
    }

    public String getId() { return id; }
    public String getSagaId() { return sagaId; }
    public String getFromAccount() { return fromAccount; }
    public String getToAccount() { return toAccount; }
    public BigDecimal getAmount() { return amount; }
    public Instant getRecordedAt() { return recordedAt; }
}