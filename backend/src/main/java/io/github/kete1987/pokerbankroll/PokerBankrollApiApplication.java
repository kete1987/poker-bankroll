package io.github.kete1987.pokerbankroll;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** The API. Scheduling runs the daily download of exchange rates. */
@SpringBootApplication
@EnableScheduling
public class PokerBankrollApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(PokerBankrollApiApplication.class, args);
    }
}
