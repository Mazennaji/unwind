package org.unwind.common.command;

import java.math.BigDecimal;

public record RefundCommand(
        String sagaId,
        String fromAccount,
        BigDecimal amount
) {}