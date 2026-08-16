package org.unwind.common.command;

import java.math.BigDecimal;

public record ReverseCreditCommand(
        String sagaId,
        String toAccount,
        BigDecimal amount
) {}