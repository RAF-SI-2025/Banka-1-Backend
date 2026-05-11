package com.banka1.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "actuary_profit_rollup")
@Getter
@Setter
@NoArgsConstructor
public class ActuaryProfitRollup {

    @Id
    @Column(name = "employee_id")
    private Long employeeId;

    @Column(nullable = false)
    private String ime;

    @Column(nullable = false)
    private String prezime;

    @Column(nullable = false, length = 16)
    private String role;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal totalProfitRsd = BigDecimal.ZERO;

    @Column(nullable = false)
    private LocalDateTime refreshedAt;
}
