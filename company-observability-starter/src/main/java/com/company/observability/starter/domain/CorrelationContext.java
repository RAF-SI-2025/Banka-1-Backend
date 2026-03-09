package com.company.observability.starter.domain;


// Predstavlja rezultat razresavanja correlation ID vrednosti
 //@param correlationId vrednost correlation ID-a
 //@param generated da li je ID generisan ili preuzet iz zaglavlja

public record CorrelationContext(String correlationId, boolean generated) {
    public CorrelationContext{
        if(correlationId == null || correlationId.isBlank()){
            throw new IllegalArgumentException("Correlation ID ne sme biti null ili prazan");
        }
    }
}
