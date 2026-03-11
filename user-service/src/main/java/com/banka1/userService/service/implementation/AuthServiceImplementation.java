package com.banka1.userService.service.implementation;

import com.banka1.userService.domain.ConfirmationToken;
import com.banka1.userService.domain.RefreshToken;
import com.banka1.userService.domain.Zaposlen;
import com.banka1.userService.dto.rabbitmq.EmailDto;
import com.banka1.userService.dto.rabbitmq.EmailType;
import com.banka1.userService.dto.requests.ActivateDto;
import com.banka1.userService.dto.requests.ForgotPasswordDto;
import com.banka1.userService.dto.requests.LoginRequestDto;
import com.banka1.userService.dto.requests.RefreshTokenRequestDto;
import com.banka1.userService.dto.responses.TokenResponseDto;
import com.banka1.userService.rabbitMQ.RabbitClient;
import com.banka1.userService.repository.ConfirmationTokenRepository;
import com.banka1.userService.repository.TokenRepository;
import com.banka1.userService.repository.ZaposlenRepository;
import com.banka1.userService.security.JWTService;
import com.banka1.userService.service.AuthService;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;


@Service
@RequiredArgsConstructor
public class AuthServiceImplementation implements AuthService {

    private final ZaposlenRepository zaposlenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JWTService jwtService;
    private final TokenRepository tokenRepository;
    private final ConfirmationTokenRepository confirmationTokenRepository;
    private final RabbitClient rabbitClient;
    @Value("${url.reset-password}")
    private String urlResetPassword;
    @Value("${token.refresh.expiration-time}")
    private Long refreshTokenExpiration;
    @Value("${token.confirmation.expiration-time}")
    private Long confirmationTokenExpiration;

    /**
     * Generise novi pristupni token i rotira refresh token.
     *
     * @param zaposlen korisnik kome se izdaju tokeni
     * @param refreshToken entitet refresh tokena koji se upisuje ili azurira
     * @return odgovor sa JWT i nehesiranim refresh tokenom
     */
    private TokenResponseDto generate(Zaposlen zaposlen,RefreshToken refreshToken)
    {
        refreshToken.setExpirationDateTime(LocalDateTime.now().plusMonths(refreshTokenExpiration));
        for(int i = 0; i < 3; i++)
        {
            String result=jwtService.generateRandomToken();
            refreshToken.setValue(jwtService.sha256Hex(result));
            try {
                tokenRepository.save(refreshToken);
                return new TokenResponseDto(jwtService.generateJwtToken(zaposlen),result,zaposlen.getRole(),zaposlen.getPermissionSet());
            }
            catch (Exception e)
            {
                //kada se dogodi kolizija tokena, pokusamo ponovo
            }
        }

        throw new RuntimeException("Greska sa generisanjem tokena");
    }

    /**
     * Validira korisnicke kredencijale i izdaje novi par tokena.
     *
     * @param loginDto podaci za prijavu
     * @return odgovor sa pristupnim i refresh tokenom
     */
    @Transactional
    @Override
    public TokenResponseDto login(LoginRequestDto loginDto) {
        Zaposlen zaposlen=zaposlenRepository.findByEmail(loginDto.getEmail()).orElse(null);
        if(zaposlen==null || !passwordEncoder.matches(loginDto.getPassword(),zaposlen.getPassword()))
            throw new RuntimeException("Greska pri loginovanju");
        if(!zaposlen.isAktivan())
            throw new RuntimeException("Korisnik nije aktivan");
        return generate(zaposlen,new RefreshToken(zaposlen));
    }


    /**
     * Validira refresh token i vrsi njegovu rotaciju.
     *
     * @param refreshToken zahtev sa nehesiranim refresh tokenom
     * @return odgovor sa novim tokenima
     */
    @Transactional
    @Override
    public TokenResponseDto refreshToken(RefreshTokenRequestDto refreshToken) {
        RefreshToken refreshTokenCur=tokenRepository.findByValue(jwtService.sha256Hex(refreshToken.getRefreshToken())).orElse(null);
        if(refreshTokenCur == null || refreshTokenCur.getExpirationDateTime().isBefore(LocalDateTime.now()))
            throw new RuntimeException("Pogresan token");
        if(refreshTokenCur.getZaposlen().isDeleted())
            throw new RuntimeException("Korisnik je obrisan");
        return generate(refreshTokenCur.getZaposlen(),refreshTokenCur);
    }


