package com.banka1.order.controller;

import com.banka1.order.dto.TaxTrackingRowResponse;
import com.banka1.order.service.TaxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
        when(taxService.getTaxTracking(eq("CLIENT"), eq("Pera"), eq("Peric"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(new TaxTrackingRowResponse("Pera", "Peric", "CLIENT", new BigDecimal("12.50")))));

        ResponseEntity<Page<TaxTrackingRowResponse>> response = controller.getTaxTracking("CLIENT", "Pera", "Peric", 0, 10);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getContent()).hasSize(1);
        verify(taxService).getTaxTracking(eq("CLIENT"), eq("Pera"), eq("Peric"), any(Pageable.class));
    }

    @Test
    void taxEndpointsHaveExpectedMappingsAndSecurity() throws Exception {
        Method collectTax = TaxController.class.getDeclaredMethod("collectTax");
        assertThat(collectTax.getAnnotation(PostMapping.class).value()).containsExactly("/tax/collect");
        assertThat(collectTax.getAnnotation(PreAuthorize.class).value()).isEqualTo("hasRole('SUPERVISOR')");

        Method runTaxCalculationInternal = TaxController.class.getDeclaredMethod("runTaxCalculationInternal");
        assertThat(runTaxCalculationInternal.getAnnotation(PostMapping.class).value()).containsExactly("/internal/tax/capital-gains/run");
        assertThat(runTaxCalculationInternal.getAnnotation(PreAuthorize.class).value()).isEqualTo("hasRole('SERVICE')");

        Method getAllDebts = TaxController.class.getDeclaredMethod("getAllDebts", int.class, int.class);
        assertThat(getAllDebts.getAnnotation(GetMapping.class).value()).containsExactly("/tax/capital-gains/debts");
        assertThat(getAllDebts.getAnnotation(PreAuthorize.class).value()).isEqualTo("hasRole('SUPERVISOR')");

        Method getTaxTracking = TaxController.class.getDeclaredMethod("getTaxTracking", String.class, String.class, String.class, int.class, int.class);
        assertThat(getTaxTracking.getAnnotation(GetMapping.class).value()).containsExactly("/tax/tracking");
        assertThat(getTaxTracking.getAnnotation(PreAuthorize.class).value()).isEqualTo("hasRole('SUPERVISOR')");
    }
}
