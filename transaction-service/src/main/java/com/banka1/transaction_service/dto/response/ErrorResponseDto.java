package com.banka1.transaction_service.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * DTO representing an error response.
 * Contains details about the error that occurred.
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponseDto {

    /** Error code identifying the type of error. */
    private String errorCode;

    /** Short, human-readable error title. */
    private String errorTitle;

    /** Error message providing details about the error. */
    private String errorDesc;

    /** Timestamp when the error occurred. */
    private LocalDateTime timestamp = LocalDateTime.now();

    /** Additional information about the error (optional). */
    private Map<String, String> validationErrors;

    /**
     * Creates an error response for general or business errors without validation details.
     *
     * @param errorCode stable error code for client processing
     * @param errorTitle short error title
     * @param errorDesc detailed error description
     */
    public ErrorResponseDto(String errorCode, String errorTitle, String errorDesc) {
        this.errorCode = errorCode;
        this.errorTitle = errorTitle;
        this.errorDesc = errorDesc;
    }

    /**
     * Creates an error response for validation errors with a map of invalid fields.
     *
     * @param errorCode stable error code for client processing
     * @param errorTitle short error title
     * @param errorDesc detailed error description
     * @param validationErrors map of fields and validation messages
     */
    public ErrorResponseDto(String errorCode, String errorTitle, String errorDesc, Map<String, String> validationErrors) {
        this.errorCode = errorCode;
        this.errorTitle = errorTitle;
        this.errorDesc = errorDesc;
        this.validationErrors = validationErrors;
    }
}