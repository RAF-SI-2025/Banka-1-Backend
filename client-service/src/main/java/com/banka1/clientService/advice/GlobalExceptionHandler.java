package com.banka1.clientService.advice;

import com.banka1.clientService.dto.responses.ErrorResponseDto;
import com.banka1.clientService.exception.BusinessException;
import com.banka1.clientService.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.amqp.AmqpException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Centralizovani hendler gresaka za sve REST kontrolere.
 * Mapira ocekivane i neocekivane izuzetke na standardizovane HTTP odgovore sa {@link ErrorResponseDto} telom.
 */
@Slf4j
@RestControllerAdvice(basePackages = "com.banka1.clientService.controller")
@Component("clientServiceGlobalExceptionHandler")
public class GlobalExceptionHandler {

    /**
     * Hendluje poznate biznis izuzetke i mapira ih na odgovarajuci HTTP status iz {@link ErrorCode}.
     *
     * @param ex biznis izuzetak koji sadrzi domen-specifican kod greske
     * @return odgovor sa detaljima biznis greske i HTTP statusom iz {@link ErrorCode}
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponseDto> handleBusinessException(BusinessException ex) {
        ErrorCode errorCode = ex.getErrorCode();
        ErrorResponseDto error = new ErrorResponseDto(
                errorCode.getCode(),
                errorCode.getTitle(),
                ex.getDetails()
        );
        return new ResponseEntity<>(error, errorCode.getHttpStatus());
    }

    /**
     * Hendluje krsenje unique ogranicenja iz baze.
     * Parsira naziv constraint-a kako bi vratio isti format odgovora kao BusinessException.
     * Jedini izvor istine za jedinstvenost email-a i JMBG-a je DB UNIQUE constraint.
     *
     * @param ex izuzetak integriteta podataka
     * @return odgovor sa kodom, naslovom i opisom greske
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponseDto> handleDataIntegrity(DataIntegrityViolationException ex) {
        ErrorCode errorCode = resolveConstraintErrorCode(ex);
        if (errorCode == null) {
            ErrorResponseDto error = new ErrorResponseDto(
                    "ERR_CONSTRAINT_VIOLATION",
                    "Podatak već postoji",
                    "Jedan od podataka je već u upotrebi."
            );
            return new ResponseEntity<>(error, HttpStatus.CONFLICT);
        }
        ErrorResponseDto error = new ErrorResponseDto(
                errorCode.getCode(),
                errorCode.getTitle(),
                errorCode.getTitle()
        );
        return new ResponseEntity<>(error, errorCode.getHttpStatus());
    }

    private ErrorCode resolveConstraintErrorCode(DataIntegrityViolationException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof ConstraintViolationException cve) {
            String name = cve.getConstraintName() != null ? cve.getConstraintName().toLowerCase() : "";
            if (name.contains("email")) return ErrorCode.EMAIL_ALREADY_EXISTS;
            if (name.contains("jmbg"))  return ErrorCode.JMBG_ALREADY_EXISTS;
        }
        // Fallback za slucaj da JDBC drajver ne wrappuje u ConstraintViolationException
        String msg = ex.getMessage() != null ? ex.getMessage().toLowerCase() : "";
        if (msg.contains("clients_email_key") || msg.contains("(email)")) return ErrorCode.EMAIL_ALREADY_EXISTS;
        if (msg.contains("clients_jmbg_key")  || msg.contains("(jmbg)"))  return ErrorCode.JMBG_ALREADY_EXISTS;
        return null;
    }

    /**
     * Hendluje greske pretrage elemenata koji ne postoje sa statusom 404 Not Found.
     *
     * @param ex izuzetak nepostojeceg elementa
     * @return odgovor sa porukom greske
     */
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ErrorResponseDto> handleNotFound(NoSuchElementException ex) {
        ErrorResponseDto error = new ErrorResponseDto(
                "ERR_NOT_FOUND",
                "Resurs nije pronađen",
                ex.getMessage()
        );
        return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
    }

    /**
     * Hendluje greske neispravnih argumenata koji ne prolaze programsku validaciju.
     *
     * @param ex izuzetak nastao pri detektovanju neispravnog argumenta
     * @return HTTP 400 Bad Request odgovor
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponseDto> handleIllegalArgument(IllegalArgumentException ex) {
        ErrorResponseDto error = new ErrorResponseDto(
                "ERR_VALIDATION",
                "Neispravni argumenti",
                ex.getMessage()
        );
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    /**
     * Hendluje greske komunikacije sa RabbitMQ brokerom.
     *
     * @param ex AMQP izuzetak nastao pri slanju poruke
     * @return HTTP 500 Internal Server Error odgovor
     */
    @ExceptionHandler(AmqpException.class)
    public ResponseEntity<ErrorResponseDto> handleRabbitMqException(AmqpException ex) {
        ErrorResponseDto error = new ErrorResponseDto(
                "ERR_INTERNAL_SERVER",
                "Serverska greška",
                "Mejl nije poslat. Naš tim je obavešten."
        );
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * Hendluje greske validacije Bean Validation sa statusom 400 Bad Request.
     * Vraca mapu polja i njihovih gresaka.
     *
     * @param ex izuzetak validacije
     * @return odgovor sa mapom gresaka po polju
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDto> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        ErrorResponseDto error = new ErrorResponseDto(
                "ERR_VALIDATION",
                "Neispravni podaci",
                "Molimo Vas proverite unete podatke.",
                fieldErrors
        );
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    /**
     * Hendluje greske nedozvoljenog pristupa sa statusom 403 Forbidden.
     * Sprečava da @PreAuthorize odbijanja budu uhvacena generickim 500 handlerom.
     * <p>
     * Spring Security 5: {@code @Secured} / {@code @PreAuthorize} -> {@link AccessDeniedException}.
     * Spring Security 6: {@code @PreAuthorize} -> {@link AuthorizationDeniedException}
     * (potklasa {@link AccessDeniedException}).
     *
     * @param ex izuzetak nedozvoljenog pristupa
     * @return HTTP 403 Forbidden odgovor
     */
    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    public ResponseEntity<ErrorResponseDto> handleAccessDenied(AccessDeniedException ex) {
        ErrorResponseDto error = new ErrorResponseDto(
                "ERR_FORBIDDEN",
                "Pristup odbijen",
                "Nemate dozvolu za ovu akciju."
        );
        return new ResponseEntity<>(error, HttpStatus.FORBIDDEN);
    }

    /**
     * Hendluje neocekivane greske sa statusom 500 Internal Server Error.
     *
     * @param ex neocekivani izuzetak
     * @return odgovor sa standardizovanim telom greske
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDto> handleUnexpectedException(Exception ex) {
        log.error("Unexpected exception", ex);
        ErrorResponseDto error = new ErrorResponseDto(
                "ERR_INTERNAL_SERVER",
                "Serverska greška",
                "Došlo je do neočekivanog problema. Naš tim je obavešten."
        );
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
