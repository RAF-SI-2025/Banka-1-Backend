package com.banka1.account_service.service.implementation;

import com.banka1.account_service.domain.Currency;
import com.banka1.account_service.domain.enums.CurrencyCode;
import com.banka1.account_service.domain.enums.Status;
import com.banka1.account_service.repository.CurrencyRepository;
import com.banka1.account_service.service.CurrencyService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CurrencyServiceImplementation implements CurrencyService {
    private final CurrencyRepository currencyRepository;

    @Override
    public List<Currency> findAll() {
        return currencyRepository.findByStatus(Status.ACTIVE);
    }

    @Override
    public Page<Currency> findAllPage(int page, int size) {
        return currencyRepository.findByStatus(Status.ACTIVE,PageRequest.of(page,size));
    }

    @Override
    public Currency findByCode(CurrencyCode code) {
        return currencyRepository.findByOznaka(code).orElseThrow((() -> new RuntimeException("Currency nije pronadjen")));
    }


}
