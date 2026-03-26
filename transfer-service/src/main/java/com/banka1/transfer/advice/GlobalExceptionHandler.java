package com.banka1.transfer.advice;

import com.banka1.transfer.dto.responses.ErrorResponseDto;
import com.banka1.transfer.exception.BusinessException;
import com.banka1.transfer.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Globalni presretač izuzetaka za Transfer mikroservis.
 * Centralizuje obradu grešaka i mapira specifične izuzetke u standardizovane {@link ErrorResponseDto} odgovore.
 */
@RestControllerAdvice
@Slf4j
@Component("transferServiceGlobalExceptionHandler")
public class GlobalExceptionHandler {

    /**
     * Obrađuje narušavanja integriteta podataka u bazi (npr. dupliranje jedinstvenog orderNumber-a).
     * @param ex izuzetak narušavanja integriteta
     * @return 409 Conflict sa detaljima o konfliktu
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponseDto> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        ErrorResponseDto error = new ErrorResponseDto(
                "ERR_CONSTRAINT_VIOLATION",
                "Konflikt podataka",
                "Došlo je do konflikta. Order broj možda već postoji."
        );
        return new ResponseEntity<>(error, HttpStatus.CONFLICT);
    }

    /**
     * Obrađuje situacije kada traženi resurs nije pronađen u sistemu.
     * @param ex izuzetak koji označava nedostatak elementa
     * @return 404 Not Found sa opisom greške
     */
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ErrorResponseDto> handleNoSuchElement(NoSuchElementException ex) {
        ErrorResponseDto error = new ErrorResponseDto(
                "ERR_NOT_FOUND",
                "Resurs nije pronađen",
                ex.getMessage()
        );
        return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
    }

    /**
     * Obrađuje neispravne argumente prosleđene metodama.
     * @param ex izuzetak neispravnog argumenta
     * @return 400 Bad Request
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
     * Obrađuje greške pri komunikaciji sa RabbitMQ sistemom.
     * @param ex AMQP specifičan izuzetak
     * @return 500 Internal Server Error sa porukom o nedostupnosti servisa notifikacija
     */
    @ExceptionHandler(AmqpException.class)
    public ResponseEntity<ErrorResponseDto> handleRabbitMqException(AmqpException ex) {
        ErrorResponseDto error = new ErrorResponseDto(
                "ERR_INTERNAL_SERVER",
                "Greška u komunikaciji",
                "Sistem za notifikacije trenutno nije dostupan."
        );
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * Obrađuje specifične biznis izuzetke definisane u domenu transfera.
     * Mapira {@link ErrorCode} u odgovarajući HTTP status i poruku.
     * @param ex prilagođeni biznis izuzetak
     * @return Dinamički HTTP status definisan u ErrorCode-u
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponseDto> handleBusinessException(BusinessException ex) {
        log.warn("Business rule violation: {}", ex.getMessage());
        ErrorCode errorCode = ex.getErrorCode();
        ErrorResponseDto error = new ErrorResponseDto(
                errorCode.getCode(),
                errorCode.getTitle(),
                ex.getMessage()
        );
        return new ResponseEntity<>(error, errorCode.getHttpStatus());
    }

    /**
     * Obrađuje neuspele validacije Bean Validation anotacija (npr. @NotBlank, @NotNull).
     * Prikuplja sve greške po poljima i pakuje ih u mapu.
     * @param ex izuzetak validacije argumenata metode
     * @return 400 Bad Request sa mapom validacionih grešaka
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDto> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> validationErrors = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            validationErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        ErrorResponseDto error = new ErrorResponseDto(
                "ERR_VALIDATION",
                "Neispravni podaci",
                "Molimo Vas proverite unete podatke.",
                validationErrors
        );
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    /**
     * Hvata sve ostale neočekivane izuzetke koji nisu specifično obrađeni.
     * @param ex bilo koji neobrađeni izuzetak
     * @return 500 Internal Server Error sa generičkom porukom o grešci
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDto> handleUnexpectedException(Exception ex) {
        log.error("CRITICAL: Neočekivana greška na serveru!", ex);
        ErrorResponseDto error = new ErrorResponseDto(
                "ERR_INTERNAL_SERVER",
                "Serverska greška",
                "Naš tim je obavešten."
        );
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}