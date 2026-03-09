package com.company.observability.starter.domain;

// Predstavlja podatke potrebne za logovanje Http zahteva
public record RequestLogContext(String HttpMethod, String uri, int status, long durationMs, String query, String authorization) {
    public RequestLogContext{
        if(HttpMethod == null || HttpMethod.isBlank()){
            throw new IllegalArgumentException("HTTP metoda ne sme biti null ili prazna");
        }
        if(uri == null || uri.isBlank()){
            throw new IllegalArgumentException("URI ne sme biti null ili prazan");
        }
        if(durationMs < 0){
            throw new IllegalArgumentException("Trajanje zahteva ne sme biti negativno");
        }
    }
}
