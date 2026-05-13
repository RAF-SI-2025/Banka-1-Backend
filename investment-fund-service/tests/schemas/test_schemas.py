"""Validation tests for all request and response Pydantic schemas."""

from decimal import Decimal

import pytest
from pydantic import ValidationError

from project.enums.performance_period import PerformancePeriod
from project.enums.transaction_status import TransactionStatus
from project.schemas.create_fund_request import CreateFundRequest
from project.schemas.invest_request import InvestRequest
from project.schemas.redeem_request import RedeemRequest
from project.schemas.update_fund_request import UpdateFundRequest


class TestCreateFundRequest:
    """Validation tests for CreateFundRequest."""

    def test_valid_request_passes(self):
        """CreateFundRequest accepts all required fields with valid values."""
        req = CreateFundRequest(naziv="My Fund", minimalni_ulog=Decimal("500"), menadzer_id=1)
        assert req.naziv == "My Fund"

    def test_empty_naziv_raises(self):
        """CreateFundRequest rejects an empty naziv string."""
        with pytest.raises(ValidationError):
            CreateFundRequest(naziv="", minimalni_ulog=Decimal("500"), menadzer_id=1)

    def test_naziv_too_long_raises(self):
        """CreateFundRequest rejects a naziv longer than 255 characters."""
        with pytest.raises(ValidationError):
            CreateFundRequest(naziv="x" * 256, minimalni_ulog=Decimal("500"), menadzer_id=1)

    def test_minimalni_ulog_zero_raises(self):
        """CreateFundRequest rejects minimalni_ulog of exactly zero."""
        with pytest.raises(ValidationError):
            CreateFundRequest(naziv="Fund", minimalni_ulog=Decimal("0"), menadzer_id=1)

    def test_minimalni_ulog_negative_raises(self):
        """CreateFundRequest rejects a negative minimalni_ulog."""
        with pytest.raises(ValidationError):
            CreateFundRequest(naziv="Fund", minimalni_ulog=Decimal("-100"), menadzer_id=1)

    def test_menadzer_id_zero_raises(self):
        """CreateFundRequest rejects menadzer_id of zero."""
        with pytest.raises(ValidationError):
            CreateFundRequest(naziv="Fund", minimalni_ulog=Decimal("100"), menadzer_id=0)

    def test_menadzer_id_negative_raises(self):
        """CreateFundRequest rejects a negative menadzer_id."""
        with pytest.raises(ValidationError):
            CreateFundRequest(naziv="Fund", minimalni_ulog=Decimal("100"), menadzer_id=-1)

    def test_opis_is_optional(self):
        """CreateFundRequest allows opis to be omitted."""
        req = CreateFundRequest(naziv="Fund", minimalni_ulog=Decimal("100"), menadzer_id=1)
        assert req.opis is None

    def test_opis_can_be_set(self):
        """CreateFundRequest accepts an optional opis value."""
        req = CreateFundRequest(naziv="Fund", minimalni_ulog=Decimal("100"), menadzer_id=1, opis="Description")
        assert req.opis == "Description"


class TestUpdateFundRequest:
    """Validation tests for UpdateFundRequest."""

    def test_all_fields_optional(self):
        """UpdateFundRequest can be created with no fields at all."""
        req = UpdateFundRequest()
        assert req.opis is None
        assert req.minimalni_ulog is None
        assert req.menadzer_id is None

    def test_minimalni_ulog_zero_raises(self):
        """UpdateFundRequest rejects minimalni_ulog of zero."""
        with pytest.raises(ValidationError):
            UpdateFundRequest(minimalni_ulog=Decimal("0"))

    def test_minimalni_ulog_positive_accepted(self):
        """UpdateFundRequest accepts a positive minimalni_ulog."""
        req = UpdateFundRequest(minimalni_ulog=Decimal("999.99"))
        assert req.minimalni_ulog == Decimal("999.99")

    def test_menadzer_id_zero_raises(self):
        """UpdateFundRequest rejects a zero menadzer_id."""
        with pytest.raises(ValidationError):
            UpdateFundRequest(menadzer_id=0)


class TestInvestRequest:
    """Validation tests for InvestRequest."""

    def test_valid_invest_request(self):
        """InvestRequest accepts valid iznos and source_account_id."""
        req = InvestRequest(iznos=Decimal("1000"), source_account_id=5)
        assert req.iznos == Decimal("1000")

    def test_iznos_zero_raises(self):
        """InvestRequest rejects iznos of zero."""
        with pytest.raises(ValidationError):
            InvestRequest(iznos=Decimal("0"), source_account_id=5)

    def test_iznos_negative_raises(self):
        """InvestRequest rejects a negative iznos."""
        with pytest.raises(ValidationError):
            InvestRequest(iznos=Decimal("-1"), source_account_id=5)

    def test_source_account_id_zero_raises(self):
        """InvestRequest rejects a source_account_id of zero."""
        with pytest.raises(ValidationError):
            InvestRequest(iznos=Decimal("100"), source_account_id=0)

    def test_idempotency_key_optional(self):
        """InvestRequest allows idempotency_key to be omitted."""
        req = InvestRequest(iznos=Decimal("100"), source_account_id=5)
        assert req.idempotency_key is None

    def test_idempotency_key_too_long_raises(self):
        """InvestRequest rejects an idempotency_key longer than 64 characters."""
        with pytest.raises(ValidationError):
            InvestRequest(iznos=Decimal("100"), source_account_id=5, idempotency_key="x" * 65)

    def test_idempotency_key_max_length_accepted(self):
        """InvestRequest accepts an idempotency_key of exactly 64 characters."""
        req = InvestRequest(iznos=Decimal("100"), source_account_id=5, idempotency_key="a" * 64)
        assert len(req.idempotency_key) == 64


class TestRedeemRequest:
    """Validation tests for RedeemRequest."""

    def test_valid_redeem_request(self):
        """RedeemRequest accepts valid iznos and destination_account_id."""
        req = RedeemRequest(iznos=Decimal("500"), destination_account_id=3)
        assert req.iznos == Decimal("500")

    def test_iznos_zero_raises(self):
        """RedeemRequest rejects iznos of zero."""
        with pytest.raises(ValidationError):
            RedeemRequest(iznos=Decimal("0"), destination_account_id=3)

    def test_iznos_negative_raises(self):
        """RedeemRequest rejects a negative iznos."""
        with pytest.raises(ValidationError):
            RedeemRequest(iznos=Decimal("-100"), destination_account_id=3)

    def test_destination_account_id_zero_raises(self):
        """RedeemRequest rejects a destination_account_id of zero."""
        with pytest.raises(ValidationError):
            RedeemRequest(iznos=Decimal("100"), destination_account_id=0)


class TestEnums:
    """Tests for enum completeness and string values."""

    def test_transaction_status_values(self):
        """TransactionStatus contains PENDING, COMPLETED, and FAILED."""
        values = {s.value for s in TransactionStatus}
        assert values == {"PENDING", "COMPLETED", "FAILED"}

    def test_performance_period_values(self):
        """PerformancePeriod contains MONTH, QUARTER, and YEAR."""
        values = {p.value for p in PerformancePeriod}
        assert values == {"MONTH", "QUARTER", "YEAR"}

    def test_transaction_status_is_str(self):
        """TransactionStatus members are strings and compare equal to their string values."""
        assert TransactionStatus.PENDING == "PENDING"
        assert TransactionStatus.COMPLETED == "COMPLETED"

    def test_performance_period_is_str(self):
        """PerformancePeriod members are strings and compare equal to their string values."""
        assert PerformancePeriod.MONTH == "MONTH"
