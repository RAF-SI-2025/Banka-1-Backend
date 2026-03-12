-- liquibase formatted sql
-- changeset ilijan:4
-- (Šifra ovde mora biti hesirana Argonom2. Recimo da je šifra 'admin123', Argon2 bi bio nešto ovako:)
INSERT INTO employees (ime, prezime, datum_rodjenja, pol, email, username, password, pozicija, departman, aktivan, role,
                       version, deleted)
VALUES ('Admin', 'Adminovic', '1990-01-01', 'M', 'admin@banka.com', 'admin',
        '$argon2id$v=19$m=65536,t=3,p=1$cml4YnF1MGJOaG5md1cxOQ$kTOwNnDZmFymtQgsCUgpYFUJC9eV8wmpBCEldnS3XeE', -- ovo je hesirano 'admin123'
        'Direktor', 'Uprava', true, 'ADMIN', 0, false),
       -- 2. SUPERVISOR (Nadzor, šefovi odeljenja, kontrola)
       ('Petar', 'Petrovic', '1985-05-12', 'M', 'petar.petrovic@banka.com', 'petar',
        '$argon2id$v=19$m=65536,t=3,p=1$cml4YnF1MGJOaG5md1cxOQ$kTOwNnDZmFymtQgsCUgpYFUJC9eV8wmpBCEldnS3XeE',
        'Sef brokerskog tima', 'Hartije od vrednosti', true, 'SUPERVISOR', 0, false),

       ('Milica', 'Milic', '1988-02-14', 'Z', 'milica.milic@banka.com', 'milica',
        '$argon2id$v=19$m=65536,t=3,p=1$cml4YnF1MGJOaG5md1cxOQ$kTOwNnDZmFymtQgsCUgpYFUJC9eV8wmpBCEldnS3XeE',
        'Sef ekspoziture', 'Prodaja', true, 'SUPERVISOR', 0, false),

       ('Vladimir', 'Vladic', '1984-08-04', 'M', 'vladimir.vladic@banka.com', 'vladimir',
        '$argon2id$v=19$m=65536,t=3,p=1$cml4YnF1MGJOaG5md1cxOQ$kTOwNnDZmFymtQgsCUgpYFUJC9eV8wmpBCEldnS3XeE',
        'Glavni kontrolor', 'Uprava', true, 'SUPERVISOR', 0, false),

-- 3. AGENT (Trgovina sa hartijama od vrednosti, OTC)
       ('Ana', 'Anic', '1995-11-05', 'Z', 'ana.anic@banka.com', 'ana',
        '$argon2id$v=19$m=65536,t=3,p=1$cml4YnF1MGJOaG5md1cxOQ$kTOwNnDZmFymtQgsCUgpYFUJC9eV8wmpBCEldnS3XeE',
        'Broker', 'Hartije od vrednosti', true, 'AGENT', 0, false),

       ('Jovan', 'Jovanovic', '1992-08-23', 'M', 'jovan.jovanovic@banka.com', 'jovan',
        '$argon2id$v=19$m=65536,t=3,p=1$cml4YnF1MGJOaG5md1cxOQ$kTOwNnDZmFymtQgsCUgpYFUJC9eV8wmpBCEldnS3XeE',
        'Diler hartija od vrednosti', 'Hartije od vrednosti', true, 'AGENT', 0, false),

       ('Stefan', 'Stefanovic', '1994-04-18', 'M', 'stefan.stefanovic@banka.com', 'stefan',
        '$argon2id$v=19$m=65536,t=3,p=1$cml4YnF1MGJOaG5md1cxOQ$kTOwNnDZmFymtQgsCUgpYFUJC9eV8wmpBCEldnS3XeE',
        'Investicioni savetnik', 'Hartije od vrednosti', true, 'AGENT', 0, false),

       ('Jovana', 'Jovic', '1997-07-22', 'Z', 'jovana.jovic@banka.com', 'jovanaj',
        '$argon2id$v=19$m=65536,t=3,p=1$cml4YnF1MGJOaG5md1cxOQ$kTOwNnDZmFymtQgsCUgpYFUJC9eV8wmpBCEldnS3XeE',
        'Broker', 'Hartije od vrednosti', true, 'AGENT', 0, false),

       ('Ivana', 'Ivanovic', '1993-11-28', 'Z', 'ivana.ivanovic@banka.com', 'ivana',
        '$argon2id$v=19$m=65536,t=3,p=1$cml4YnF1MGJOaG5md1cxOQ$kTOwNnDZmFymtQgsCUgpYFUJC9eV8wmpBCEldnS3XeE',
        'OTC Agent', 'Hartije od vrednosti', true, 'AGENT', 0, false),

       ('Milan', 'Milanovic', '1985-09-09', 'M', 'milan.milanovic@banka.com', 'milan',
        '$argon2id$v=19$m=65536,t=3,p=1$cml4YnF1MGJOaG5md1cxOQ$kTOwNnDZmFymtQgsCUgpYFUJC9eV8wmpBCEldnS3XeE',
        'Portfolio menadzer', 'Hartije od vrednosti', true, 'AGENT', 0, false),

