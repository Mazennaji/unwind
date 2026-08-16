package org.unwind.account;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;

@Component
public class DataSeeder implements CommandLineRunner {

    private final AccountRepository repository;

    public DataSeeder(AccountRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(String... args) {
        seed("acct-A", new BigDecimal("1000.00"));
        seed("acct-B", new BigDecimal("500.00"));
    }

    private void seed(String id, BigDecimal balance) {
        if (!repository.existsById(id)) {
            repository.save(new Account(id, balance));
        }
    }
}