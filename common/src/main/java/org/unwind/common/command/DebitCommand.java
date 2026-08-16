package org.unwind.common.command;

import org.unwind.common.FailStep;
import java.math.BigDecimal;

public record DebitCommand(
        String sagaId,
        String fromAccount,
        BigDecimal amount,
        FailStep failStep
) {}