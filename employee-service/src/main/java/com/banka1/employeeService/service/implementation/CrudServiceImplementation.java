package com.banka1.employeeService.service.implementation;

import com.banka1.employeeService.domain.ConfirmationToken;
import com.banka1.employeeService.domain.Zaposlen;
import com.banka1.employeeService.domain.enums.Role;
import com.banka1.employeeService.dto.rabbitmq.EmailDto;
import com.banka1.employeeService.dto.rabbitmq.EmailType;
import com.banka1.employeeService.dto.requests.EmployeeCreateRequestDto;
import com.banka1.employeeService.dto.requests.EmployeeEditRequestDto;
import com.banka1.employeeService.dto.requests.EmployeeUpdateRequestDto;
import com.banka1.employeeService.dto.responses.EmployeeResponseDto;
import com.banka1.employeeService.exception.BusinessException;
import com.banka1.employeeService.exception.ErrorCode;
import com.banka1.employeeService.mappers.EmployeeMapper;
import com.banka1.employeeService.rabbitMQ.RabbitClient;
import com.banka1.employeeService.repository.ConfirmationTokenRepository;
import com.banka1.employeeService.repository.ZaposlenRepository;
import com.banka1.employeeService.security.JWTService;
import com.banka1.employeeService.service.CrudService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.time.Period;

