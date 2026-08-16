package org.unwind.common.command;

import org.unwind.common.FailStep;
import java.math.BigDecimal;

public record CreditCommand(
        String sagaId,
        String toAccount,
        BigDecimal amount,
        FailStep failStep
) {}