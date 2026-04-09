package com.banka1.credit_service.service.implementation;

import com.banka1.credit_service.domain.Installment;
import com.banka1.credit_service.domain.InterestRateStore;
import com.banka1.credit_service.domain.Loan;
import com.banka1.credit_service.domain.LoanRequest;
import com.banka1.credit_service.domain.enums.*;
import com.banka1.credit_service.dto.request.BankPaymentDto;
import com.banka1.credit_service.dto.request.LoanRequestDto;
import com.banka1.credit_service.dto.response.*;
import com.banka1.credit_service.repository.InstallmentRepository;
import com.banka1.credit_service.repository.LoanRepository;
import com.banka1.credit_service.repository.LoanRequestRepository;
import com.banka1.credit_service.rest_client.AccountService;
import com.banka1.credit_service.rest_client.ExchangeService;
import com.banka1.credit_service.service.LoanService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor

public class LoanServiceImplementation implements LoanService {


    private final AccountService accountService;
    private final ExchangeService exchangeService;
    private final LoanRequestRepository loanRequestRepository;
    private final InstallmentRepository installmentRepository;
    private final LoanRepository loanRepository;

    @Value("${banka.security.id}")
    private String appPropertiesId;
    @Value("${banka.security.roles-claim}")
    private String roles;

    private final double startRange=-1.5;
    private final double endRange=1.5;
    private Set<String> employeeRoles=new HashSet<>(Set.of("BASIC","AGENT","SUPERVISOR","ADMIN"));
    private BigDecimal referenceRate=BigDecimal.valueOf(ThreadLocalRandom.current().nextDouble(startRange, endRange)).setScale(4, RoundingMode.HALF_UP);


    BigDecimal[] iznosi={BigDecimal.valueOf(500_000), BigDecimal.valueOf(1_000_000), BigDecimal.valueOf(2_000_000), BigDecimal.valueOf(5_000_000), BigDecimal.valueOf(10_000_000), BigDecimal.valueOf(20_000_000)};

    private InterestRateStore interestRate(BigDecimal amount, CurrencyCode currencyCode,LoanType loanType, InterestType interestType,Status status)
    {

        if(currencyCode!=CurrencyCode.RSD)
        {
            ConversionResponseDto conversionResponseDto=exchangeService.calculate(currencyCode,CurrencyCode.RSD,amount);
            amount=conversionResponseDto.toAmount();
            if(amount==null)
                throw new RuntimeException("Greska sa exchange servisom");
        }

        int start=0;
        int end=iznosi.length-1;
        while(start<=end)
        {
            int mid=start + (end-start)/2;
            int result=amount.compareTo(iznosi[mid]);
            switch (result)
            {
                case 0 -> start=mid;
                case -1-> end=mid-1;
                case 1-> start=mid+1;
            }
            if(result==0)
                break;
        }
        BigDecimal number=BigDecimal.valueOf(1200);
        BigDecimal val=BigDecimal.valueOf(6.25).subtract(BigDecimal.valueOf(0.25).multiply(BigDecimal.valueOf(start))).add(loanType.getMarza());
        if(status==Status.OVERDUE)
            val=val.add(BigDecimal.valueOf(0.05));
        InterestRateStore interestRateStore=new InterestRateStore(val.divide(number,10, RoundingMode.HALF_UP));
        if(interestType==InterestType.VARIABLE) {
            interestRateStore.setEffectiveInterestRate(val.add(getReferenceRate()).divide(number,10, RoundingMode.HALF_UP));
        }
        else {
            interestRateStore.setEffectiveInterestRate(interestRateStore.getNominalInterestRate());
        }
        return interestRateStore;
    }

    @Transactional
    @Override
    public LoanRequestResponseDto request(Jwt jwt, LoanRequestDto loanRequestDto) {
        if(loanRequestDto.getLoanType()== LoanType.STAMBENI)
        {
                if(loanRequestDto.getRepaymentPeriod()>360 || loanRequestDto.getRepaymentPeriod() % 60 != 0)
                {
                    throw new IllegalArgumentException("Nevalidan repaymentPeriod, mora biti 60, 120, 180, 240, 300 ili 360");
                }
        }
        else
        {
            if(loanRequestDto.getRepaymentPeriod()>84 || loanRequestDto.getRepaymentPeriod() % 12 != 0)
            {
                throw new IllegalArgumentException("Nevalidan repaymentPeriod, mora biti 12, 24, 36, 48, 60, 72 ili 84");
            }
        }
        AccountDetailsResponseDto accountDetailsResponseDto=accountService.getDetails(loanRequestDto.getAccountNumber());
        if(accountDetailsResponseDto == null)
            throw new IllegalArgumentException("Ne postoji racun:"+loanRequestDto.getAccountNumber());
        if(accountDetailsResponseDto.getOwnerId()==null || !accountDetailsResponseDto.getOwnerId().equals(((Number) jwt.getClaim(appPropertiesId)).longValue()))
            throw new IllegalArgumentException("Nisi vlasnik racuna");
        if(accountDetailsResponseDto.getCurrency()!=loanRequestDto.getCurrency())
        {
            throw new IllegalArgumentException("Valuta racuna ne odgovara valuti kredita");
        }
        LoanRequest loanRequest=loanRequestRepository.save(new LoanRequest(loanRequestDto.getLoanType(),loanRequestDto.getInterestType(),loanRequestDto.getAmount(),loanRequestDto.getCurrency(),loanRequestDto.getPurpose(),loanRequestDto.getMonthlySalary(),loanRequestDto.getEmploymentStatus(),loanRequestDto.getCurrentEmploymentPeriod(),loanRequestDto.getRepaymentPeriod(),loanRequestDto.getContactPhone(),loanRequestDto.getAccountNumber(),loanRequestDto.getClientId(), Status.PENDING,accountDetailsResponseDto.getEmail(),accountDetailsResponseDto.getUsername()));
        return new LoanRequestResponseDto(loanRequest.getId(),loanRequest.getCreatedAt());
    }

