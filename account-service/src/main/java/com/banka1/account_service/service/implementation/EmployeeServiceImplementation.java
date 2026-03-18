package com.banka1.account_service.service.implementation;

import com.banka1.account_service.domain.*;
import com.banka1.account_service.domain.Currency;
import com.banka1.account_service.domain.enums.AccountOwnershipType;
import com.banka1.account_service.domain.enums.CurrencyCode;
import com.banka1.account_service.domain.enums.Status;
import com.banka1.account_service.dto.request.CheckingDto;
import com.banka1.account_service.dto.request.FirmaDto;
import com.banka1.account_service.dto.request.FxDto;
import com.banka1.account_service.dto.request.UpdateCardDto;
import com.banka1.account_service.dto.response.AccountSearchResponseDto;
import com.banka1.account_service.dto.response.ClientResponseDto;
import com.banka1.account_service.repository.AccountRepository;
import com.banka1.account_service.repository.CompanyRepository;
import com.banka1.account_service.repository.CurrencyRepository;
import com.banka1.account_service.repository.SifraDelatnostiRepository;
import com.banka1.account_service.rest_client.ClientServiceClient;
import com.banka1.account_service.service.EmployeeService;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;


@Service
public class EmployeeServiceImplementation implements EmployeeService {
    private final Random random;
    private final ClientServiceClient clientServiceClient;
    private final AccountRepository accountRepository;
    @Value("${banka.security.id}")
    private String appPropertiesId;
    private final CurrencyRepository currencyRepository;
    private final SifraDelatnostiRepository sifraDelatnostiRepository;
    private final CompanyRepository companyRepository;

    public EmployeeServiceImplementation(@Value("${random.seed}") Long seed, ClientServiceClient clientServiceClient, CurrencyRepository currencyRepository, SifraDelatnostiRepository sifraDelatnostiRepository, CompanyRepository companyRepository, AccountRepository accountRepository)
    {
        this.clientServiceClient=clientServiceClient;
        this.random=new Random(seed);
        this.currencyRepository=currencyRepository;
        this.sifraDelatnostiRepository=sifraDelatnostiRepository;
        this.companyRepository=companyRepository;
        this.accountRepository = accountRepository;
    }

    private void validateFxDto(FxDto dto) {
        if (dto.getCurrencyCode() == CurrencyCode.RSD)
            throw new IllegalArgumentException("Ne moze RSD");
        validateOwner(dto.getIdVlasnika(), dto.getJmbg());
        if ((dto.getFirma() == null && dto.getTipRacuna() == AccountOwnershipType.BUSINESS)
                || (dto.getFirma() != null && dto.getTipRacuna() == AccountOwnershipType.PERSONAL))
            throw new IllegalArgumentException("Pogresan tip racuna");
    }

    private void validateCheckingDto(CheckingDto dto) {
        validateOwner(dto.getIdVlasnika(), dto.getJmbg());

        if ((dto.getFirma() == null && dto.getVrstaRacuna().getAccountOwnershipType() == AccountOwnershipType.BUSINESS)
                || (dto.getFirma() != null && dto.getVrstaRacuna().getAccountOwnershipType() == AccountOwnershipType.PERSONAL))
            throw new IllegalArgumentException("Pogresan tip racuna");
    }

    private void validateOwner(Long id, String jmbg) {
        if (id == null && (jmbg == null || jmbg.isBlank()))
            throw new IllegalArgumentException("Unesi id ili jmbg");
    }

    private Currency getCurrencyOrThrow(CurrencyCode code) {
        Currency currency = currencyRepository.findByOznaka(code).orElse(null);
        if (currency == null)
            throw new IllegalArgumentException("Nisu unete valute");
        if (currency.getStatus() == Status.INACTIVE)
            throw new IllegalArgumentException("Deaktivirana valuta");
        return currency;
    }
    private Company createCompanyIfNeeded(FirmaDto firmaDto) {
        if (firmaDto == null) return null;
        SifraDelatnosti sifra = sifraDelatnostiRepository
                .findByOznaka(firmaDto.getSifraDelatnosti())
                .orElse(null);
        if (sifra == null)
            throw new IllegalArgumentException("Nije uneta sifra delatnosti");
        Company company = new Company(
                firmaDto.getNaziv(),
                firmaDto.getMaticniBroj(),
                firmaDto.getPoreskiBroj(),
                sifra,
                firmaDto.getAdresa(),
                firmaDto.getVlasnik()
        );

        return companyRepository.save(company);
    }

