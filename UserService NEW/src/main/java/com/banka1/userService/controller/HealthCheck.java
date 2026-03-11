package com.banka1.userService.controller;


import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;

import org.springframework.web.bind.annotation.RestController;

//todo ovo se vrv brise posto imamo actuator
@RestController
@AllArgsConstructor
public class HealthCheck {
    /**
     * Vraca jednostavan health-check odgovor.
     *
     * @return odgovor koji potvrdjuje da je servis dostupan
     */
    @GetMapping("/healthCheck")
    public ResponseEntity<String> hc()
    {
        return new ResponseEntity<>("Alive", HttpStatus.OK);
    }
}
