package com.banka1.userService.service.implementation;

import com.banka1.userService.domain.ConfirmationToken;
import com.banka1.userService.domain.Zaposlen;
import com.banka1.userService.domain.enums.Role;
import com.banka1.userService.dto.rabbitmq.EmailDto;
import com.banka1.userService.dto.rabbitmq.EmailType;
import com.banka1.userService.dto.requests.EmployeeCreateRequestDto;
import com.banka1.userService.dto.requests.EmployeeEditRequestDto;
import com.banka1.userService.dto.requests.EmployeeUpdateRequestDto;
import com.banka1.userService.dto.responses.EmployeeResponseDto;
import com.banka1.userService.exception.BusinessException;
import com.banka1.userService.exception.ErrorCode;
import com.banka1.userService.mappers.EmployeeMapper;
import com.banka1.userService.rabbitMQ.RabbitClient;
import com.banka1.userService.repository.ConfirmationTokenRepository;
import com.banka1.userService.repository.ZaposlenRepository;
import com.banka1.userService.security.JWTService;
import com.banka1.userService.service.CrudService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class CrudServiceImplementation implements CrudService {


    private final ZaposlenRepository zaposlenRepository;
    private final JWTService jwtService;
    private final ConfirmationTokenRepository confirmationTokenRepository;
    private final RabbitClient rabbitClient;
    private final EmployeeMapper employeeMapper;

    @Value("${banka.security.roles-claim}")
    private String role;

    @Value("${url.activate-account}")
    private String activateAccount;


    /**
     * Kreira novog zaposlenog i salje aktivacioni mejl nakon uspesnog commita transakcije.
     *
     * @param dto podaci za kreiranje zaposlenog
     * @return kreirani zaposleni mapiran u odgovor
     */
    @Override
    public EmployeeResponseDto createEmployee(EmployeeCreateRequestDto dto) {
        if (zaposlenRepository.existsByEmail(dto.getEmail()))
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS, "Email: " + dto.getEmail());

        if (zaposlenRepository.existsByUsername(dto.getUsername()))
            throw new BusinessException(ErrorCode.USERNAME_ALREADY_EXISTS, "Username: " + dto.getUsername());


        if(Period.between(dto.getDatumRodjenja(),LocalDate.now()).getYears()<18)
            throw new BusinessException(ErrorCode.USER_TOO_YOUNG,"Korisnik mora biti punoletan");


        // Mapiranje DTO u Entitet
        Zaposlen zaposlen = employeeMapper.toEntity(dto);

        Zaposlen savedEmployee = zaposlenRepository.save(zaposlen);

        String generated= jwtService.generateRandomToken();
        ConfirmationToken confirmationToken=new ConfirmationToken(jwtService.sha256Hex(generated),savedEmployee);
        confirmationTokenRepository.save(confirmationToken);
        savedEmployee.setConfirmationToken(confirmationToken);

        EmailDto emailDto = new EmailDto(
                zaposlen.getIme(),
                zaposlen.getEmail(),
                EmailType.ACTIVATION,
                activateAccount+generated);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                rabbitClient.sendEmailNotification(emailDto);
            }
        });

        return employeeMapper.toDto(savedEmployee);
    }

    /**
     * Pretrazuje zaposlene po pojedinacnim filterima uz paginaciju.
     *
     * @param ime filter po imenu
     * @param prezime filter po prezimenu
     * @param email filter po email adresi
     * @param departman filter po departmanu
     * @param pozicija filter po poziciji
     * @param pageable parametri paginacije
     * @return stranica zaposlenih mapirana u DTO objekte
     */
    @Override
    @Transactional(readOnly = true)
    public Page<EmployeeResponseDto> searchEmployees(
            String ime,
            String prezime,
            String email,
            String departman,
            String pozicija,
            Pageable pageable
    ) {

        // Ako je filter null, šaljemo prazan string koji se u bazi prevodi u LIKE '%%' (pronalazi sve)
        String safeIme = (ime != null) ? ime : "";
        String safePrezime = (prezime != null) ? prezime : "";
        String safeEmail = (email != null) ? email : "";
        String safePozicija = (pozicija != null) ? pozicija : "";
        String safeDepartman = (departman != null) ? departman : "";

        Page<Zaposlen> employeesPage = zaposlenRepository.searchEmployees(safeIme , safePrezime, safeEmail, safePozicija, safeDepartman, pageable);

        return employeesPage.map(employeeMapper::toDto);
    }

    /**
     * Azurira zaposlenog i proverava da li korisnik ima dovoljno jaku rolu za izmenu.
     *
     * @param jwt JWT korisnika koji vrsi izmenu
     * @param id identifikator zaposlenog
     * @param dto podaci za azuriranje
     * @return azurirani zaposleni
     */
    @Override
    public EmployeeResponseDto updateEmployee(Jwt jwt, Long id, EmployeeUpdateRequestDto dto) {
        Zaposlen zaposlen = zaposlenRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "ID: " + id));

        Role role1=Role.valueOf((String) jwt.getClaims().get(role));
        if(role1.getPower()<=zaposlen.getRole().getPower())
            throw new BusinessException(ErrorCode.NOT_STRONG_ROLE,"Slab si");

        // Prepustamo Mapperu da odradi spajanje novih podataka
        employeeMapper.updateEntityFromDto(zaposlen, dto,role1);

        Zaposlen updated = zaposlenRepository.save(zaposlen);

        Boolean aktivan = dto.getAktivan();

        if(aktivan != null && !aktivan) {
            EmailDto emailDto = new EmailDto(
                    zaposlen.getIme(),
                    zaposlen.getEmail(),
                    EmailType.ACCOUNT_DEACTIVATION
            );

            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                        rabbitClient.sendEmailNotification(emailDto);
                }
            });
        }


        return employeeMapper.toDto(updated);
    }

    /**
     * Menja podatke trenutno prijavljenog korisnika.
     *
     * @param jwt JWT prijavljenog korisnika
     * @param dto podaci za izmenu profila
     * @return azurirani korisnik
     */
    @Override
    public EmployeeResponseDto editEmployee(Jwt jwt, EmployeeEditRequestDto dto) {
        Long id=((Number)jwt.getClaim("id")).longValue();
        Zaposlen zaposlen=zaposlenRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "ID: " + id));
        employeeMapper.updateEntityFromDto(zaposlen, dto);
        return employeeMapper.toDto(zaposlenRepository.save(zaposlen));
    }

    /**
     * Soft-brise zaposlenog i salje notifikaciju o deaktivaciji naloga.
     *
     * @param id identifikator zaposlenog koji se brise
     */
    @Override
    public void deleteEmployee(Long id) {
        // Zahvaljujući @SQLRestriction("deleted = false"), findById će vratiti prazan Optional
        // ako je korisnik već soft-obrisan!
        Zaposlen zaposlen = zaposlenRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "ID: " + id));

        // Kada pozovemo delete, Hibernate presreće komandu i umesto DELETE FROM employees
        // izvršava naš UPDATE employees SET deleted = true WHERE id = ?
        zaposlenRepository.delete(zaposlen);

        EmailDto emailDto = new EmailDto(
                zaposlen.getIme(),
                zaposlen.getEmail(),
                EmailType.ACCOUNT_DEACTIVATION
        );

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                rabbitClient.sendEmailNotification(emailDto);
            }
        });
    }
    /**
     * Vrsi globalnu pretragu zaposlenih preko jedinstvenog tekstualnog upita.
     *
     * @param query tekstualni upit za pretragu
     * @param pageable parametri paginacije
     * @return stranica rezultata mapirana u DTO objekte
     */
    @Transactional(readOnly = true)
    public Page<EmployeeResponseDto> globalSearchEmployees(String query, Pageable pageable) {

        // Rešavanje PostgreSQL baga: ako je query null, stavljamo prazan string ("%%" pronalazi sve)
        String safeQuery = (query != null) ? query : "";

        // Repo poziv
        Page<Zaposlen> employeesPage = zaposlenRepository.globalSearchEmployees(safeQuery, pageable);

        // Zadržavamo paginaciju i mapiramo u DTO
        return employeesPage.map(employeeMapper::toDto);
    }
}