    private Long resolveClientId(Long id, String jmbg) {
        if (id != null) return id;
        return clientServiceClient.getUser(jmbg).getId();
    }


    private int calculate(String s)
    {
        int sum=0;
        for(char x:s.toCharArray())
        {
            sum+=x-'0';
        }
        return (11-sum%11)%11;
    }

    private String generateAccountNumber(String typeVal) {
        StringBuilder sb = new StringBuilder();
        boolean exists=true;
        String val="";
        while (exists) {
            sb.setLength(0);
            for (int i = 0; i < 9; i++) {
                sb.append(random.nextInt(10));
            }
            val="111"+"0001" + sb + typeVal;
            int result=calculate(val);
            if(result==10)
                continue;
            val+=result;
            exists = accountRepository.existsByBrojRacuna(val);
        }
        return val;
    }

    private void populateAccount(Account account,
                                 String broj,
                                 String naziv,
                                 Long vlasnikId,
                                 Jwt jwt,
                                 Currency currency,
                                 Company company) {

        account.setBrojRacuna(broj);
        account.setNazivRacuna(naziv);
        account.setVlasnik(vlasnikId);
        account.setZaposlen(((Number) jwt.getClaim(appPropertiesId)).longValue());
        account.setDatumIVremeKreiranja(LocalDateTime.now());
        account.setCurrency(currency);
        account.setStatus(Status.ACTIVE);
        account.setCompany(company);
    }


    @Transactional
    @Override
    public String createFxAccount(Jwt jwt, FxDto fxDto) {
        validateFxDto(fxDto);
        Currency currency = getCurrencyOrThrow(fxDto.getCurrencyCode());
        Company company = createCompanyIfNeeded(fxDto.getFirma());
        Long id = resolveClientId(fxDto.getIdVlasnika(), fxDto.getJmbg());
        String broj = generateAccountNumber(String.valueOf(fxDto.getTipRacuna().getVal()));
        Account account = new FxAccount(fxDto.getTipRacuna());
        populateAccount(account, broj, fxDto.getNazivRacuna(), id, jwt, currency, company);
        accountRepository.save(account);
        return "Uspesno kreiran fx account";
    }

    @Transactional
    @Override
    public String createCheckingAccount(Jwt jwt, CheckingDto checkingDto) {
        validateCheckingDto(checkingDto);
        Currency currency = getCurrencyOrThrow(CurrencyCode.RSD);
        Company company = createCompanyIfNeeded(checkingDto.getFirma());
        Long id = resolveClientId(checkingDto.getIdVlasnika(), checkingDto.getJmbg());
        String broj = generateAccountNumber(String.valueOf(checkingDto.getVrstaRacuna().getVal()));
        Account account = new CheckingAccount(checkingDto.getVrstaRacuna());
        populateAccount(account, broj, checkingDto.getNazivRacuna(), id, jwt, currency, company);
        accountRepository.save(account);
        return "Uspesno kreiran checking account";
    }

    public Page<AccountSearchResponseDto> searchAllAccounts(
            Jwt jwt,
            String ime,
            String prezime,
            String accountNumber,
            int page,
            int size
    ) {
        Page<ClientResponseDto> clientPage =
                clientServiceClient.searchClients(ime, prezime, page, size);

        List<ClientResponseDto> clients = clientPage.getContent();

        if (clients.isEmpty()) {
            return Page.empty();
        }

        // mapiranje id -> client
        Map<Long, ClientResponseDto> clientMap =
                clients.stream().collect(Collectors.toMap(ClientResponseDto::getId, c -> c));

        Page<Account> accounts = accountRepository.searchAccounts(
                accountNumber,
                clients.stream().map(ClientResponseDto::getId).toList(),
                PageRequest.of(page, size)
        );

        List<AccountSearchResponseDto> dtos = accounts.getContent().stream()
                .map(acc -> {
                    ClientResponseDto client = clientMap.get(acc.getVlasnik());
                    return new AccountSearchResponseDto(acc, client);
                })
                .sorted(Comparator.comparing(AccountSearchResponseDto::getPrezime,
                        Comparator.nullsLast(String::compareTo)))
                .toList();

        return new PageImpl<>(dtos, accounts.getPageable(), accounts.getTotalElements());
    }

    @Override
    public String updateCard(Jwt jwt, Long id, UpdateCardDto updateCardDto) {
        return "";
    }
}
