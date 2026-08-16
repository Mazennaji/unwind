package org.unwind.common;

public final class Messaging {
    private Messaging() {}

    public static final String EXCHANGE = "unwind.saga";

    public static final String RK_DEBIT = "cmd.debit";
    public static final String RK_CREDIT = "cmd.credit";
    public static final String RK_LEDGER = "cmd.ledger";
    public static final String RK_REFUND = "cmd.refund";
    public static final String RK_REVERSE_CREDIT = "cmd.reverse-credit";

    public static final String RK_REPLY = "reply.step";

    public static final String Q_ACCOUNT = "account.commands";
    public static final String Q_LEDGER = "ledger.commands";
    public static final String Q_REPLY = "orchestrator.replies";
}