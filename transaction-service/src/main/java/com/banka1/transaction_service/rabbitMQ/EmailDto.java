package com.banka1.transaction_service.rabbitMQ;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO sent to the RabbitMQ email service.
 * Contains all necessary data for generating and sending email notifications.
 * Fields with {@code null} values are excluded from JSON serialization.
 */
@NoArgsConstructor
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EmailDto {

    /**
     * Email address of the notification recipient.
     */
    private String userEmail;

    /**
     * Name or username of the recipient (used in the email text).
     */
    private String username;

    /**
     * Type of email notification that determines the content and template of the email.
     */
    private EmailType emailType;

    /**
     * Creates a payload for an email intended to notify the user about a transaction.
     *
     * @param username the username or display name for the email
     * @param userEmail the email address of the recipient
     * @param emailType the type of notification (TRANSACTION_COMPLETED or TRANSACTION_DENIED)
     */
    public EmailDto(String username, String userEmail, EmailType emailType) {
        this.userEmail = userEmail;
        this.username = username;
        this.emailType = emailType;
    }
}
