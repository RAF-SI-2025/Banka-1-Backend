package com.banka1.userService.controller;

import com.banka1.userService.dto.requests.ActivateDto;
import com.banka1.userService.dto.requests.ForgotPasswordDto;
import com.banka1.userService.dto.requests.LoginRequestDto;
import com.banka1.userService.dto.requests.RefreshTokenRequestDto;
import com.banka1.userService.dto.responses.TokenResponseDto;
import com.banka1.userService.service.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@AllArgsConstructor
public class AuthController {

    private AuthService authService;

    /**
     * Autentifikuje korisnika i vraca pristupni i refresh token.
     *
     * @param loginDto kredencijali za prijavu
     * @return odgovor sa generisanim tokenima
     */
    @PostMapping("/login")
    public ResponseEntity<TokenResponseDto> login(@RequestBody @Valid LoginRequestDto loginDto)
    {
        return new ResponseEntity<>(authService.login(loginDto), HttpStatus.OK);
    }
    /**
     * Rotira refresh token i vraca novi par tokena.
     *
     * @param refreshToken zahtev sa postojecim refresh tokenom
     * @return odgovor sa novim tokenima
     */
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponseDto> refresh(@RequestBody @Valid RefreshTokenRequestDto refreshToken)
    {
        return new ResponseEntity<>(authService.refreshToken(refreshToken),HttpStatus.OK);
    }
    /**
     * Proverava validnost aktivacionog tokena.
     *
     * @param confirmationToken token dobijen iz aktivacionog linka
     * @return identifikator potvrde ako je token validan
     */
    @GetMapping("/checkActivate")
    public ResponseEntity<Long> checkActivate(@RequestParam  String confirmationToken)
    {
        return new ResponseEntity<>(authService.check(confirmationToken),HttpStatus.OK);
    }
    /**
     * Aktivira nalog i postavlja novu lozinku za korisnika.
     *
     * @param activateDto podaci potrebni za aktivaciju naloga
     * @return poruka o rezultatu aktivacije
     */
    @PostMapping("/activate")
    public ResponseEntity<String> activate(@RequestBody @Valid ActivateDto activateDto)
    {
        return new ResponseEntity<>(authService.editPassword(activateDto,true),HttpStatus.OK);
    }
    /**
     * Pokrece proces resetovanja lozinke slanjem mejla sa linkom.
     *
     * @param forgotPasswordDto zahtev sa email adresom korisnika
     * @return poruka o rezultatu slanja mejla
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<String> forgotPassword(@RequestBody @Valid ForgotPasswordDto forgotPasswordDto)
    {
        return new ResponseEntity<>(authService.forgotPassword(forgotPasswordDto),HttpStatus.OK);
    }
    /**
     * Proverava validnost tokena za reset lozinke.
     *
     * @param confirmationToken token dobijen iz reset linka
     * @return identifikator potvrde ako je token validan
     */
    @GetMapping("/checkResetPassword")
    public ResponseEntity<Long> checkResetPassword(@RequestParam  String confirmationToken)
    {
        return new ResponseEntity<>(authService.check(confirmationToken),HttpStatus.OK);
    }
    /**
     * Menja lozinku korisnika na osnovu validnog reset tokena.
     *
     * @param activateDto podaci potrebni za promenu lozinke
     * @return poruka o rezultatu promene lozinke
     */
    @PostMapping("/resetPassword")
    public ResponseEntity<String> resetPassword(@RequestBody @Valid ActivateDto activateDto)
    {
        return new ResponseEntity<>(authService.editPassword(activateDto,false),HttpStatus.OK);
    }

}
