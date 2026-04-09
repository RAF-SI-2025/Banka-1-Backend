package com.banka1.order.service;

import com.banka1.order.dto.AuthenticatedUser;
import com.banka1.order.dto.PortfolioResponse;
import com.banka1.order.dto.PortfolioSummaryResponse;
import com.banka1.order.dto.SetPublicQuantityRequestDto;

/**
 * Business logic interface for "Portal: Moj Portfolio".
 * Provides operations for retrieving portfolio holdings, managing public stock exposure,
 * and exercising option contracts.
 */
public interface PortfolioService {

    /**
     * Returns the full portfolio for the specified user, including all holdings
     * enriched with market data retrieved from stock-service.
     *
     * Each portfolio item includes:
     * - listing type and ticker
     * - quantity and current market price
     * - calculated profit per position
     * - last modification timestamp
     * - public quantity (for STOCK only)
     * - option exercise availability (for OPTION only)
     *
     * The response also includes aggregated portfolio metrics:
     * - total profit
     * - tax paid for the current year
     * - unpaid tax for the current month
     *
     * @param userId the ID of the portfolio owner
     * @return list of portfolio positions with enriched market data
     */
    PortfolioSummaryResponse getPortfolio(AuthenticatedUser user);

    /**
     * Sets the number of units available for public OTC trading.
     * Applicable only to STOCK listings.
     *
     * If the requested public quantity exceeds the total quantity,
     * an {@link IllegalArgumentException} is thrown.
     *
     * @param portfolioId the portfolio position identifier
     * @param request     request containing the new public quantity
     */
    void setPublicQuantity(AuthenticatedUser user, Long portfolioId, SetPublicQuantityRequestDto request);

    /**
     * Executes an option contract for the given portfolio position.
     * Can only be performed by users with the AGENT security role
     * (called "actuary" in business-facing responses).
     *
     * Preconditions:
     * - settlement date must not have passed
     * - option must be in-the-money:
     *   CALL: market price > strike price
     *   PUT: market price < strike price
     *
     * Execution logic:
     * - each option contract represents 100 underlying shares
     * - CALL results in buying shares at strike price
     * - PUT results in selling shares at strike price
     * - portfolio is updated accordingly
     * - account-service is called for cash settlement
     *
     * @param portfolioId the portfolio option position
     * @param userId      the ID of the user executing the option
     */
    void exerciseOption(AuthenticatedUser user, Long portfolioId);
}
