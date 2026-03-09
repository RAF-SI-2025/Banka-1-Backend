package com.company.observability.starter.web;

import java.time.OffsetDateTime;

// Genericki odgovor koji klijent dobija kada se desi neocekivana greska
public record ErrorResponse(OffsetDateTime timestamp, int status, String error, String message, String correlationId, String path) {

}
