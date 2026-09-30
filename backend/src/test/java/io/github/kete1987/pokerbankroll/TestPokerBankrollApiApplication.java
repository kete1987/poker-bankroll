package io.github.kete1987.pokerbankroll;

import org.springframework.boot.SpringApplication;

/**
 * Runs the API locally against a throwaway PostgreSQL container (requires Docker):
 * {@code ./mvnw spring-boot:test-run}.
 */
public class TestPokerBankrollApiApplication {

    public static void main(String[] args) {
        SpringApplication.from(PokerBankrollApiApplication::main).with(TestcontainersConfiguration.class).run(args);
    }
}