-- 4. BASIC (Osnovno upravljanje, operativni poslovi, šalter, IT)
       ('Marko', 'Markovic', '1991-07-30', 'M', 'marko.markovic@banka.com', 'marko',
        '$argon2id$v=19$m=65536,t=3,p=1$cml4YnF1MGJOaG5md1cxOQ$kTOwNnDZmFymtQgsCUgpYFUJC9eV8wmpBCEldnS3XeE',
        'IT Administrator', 'IT', true, 'BASIC', 0, false),

       ('Nikola', 'Nikolic', '1993-09-15', 'M', 'nikola.nikolic@banka.com', 'nikola',
        '$argon2id$v=19$m=65536,t=3,p=1$cml4YnF1MGJOaG5md1cxOQ$kTOwNnDZmFymtQgsCUgpYFUJC9eV8wmpBCEldnS3XeE',
        'Salterski radnik', 'Prodaja', true, 'BASIC', 0, false),

       ('Luka', 'Lukic', '1996-03-11', 'M', 'luka.lukic@banka.com', 'luka',
        '$argon2id$v=19$m=65536,t=3,p=1$cml4YnF1MGJOaG5md1cxOQ$kTOwNnDZmFymtQgsCUgpYFUJC9eV8wmpBCEldnS3XeE',
        'Salterski radnik', 'Prodaja', true, 'BASIC', 0, false),

       ('Aleksandar', 'Aleksic', '1991-02-03', 'M', 'aleksandar.aleksic@banka.com', 'aleksandar',
        '$argon2id$v=19$m=65536,t=3,p=1$cml4YnF1MGJOaG5md1cxOQ$kTOwNnDZmFymtQgsCUgpYFUJC9eV8wmpBCEldnS3XeE',
        'Klijentski savetnik', 'Prodaja', true, 'BASIC', 0, false),

       ('Jelena', 'Jelic', '1987-12-01', 'Z', 'jelena.jelic@banka.com', 'jelena',
        '$argon2id$v=19$m=65536,t=3,p=1$cml4YnF1MGJOaG5md1cxOQ$kTOwNnDZmFymtQgsCUgpYFUJC9eV8wmpBCEldnS3XeE',
        'Kreditni sluzbenik', 'Finansije', true, 'BASIC', 0, false),

       ('Marija', 'Maric', '1990-06-25', 'Z', 'marija.maric@banka.com', 'marija',
        '$argon2id$v=19$m=65536,t=3,p=1$cml4YnF1MGJOaG5md1cxOQ$kTOwNnDZmFymtQgsCUgpYFUJC9eV8wmpBCEldnS3XeE',
        'Racunovodja', 'Finansije', true, 'BASIC', 0, false),

       ('Filip', 'Filipovic', '1986-05-20', 'M', 'filip.filipovic@banka.com', 'filip',
        '$argon2id$v=19$m=65536,t=3,p=1$cml4YnF1MGJOaG5md1cxOQ$kTOwNnDZmFymtQgsCUgpYFUJC9eV8wmpBCEldnS3XeE',
        'Finansijski Analiticar', 'Finansije', true, 'BASIC', 0, false),

       ('Katarina', 'Katic', '1989-10-08', 'Z', 'katarina.katic@banka.com', 'katarina',
        '$argon2id$v=19$m=65536,t=3,p=1$cml4YnF1MGJOaG5md1cxOQ$kTOwNnDZmFymtQgsCUgpYFUJC9eV8wmpBCEldnS3XeE',
        'Pravnik', 'Pravna sluzba', true, 'BASIC', 0, false),

       ('Nevena', 'Nenic', '1992-01-16', 'Z', 'nevena.nenic@banka.com', 'nevena',
        '$argon2id$v=19$m=65536,t=3,p=1$cml4YnF1MGJOaG5md1cxOQ$kTOwNnDZmFymtQgsCUgpYFUJC9eV8wmpBCEldnS3XeE',
        'Marketing Savetnik', 'Marketing', true, 'BASIC', 0, false),

       ('Sara', 'Saric', '1998-12-14', 'Z', 'sara.saric@banka.com', 'sara',
        '$argon2id$v=19$m=65536,t=3,p=1$cml4YnF1MGJOaG5md1cxOQ$kTOwNnDZmFymtQgsCUgpYFUJC9eV8wmpBCEldnS3XeE',
        'Asistent u HR-u', 'Ljudski resursi', true, 'BASIC', 0, false);