    @Transactional
    @Override
    public String confirmation(Jwt jwt, Long id,Status status) {
        String req="ODBIJEN";
        if(loanRequestRepository.updateStatus(id,status)!=1)
        {
            LoanRequest loanRequest=loanRequestRepository.findById(id).orElse(null);
            if(loanRequest==null)
                throw new RuntimeException("Ne postoji loanRequest sa ovim id-em");
            throw new RuntimeException("Umesto PENDING status je: "+loanRequest.getStatus());
        }
        if(status==Status.APPROVED)
        {
            LoanRequest loanRequest=loanRequestRepository.findById(id).orElse(null);
            if(loanRequest==null)
                throw new IllegalStateException("Ako se ovo desi obavezno neka me neko kontaktira (Ognjen) posto ovo ne bi trebalo da je moguce");
            InterestRateStore interest=interestRate(loanRequest.getAmount(),loanRequest.getCurrency(),loanRequest.getLoanType(),loanRequest.getInterestType(),Status.ACTIVE);
            BigDecimal stepen=interest.getEffectiveInterestRate().add(BigDecimal.ONE).pow(loanRequest.getRepaymentPeriod());
            BigDecimal val=interest.getEffectiveInterestRate().multiply(stepen).divide(stepen.subtract(BigDecimal.ONE),10, RoundingMode.HALF_UP);
            BigDecimal monthlyRate=loanRequest.getAmount().multiply(val);
            accountService.transactionFromBank(new BankPaymentDto(null,loanRequest.getAccountNumber(),loanRequest.getAmount()));
            Loan loan=new Loan();
            loan.setLoanType(loanRequest.getLoanType());
            loan.setAccountNumber(loanRequest.getAccountNumber());
            loan.setAmount(loanRequest.getAmount());
            loan.setRepaymentPeriod(loanRequest.getRepaymentPeriod());
            loan.setNominalInterestRate(interest.getNominalInterestRate());
            loan.setEffectiveInterestRate(interest.getEffectiveInterestRate());
            loan.setInterestType(loanRequest.getInterestType());
            loan.setAgreementDate(LocalDate.now());
            loan.setMaturityDate(loan.getAgreementDate().plusMonths(loanRequest.getRepaymentPeriod()));
            loan.setInstallmentAmount(monthlyRate);
            loan.setNextInstallmentDate(loan.getAgreementDate().plusMonths(1));
            loan.setRemainingDebt(loanRequest.getAmount());
            loan.setCurrency(loanRequest.getCurrency());
            //todo ovo bi trebalo da radi ali proveri
            loan.setStatus(Status.ACTIVE);
            loan.setUserEmail(loanRequest.getUserEmail());
            loan.setUsername(loanRequest.getUsername());
            loanRepository.save(loan);
            installmentRepository.save(new Installment(loan,monthlyRate,interest.getEffectiveInterestRate(),loan.getCurrency(),loan.getNextInstallmentDate(),null, PaymentStatus.UNPAID));
            req="ODOBREN";
        };
        //TODO rabbit mq
        return req+" ZAHTEV";
    }


