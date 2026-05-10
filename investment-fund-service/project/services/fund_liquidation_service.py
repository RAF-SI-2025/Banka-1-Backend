"""SAGA service for liquidating fund securities to cover large redemptions."""

from decimal import Decimal
from typing import List

from project.clients.banking_client import BankingClient
from project.clients.order_client import OrderClient
from project.enums.transaction_status import TransactionStatus
from project.models.investment_fund import InvestmentFund
from project.repositories.client_fund_position_repository import ClientFundPositionRepository
from project.repositories.client_fund_transaction_repository import ClientFundTransactionRepository
from project.repositories.investment_fund_repository import InvestmentFundRepository


class FundLiquidationService:
    """Orchestrates the FUND_LIQUIDATION_FOR_REDEMPTION SAGA when liquid assets are insufficient."""

    def __init__(self, fund_repo: InvestmentFundRepository, tx_repo: ClientFundTransactionRepository, position_repo: ClientFundPositionRepository, banking_client: BankingClient, order_client: OrderClient) -> None:
        """Initialise with all required repositories and service clients."""
        self._fund_repo = fund_repo
        self._tx_repo = tx_repo
        self._position_repo = position_repo
        self._banking = banking_client
        self._order_client = order_client

    async def start_liquidation(self, fund: InvestmentFund, amount_needed: Decimal, klijent_id: int, destination_account_id: int, tx_id: int, bearer_token: str) -> None:
        """Place FIFO MARKET SELL orders to cover the delta and leave the tx PENDING until filled."""
        delta = amount_needed - fund.likvidna_sredstva
        target = delta * Decimal("1.05")
        await self._sell_securities_fifo(fund, target, bearer_token)

    async def _sell_securities_fifo(self, fund: InvestmentFund, target: Decimal, bearer_token: str) -> None:
        """Iterate fund holdings in FIFO order, placing MARKET SELL orders until target is covered."""
        try:
            holdings = await self._order_client.get_fund_portfolio(fund.id, bearer_token)
        except Exception:
            return
        sorted_holdings = sorted(holdings, key=lambda h: h.get("acquisitionDate", ""))
        cumulative = Decimal("0")
        for holding in sorted_holdings:
            if cumulative >= target:
                break
            listing_id = holding.get("listingId") or holding.get("id")
            quantity = int(holding.get("quantity", 0))
            current_price = Decimal(str(holding.get("currentPrice", 0)))
            if not listing_id or quantity <= 0 or current_price <= 0:
                continue
            try:
                await self._order_client.create_sell_order(int(listing_id), quantity, fund.account_id, bearer_token)
                cumulative += current_price * quantity
            except Exception:
                continue

    async def complete_liquidation(self, fund_id: int, tx_id: int, destination_account_id: int, amount: Decimal, klijent_id: int, bearer_token: str) -> None:
        """Complete the SAGA by executing the final transfer to the client and marking the tx COMPLETED."""
        fund = await self._fund_repo.find_by_id(fund_id)
        if not fund or fund.likvidna_sredstva < amount:
            return
        fund_details = await self._banking.get_account_details(fund.account_id)
        dest_details = await self._banking.get_account_details(destination_account_id)
        fund_number = fund_details.get("accountNumber") or fund_details.get("account_number")
        dest_number = dest_details.get("accountNumber") or dest_details.get("account_number")
        try:
            await self._banking.transfer(fund_number, dest_number, amount, Decimal("0"), klijent_id)
            await self._fund_repo.update_likvidna_sredstva(fund_id, -amount)
            position = await self._position_repo.find_by_klijent_and_fund(klijent_id, fund_id)
            if position:
                await self._position_repo.upsert(klijent_id, fund_id, -amount)
            await self._tx_repo.update_status(tx_id, TransactionStatus.COMPLETED)
        except Exception:
            await self._tx_repo.update_status(tx_id, TransactionStatus.FAILED)

    async def poll_pending_liquidations(self, bearer_token: str) -> None:
        """Check all funds for pending redemption transactions that can now be completed."""
        funds = await self._fund_repo.find_all()
        for fund in funds:
            pending_txs = await self._tx_repo.find_pending_outflow_by_fund(fund.id)
            for tx in pending_txs:
                if fund.likvidna_sredstva >= tx.iznos:
                    await self.complete_liquidation(fund.id, tx.id, tx.source_account_id, tx.iznos, tx.klijent_id, bearer_token)
