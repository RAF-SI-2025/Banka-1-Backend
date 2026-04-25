-- liquibase formatted sql

-- changeset account-service:5
-- comment: Seed the State (Republika Srbija) as a separate company entity with a dedicated
-- RSD checking account. The State is modelled as a company with owner (vlasnik) = -2 to
-- distinguish it from both regular clients and the bank's own entity (vlasnik = -1).
-- The sifra_delatnosti 84.12 ('Regulisanje delatnosti privrede') is used because it is the
-- closest existing row to government/state regulation and matches real NACE/ISIC classifications.
--
-- Rationale: Previously the order-service looked up the tax and option-exercise settlement
-- account on the bank's own RSD account (vlasnik = -1), which conflated bank equity with
-- government receivables. This changeset creates a first-class State entity so those flows
-- can settle against a distinct account, matching the specification in Celina 3.

-- =========================
-- STATE COMPANY
-- =========================
INSERT INTO company_table (
    version,
    naziv,
    maticni_broj,
    poreski_broj,
    sifra_delatnosti_id,
    adresa,
    vlasnik
)
SELECT
    0,
    'Republika Srbija',
    '07020000',
    '100000002',
    sd.id,
    'Nemanjina 11, 11000 Beograd',
    -2
FROM sifra_delatnosti_table sd
WHERE sd.sifra = '84.12';

-- =========================
-- STATE RSD CHECKING ACCOUNT
-- =========================
-- Branch code 0002 is used to keep state account numbers visually distinct from the bank's
-- own branch (0001). The account is modelled as a PERSONAL checking account without a
-- company link because the JPA validation in CheckingAccount rejects BUSINESS accounts
-- without a company reference and the existing bank RSD account already uses the same
-- STANDARDNI/PERSONAL workaround (see 004-fix-bank-account-concrete.sql). The state
-- company record above exists purely to satisfy the "state as a separate company" spec;
-- inter-service lookups use vlasnik = -2 for identification, not the company link.
INSERT INTO account_table (
    version,
    account_type,
    broj_racuna,
    ime_vlasnika_racuna,
    prezime_vlasnika_racuna,
    naziv_racuna,
    vlasnik,
    zaposlen,
    stanje,
    raspolozivo_stanje,
    datum_i_vreme_kreiranja,
    datum_isteka,
    currency_id,
    status,
    dnevni_limit,
    mesecni_limit,
    dnevna_potrosnja,
    mesecna_potrosnja,
    company_id,
    account_concrete,
    odrzavanje_racuna,
    account_ownership_type
)
SELECT
    0,
    'CHECKING',
    '1110002000000000011',
    'Republika',
    'Srbija',
    'State RSD Account',
    -2,
    -1,
    0.00,
    0.00,
    NOW(),
    NULL,
    c.id,
    'ACTIVE',
    999999999.99,
    999999999.99,
    0.00,
    0.00,
    NULL,
    'STANDARDNI',
    0.00,
    'PERSONAL'
FROM currency_table c
WHERE c.oznaka = 'RSD';