    @Scheduled(cron = "0 0 0 * * *", zone = "Europe/Belgrade")
    @Transactional
    public void cronForRates()
    {
        LocalDate today=LocalDate.now();
        List<Installment> list=installmentRepository.findInstallmentByExpectedDueDateLessThanEqualAndPaymentStatusNot(today,PaymentStatus.PAID);
        for(Installment x:list) {
            try {
                accountService.transactionFromBank(new BankPaymentDto(x.getLoan().getAccountNumber(),null, x.getInstallmentAmount()));
            }
            //todo ako ne radi staviti samo Exception umesto HttpClientErrorException
            catch (HttpClientErrorException e)
            {
                //todo rabbitmq
                if(x.getPaymentStatus()!=PaymentStatus.OVERDUE) {
                    if (x.getRetry() == 0) {
                        LocalDate datum = today.plusDays(3);
                        x.setExpectedDueDate(datum);
                        x.getLoan().setNextInstallmentDate(datum);
                        x.setRetry(1);
                    } else {
                        LocalDate datum = today.plusDays(1);
                        x.setExpectedDueDate(datum);
                        x.getLoan().setNextInstallmentDate(datum);
                        x.setPaymentStatus(PaymentStatus.OVERDUE);
                        x.getLoan().setStatus(Status.OVERDUE);
                    }
                }
                continue;
            }
            BigDecimal interestPart = x.getLoan().getRemainingDebt().multiply(x.getInterestRateAtPayment());
            BigDecimal principalPart = x.getInstallmentAmount().subtract(interestPart);
            x.setPaymentStatus(PaymentStatus.PAID);
            x.setActualDueDate(today);
            x.getLoan().setRemainingDebt(x.getLoan().getRemainingDebt().subtract(principalPart));
            x.getLoan().setInstallmentCount(x.getLoan().getInstallmentCount()+1);
            if(x.getLoan().getRemainingDebt().compareTo(BigDecimal.ZERO)>0 && x.getLoan().getInstallmentCount()<x.getLoan().getRepaymentPeriod()) {
                LocalDate datum = today.plusMonths(1);
                x.getLoan().setNextInstallmentDate(datum);
                InterestRateStore interest = interestRate(x.getLoan().getRemainingDebt(),x.getLoan().getCurrency(),x.getLoan().getLoanType(),x.getLoan().getInterestType(),x.getLoan().getStatus());
                int remaining = x.getLoan().getRepaymentPeriod() - x.getLoan().getInstallmentCount();
                BigDecimal rate = interest.getEffectiveInterestRate();
                BigDecimal stepen = rate.add(BigDecimal.ONE).pow(remaining);
                BigDecimal val = rate.multiply(stepen).divide(stepen.subtract(BigDecimal.ONE), 10, RoundingMode.HALF_UP);
                BigDecimal monthlyRate = x.getLoan().getRemainingDebt().multiply(val);
                installmentRepository.save(new Installment(x.getLoan(),monthlyRate,rate,x.getLoan().getCurrency(), x.getLoan().getNextInstallmentDate(), null, PaymentStatus.UNPAID));
            }
        }

    }


    @Scheduled(cron = "0 0 0 1 * *", zone = "Europe/Belgrade")
    public void generateReferenceRate()
    {
        double random = ThreadLocalRandom.current().nextDouble(startRange, endRange);
        setReferenceRate(BigDecimal.valueOf(random).setScale(4, RoundingMode.HALF_UP));
    }

    public synchronized BigDecimal getReferenceRate() {
        return referenceRate;
    }

    public synchronized void setReferenceRate(BigDecimal referenceRate) {
        this.referenceRate = referenceRate;
    }

    @Override
    public Page<LoanResponseDto> find(Jwt jwt, int page, int size) {
        return loanRepository.findByClientIdOrderByAmountDesc(((Number) jwt.getClaim(appPropertiesId)).longValue(), PageRequest.of(page,size)).map(s->new LoanResponseDto(s.getId(),s.getLoanType(),s.getAmount(),s.getStatus()));
    }

    @Override
    public LoanInfoResponseDto info(Jwt jwt, Long id) {
        if(!(employeeRoles.contains(jwt.getClaim(roles).toString()) || ((Number) jwt.getClaim(appPropertiesId)).longValue()==id))
            throw new IllegalArgumentException("Nemas dozvolu za ovu metodu");
        Loan loan=loanRepository.findById(id).orElse(null);
        if(loan==null)
            throw new IllegalArgumentException("Ne postoji loan sa ovim id");
        List<InstallmentResponseDto>list=new ArrayList<>();
        List<Installment> installments=loan.getInstallments();
        for(Installment x:installments)
        {
            list.add(new InstallmentResponseDto(x));
        }
        return new LoanInfoResponseDto(new LoanResponseDto(loan),list);
    }

    @Override
    public Page<LoanRequest> findAllLoanRequest(Jwt jwt, LoanType vrstaKredita, String brojRacuna, int page, int size) {
        return loanRequestRepository.findAllWithFilters(vrstaKredita,brojRacuna,PageRequest.of(page,size));
    }

    @Override
    public Page<LoanResponseDto> findAllLoans(Jwt jwt, LoanType vrstaKredita, String brojRacuna, Status status, int page, int size) {
        return loanRepository.findAllWithFilters(vrstaKredita,brojRacuna,status,PageRequest.of(page,size)).map(LoanResponseDto::new);
    }
}
