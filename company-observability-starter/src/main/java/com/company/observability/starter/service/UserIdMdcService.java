package com.company.observability.starter.service;

import com.company.observability.starter.config.ObservabilityProperties;
import com.company.observability.starter.domain.UserIdExtractor;
import org.slf4j.MDC;

// Servis klasa za upisivanje i brisanje userid iz MDC
public class UserIdMdcService {
    public static final String MDC_KEY = "userId";
    private final UserIdExtractor userIdExtractor;
    private final ObservabilityProperties observabilityProperties;
    public UserIdMdcService(UserIdExtractor userIdExtractor, ObservabilityProperties observabilityProperties) {
        this.userIdExtractor = userIdExtractor;
        this.observabilityProperties = observabilityProperties;
    }

    public void putUserIdIfPresent(){
        if(!observabilityProperties.isUserIdMdcEnabled()){
            return;
        }
        userIdExtractor.extractUserId().ifPresent(userId -> MDC.put(MDC_KEY,userId));
    }

    public void clear(){
        if(observabilityProperties.isUserIdMdcEnabled()){
            MDC.remove(MDC_KEY);
        }
    }
}
