package com.banka1.order.service;

import com.banka1.order.dto.TaxDebtResponse;
import com.banka1.order.dto.TaxTrackingRowResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Service responsible for calculating and collecting capital gains tax.
 *
 * <p>
 * This service handles:
 * <ul>
 *     <li>Monthly calculation of capital gains tax (15%)</li>
 *     <li>Conversion of profit into RSD via exchange service</li>
 *     <li>Transfer of collected tax to state account</li>
 *     <li>Ensuring idempotent execution (no duplicate tax charging)</li>
 * </ul>
 * </p>
 */
public interface TaxService {

    /**
     * Calculates and collects capital gains tax for all eligible trades
     * from the previous calendar month.
     *
     * <p>
     * Process includes:
     * <ul>
     *     <li>Fetching all SELL trades from previous month</li>
     *     <li>Calculating profit per trade</li>
     *     <li>Applying 15% tax rate</li>
     *     <li>Converting amount to RSD via exchange-service</li>
     *     <li>Transferring funds to state account via account-service</li>
     *     <li>Marking trades as processed to prevent duplication</li>
     * </ul>
     * </p>
     *
     * <p>
     * This method is typically executed by:
     * <ul>
     *     <li>Scheduled cron job (monthly)</li>
     *     <li>Manual trigger via supervisor portal</li>
     * </ul>
     * </p>
     */
    void collectMonthlyTax();

    /**
     * Manually triggers tax collection for administrative purposes.
     *
     * <p>
     * Intended for supervisor usage via API.
     * Executes the same logic as the scheduled monthly job.
     * </p>
     */
    void collectMonthlyTaxManually();

    /**
     * Retrieves aggregated tax debt per user in RSD.
     *
     * <p>
     * Used for supervisor dashboard to display current liabilities.
     * </p>
     *
     * @return list of users with their tax debts
     */
    Page<TaxDebtResponse> getAllDebts(Pageable pageable);

    /**
     * Retrieves tax debt for a specific user.
     *
     * @param userId ID of the user
     * @return user tax debt in RSD
     */
    TaxDebtResponse getUserDebt(Long userId);

    /**
     * Returns tax already paid in the current calendar year.
     *
     * @param userId the user identifier
     * @return paid tax amount
     */
    java.math.BigDecimal getCurrentYearPaidTax(Long userId);

    /**
     * Returns tax accrued for the current month that is not yet treated as paid.
     *
     * @param userId the user identifier
     * @return unpaid tax amount for the current month
     */
    java.math.BigDecimal getCurrentMonthUnpaidTax(Long userId);

    /**
     * Returns supervisor-facing tax tracking rows for tradable users.
     *
     * @param userType optional user type filter: CLIENT or ACTUARY
     * @param firstName optional first name filter
     * @param lastName optional last name filter
     * @return tax tracking rows with debt in RSD
     */
    Page<TaxTrackingRowResponse> getTaxTracking(String userType, String firstName, String lastName, Pageable pageable);
}
