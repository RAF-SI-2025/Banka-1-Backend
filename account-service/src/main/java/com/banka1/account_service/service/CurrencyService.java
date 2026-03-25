package com.banka1.account_service.service;

import com.banka1.account_service.domain.Currency;
import com.banka1.account_service.domain.enums.CurrencyCode;
import org.springframework.data.domain.Page;

import java.util.List;

public interface CurrencyService {

    List<Currency>findAll();
    Page<Currency> findAllPage(int page,int size);

    Currency findByCode(CurrencyCode code);

}