-- Permissions for seed users
-- Permissions are cumulative by role power (matching ZaposlenServiceImplementation logic):
--   BASIC      -> BANKING_BASIC, CLIENT_MANAGE
--   AGENT      -> BASIC + SECURITIES_TRADE_LIMITED
--   SUPERVISOR -> AGENT + SECURITIES_TRADE_UNLIMITED, TRADE_UNLIMITED, OTC_TRADE, FUND_AGENT_MANAGE
--   ADMIN      -> SUPERVISOR + EMPLOYEE_MANAGE_ALL
--
-- Row 1:  admin (ADMIN)
-- Row 2:  petar (SUPERVISOR)
-- Row 3:  milica (SUPERVISOR)
-- Row 4:  vladimir (SUPERVISOR)
-- Row 5:  ana (AGENT)
-- Row 6:  jovan (AGENT)
-- Row 7:  stefan (AGENT)
-- Row 8:  jovana (AGENT)
-- Row 9:  ivana (AGENT)
-- Row 10: milan (AGENT)
-- Row 11: marko (BASIC)
-- Row 12: nikola (BASIC)
-- Row 13: luka (BASIC)
-- Row 14: aleksandar (BASIC)
-- Row 15: jelena (BASIC)
-- Row 16: marija (BASIC)
-- Row 17: filip (BASIC)
-- Row 18: katarina (BASIC)
-- Row 19: nevena (BASIC)
-- Row 20: sara (BASIC)

