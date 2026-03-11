package com.banka1.userService.service;

import com.banka1.userService.dto.requests.ActivateDto;
import com.banka1.userService.dto.requests.ForgotPasswordDto;
import com.banka1.userService.dto.requests.LoginRequestDto;
import com.banka1.userService.dto.requests.RefreshTokenRequestDto;
import com.banka1.userService.dto.responses.TokenResponseDto;

public interface AuthService {
    /**
     * Autentifikuje korisnika i izdaje novi par tokena.
     *
     * @param loginDto podaci za prijavu
     * @return pristupni i refresh token
     */
    TokenResponseDto login(LoginRequestDto loginDto);

    /**
     * Rotira postojeci refresh token i izdaje novi par tokena.
     *
     * @param refreshToken zahtev sa postojecim refresh tokenom
     * @return novi pristupni i refresh token
     */
    TokenResponseDto refreshToken(RefreshTokenRequestDto refreshToken);

    /**
     * Proverava validnost aktivacionog ili reset tokena.
     *
     * @param confirmationToken token iz korisnickog linka
     * @return identifikator potvrde ako je token validan
     */
    Long check(String confirmationToken);

    /**
     * Aktivira nalog ili menja lozinku u zavisnosti od prosledjenog moda rada.
     *
     * @param activateDto podaci sa potvrdom i novom lozinkom
     * @param aktiviraj oznacava da li se radi o aktivaciji naloga
     * @return poruka o rezultatu operacije
     */
    String editPassword(ActivateDto activateDto,boolean aktiviraj);

    /**
     * Pokrece postupak zaboravljene lozinke za zadatu email adresu.
     *
     * @param forgotPasswordDto podaci potrebni za reset lozinke
     * @return poruka o rezultatu slanja reset linka
     */
    String forgotPassword(ForgotPasswordDto forgotPasswordDto);
}
