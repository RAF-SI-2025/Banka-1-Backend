
```markdown
# Banka 2025 - User Service (Upravljanje zaposlenima)

Ovaj mikroservis je zadužen za upravljanje zaposlenima, RBAC (Role-Based Access Control) autentifikaciju i autorizaciju. 
Izgrađen je korišćenjem **Spring Boot 4.0.3**, **PostgreSQL** baze podataka i **Liquibase** alata za migracije šeme, a ceo sistem je potpuno **Dockerizovan**.

---

## 🛠️ Preduslovi za lokalni razvoj

Pre nego što pokrenete projekat, uverite se da imate instalirano sledeće:
1. **Java 21**
2. **Docker Desktop** ([Link za preuzimanje](https://www.docker.com/products/docker-desktop/)) - *Uverite se da je Docker Desktop aplikacija pokrenuta u pozadini pre kucanja komandi!*

### 🔑 Podešavanje `.env` fajla (OBAVEZNO)
Konfiguracija baze i portova se ne nalazi u repozitorijumu iz bezbednosnih razloga. 

---

## 🚀 Pokretanje aplikacije (Dva načina)

Projekat možete pokrenuti na dva načina, u zavisnosti od toga da li samo želite da podignete servis ili želite da aktivno programirate.

### Opcija 1: Hibridni režim (Preporučeno za razvoj)
Ovo je najbolji način za programiranje. Baza se vrti u Dockeru, a Spring Boot aplikaciju pokrećete iz IntelliJ-a kako biste imali pristup Debugger-u i uživo logovima.

1. U terminalu (u root-u projekta) pokrenite **samo bazu**:
   ```bash
   docker compose up -d postgres
   ```
2. U IntelliJ-u, pokrenite aplikaciju pritiskom na zeleno **Play** dugme (klasa `UserServiceApplication`). 
   *Spring će iskoristiti fallback `localhost` vrednosti iz `application.properties` i uspešno se povezati na Docker bazu.*

### Opcija 2: Pokretanje punog Docker paketa (Baza + Servis)
Koristite ovo kada želite da testirate kako se cela aplikacija ponaša u potpuno izolovanom produkcionom okruženju.

1. Prvo kompajlirajte najnoviji kod u `.jar` fajl:
   ```bash
   ./gradlew clean bootJar
   ```
2. Podignite i bazu i servis pomoću Dockera:
   ```bash
   docker compose up -d --build
   ```
   *(Aplikacija će sada biti dostupna na `http://localhost:8081`)*

**Korisne Docker komande:**
* Gledanje logova servisa: `docker compose logs -f user-service`
* Gašenje svih kontejnera: `docker compose down`
* **BRISANJE CELE BAZE:** `docker compose down -v` *(Briše kontejner i trajno briše volume sa podacima - baza se vraća na fabrička podešavanja!)*

---

## 🗄️ Baza podataka i Liquibase (Migracije)

Ovaj projekat **NE KORISTI H2 in-memory bazu** i **ne koristi** Hibernate za generisanje tabela. 

### Ključne razlike u odnosu na ranije (H2 + Hibernate):
1. **Podaci ostaju trajni:** Za razliku od H2 baze koja se briše pri svakom gašenju, PostgreSQL podaci preživljavaju restartovanje zahvaljujući Docker Volume-ima.
2. **Hibernate je read-only za šemu:** Podešavanje `spring.jpa.hibernate.ddl-auto` je postavljeno na `validate`. Hibernate **neće** automatski praviti tabele na osnovu vaših `@Entity` klasa. On samo proverava da li se Java kod poklapa sa bazom i baca grešku ako se ne poklapaju.
3. **Liquibase je gazda:** Sve tabele se kreiraju isključivo kroz SQL skripte koje se nalaze u `src/main/resources/db/changelog/`.

### ⚠️ Šta raditi kada treba izmeniti/dodati tabelu?

**NIKADA NE MENJAJTE POSTOJEĆE `.sql` FAJLOVE KOJI SU VEĆ POKRENUTI!**
Liquibase pravi "checksum" (heš) svakog fajla nakon što ga izvrši. Ako promenite i jedan zarez u fajlu `001-initial-schema.sql`, aplikacija će odbiti da se pokrene jer detektuje da je fajl naknadno menjan.

**Pravilan proces izmene šeme:**
1. Napravite novi fajl, npr. `003-dodavanje-jmbg-polja.sql` u `db/changelog/` folderu.
2. Napišite čist SQL za izmenu:
   ```sql
   -- changeset vase-ime:1
   ALTER TABLE employees ADD COLUMN jmbg VARCHAR(13);
   ```
3. Prijavite taj novi fajl u glavni meni: `db.changelog-master.xml`.
4. Ažurirajte vašu `@Entity` Java klasu dodavanjem novog polja.
5. Pokrenite aplikaciju. Liquibase će videti novi fajl, izvršiti ga bez diranja postojećih podataka i ažurirati bazu!
```