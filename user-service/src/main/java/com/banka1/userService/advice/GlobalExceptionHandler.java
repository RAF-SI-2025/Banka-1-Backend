package com.banka1.userService.advice;

import com.banka1.userService.dto.responses.ErrorResponseDto;
import com.banka1.userService.exception.BusinessException;
import com.banka1.userService.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@Slf4j // Lombok automatski ubacuje: private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
@RestControllerAdvice
public class GlobalExceptionHandler {




    @ExceptionHandler(AmqpException.class)
    public ResponseEntity<ErrorResponseDto> handleRabbitMqException(AmqpException ex) {
        // Logger + stacktrace
        log.error("Mejl nije poslat", ex);

        ErrorResponseDto error = new ErrorResponseDto(
                "ERR_INTERNAL_SERVER",
                "Serverska greška",
                "Mejl nije poslat. Naš tim je obavešten."
        );
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }


    // Neocekivane greske (500)
    /**
     * Obradjuje neocekivane izuzetke i vraca genericki odgovor za internu gresku.
     *
     * @param ex neocekivani izuzetak
     * @return HTTP 500 odgovor sa standardizovanim telom greske
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDto> handleUnexpectedException(Exception ex) {
        // Logger + stacktrace
        log.error("CRITICAL: Unexpected internal error occurred: ", ex);

        ErrorResponseDto error = new ErrorResponseDto(
                "ERR_INTERNAL_SERVER",
                "Serverska greška",
                "Došlo je do neočekivanog problema. Naš tim je obavešten."
        );
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // Očekivane greške - Unificirani hendler za raznu biznis logiku
    /**
     * Obradjuje poznate biznis izuzetke i mapira ih na odgovarajuci HTTP status.
     *
     * @param ex biznis izuzetak koji sadrzi domen-specifican kod greske
     * @return odgovor sa detaljima biznis greske
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponseDto> handleBusinessException(BusinessException ex) {
        ErrorCode errorCode = ex.getErrorCode(); // Izvlacimo instancu error koda koja sadrzi sve sto je bitno o gresci za frontend

        // Logujemo info, jer ovo nije pad sistema, već normalna biznis restrikcija
        log.info("Business exception occurred: {} - {}", errorCode.getCode(), ex.getMessage());

        ErrorResponseDto error = new ErrorResponseDto(
                errorCode.getCode(),
                errorCode.getTitle(),
                ex.getMessage() // Specifičan opis prosleđen iz servisa
        );
        return new ResponseEntity<>(error, errorCode.getHttpStatus());
    }

    // Očekivane greške - Validacija DTO objekata (400 Bad Request)
    /**
     * Obradjuje greske validacije DTO zahteva i vraca listu neispravnih polja.
     *
     * @param ex izuzetak nastao pri validaciji ulaznih podataka
     * @return HTTP 400 odgovor sa mapom validacionih gresaka
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDto> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> validationErrors = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors())
        {
            validationErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }

        // Na primer. Ovi literali mogu biti i referenca ka nekom prevodu ako zelimo da podrzavamo vise jezika u app
        // A ovaj "ERR_VALIDATION" moze biti enum koji cemo ili cuvati u scopeu ovog mikroservica, ili izdvojiti u globalnu listu kodova
        // u neki dependency koji svi korist kao sto je securitylib
        ErrorResponseDto error = new ErrorResponseDto(
                "ERR_VALIDATION",
                "Neispravni podaci",
                "Molimo Vas proverite unete podatke.",
                validationErrors
        );

        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }
}
