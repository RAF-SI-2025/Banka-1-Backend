"""Response schema for a client's position in a fund."""

from datetime import datetime
from decimal import Decimal

from pydantic import BaseModel


class PositionResponse(BaseModel):
    """Client fund position including computed ownership percentage and value."""

    id: int
    klijent_id: int
    fund_id: int
    ukupan_ulozeni_iznos: Decimal
    datum_poslednje_promene: datetime
    procenat_fonda: Decimal
    trenutna_vrednost_pozicije: Decimal
