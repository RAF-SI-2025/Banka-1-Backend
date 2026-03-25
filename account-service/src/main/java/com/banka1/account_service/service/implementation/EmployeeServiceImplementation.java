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
import com.banka1.account_service.dto.response.ClientInfoResponseDto;
import com.banka1.account_service.dto.response.ClientResponseDto;
import com.banka1.account_service.rabbitMQ.EmailDto;
import com.banka1.account_service.rabbitMQ.EmailType;
import com.banka1.account_service.rabbitMQ.RabbitClient;
import com.banka1.account_service.repository.AccountRepository;
import com.banka1.account_service.repository.CompanyRepository;
import com.banka1.account_service.repository.CurrencyRepository;
import com.banka1.account_service.repository.SifraDelatnostiRepository;
import com.banka1.account_service.rest_client.ClientService;
import com.banka1.account_service.service.EmployeeService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;


//@RequiredArgsConstructor
@Service
public class EmployeeServiceImplementation implements EmployeeService {
    private final Random random;
    private final ClientService clientService;
    private final AccountRepository accountRepository;
    @Value("${banka.security.id}")
    private String appPropertiesId;
    private final CurrencyRepository currencyRepository;
    private final SifraDelatnostiRepository sifraDelatnostiRepository;
    private final CompanyRepository companyRepository;
    private final RabbitClient rabbitClient;

    public EmployeeServiceImplementation(@Value("${my.random.seed}") Long seed, ClientService clientService, CurrencyRepository currencyRepository, SifraDelatnostiRepository sifraDelatnostiRepository, CompanyRepository companyRepository, AccountRepository accountRepository, RabbitClient rabbitClient)
    {
        this.clientService = clientService;
        this.rabbitClient = rabbitClient;
        this.random=new Random();
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
                .findBySifra(firmaDto.getSifraDelatnosti())
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

    //todo menjati gresku
    private ClientInfoResponseDto resolveClientId(Long id, String jmbg) {
        ClientInfoResponseDto clientInfoResponseDto;
        if (id != null)
            clientInfoResponseDto= clientService.getUser(id);
        else
            clientInfoResponseDto=clientService.getUser(jmbg);
        if(clientInfoResponseDto==null)
            throw new RuntimeException("Greska sa komunikacijom izmedju servisa");
        return clientInfoResponseDto;
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
                                 String name,
                                 String surname,
                                 Jwt jwt,
                                 Currency currency,
                                 Company company,
                                 BigDecimal balance) {

        account.setBrojRacuna(broj);
        account.setImeVlasnikaRacuna(name);
        account.setPrezimeVlasnikaRacuna(surname);
        account.setNazivRacuna(naziv);
        account.setVlasnik(vlasnikId);
        account.setZaposlen(((Number) jwt.getClaim(appPropertiesId)).longValue());
        account.setDatumIVremeKreiranja(LocalDateTime.now());
        account.setCurrency(currency);
        account.setCompany(company);
        account.setStanje(balance);
        account.setRaspolozivoStanje(balance);
    }

    //todo rabit mq
    @Transactional
    @Override
    public String createFxAccount(Jwt jwt, FxDto fxDto) {
        validateFxDto(fxDto);
        Currency currency = getCurrencyOrThrow(fxDto.getCurrencyCode());
        Company company = createCompanyIfNeeded(fxDto.getFirma());
        ClientInfoResponseDto clientInfoResponseDto = resolveClientId(fxDto.getIdVlasnika(), fxDto.getJmbg());
        String broj = generateAccountNumber(String.valueOf(fxDto.getTipRacuna().getVal()));
        Account account = new FxAccount(fxDto.getTipRacuna());
        populateAccount(account, broj, fxDto.getNazivRacuna(), clientInfoResponseDto.getId(), clientInfoResponseDto.getName(),clientInfoResponseDto.getLastName(),jwt, currency, company,fxDto.getInitialBalance());
        accountRepository.save(account);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                //todo gresku ostavljam namerno da me upozori da se vratim na ovo, mora i da se posalje AccountCreated event za card create
                rabbitClient.sendEmailNotification(new EmailDto(clientInfoResponseDto.getUsername(),clientInfoResponseDto.getEmail(), EmailType.ACCOUNT_CREATED));
            }
        });
        return "Uspesno kreiran fx account";
    }

    //todo rabit mq
    @Transactional
    @Override
    public String createCheckingAccount(Jwt jwt, CheckingDto checkingDto) {
        validateCheckingDto(checkingDto);
        Currency currency = getCurrencyOrThrow(CurrencyCode.RSD);
        Company company = createCompanyIfNeeded(checkingDto.getFirma());
        ClientInfoResponseDto clientInfoResponseDto = resolveClientId(checkingDto.getIdVlasnika(), checkingDto.getJmbg());
        String broj = generateAccountNumber(String.valueOf(checkingDto.getVrstaRacuna().getVal()));
        Account account = new CheckingAccount(checkingDto.getVrstaRacuna());
        populateAccount(account, broj, checkingDto.getNazivRacuna(), clientInfoResponseDto.getId(), clientInfoResponseDto.getName(),clientInfoResponseDto.getLastName(),jwt, currency, company,checkingDto.getInitialBalance());
        accountRepository.save(account);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                //todo gresku ostavljam namerno da me upozori da se vratim na ovo, mora i da se posalje AccountCreated event za card create
                rabbitClient.sendEmailNotification(new EmailDto(clientInfoResponseDto.getUsername(),clientInfoResponseDto.getEmail(), EmailType.ACCOUNT_CREATED));

            }
        });
        return "Uspesno kreiran checking account";
    }

    private String myTrim(String s)
    {
        if(s!=null)
            return s.trim();
        return s;
    }

    @Transactional
    public Page<AccountSearchResponseDto> searchAllAccounts(Jwt jwt,String ime,String prezime,String accountNumber,int page,int size)
    {
        return accountRepository.searchAccounts(myTrim(accountNumber),myTrim(ime),myTrim(prezime),PageRequest.of(page,size)).map(AccountSearchResponseDto::new);
    }

    //todo rabit mq
    @Transactional
    @Override
    public String updateCard(Jwt jwt, Long id, UpdateCardDto updateCardDto) {
//        Account account=accountRepository.findById(id).orElse(null);
//        if(account==null)
//            throw new IllegalArgumentException("Ne postoji racun sa tim id");
//        if(!account.getVlasnik().equals(((Number) jwt.getClaim(appPropertiesId)).longValue()))
//            throw new IllegalArgumentException("Nisi vlasnik racuna");
//        if(account.getStatus()==updateCardDto.getCardStatus())
//            throw new IllegalArgumentException("Kartica je vec "+account.getStatus().name());
//        if(account.getStatus()==CardStatus.DEACTIVATED && updateCardDto.getCardStatus()==CardStatus.BLOCKED
//            || account.getStatus()==CardStatus.BLOCKED && updateCardDto.getCardStatus()==CardStatus.ACTIVATED)
//            throw new IllegalArgumentException("Ne moze "+account.getStatus().name()+" u "+updateCardDto.getCardStatus());
//
//        account.setStatus(updateCardDto.getCardStatus());
        return "Uspesno updetovan status";
    }
}
