package com.banka1.order.controller;

import com.banka1.order.dto.ActuaryProfitResponse;
import com.banka1.order.service.BankProfitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BankProfitControllerTest {

    @Mock
    private BankProfitService bankProfitService;

    private BankProfitController controller;

    @BeforeEach
    void setUp() {
        controller = new BankProfitController(bankProfitService);
    }

    @Test
    void getActuaryProfits_delegatesToService() {
        List<ActuaryProfitResponse> profits = List.of(
                new ActuaryProfitResponse(1L, "Pera", "Peric", "AGENT", new BigDecimal("100.00"))
        );
        when(bankProfitService.getActuaryProfits()).thenReturn(profits);

        ResponseEntity<List<ActuaryProfitResponse>> response = controller.getActuaryProfits();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(profits);
        verify(bankProfitService).getActuaryProfits();
    }

    @Test
    void getActuaryProfits_hasExpectedMappingAndSecurity() throws Exception {
        RequestMapping requestMapping = BankProfitController.class.getAnnotation(RequestMapping.class);
        assertThat(requestMapping.value()).containsExactly("/bank-profit");

        Method method = BankProfitController.class.getDeclaredMethod("getActuaryProfits");
        assertThat(method.getAnnotation(GetMapping.class).value()).containsExactly("/actuaries");
        assertThat(method.getAnnotation(PreAuthorize.class).value()).isEqualTo("hasRole('SUPERVISOR')");
    }
}
