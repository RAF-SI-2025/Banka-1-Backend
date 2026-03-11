-- liquibase formatted sql

-- changeset ilijan:1
CREATE TABLE employees (
                           id BIGSERIAL PRIMARY KEY, -- BIGSERIAL je Postgresov Auto-Increment (GenerationType.IDENTITY)
                           ime VARCHAR(255) NOT NULL,
                           prezime VARCHAR(255) NOT NULL,
                           datum_rodjenja DATE NOT NULL,
                           pol VARCHAR(10) NOT NULL,
                           email VARCHAR(255) NOT NULL UNIQUE,
                           broj_telefona VARCHAR(255),
                           adresa VARCHAR(255),
                           username VARCHAR(255) NOT NULL UNIQUE,
                           password VARCHAR(255),
                           pozicija VARCHAR(255) NOT NULL,
                           departman VARCHAR(255) NOT NULL,
                           aktivan BOOLEAN NOT NULL DEFAULT TRUE,
                           role VARCHAR(50) NOT NULL,

    -- BaseEntity polja
                           version BIGINT DEFAULT 0,
                           deleted BOOLEAN NOT NULL DEFAULT FALSE,
                           created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                           updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);



-- changeset ilijan:2
CREATE TABLE refresh_tokens (
                                id BIGSERIAL PRIMARY KEY,
                                value VARCHAR(255) NOT NULL UNIQUE,
                                expiration_date_time TIMESTAMP NOT NULL,
                                zaposlen_id BIGINT NOT NULL,

    -- BaseEntity polja
                                version BIGINT DEFAULT 0,
                                deleted BOOLEAN NOT NULL DEFAULT FALSE,
                                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

                                CONSTRAINT fk_rt_zaposlen FOREIGN KEY (zaposlen_id) REFERENCES employees (id) ON DELETE CASCADE
);


-- changeset ognjen:1
CREATE TABLE confirmation_token (
                                    id BIGSERIAL PRIMARY KEY,
                                    value VARCHAR(255) NOT NULL UNIQUE,
                                    expiration_date_time TIMESTAMP,
                                    zaposlen_id BIGINT NOT NULL UNIQUE,

                                    version BIGINT DEFAULT 0,
                                    deleted BOOLEAN NOT NULL DEFAULT FALSE,
                                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

                                    CONSTRAINT fk_ct_zaposlen
                                        FOREIGN KEY (zaposlen_id)
                                            REFERENCES employees(id)
                                            ON DELETE CASCADE
);

-- changeset ognjen:2
CREATE TABLE zaposlen_permissions (
                                      zaposlen_id BIGINT NOT NULL,
                                      permission VARCHAR(100) NOT NULL,

                                      PRIMARY KEY (zaposlen_id, permission),

                                      CONSTRAINT fk_zp_zaposlen
                                          FOREIGN KEY (zaposlen_id)
                                              REFERENCES employees(id)
                                              ON DELETE CASCADE
);

-- changeset ilijan:3
-- Index za brzo pretrazivanje po imenu i prezimenu
CREATE INDEX idx_employees_ime_prezime ON employees (ime, prezime);

-- Index za filtriranje po poziciji
CREATE INDEX idx_employees_pozicija ON employees (pozicija);

-- Partial Index: Ekstremno ubrzava sve upite jer Hibernate uvek trazi deleted = false todo ostaviti ovo samo ako budemo ostavili onaj @SQLDELETE za softdelete
CREATE INDEX idx_employees_active ON employees (deleted, id) WHERE deleted = false;

-- changeset ilijan:4
ALTER TABLE employees ALTER COLUMN password DROP NOT NULL;