/**
 * Implementacija {@link CrudService} koja upravlja CRUD operacijama nad entitetom zaposlenog.
 * Kreiranje zaposlenog generise aktivacioni token i salje email asinhorno putem RabbitMQ-a.
 * Sve pretrage koriste LIKE escapovanje radi zastite od SQL injection putem metakaraktera.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CrudServiceImplementation implements CrudService {

    /**
     * Repozitorijum za pristup entitetima zaposlenih.
     */
    private final ZaposlenRepository zaposlenRepository;

    /**
     * Servis za generisanje jednokratnih tokena.
     */
    private final JWTService jwtService;

    /**
     * Repozitorijum za pristup confirmation tokenima.
     */
    private final ConfirmationTokenRepository confirmationTokenRepository;

    /**
     * Klijent za slanje email notifikacija putem RabbitMQ-a.
     */
    private final RabbitClient rabbitClient;

    /**
     * Mapper za konverziju izmedju DTO i JPA entiteta zaposlenog.
     */
    private final EmployeeMapper employeeMapper;

    /**
     * Naziv JWT claim-a koji nosi naziv uloge korisnika.
     */
    @Value("${banka.security.roles-claim}")
    private String role;

    /**
     * Bazni URL za aktivaciju naloga (token se dodaje kao query parametar).
     */
    @Value("${url.activate-account}")
    private String activateAccount;

    /**
     * Kreira novog zaposlenog i salje aktivacioni mejl nakon uspesnog commita transakcije.
     * Proverava jedinstvenost email-a i korisnickog imena, kao i punoletnost.
     *
     * @param dto podaci za kreiranje zaposlenog
     * @return kreirani zaposleni mapiran u odgovor
     * @throws BusinessException ako email ili korisnicko ime vec postoje, ili je korisnik maloletan
     */
    @Override
    public EmployeeResponseDto createEmployee(EmployeeCreateRequestDto dto) {
        if (zaposlenRepository.existsByEmail(dto.getEmail()))
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS, "Email: " + dto.getEmail());

        if (zaposlenRepository.existsByUsername(dto.getUsername()))
            throw new BusinessException(ErrorCode.USERNAME_ALREADY_EXISTS, "Username: " + dto.getUsername());

        if (Period.between(dto.getDatumRodjenja(), LocalDate.now()).getYears() < 18)
            throw new BusinessException(ErrorCode.USER_TOO_YOUNG, "Korisnik mora biti punoletan");

        Zaposlen zaposlen = employeeMapper.toEntity(dto);
        Zaposlen savedEmployee = zaposlenRepository.save(zaposlen);

        String generated = jwtService.generateRandomToken();
        ConfirmationToken confirmationToken = new ConfirmationToken(jwtService.sha256Hex(generated), savedEmployee);
        confirmationTokenRepository.save(confirmationToken);
        savedEmployee.setConfirmationToken(confirmationToken);

        EmailDto emailDto = new EmailDto(
                zaposlen.getIme(),
                zaposlen.getEmail(),
                EmailType.EMPLOYEE_CREATED,
                activateAccount + generated);

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
     * Vrednost {@code null} se tretira kao wildcard (prazan string); LIKE metakarakteri se eskejpuju.
     *
     * @param ime       filter po imenu
     * @param prezime   filter po prezimenu
     * @param email     filter po email adresi
     * @param departman filter po departmanu
     * @param pozicija  filter po poziciji
     * @param pageable  parametri paginacije
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
        String safeIme = (ime != null) ? escapeLike(ime) : "";
        String safePrezime = (prezime != null) ? escapeLike(prezime) : "";
        String safeEmail = (email != null) ? escapeLike(email) : "";
        String safePozicija = (pozicija != null) ? escapeLike(pozicija) : "";
        String safeDepartman = (departman != null) ? escapeLike(departman) : "";

        Page<Zaposlen> employeesPage = zaposlenRepository.searchEmployees(safeIme, safePrezime, safeEmail, safePozicija, safeDepartman, pageable);
        return employeesPage.map(employeeMapper::toDto);
    }

    /**
     * Azurira zaposlenog i proverava da li korisnik ima dovoljno jaku ulogu za izmenu.
     * Ako se nalog deaktivira, salje notifikacioni email asinhorno putem RabbitMQ-a.
     *
     * @param jwt JWT korisnika koji vrsi izmenu
     * @param id  identifikator zaposlenog
     * @param dto podaci za azuriranje
     * @return azurirani zaposleni
     * @throws BusinessException ako zaposleni nije nadjen ili pozivalac nema dovoljno jaku ulogu
     */
    @Override
    public EmployeeResponseDto updateEmployee(Jwt jwt, Long id, EmployeeUpdateRequestDto dto) {
        Zaposlen zaposlen = zaposlenRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "ID: " + id));

        Role role1 = Role.valueOf((String) jwt.getClaims().get(role));
        if (role1.getPower() <= zaposlen.getRole().getPower())
            throw new BusinessException(ErrorCode.NOT_STRONG_ROLE, "Slab si");

        employeeMapper.updateEntityFromDto(zaposlen, dto, role1);
        Zaposlen updated = zaposlenRepository.save(zaposlen);

        Boolean aktivan = dto.getAktivan();
        if (aktivan != null && !aktivan) {
            EmailDto emailDto = new EmailDto(
                    zaposlen.getIme(),
                    zaposlen.getEmail(),
                    EmailType.EMPLOYEE_ACCOUNT_DEACTIVATED
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
     * Identifikator korisnika se izvlaci iz JWT claim-a {@code id}.
     *
     * @param jwt JWT prijavljenog korisnika
     * @param dto podaci za izmenu profila
     * @return azurirani korisnik
     * @throws BusinessException ako JWT ne sadrzi claim {@code id} ili korisnik nije nadjen
     */
    @Override
    public EmployeeResponseDto editEmployee(Jwt jwt, EmployeeEditRequestDto dto) {
        Object idClaim = jwt.getClaim("id");
        if (idClaim == null) throw new BusinessException(ErrorCode.INVALID_TOKEN, "JWT ne sadrži id claim");
        Long id = ((Number) idClaim).longValue();
        Zaposlen zaposlen = zaposlenRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "ID: " + id));
        employeeMapper.updateEntityFromDto(zaposlen, dto);
        return employeeMapper.toDto(zaposlenRepository.save(zaposlen));
    }

    /**
     * Vraca jednog zaposlenog po identifikatoru.
     *
     * @param id identifikator zaposlenog
     * @return zaposleni
     * @throws BusinessException ako zaposleni nije nadjen
     */
    @Override
    @Transactional(readOnly = true)
    public EmployeeResponseDto getEmployee(Long id) {
        Zaposlen zaposlen = zaposlenRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "ID: " + id));
        return employeeMapper.toDto(zaposlen);
    }

    /**
     * Soft-brise zaposlenog i salje notifikaciju o deaktivaciji naloga.
     *
     * @param id identifikator zaposlenog koji se brise
     * @throws BusinessException ako zaposleni nije nadjen
     */
    @Override
    public void deleteEmployee(Long id, Jwt jwt) {
        Zaposlen zaposlen = zaposlenRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "ID: " + id));

        Role role1 = Role.valueOf((String) jwt.getClaims().get(role));
        if (role1.getPower() <= zaposlen.getRole().getPower())
            throw new BusinessException(ErrorCode.NOT_STRONG_ROLE, "Slab si");

        zaposlenRepository.delete(zaposlen);

        EmailDto emailDto = new EmailDto(
                zaposlen.getIme(),
                zaposlen.getEmail(),
                EmailType.EMPLOYEE_ACCOUNT_DEACTIVATED
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
     * Upit se poredi sa imenom, prezimenom, emailom, departmanom i pozicijom.
     *
     * @param query    tekstualni upit za pretragu
     * @param pageable parametri paginacije
     * @return stranica rezultata mapirana u DTO objekte
     */
    @Transactional(readOnly = true)
    public Page<EmployeeResponseDto> globalSearchEmployees(String query, Pageable pageable) {
        String safeQuery = (query != null) ? escapeLike(query) : "";
        Page<Zaposlen> employeesPage = zaposlenRepository.globalSearchEmployees(safeQuery, pageable);
        return employeesPage.map(employeeMapper::toDto);
    }

    /**
     * Eskejpuje SQL LIKE metakaraktere u pretrazi radi sprecavanja neocekivanih widcard podudaranja.
     * Zamenjuje {@code \} sa {@code \\}, {@code %} sa {@code \%} i {@code _} sa {@code \_}.
     *
     * @param s ulazni string koji se eskejpuje
     * @return eskejpovan string bezbedan za upotrebu u LIKE klauzuli
     */
    private String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
