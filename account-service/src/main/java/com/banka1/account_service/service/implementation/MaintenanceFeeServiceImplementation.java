package com.banka1.account_service.service.implementation;

import com.banka1.account_service.domain.Account;
import com.banka1.account_service.domain.CheckingAccount;
import com.banka1.account_service.domain.Currency;
import com.banka1.account_service.domain.TransactionRecord;
import com.banka1.account_service.domain.enums.CurrencyCode;
import com.banka1.account_service.repository.AccountRepository;
import com.banka1.account_service.repository.CurrencyRepository;
import com.banka1.account_service.repository.TransactionRecordRepository;
import com.banka1.account_service.service.MaintenanceFeeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MaintenanceFeeServiceImplementation implements MaintenanceFeeService {

    private final AccountRepository accountRepository;
    private final CurrencyRepository currencyRepository;
    private final TransactionRecordRepository transactionRecordRepository;

    @Transactional
    @Override
    public void process() {
        List<CheckingAccount> accounts = accountRepository.findAllActiveCheckingAccountsWithMaintenanceFee();
        Currency currency=currencyRepository.findByOznaka(CurrencyCode.RSD).orElse(null);
        if(currency==null)
            throw new IllegalStateException("Ne postoji RSD currency");
         Account bankAccount = accountRepository.findByIdAndCurrency((long) -1,currency).orElseThrow(() -> new RuntimeException("Bank RSD account not found"));
        BigDecimal total = BigDecimal.ZERO;

        for (CheckingAccount acc : accounts) {
            BigDecimal fee = acc.getOdrzavanjeRacuna();
            if (fee == null || fee.signum() <= 0)
                continue;
            acc.setStanje(acc.getStanje().subtract(fee));
            acc.setRaspolozivoStanje(acc.getRaspolozivoStanje().subtract(fee));
            transactionRecordRepository.save(new TransactionRecord(acc.getBrojRacuna(),bankAccount.getBrojRacuna(),fee));
            total = total.add(fee);
            log.info("Fee deducted | acc={} fee={} newBalance={}", acc.getBrojRacuna(), fee, acc.getStanje());
        }
        bankAccount.setStanje(bankAccount.getStanje().add(total));
        bankAccount.setRaspolozivoStanje(bankAccount.getRaspolozivoStanje().add(total));
        log.info("Monthly maintenance done | accounts={} total={}", accounts.size(), total);

    }
}
