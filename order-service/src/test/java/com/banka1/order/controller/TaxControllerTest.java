package com.banka1.order.controller;

import com.banka1.order.dto.TaxTrackingRowResponse;
import com.banka1.order.service.TaxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaxControllerTest {

    @Mock
    private TaxService taxService;

    private TaxController controller;

    @BeforeEach
    void setUp() {
        controller = new TaxController(taxService);
    }

    @Test
    void runTaxEndpoints_delegateToTaxService() {
        ResponseEntity<Void> collectResponse = controller.collectTax();
        ResponseEntity<Void> internalResponse = controller.runTaxCalculationInternal();

        assertThat(collectResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(internalResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(taxService).collectMonthlyTaxManually();
        verify(taxService).collectMonthlyTax();
    }

    @Test
    void getTaxTracking_delegatesToService() {
        when(taxService.getTaxTracking("CLIENT", "Pera", "Peric"))
                .thenReturn(List.of(new TaxTrackingRowResponse("Pera", "Peric", "CLIENT", new BigDecimal("12.50"))));

        ResponseEntity<List<TaxTrackingRowResponse>> response = controller.getTaxTracking("CLIENT", "Pera", "Peric");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        verify(taxService).getTaxTracking("CLIENT", "Pera", "Peric");
    }

    @Test
    void taxEndpointsHaveExpectedMappingsAndSecurity() throws Exception {
        Method collectTax = TaxController.class.getDeclaredMethod("collectTax");
        assertThat(collectTax.getAnnotation(PostMapping.class).value()).containsExactly("/api/tax/collect");
        assertThat(collectTax.getAnnotation(PreAuthorize.class).value()).isEqualTo("hasRole('SUPERVISOR')");

        Method runTaxCalculationInternal = TaxController.class.getDeclaredMethod("runTaxCalculationInternal");
        assertThat(runTaxCalculationInternal.getAnnotation(PostMapping.class).value()).containsExactly("/internal/tax/capital-gains/run");
        assertThat(runTaxCalculationInternal.getAnnotation(PreAuthorize.class).value()).isEqualTo("hasRole('SERVICE')");

        Method getAllDebts = TaxController.class.getDeclaredMethod("getAllDebts");
        assertThat(getAllDebts.getAnnotation(GetMapping.class).value()).containsExactly("/api/tax/capital-gains/debts");
        assertThat(getAllDebts.getAnnotation(PreAuthorize.class).value()).isEqualTo("hasRole('SUPERVISOR')");

        Method getTaxTracking = TaxController.class.getDeclaredMethod("getTaxTracking", String.class, String.class, String.class);
        assertThat(getTaxTracking.getAnnotation(GetMapping.class).value()).containsExactly("/api/tax/tracking");
        assertThat(getTaxTracking.getAnnotation(PreAuthorize.class).value()).isEqualTo("hasRole('SUPERVISOR')");
    }
}
