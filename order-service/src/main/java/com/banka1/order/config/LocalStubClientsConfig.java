package com.banka1.order.config;

import com.banka1.order.client.AccountClient;
import com.banka1.order.client.ClientClient;
import com.banka1.order.client.EmployeeClient;
import com.banka1.order.client.ExchangeClient;
import com.banka1.order.client.StockClient;
import com.banka1.order.dto.AccountDetailsDto;
import com.banka1.order.dto.AccountTransactionRequest;
import com.banka1.order.dto.BankAccountDto;
import com.banka1.order.dto.CustomerDto;
import com.banka1.order.dto.CustomerPageResponse;
import com.banka1.order.dto.EmployeeDto;
import com.banka1.order.dto.EmployeePageResponse;
import com.banka1.order.dto.ExchangeRateDto;
import com.banka1.order.dto.ExchangeStatusDto;
import com.banka1.order.dto.StockExchangeDto;
import com.banka1.order.dto.StockListingDto;
import com.banka1.order.dto.client.PaymentDto;
import com.banka1.order.dto.response.UpdatedBalanceResponseDto;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.math.BigDecimal;

@Configuration
@Profile("local")
class LocalStubClientsConfig {

    @Bean
    AccountClient localAccountClient() {
        return new AccountClient() {
            @Override
            public AccountDetailsDto getAccountDetails(String accountNumber) {
                AccountDetailsDto dto = defaultAccountDetails();
                dto.setAccountNumber(accountNumber);
                return dto;
            }

            @Override
            public AccountDetailsDto getAccountDetails(Long accountId) {
                return defaultAccountDetails();
            }

            @Override
            public AccountDetailsDto getGovernmentBankAccountRsd() {
                AccountDetailsDto dto = defaultAccountDetails();
                dto.setCurrency("RSD");
                return dto;
            }

            @Override
            public void transfer(AccountTransactionRequest request) {
            }

            @Override
            public UpdatedBalanceResponseDto transaction(PaymentDto payment) {
                return null;
            }

            private AccountDetailsDto defaultAccountDetails() {
                AccountDetailsDto dto = new AccountDetailsDto();
                dto.setAccountNumber("LOCAL-ACCOUNT");
                dto.setBalance(new BigDecimal("1000000.00"));
                dto.setAvailableCredit(BigDecimal.ZERO);
                dto.setCurrency("USD");
                dto.setOwnerId(0L);
                return dto;
            }
        };
    }

    @Bean
    ClientClient localClientClient() {
        return new ClientClient() {
            @Override
            public CustomerDto getCustomer(Long id) {
                CustomerDto dto = new CustomerDto();
                dto.setId(id);
                dto.setEmail("local-client@example.com");
                return dto;
            }

            @Override
            public CustomerPageResponse searchCustomers(String ime, String prezime, int page, int size) {
                return new CustomerPageResponse();
            }
        };
    }

    @Bean
    EmployeeClient localEmployeeClient() {
        return new EmployeeClient() {
            @Override
            public EmployeeDto getEmployee(Long id) {
                EmployeeDto dto = new EmployeeDto();
                dto.setId(id);
                dto.setIme("Local");
                dto.setPrezime("Employee");
                dto.setEmail("local-employee@example.com");
                return dto;
            }

            @Override
            public EmployeePageResponse searchEmployees(String email, String ime, String prezime, String pozicija, int page, int size) {
                return new EmployeePageResponse();
            }

            @Override
            public BankAccountDto getBankAccount(String currency) {
                BankAccountDto dto = new BankAccountDto();
                dto.setAccountId(1L);
                return dto;
            }
        };
    }

    @Bean
    ExchangeClient localExchangeClient() {
        return new ExchangeClient() {
            @Override
            public ExchangeRateDto calculate(String fromCurrency, String toCurrency, BigDecimal amount) {
                return response(amount);
            }

            @Override
            public ExchangeRateDto calculateWithoutCommission(String fromCurrency, String toCurrency, BigDecimal amount) {
                return response(amount);
            }

            private ExchangeRateDto response(BigDecimal amount) {
                ExchangeRateDto dto = new ExchangeRateDto();
                dto.setConvertedAmount(amount);
                dto.setCommission(BigDecimal.ZERO);
                return dto;
            }
        };
    }

    @Bean
    StockClient localStockClient() {
        return new StockClient() {
            @Override
            public StockListingDto getListing(Long id) {
                throw new UnsupportedOperationException("Local profile stub does not provide listings");
            }

            @Override
            public StockExchangeDto getStockExchange(Long id) {
                return new StockExchangeDto();
            }

            @Override
            public Boolean isStockExchangeOpen(Long id) {
                return Boolean.TRUE;
            }

            @Override
            public ExchangeStatusDto getExchangeStatus(Long id) {
                ExchangeStatusDto dto = new ExchangeStatusDto();
                dto.setOpen(true);
                dto.setClosed(false);
                dto.setAfterHours(false);
                return dto;
            }
        };
    }
}
