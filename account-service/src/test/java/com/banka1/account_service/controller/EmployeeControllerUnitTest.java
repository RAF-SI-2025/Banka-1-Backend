package com.banka1.account_service.controller;

import com.banka1.account_service.domain.CheckingAccount;
import com.banka1.account_service.domain.Currency;
import com.banka1.account_service.domain.enums.AccountConcrete;
import com.banka1.account_service.domain.enums.AccountOwnershipType;
import com.banka1.account_service.domain.enums.CurrencyCode;
import com.banka1.account_service.domain.enums.Status;
import com.banka1.account_service.dto.request.CheckingDto;
import com.banka1.account_service.dto.request.EditStatus;
import com.banka1.account_service.dto.request.FxDto;
import com.banka1.account_service.dto.request.UpdateCompanyDto;
import com.banka1.account_service.dto.response.AccountDetailsResponseDto;
import com.banka1.account_service.dto.response.AccountSearchResponseDto;
import com.banka1.account_service.dto.response.CompanyResponseDto;
import com.banka1.account_service.service.ClientService;
import com.banka1.account_service.service.EmployeeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.Set;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeControllerUnitTest {

    @Mock
    private EmployeeService employeeService;

    @Mock
    private ClientService clientService;

    @Test
    void createCheckingAccountReturnsOkAndDelegates() {
        EmployeeController controller = new EmployeeController(employeeService, clientService);
        CheckingDto dto = new CheckingDto("Tekuci", 1L, null, AccountConcrete.STANDARDNI, null, new BigDecimal("100"), false);
        AccountDetailsResponseDto expected = accountDetailsResponseDto();
        when(employeeService.createCheckingAccount(null, dto)).thenReturn(expected);

        ResponseEntity<AccountDetailsResponseDto> response = controller.createCheckingAccount(null, dto);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expected);
        verify(employeeService).createCheckingAccount(null, dto);
    }

    @Test
    void createFxAccountReturnsOkAndDelegates() {
        EmployeeController controller = new EmployeeController(employeeService, clientService);
        FxDto dto = new FxDto("Devizni", 1L, null, CurrencyCode.EUR, AccountOwnershipType.PERSONAL, new BigDecimal("100"), false, null);
        AccountDetailsResponseDto expected = accountDetailsResponseDto();
        when(employeeService.createFxAccount(null, dto)).thenReturn(expected);

        ResponseEntity<AccountDetailsResponseDto> response = controller.createFxAccount(null, dto);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expected);
        verify(employeeService).createFxAccount(null, dto);
    }

    @Test
    void searchAllAccountsReturnsOkAndDelegates() {
        EmployeeController controller = new EmployeeController(employeeService, clientService);
        Page<AccountSearchResponseDto> page = new PageImpl<>(List.of(new AccountSearchResponseDto()));
        when(employeeService.searchAllAccounts(null, "Ime", "Prezime", "111", 0, 10)).thenReturn(page);

        ResponseEntity<Page<AccountSearchResponseDto>> response = controller.searchAllAccounts(null, "Ime", "Prezime", "111", 0, 10);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(page);
        verify(employeeService).searchAllAccounts(null, "Ime", "Prezime", "111", 0, 10);
    }

    @Test
    void editStatusReturnsOkAndDelegatesToClientService() {
        EmployeeController controller = new EmployeeController(employeeService, clientService);
        EditStatus dto = new EditStatus(Status.INACTIVE);
        when(clientService.editStatus(null, "111000100000000011", dto)).thenReturn("ok");

        ResponseEntity<String> response = controller.editStatus(null, "111000100000000011", dto);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("ok");
        verify(clientService).editStatus(null, "111000100000000011", dto);
    }

    @Test
    void updateCompanyReturnsOkAndDelegates() {
        EmployeeController controller = new EmployeeController(employeeService, clientService);
        UpdateCompanyDto dto = new UpdateCompanyDto("Firma", "1234", "Adresa", 3L);
        CompanyResponseDto expected = new CompanyResponseDto();
        when(employeeService.updateCompany(10L, dto)).thenReturn(expected);

        ResponseEntity<CompanyResponseDto> response = controller.updateCompany(null, 10L, dto);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expected);
        verify(employeeService).updateCompany(10L, dto);
    }

    private AccountDetailsResponseDto accountDetailsResponseDto() {
        CheckingAccount account = new CheckingAccount(AccountConcrete.STANDARDNI);
        account.setBrojRacuna("1110001000000000115");
        account.setNazivRacuna("Test");
        account.setVlasnik(1L);
        account.setCurrency(new Currency("Dinar", CurrencyCode.RSD, "RSD", Set.of("RS"), "desc", Status.ACTIVE));
        account.setStanje(new BigDecimal("100"));
        account.setRaspolozivoStanje(new BigDecimal("100"));
        return new AccountDetailsResponseDto(account);
    }
}


