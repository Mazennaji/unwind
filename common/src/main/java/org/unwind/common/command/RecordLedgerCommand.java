package org.unwind.common.command;

import org.unwind.common.FailStep;
import java.math.BigDecimal;

public record RecordLedgerCommand(
        String sagaId,
        String fromAccount,
        String toAccount,
        BigDecimal amount,
        FailStep failStep
) {}