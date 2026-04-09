package com.banka1.order.controller;

import com.banka1.order.dto.AuthenticatedUser;
import com.banka1.order.dto.PortfolioSummaryResponse;
import com.banka1.order.dto.SetPublicQuantityRequestDto;
import com.banka1.order.service.PortfolioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioControllerTest {

    @Mock
    private PortfolioService portfolioService;

    private PortfolioController controller;

    @BeforeEach
    void setUp() {
        controller = new PortfolioController(portfolioService);
    }

    @Test
    void getPortfolio_usesAuthenticatedPrincipalInsteadOfRequestUserId() {
        PortfolioSummaryResponse summary = new PortfolioSummaryResponse();
        when(portfolioService.getPortfolio(org.mockito.ArgumentMatchers.any())).thenReturn(summary);

        Jwt jwt = Jwt.withTokenValue("token")
                .subject("42")
                .claim("roles", List.of("CLIENT_TRADING"))
                .claim("permissions", List.of())
                .header("alg", "none")
                .build();

        ResponseEntity<PortfolioSummaryResponse> response = controller.getPortfolio(jwt);

        assertThat(response.getBody()).isSameAs(summary);
        ArgumentCaptor<AuthenticatedUser> captor = ArgumentCaptor.forClass(AuthenticatedUser.class);
        verify(portfolioService).getPortfolio(captor.capture());
        assertThat(captor.getValue().userId()).isEqualTo(42L);
    }

    @Test
    void setPublicAndExercise_delegateAuthenticatedUser() {
        Jwt jwt = Jwt.withTokenValue("token")
                .subject("7")
                .claim("roles", List.of("AGENT"))
                .claim("permissions", List.of())
                .header("alg", "none")
                .build();

        SetPublicQuantityRequestDto request = new SetPublicQuantityRequestDto();
        request.setPublicQuantity(3);

        controller.setPublicQuantity(jwt, 11L, request);
        controller.exerciseOption(jwt, 22L);

        ArgumentCaptor<AuthenticatedUser> userCaptor = ArgumentCaptor.forClass(AuthenticatedUser.class);
        verify(portfolioService).setPublicQuantity(userCaptor.capture(), org.mockito.ArgumentMatchers.eq(11L), org.mockito.ArgumentMatchers.same(request));
        assertThat(userCaptor.getValue().userId()).isEqualTo(7L);

        ArgumentCaptor<AuthenticatedUser> exerciseCaptor = ArgumentCaptor.forClass(AuthenticatedUser.class);
        verify(portfolioService).exerciseOption(exerciseCaptor.capture(), org.mockito.ArgumentMatchers.eq(22L));
        assertThat(exerciseCaptor.getValue().userId()).isEqualTo(7L);
    }

    @Test
    void portfolioEndpointsHaveExpectedMappingsAndSecurity() throws Exception {
        Method getPortfolio = PortfolioController.class.getDeclaredMethod("getPortfolio", Jwt.class);
        assertThat(getPortfolio.getAnnotation(GetMapping.class)).isNotNull();
        assertThat(getPortfolio.getAnnotation(PreAuthorize.class).value()).isEqualTo("hasAnyRole('CLIENT_BASIC','CLIENT_TRADING','AGENT','SUPERVISOR')");

        Method setPublic = PortfolioController.class.getDeclaredMethod("setPublicQuantity", Jwt.class, Long.class, SetPublicQuantityRequestDto.class);
        PutMapping setPublicMapping = setPublic.getAnnotation(PutMapping.class);
        assertThat(setPublicMapping).isNotNull();
        assertThat(setPublicMapping.value()).containsExactly("/{id}/set-public");
        assertThat(setPublic.getAnnotation(PreAuthorize.class).value()).isEqualTo("hasAnyRole('CLIENT_BASIC','CLIENT_TRADING','AGENT','SUPERVISOR')");

        Method exercise = PortfolioController.class.getDeclaredMethod("exerciseOption", Jwt.class, Long.class);
        PostMapping exerciseMapping = exercise.getAnnotation(PostMapping.class);
        assertThat(exerciseMapping).isNotNull();
        assertThat(exerciseMapping.value()).containsExactly("/{id}/exercise-option");
        assertThat(exercise.getAnnotation(PreAuthorize.class).value()).isEqualTo("hasAnyRole('AGENT','SUPERVISOR')");
    }
}
