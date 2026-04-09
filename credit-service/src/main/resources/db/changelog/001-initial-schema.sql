-- =========================
-- LOAN REQUEST
-- =========================
CREATE TABLE loan_request_table (
                                    id BIGSERIAL PRIMARY KEY,
                                    version BIGINT,
                                    deleted BOOLEAN NOT NULL DEFAULT FALSE,
                                    created_at TIMESTAMP,
                                    updated_at TIMESTAMP,

                                    loan_type VARCHAR(50) NOT NULL,
                                    interest_type VARCHAR(50) NOT NULL,
                                    amount DECIMAL(19,2) NOT NULL,
                                    currency VARCHAR(10) NOT NULL,
                                    purpose VARCHAR(255) NOT NULL,
                                    monthly_salary DECIMAL(19,2) NOT NULL,
                                    employment_status VARCHAR(50) NOT NULL,
                                    current_employment_period INTEGER NOT NULL,
                                    repayment_period INTEGER NOT NULL,
                                    contact_phone VARCHAR(50) NOT NULL,
                                    account_number VARCHAR(50) NOT NULL,
                                    client_id BIGINT NOT NULL,
                                    status VARCHAR(50) NOT NULL,
                                    userEmail VARCHAR(50) NOT NULL,
                                    username VARCHAR(50) NOT NULL
);

-- =========================
-- LOAN
-- =========================
CREATE TABLE loan_table (
                            id BIGSERIAL PRIMARY KEY,
                            version BIGINT,
                            deleted BOOLEAN NOT NULL DEFAULT FALSE,
                            created_at TIMESTAMP,
                            updated_at TIMESTAMP,

                            loan_type VARCHAR(50) NOT NULL,
                            account_number VARCHAR(50) NOT NULL,
                            amount DECIMAL(19,2) NOT NULL,
                            repayment_period INTEGER NOT NULL,
                            nominal_interest_rate DECIMAL(10,4) NOT NULL,
                            effective_interest_rate DECIMAL(10,4) NOT NULL,
                            interest_type VARCHAR(50) NOT NULL,
                            agreement_date DATE NOT NULL,
                            maturity_date DATE NOT NULL,
                            installment_amount DECIMAL(19,2) NOT NULL,
                            next_installment_date DATE NOT NULL,
                            remaining_debt DECIMAL(19,2) NOT NULL,
                            currency VARCHAR(10) NOT NULL,
                            status VARCHAR(50) NOT NULL,
                            userEmail VARCHAR(50) NOT NULL,
                            username VARCHAR(50) NOT NULL,
                            client_id BIGINT NOT NULL,
                            installmentCount INTEGER NOT NULL
);

-- =========================
-- INSTALLMENT
-- =========================
CREATE TABLE installment_table (
                                   id BIGSERIAL PRIMARY KEY,
                                   version BIGINT,
                                   deleted BOOLEAN NOT NULL DEFAULT FALSE,
                                   created_at TIMESTAMP,
                                   updated_at TIMESTAMP,

                                   loan_id BIGINT NOT NULL,
                                   installment_amount DECIMAL(19,2) NOT NULL,
                                   interest_rate_at_payment DECIMAL(10,4) NOT NULL,
                                   currency VARCHAR(10) NOT NULL,
                                   expected_due_date DATE NOT NULL,
                                   actual_due_date DATE,
                                   payment_status VARCHAR(50) NOT NULL,
                                   retry INTEGER NOT NULL,

                                   CONSTRAINT fk_installment_loan
                                       FOREIGN KEY (loan_id)
                                           REFERENCES loan_table(id)
                                           ON DELETE CASCADE
);

-- =========================
-- INDEXES (optional ali preporuka)
-- =========================
CREATE INDEX idx_installment_loan_id ON installment_table(loan_id);
CREATE INDEX idx_loan_account_number ON loan_table(account_number);
CREATE INDEX idx_loan_request_client_id ON loan_request_table(client_id);