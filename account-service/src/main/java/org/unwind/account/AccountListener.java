package org.unwind.account;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.unwind.common.FailStep;
import org.unwind.common.Messaging;
import org.unwind.common.command.*;
import org.unwind.common.event.StepResult;

import java.math.BigDecimal;

@Component
public class AccountListener {

    private final AccountRepository accounts;
    private final RabbitTemplate rabbit;

    public AccountListener(AccountRepository accounts, RabbitTemplate rabbit) {
        this.accounts = accounts;
        this.rabbit = rabbit;
    }

    private void reply(String sagaId, String step, boolean success, String detail) {
        rabbit.convertAndSend(
                Messaging.EXCHANGE,
                Messaging.RK_REPLY,
                new StepResult(sagaId, step, success, detail)
        );
    }

    @RabbitListener(queues = Messaging.Q_ACCOUNT)
    @Transactional
    public void onMessage(Object message) {
        if (message instanceof DebitCommand cmd) {
            handleDebit(cmd);
        } else if (message instanceof CreditCommand cmd) {
            handleCredit(cmd);
        } else if (message instanceof RefundCommand cmd) {
            handleRefund(cmd);
        } else if (message instanceof ReverseCreditCommand cmd) {
            handleReverse(cmd);
        }
    }

    private void handleDebit(DebitCommand cmd) {
        if (cmd.failStep() == FailStep.DEBIT) {
            reply(cmd.sagaId(), "DEBIT", false, "injected failure");
            return;
        }
        Account acct = accounts.findById(cmd.fromAccount()).orElse(null);
        if (acct == null) {
            reply(cmd.sagaId(), "DEBIT", false, "account not found");
            return;
        }
        if (acct.getBalance().compareTo(cmd.amount()) < 0) {
            reply(cmd.sagaId(), "DEBIT", false, "insufficient funds");
            return;
        }
        acct.setBalance(acct.getBalance().subtract(cmd.amount()));
        accounts.save(acct);
        reply(cmd.sagaId(), "DEBIT", true, "debited " + cmd.amount());
    }

    private void handleCredit(CreditCommand cmd) {
        if (cmd.failStep() == FailStep.CREDIT) {
            reply(cmd.sagaId(), "CREDIT", false, "injected failure");
            return;
        }
        Account acct = accounts.findById(cmd.toAccount()).orElse(null);
        if (acct == null) {
            reply(cmd.sagaId(), "CREDIT", false, "account not found");
            return;
        }
        acct.setBalance(acct.getBalance().add(cmd.amount()));
        accounts.save(acct);
        reply(cmd.sagaId(), "CREDIT", true, "credited " + cmd.amount());
    }

    private void handleRefund(RefundCommand cmd) {
        Account acct = accounts.findById(cmd.fromAccount()).orElse(null);
        if (acct != null) {
            acct.setBalance(acct.getBalance().add(cmd.amount()));
            accounts.save(acct);
        }
        reply(cmd.sagaId(), "REFUND", true, "refunded " + cmd.amount());
    }

    private void handleReverse(ReverseCreditCommand cmd) {
        Account acct = accounts.findById(cmd.toAccount()).orElse(null);
        if (acct != null) {
            acct.setBalance(acct.getBalance().subtract(cmd.amount()));
            accounts.save(acct);
        }
        reply(cmd.sagaId(), "REVERSE_CREDIT", true, "reversed " + cmd.amount());
    }
}