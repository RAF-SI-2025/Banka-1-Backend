-- liquibase formatted sql
-- changeset ilijan:4
-- (Šifra ovde mora biti hesirana Argonom2. Recimo da je šifra 'admin123', Argon2 bi bio nešto ovako:)
INSERT INTO employees (ime, prezime, datum_rodjenja, pol, email, username, password, pozicija, departman, aktivan,role,version, deleted)
VALUES (
           'Admin', 'Adminovic', '1990-01-01', 'M', 'admin@banka.com', 'admin',
           '$argon2id$v=19$m=65536,t=3,p=1$cml4YnF1MGJOaG5md1cxOQ$kTOwNnDZmFymtQgsCUgpYFUJC9eV8wmpBCEldnS3XeE', -- ovo je hesirano 'admin123'
           'Direktor', 'Uprava', true, 'ADMIN',0, false
       ),
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
