package com.banka1.order.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Stores actuary-specific data for each employee who is an actuary (agent or supervisor).
 * Supervisors have a null limit and needApproval always set to false.
 * Agents have a configurable daily limit in RSD and a needApproval flag.
 */
@Entity
@Table(name = "actuary_info")
@Getter
@Setter
@NoArgsConstructor
public class ActuaryInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Reference to the employee in employee-service. Unique per actuary. */
    @Column(nullable = false, unique = true)
    private Long employeeId;

    /**
     * Daily trading limit in RSD. Null for supervisors.
     * When an agent trades in a foreign currency, the amount is converted to RSD
     * via exchange-service (without commission) before comparing against this limit.
     */
    @Column(name = "\"limit\"", precision = 19, scale = 4)
    private BigDecimal limit;

    /** Amount of the daily limit already consumed today, in RSD. Resets at 23:59 every day. */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal usedLimit = BigDecimal.ZERO;

    /** Exposure reserved by pending/approved orders that has not yet been fully executed, in RSD. */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal reservedLimit = BigDecimal.ZERO;

    /**
     * If true, a supervisor must approve every order placed by this agent.
     * Always false for supervisors.
     */
    @Column(nullable = false)
    private Boolean needApproval = false;
}