INSERT INTO zaposlen_permissions (zaposlen_id, permission)
SELECT e.id, p.permission
FROM employees e
         JOIN (VALUES
                   -- ADMIN (id=1): all permissions
                   ('admin@banka.com', 'BANKING_BASIC'),
                   ('admin@banka.com', 'CLIENT_MANAGE'),
                   ('admin@banka.com', 'SECURITIES_TRADE_LIMITED'),
                   ('admin@banka.com', 'SECURITIES_TRADE_UNLIMITED'),
                   ('admin@banka.com', 'TRADE_UNLIMITED'),
                   ('admin@banka.com', 'OTC_TRADE'),
                   ('admin@banka.com', 'FUND_AGENT_MANAGE'),
                   ('admin@banka.com', 'EMPLOYEE_MANAGE_ALL'),

                   -- SUPERVISOR: BASIC + AGENT + SUPERVISOR permissions
                   ('petar.petrovic@banka.com', 'BANKING_BASIC'),
                   ('petar.petrovic@banka.com', 'CLIENT_MANAGE'),
                   ('petar.petrovic@banka.com', 'SECURITIES_TRADE_LIMITED'),
                   ('petar.petrovic@banka.com', 'SECURITIES_TRADE_UNLIMITED'),
                   ('petar.petrovic@banka.com', 'TRADE_UNLIMITED'),
                   ('petar.petrovic@banka.com', 'OTC_TRADE'),
                   ('petar.petrovic@banka.com', 'FUND_AGENT_MANAGE'),

                   ('milica.milic@banka.com', 'BANKING_BASIC'),
                   ('milica.milic@banka.com', 'CLIENT_MANAGE'),
                   ('milica.milic@banka.com', 'SECURITIES_TRADE_LIMITED'),
                   ('milica.milic@banka.com', 'SECURITIES_TRADE_UNLIMITED'),
                   ('milica.milic@banka.com', 'TRADE_UNLIMITED'),
                   ('milica.milic@banka.com', 'OTC_TRADE'),
                   ('milica.milic@banka.com', 'FUND_AGENT_MANAGE'),

                   ('vladimir.vladic@banka.com', 'BANKING_BASIC'),
                   ('vladimir.vladic@banka.com', 'CLIENT_MANAGE'),
                   ('vladimir.vladic@banka.com', 'SECURITIES_TRADE_LIMITED'),
                   ('vladimir.vladic@banka.com', 'SECURITIES_TRADE_UNLIMITED'),
                   ('vladimir.vladic@banka.com', 'TRADE_UNLIMITED'),
                   ('vladimir.vladic@banka.com', 'OTC_TRADE'),
                   ('vladimir.vladic@banka.com', 'FUND_AGENT_MANAGE'),

                   -- AGENT: BASIC + AGENT permissions
                   ('ana.anic@banka.com', 'BANKING_BASIC'),
                   ('ana.anic@banka.com', 'CLIENT_MANAGE'),
                   ('ana.anic@banka.com', 'SECURITIES_TRADE_LIMITED'),

                   ('jovan.jovanovic@banka.com', 'BANKING_BASIC'),
                   ('jovan.jovanovic@banka.com', 'CLIENT_MANAGE'),
                   ('jovan.jovanovic@banka.com', 'SECURITIES_TRADE_LIMITED'),

                   ('stefan.stefanovic@banka.com', 'BANKING_BASIC'),
                   ('stefan.stefanovic@banka.com', 'CLIENT_MANAGE'),
                   ('stefan.stefanovic@banka.com', 'SECURITIES_TRADE_LIMITED'),

                   ('jovana.jovic@banka.com', 'BANKING_BASIC'),
                   ('jovana.jovic@banka.com', 'CLIENT_MANAGE'),
                   ('jovana.jovic@banka.com', 'SECURITIES_TRADE_LIMITED'),

                   ('ivana.ivanovic@banka.com', 'BANKING_BASIC'),
                   ('ivana.ivanovic@banka.com', 'CLIENT_MANAGE'),
                   ('ivana.ivanovic@banka.com', 'SECURITIES_TRADE_LIMITED'),

                   ('milan.milanovic@banka.com', 'BANKING_BASIC'),
                   ('milan.milanovic@banka.com', 'CLIENT_MANAGE'),
                   ('milan.milanovic@banka.com', 'SECURITIES_TRADE_LIMITED'),

                   -- BASIC: BASIC permissions only
                   ('marko.markovic@banka.com', 'BANKING_BASIC'),
                   ('marko.markovic@banka.com', 'CLIENT_MANAGE'),

                   ('nikola.nikolic@banka.com', 'BANKING_BASIC'),
                   ('nikola.nikolic@banka.com', 'CLIENT_MANAGE'),

                   ('luka.lukic@banka.com', 'BANKING_BASIC'),
                   ('luka.lukic@banka.com', 'CLIENT_MANAGE'),

                   ('aleksandar.aleksic@banka.com', 'BANKING_BASIC'),
                   ('aleksandar.aleksic@banka.com', 'CLIENT_MANAGE'),

                   ('jelena.jelic@banka.com', 'BANKING_BASIC'),
                   ('jelena.jelic@banka.com', 'CLIENT_MANAGE'),

                   ('marija.maric@banka.com', 'BANKING_BASIC'),
                   ('marija.maric@banka.com', 'CLIENT_MANAGE'),

                   ('filip.filipovic@banka.com', 'BANKING_BASIC'),
                   ('filip.filipovic@banka.com', 'CLIENT_MANAGE'),

                   ('katarina.katic@banka.com', 'BANKING_BASIC'),
                   ('katarina.katic@banka.com', 'CLIENT_MANAGE'),

                   ('nevena.nenic@banka.com', 'BANKING_BASIC'),
                   ('nevena.nenic@banka.com', 'CLIENT_MANAGE'),

                   ('sara.saric@banka.com', 'BANKING_BASIC'),
                   ('sara.saric@banka.com', 'CLIENT_MANAGE')) AS p(email, permission) ON e.email = p.email;

