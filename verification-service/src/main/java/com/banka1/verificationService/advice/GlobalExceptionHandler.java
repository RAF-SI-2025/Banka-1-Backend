package com.banka1.verificationService.advice;

import com.banka1.verificationService.dto.response.ErrorResponseDto;
import com.banka1.verificationService.exception.BusinessException;
import org.springframework.amqp.AmqpException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * Centralizovani handler grešaka za sve REST kontrolere verification servisa.
 * Mapira očekivane i neočekivane izuzetke na standardizovane HTTP odgovore sa {@link ErrorResponseDto} telom.
 */
@RestControllerAdvice
@Component("verificationServiceGlobalExceptionHandler")
public class GlobalExceptionHandler {

    /**
     * Obradjuje poslovne greške aplikacije.
     *
     * @param ex izuzetak nastao pri poslovnoj grešci
     * @return HTTP odgovor na osnovu ErrorCode-a
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponseDto> handleBusinessException(BusinessException ex) {
        ErrorResponseDto error = new ErrorResponseDto(
                ex.getErrorCode().getCode(),
                ex.getErrorCode().getTitle(),
                ex.getDetails()
        );
        return new ResponseEntity<>(error, ex.getErrorCode().getHttpStatus());
    }

    /**
     * Obradjuje greske narusavanja ogranicenja baze podataka (npr. duplikat unique kolone).
     *
     * @param ex izuzetak nastao pri krsenju integrity ogranicenja
     * @return HTTP 409 Conflict odgovor
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponseDto> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        ErrorResponseDto error = new ErrorResponseDto(
                "ERR_CONSTRAINT_VIOLATION",
                "Podatak već postoji",
                "Jedan od podataka je već u upotrebi."
        );
        return new ResponseEntity<>(error, HttpStatus.CONFLICT);
    }

    /**
     * Obradjuje greske neispravnih argumenata koji ne prolaze programsku validaciju.
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
     * Obradjuje greske validacije DTO zahteva i vraca listu neispravnih polja.
     *
     * @param ex izuzetak nastao pri validaciji ulaznih podataka
     * @return HTTP 400 odgovor sa mapom validacionih gresaka po poljima
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
     * Obradjuje greske komunikacije sa RabbitMQ brokerom.
     *
     * @param ex AMQP izuzetak nastao pri slanju poruke
     * @return HTTP 500 Internal Server Error odgovor
     */
    @ExceptionHandler(AmqpException.class)
    public ResponseEntity<ErrorResponseDto> handleAmqpException(AmqpException ex) {
        ErrorResponseDto error = new ErrorResponseDto(
                "ERR_INTERNAL_SERVER",
                "Serverska greška",
                "Poruka nije dostavljena. Naš tim je obavešten."
        );
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * Obradjuje greske nedozvoljenog pristupa.
     * Sprecava da @PreAuthorize odbijanja budu uhvacena generickim 500 handlerom.
     *
     * @param ex izuzetak nedozvoljenog pristupa
     * @return HTTP 403 Forbidden odgovor
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponseDto> handleAccessDenied(AccessDeniedException ex) {
        ErrorResponseDto error = new ErrorResponseDto(
                "ERR_FORBIDDEN",
                "Pristup odbijen",
                "Nemate dozvolu za ovu akciju."
        );
        return new ResponseEntity<>(error, HttpStatus.FORBIDDEN);
    }

    /**
     * Obradjuje neocekivane izuzetke i vraca genericki odgovor za internu gresku.
     *
     * @param ex neocekivani izuzetak
     * @return HTTP 500 odgovor sa standardizovanim telom greske
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDto> handleUnexpectedException(Exception ex) {
        ErrorResponseDto error = new ErrorResponseDto(
                "ERR_INTERNAL_SERVER",
                "Serverska greška",
                "Došlo je do neočekivanog problema. Naš tim je obavešten."
        );
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
