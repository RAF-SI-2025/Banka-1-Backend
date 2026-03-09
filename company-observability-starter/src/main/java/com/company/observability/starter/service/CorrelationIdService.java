package com.company.observability.starter.service;

import com.company.observability.starter.domain.CorrelationContext;
import com.company.observability.starter.domain.UuidCorrelationIdGenerator;

// Servis zaduzen za razresavanje correlation id vrednosti
public class CorrelationIdService {
    private final UuidCorrelationIdGenerator uuidGenerator;
    public CorrelationIdService(UuidCorrelationIdGenerator uuidGenerator) {
        this.uuidGenerator = uuidGenerator;
    }

    public CorrelationContext resolve(String incomingCID){
        if(hasText(incomingCID)){
            return new CorrelationContext(incomingCID.trim(),false);
        }
        String generatedCID = uuidGenerator.generate();
        return new CorrelationContext(generatedCID,true);
    }

    private boolean hasText(String value){
        return value != null && !value.trim().isEmpty();
    }
}
