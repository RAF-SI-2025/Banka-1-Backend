package com.banka1.account_service.advice;

import com.banka1.account_service.dto.response.ErrorResponseDto;
import com.banka1.account_service.exception.BusinessException;
import com.banka1.account_service.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.lang.reflect.Method;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerUnitTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleBusinessExceptionMapsStatusAndPayload() {
        BusinessException ex = new BusinessException(ErrorCode.VERIFICATION_FAILED, "Kod nije validan");

        ResponseEntity<ErrorResponseDto> response = handler.handleBusinessException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().getErrorCode()).isEqualTo(ErrorCode.VERIFICATION_FAILED.getCode());
        assertThat(response.getBody().getErrorTitle()).isEqualTo(ErrorCode.VERIFICATION_FAILED.getTitle());
        assertThat(response.getBody().getErrorDesc()).isEqualTo("Kod nije validan");
    }

    @Test
    void handleIllegalArgumentMapsBadRequest() {
        ResponseEntity<ErrorResponseDto> response = handler.handleIllegalArgument(new IllegalArgumentException("los zahtev"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getErrorCode()).isEqualTo("ERR_VALIDATION");
    }

    @Test
    void handleDataIntegrityViolationMapsConflict() {
        ResponseEntity<ErrorResponseDto> response = handler.handleDataIntegrityViolation(new DataIntegrityViolationException("duplicate"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getErrorCode()).isEqualTo("ERR_CONSTRAINT_VIOLATION");
    }

    @Test
    void handleNoSuchElementMapsNotFound() {
        ResponseEntity<ErrorResponseDto> response = handler.handleNoSuchElement(new NoSuchElementException("nema"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getErrorCode()).isEqualTo("ERR_NOT_FOUND");
        assertThat(response.getBody().getErrorDesc()).isEqualTo("nema");
    }

    @Test
    void handleRabbitMqExceptionMapsInternalServerError() {
        ResponseEntity<ErrorResponseDto> response = handler.handleRabbitMqException(new AmqpException("mq"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getErrorCode()).isEqualTo("ERR_INTERNAL_SERVER");
    }

    @Test
    void handleAccessDeniedMapsForbidden() {
        ResponseEntity<ErrorResponseDto> response = handler.handleAccessDenied(new AccessDeniedException("denied"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().getErrorCode()).isEqualTo("ERR_FORBIDDEN");
    }

    @Test
    void handleAuthenticationExceptionMapsUnauthorized() {
        ResponseEntity<ErrorResponseDto> response = handler.handleAuthenticationException(new BadCredentialsException("bad"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().getErrorCode()).isEqualTo("ERR_UNAUTHORIZED");
    }

    @Test
    void handleUnexpectedExceptionMapsInternalServerError() {
        ResponseEntity<ErrorResponseDto> response = handler.handleUnexpectedException(new RuntimeException("boom"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getErrorCode()).isEqualTo("ERR_INTERNAL_SERVER");
    }

    @Test
    void handleValidationBuildsValidationErrorsMap() throws Exception {
        DummyRequest target = new DummyRequest();
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(target, "dummyRequest");
        bindingResult.addError(new FieldError("dummyRequest", "fieldA", "fieldA missing"));
        bindingResult.addError(new FieldError("dummyRequest", "fieldB", "fieldB invalid"));

        Method method = DummyController.class.getDeclaredMethod("dummy", DummyRequest.class);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(new org.springframework.core.MethodParameter(method, 0), bindingResult);

        ResponseEntity<ErrorResponseDto> response = handler.handleValidation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getErrorCode()).isEqualTo("ERR_VALIDATION");
        assertThat(response.getBody().getValidationErrors()).containsEntry("fieldA", "fieldA missing");
        assertThat(response.getBody().getValidationErrors()).containsEntry("fieldB", "fieldB invalid");
    }

    private static class DummyController {
        @SuppressWarnings("unused")
        public void dummy(DummyRequest request) {
        }
    }

    private static class DummyRequest {
    }
}