    /**
     * Proverava da li je potvrda za aktivaciju ili reset lozinke validna.
     *
     * @param confirmationToken token iz korisnickog linka
     * @return identifikator potvrde ako je token validan
     */
    @Transactional
    @Override
    public Long check(String confirmationToken) {
        if(confirmationToken==null || confirmationToken.isBlank() || confirmationToken.length()!=43)
            throw new RuntimeException("Pogresan token");
        ConfirmationToken confirmationTokenCur=confirmationTokenRepository.findByValue(jwtService.sha256Hex(confirmationToken)).orElse(null);
        if(confirmationTokenCur == null || confirmationTokenCur.getExpirationDateTime()!=null && confirmationTokenCur.getExpirationDateTime().isBefore(LocalDateTime.now()))
            throw new RuntimeException("Pogresan token");
        if(confirmationTokenCur.getZaposlen().isDeleted())
            throw new RuntimeException("Korisnik je obrisan");
        return confirmationTokenCur.getId();
    }

    /**
     * Menja lozinku korisnika, a po potrebi i aktivira nalog.
     *
     * @param activateDto podaci sa potvrdom i novom lozinkom
     * @param aktiviraj oznacava da li operacija aktivira nalog
     * @return poruka o uspesnom zavrsetku operacije
     */
    @Transactional
    @Override
    public String editPassword(ActivateDto activateDto,boolean aktiviraj)
    {
        ConfirmationToken confirmationTokenCur=confirmationTokenRepository.findById(activateDto.getId()).orElse(null);
        if(confirmationTokenCur == null || !confirmationTokenCur.getValue().equals(jwtService.sha256Hex(activateDto.getConfirmationToken())) || confirmationTokenCur.getExpirationDateTime()!=null && confirmationTokenCur.getExpirationDateTime().isBefore(LocalDateTime.now()))
            throw new RuntimeException("Pogresan token");
        Zaposlen zaposlen=confirmationTokenCur.getZaposlen();
        if(zaposlen.isDeleted())
            throw new RuntimeException("Ne moze se editovati obrisani korisnik");
        if(!(aktiviraj || zaposlen.isAktivan()))
            throw new RuntimeException("Korisnik nije aktivan");

        zaposlen.setPassword(passwordEncoder.encode(activateDto.getPassword()));
        zaposlen.setConfirmationToken(null);
        confirmationTokenRepository.delete(confirmationTokenCur);
        if(aktiviraj) {
            zaposlen.setAktivan(true);
            return "Uspesno aktiviranje usera";
        }
        return "Uspesna promena lozinke";


    }

    /**
     * Generise i salje token za reset lozinke na email korisnika.
     *
     * @param forgotPasswordDto zahtev sa email adresom korisnika
     * @return poruka o rezultatu operacije
     */
    @Transactional
    @Override
    public String forgotPassword(ForgotPasswordDto forgotPasswordDto) {
        Zaposlen zaposlen=zaposlenRepository.findByEmail(forgotPasswordDto.getEmail()).orElse(null);
        if(zaposlen == null)
            throw new RuntimeException("Ne postoji korisnik sa ovim emailom");
        if(!zaposlen.isAktivan())
            throw new RuntimeException("Korisnik nije aktivan");


        String generated= jwtService.generateRandomToken();
        //System.out.println(generated);
        if(zaposlen.getConfirmationToken()!=null) {
            zaposlen.getConfirmationToken().setValue(jwtService.sha256Hex(generated));
            zaposlen.getConfirmationToken().setExpirationDateTime(LocalDateTime.now().plusMinutes(confirmationTokenExpiration));
        }
        else {
            ConfirmationToken confirmationToken=new ConfirmationToken(jwtService.sha256Hex(generated),LocalDateTime.now().plusMinutes(15),zaposlen);
            zaposlen.setConfirmationToken(confirmationToken);
            confirmationTokenRepository.save(confirmationToken);
        }


        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                rabbitClient.sendEmailNotification(new EmailDto(zaposlen.getIme(),zaposlen.getEmail(), EmailType.PASSWORD_RESET,urlResetPassword+generated));
            }
        });
        return "Poslat mejl";
    }
}
