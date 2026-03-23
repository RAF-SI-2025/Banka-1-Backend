package com.banka1.transaction_service.service.implementation;

import com.banka1.transaction_service.domain.enums.TransactionStatus;
import com.banka1.transaction_service.dto.request.ApproveDto;
import com.banka1.transaction_service.dto.request.NewPaymentDto;
import com.banka1.transaction_service.dto.response.TransactionResponseDto;
import com.banka1.transaction_service.service.TransactionService;
import org.springframework.data.domain.Page;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
public class TransactionServiceImplementation implements TransactionService {
    @Override
    public String newPayment(Jwt jwt, NewPaymentDto newPaymentDto) {
        return "";
    }

    @Override
    public String approveTransaction(Jwt jwt, Long id, ApproveDto newPaymentDto) {
        return "";
    }

    //todo za sad ovo ostavljam ovde, validacije bi trebalo da budu zaseban servis, if-ove sam ostavio just in case
    //TODO menjati exceptione

//    private void  validation(AccountDto account,Jwt jwt)
//    {
//        if(account==null)
//            throw new IllegalArgumentException("Ne postoji unet racun");
//        if(!account.getVlasnik().equals(((Number) jwt.getClaim(appPropertiesId)).longValue()))
//            throw new IllegalArgumentException("Nisi vlasnik racuna");
//    }


    @Override
    public Page<TransactionResponseDto> findAllTransactions(Jwt jwt, Long id, TransactionStatus transactionStatus, LocalDate fromDate, LocalDate toDate, BigDecimal minAmount, BigDecimal maxAmount, int page, int size) {
        //validate(account,jwt);
        return null;
    }



}
