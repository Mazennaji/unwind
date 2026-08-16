package org.unwind.account;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.unwind.common.FailStep;
import org.unwind.common.Messaging;
import org.unwind.common.command.*;
import org.unwind.common.event.StepResult;

@Component
public class AccountListener {

    private final AccountRepository accounts;
    private final RabbitTemplate rabbit;
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new ParameterNamesModule());

    public AccountListener(AccountRepository accounts, RabbitTemplate rabbit) {
        this.accounts = accounts;
        this.rabbit = rabbit;
    }

    private void reply(String sagaId, String step, boolean success, String detail) {
        System.out.println(">>> replying " + step + " success=" + success + " detail=" + detail);
        rabbit.convertAndSend(
                Messaging.EXCHANGE,
                Messaging.RK_REPLY,
                new StepResult(sagaId, step, success, detail)
        );
    }

    @RabbitListener(queues = Messaging.Q_ACCOUNT)
    @Transactional
    public void onMessage(Message message) {
        String routingKey = message.getMessageProperties().getReceivedRoutingKey();
        String body = new String(message.getBody());
        System.out.println(">>> account received routingKey=" + routingKey + " body=" + body);

        try {
            switch (routingKey) {
                case Messaging.RK_DEBIT ->
                        handleDebit(mapper.readValue(body, DebitCommand.class));
                case Messaging.RK_CREDIT ->
                        handleCredit(mapper.readValue(body, CreditCommand.class));
                case Messaging.RK_REFUND ->
                        handleRefund(mapper.readValue(body, RefundCommand.class));
                case Messaging.RK_REVERSE_CREDIT ->
                        handleReverse(mapper.readValue(body, ReverseCreditCommand.class));
                default -> System.out.println(">>> unknown routing key: " + routingKey);
            }
        } catch (Exception e) {
            System.out.println(">>> account processing error: " + e.getMessage());
            e.printStackTrace();
            throw new AmqpRejectAndDontRequeueException("processing failed", e);
        }
    }

    private void handleDebit(DebitCommand cmd) {
        System.out.println(">>> DEBIT for " + cmd.fromAccount() + " amount " + cmd.amount());
        if (cmd.failStep() == FailStep.DEBIT) {
            reply(cmd.sagaId(), "DEBIT", false, "injected failure");
            return;
        }
        Account acct = accounts.findById(cmd.fromAccount()).orElse(null);
        System.out.println(">>> findById(" + cmd.fromAccount() + ") returned: "
                + (acct == null ? "NULL" : "balance=" + acct.getBalance()));
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
        System.out.println(">>> saved new balance for " + acct.getId() + " = " + acct.getBalance());
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