package io.github.kete1987.pokerbankroll.catalog;

import java.util.List;

import org.springframework.data.repository.Repository;

public interface CurrencyRepository extends Repository<Currency, String> {

    List<Currency> findAllByOrderByCode();

    boolean existsById(String code);
}
