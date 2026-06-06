package user

import (
	"context"
	"errors"
	"strings"
	"time"

	"banka1/user-service-go/internal/platform"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
	"github.com/jackc/pgx/v5/pgxpool"
)

// pgxPool is the subset of *pgxpool.Pool used by Repository. Declaring it as an
// interface lets unit tests inject a mock pool; *pgxpool.Pool satisfies it.
type pgxPool interface {
	Begin(ctx context.Context) (pgx.Tx, error)
	Exec(ctx context.Context, sql string, args ...any) (pgconn.CommandTag, error)
	Query(ctx context.Context, sql string, args ...any) (pgx.Rows, error)
	QueryRow(ctx context.Context, sql string, args ...any) pgx.Row
}

type Repository struct {
	db   pgxPool
	jmbg *platform.JMBGCrypto
}

func NewRepository(db *pgxpool.Pool, jmbg *platform.JMBGCrypto) *Repository {
	return &Repository{db: db, jmbg: jmbg}
}

func (r *Repository) EmployeeByLogin(ctx context.Context, login string) (Employee, error) {
	rows := r.db.QueryRow(ctx, `
		SELECT id, ime, prezime, datum_rodjenja, pol, email, broj_telefona, adresa,
		       username, password, pozicija, departman, aktivan, role,
		       failed_login_attempts, locked_until, created_at, updated_at
		  FROM employees
		 WHERE deleted = false AND (lower(email) = lower($1) OR lower(username) = lower($1))`, login)
	return scanEmployee(rows)
}

func (r *Repository) EmployeeByID(ctx context.Context, id int64) (Employee, error) {
	rows := r.db.QueryRow(ctx, `
		SELECT id, ime, prezime, datum_rodjenja, pol, email, broj_telefona, adresa,
		       username, password, pozicija, departman, aktivan, role,
		       failed_login_attempts, locked_until, created_at, updated_at
		  FROM employees
		 WHERE deleted = false AND id = $1`, id)
	return scanEmployee(rows)
}

func (r *Repository) FirstActiveEmployeeIDByRoleExcluding(ctx context.Context, role string, excludedID int64) (int64, error) {
	var id int64
	err := r.db.QueryRow(ctx, `
		SELECT id
		  FROM employees
		 WHERE deleted = false
		   AND aktivan = true
		   AND role = $1
		   AND id <> $2
		 ORDER BY id
		 LIMIT 1`, role, excludedID).Scan(&id)
	if err != nil {
		return 0, mapPgError(err)
	}
	return id, nil
}

func (r *Repository) ClientByEmail(ctx context.Context, email string) (Client, error) {
	rows := r.db.QueryRow(ctx, `
		SELECT id, ime, prezime, datum_rodjenja, pol, email, broj_telefona, adresa,
		       password, NULL::text AS jmbg, jmbg_encrypted, aktivan, role, created_at, updated_at
		  FROM clients
		 WHERE deleted = false AND lower(email) = lower($1)`, email)
	return scanClient(rows)
}

func (r *Repository) ClientByID(ctx context.Context, id int64) (Client, error) {
	rows := r.db.QueryRow(ctx, `
		SELECT id, ime, prezime, datum_rodjenja, pol, email, broj_telefona, adresa,
		       password, NULL::text AS jmbg, jmbg_encrypted, aktivan, role, created_at, updated_at
		  FROM clients
		 WHERE deleted = false AND id = $1`, id)
	return scanClient(rows)
}

func (r *Repository) ClientByPlainJMBG(ctx context.Context, jmbg string) (Client, error) {
	rows := r.db.QueryRow(ctx, `
		SELECT id, ime, prezime, datum_rodjenja, pol, email, broj_telefona, adresa,
		       password, jmbg, jmbg_encrypted, aktivan, role, created_at, updated_at
		  FROM clients
		 WHERE deleted = false AND jmbg = $1`, jmbg)
	client, err := scanClient(rows)
	if err != nil && isUndefinedColumn(err) {
		return r.clientByEncryptedJMBG(ctx, jmbg)
	}
	return client, err
}

func (r *Repository) clientByEncryptedJMBG(ctx context.Context, plaintext string) (Client, error) {
	rows, err := r.db.Query(ctx, `
		SELECT id, ime, prezime, datum_rodjenja, pol, email, broj_telefona, adresa,
		       password, NULL::text AS jmbg, jmbg_encrypted, aktivan, role, created_at, updated_at
		  FROM clients
		 WHERE deleted = false AND jmbg_encrypted IS NOT NULL`)
	if err != nil {
		return Client{}, err
	}
	defer rows.Close()
	for rows.Next() {
		client, err := scanClient(rows)
		if err != nil {
			return Client{}, err
		}
		if client.JMBGEncrypted == nil {
			continue
		}
		decrypted, err := r.jmbg.Decrypt(*client.JMBGEncrypted)
		if err == nil && decrypted == plaintext {
			return client, nil
		}
	}
	if err := rows.Err(); err != nil {
		return Client{}, err
	}
	return Client{}, ErrNotFound
}

func (r *Repository) EmployeePermissions(ctx context.Context, id int64, role string) []string {
	rows, err := r.db.Query(ctx, `SELECT permission FROM zaposlen_permissions WHERE zaposlen_id = $1`, id)
	if err != nil {
		return employeePermissions(role)
	}
	defer rows.Close()
	var out []string
	for rows.Next() {
		var permission string
		if rows.Scan(&permission) == nil {
			out = append(out, permission)
		}
	}
	if len(out) == 0 {
		return employeePermissions(role)
	}
	return out
}

func (r *Repository) ReplaceEmployeePermissions(ctx context.Context, id int64, permissions []string) error {
	tx, err := r.db.Begin(ctx)
	if err != nil {
		return err
	}
	if _, err := tx.Exec(ctx, `DELETE FROM zaposlen_permissions WHERE zaposlen_id = $1`, id); err != nil {
		_ = tx.Rollback(ctx)
		return err
	}
	for _, permission := range permissions {
		if _, err := tx.Exec(ctx, `INSERT INTO zaposlen_permissions(zaposlen_id, permission) VALUES ($1, $2) ON CONFLICT DO NOTHING`, id, permission); err != nil {
			_ = tx.Rollback(ctx)
			return err
		}
	}
	return tx.Commit(ctx)
}

func (r *Repository) ClientPermissions(ctx context.Context, id int64, role string) []string {
	rows, err := r.db.Query(ctx, `SELECT permission FROM client_permissions WHERE client_id = $1`, id)
	if err != nil {
		return clientPermissions(role)
	}
	defer rows.Close()
	var out []string
	for rows.Next() {
		var permission string
		if rows.Scan(&permission) == nil {
			out = append(out, permission)
		}
	}
	if len(out) == 0 {
		return clientPermissions(role)
	}
	return out
}

func (r *Repository) OTCTradingClientIDs(ctx context.Context) ([]int64, error) {
	rows, err := r.db.Query(ctx, `
		SELECT DISTINCT c.id
		  FROM clients c
		  LEFT JOIN client_permissions cp ON cp.client_id = c.id
		 WHERE c.deleted = false
		   AND c.aktivan = true
		   AND (
		        c.role = 'CLIENT_TRADING'
		        OR cp.permission IN ('CLIENT_OTC_TRADE', 'CLIENT_SECURITIES_TRADE')
		   )
		 ORDER BY c.id`)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	ids := make([]int64, 0)
	for rows.Next() {
		var id int64
		if err := rows.Scan(&id); err != nil {
			return nil, err
		}
		ids = append(ids, id)
	}
	return ids, rows.Err()
}

func (r *Repository) ResetEmployeeLoginFailures(ctx context.Context, id int64) error {
	_, err := r.db.Exec(ctx, `UPDATE employees SET failed_login_attempts = 0, locked_until = NULL, updated_at = now() WHERE id = $1`, id)
	return err
}

func (r *Repository) RegisterFailedEmployeeLogin(ctx context.Context, employee Employee, maxAttempts int, lockout time.Duration) error {
	attempts := employee.FailedLoginAttempts + 1
	var lockedUntil any
	if attempts >= maxAttempts {
		lockedUntil = time.Now().Add(lockout)
	} else {
		lockedUntil = employee.LockedUntil
	}
	_, err := r.db.Exec(ctx, `
		UPDATE employees
		   SET failed_login_attempts = $2, locked_until = $3, updated_at = now()
		 WHERE id = $1`, employee.ID, attempts, lockedUntil)
	return err
}

func (r *Repository) StoreEmployeeRefreshToken(ctx context.Context, employeeID int64, token string, expiresAt time.Time) error {
	_, err := r.db.Exec(ctx, `
		INSERT INTO refresh_tokens(value, expiration_date_time, zaposlen_id, created_at, updated_at)
		VALUES ($1, $2, $3, now(), now())`, token, expiresAt, employeeID)
	return err
}

func (r *Repository) EmployeeByRefreshToken(ctx context.Context, token string) (Employee, error) {
	rows := r.db.QueryRow(ctx, `
		SELECT e.id, e.ime, e.prezime, e.datum_rodjenja, e.pol, e.email, e.broj_telefona, e.adresa,
		       e.username, e.password, e.pozicija, e.departman, e.aktivan, e.role,
		       e.failed_login_attempts, e.locked_until, e.created_at, e.updated_at
		  FROM refresh_tokens rt
		  JOIN employees e ON e.id = rt.zaposlen_id
		 WHERE rt.deleted = false AND e.deleted = false AND rt.value = $1 AND rt.expiration_date_time > now()`, token)
	return scanEmployee(rows)
}

func (r *Repository) DeleteRefreshToken(ctx context.Context, token string) error {
	_, err := r.db.Exec(ctx, `UPDATE refresh_tokens SET deleted = true, updated_at = now() WHERE value = $1`, token)
	return err
}

func (r *Repository) ConfirmationIDByToken(ctx context.Context, table, ownerColumn, token string) (int64, error) {
	query := `SELECT id FROM ` + table + ` WHERE deleted = false AND value = $1 AND expiration_date_time > now() AND ` + ownerColumn + ` IS NOT NULL`
	var id int64
	if err := r.db.QueryRow(ctx, query, token).Scan(&id); err != nil {
		return 0, mapNotFound(err)
	}
	return id, nil
}

func (r *Repository) UpsertEmployeeConfirmation(ctx context.Context, employeeID int64, tokenHash string, expiresAt time.Time) error {
	_, err := r.db.Exec(ctx, `
		INSERT INTO confirmation_token(value, expiration_date_time, zaposlen_id, created_at, updated_at)
		VALUES ($1, $2, $3, now(), now())
		ON CONFLICT (zaposlen_id)
		DO UPDATE SET value = EXCLUDED.value, expiration_date_time = EXCLUDED.expiration_date_time,
		              deleted = false, updated_at = now()`, tokenHash, expiresAt, employeeID)
	return err
}

func (r *Repository) UpsertClientConfirmation(ctx context.Context, clientID int64, tokenHash string, expiresAt time.Time) error {
	_, err := r.db.Exec(ctx, `
		INSERT INTO client_confirmation_token(value, expiration_date_time, klijent_id, created_at, updated_at)
		VALUES ($1, $2, $3, now(), now())
		ON CONFLICT (klijent_id)
		DO UPDATE SET value = EXCLUDED.value, expiration_date_time = EXCLUDED.expiration_date_time,
		              deleted = false, updated_at = now()`, tokenHash, expiresAt, clientID)
	return err
}

func (r *Repository) ActivateEmployeePassword(ctx context.Context, confirmationID int64, tokenHash string, passwordHash string) error {
	cmd, err := r.db.Exec(ctx, `
		UPDATE employees e
		   SET password = $2, aktivan = true, updated_at = now()
		  FROM confirmation_token ct
		 WHERE ct.id = $1 AND ct.value = $3 AND ct.zaposlen_id = e.id AND ct.deleted = false
		   AND (ct.expiration_date_time IS NULL OR ct.expiration_date_time > now())`, confirmationID, passwordHash, tokenHash)
	if err != nil {
		return err
	}
	if cmd.RowsAffected() == 0 {
		return ErrInvalidToken
	}
	_, _ = r.db.Exec(ctx, `UPDATE confirmation_token SET deleted = true, updated_at = now() WHERE id = $1`, confirmationID)
	return nil
}

func (r *Repository) ActivateClientPassword(ctx context.Context, confirmationID int64, tokenHash string, passwordHash string) error {
	cmd, err := r.db.Exec(ctx, `
		UPDATE clients c
		   SET password = $2, aktivan = true, updated_at = now()
		  FROM client_confirmation_token ct
		 WHERE ct.id = $1 AND ct.value = $3 AND ct.klijent_id = c.id AND ct.deleted = false
		   AND (ct.expiration_date_time IS NULL OR ct.expiration_date_time > now())`, confirmationID, passwordHash, tokenHash)
	if err != nil {
		return err
	}
	if cmd.RowsAffected() == 0 {
		return ErrInvalidToken
	}
	_, _ = r.db.Exec(ctx, `UPDATE client_confirmation_token SET deleted = true, updated_at = now() WHERE id = $1`, confirmationID)
	return nil
}

func (r *Repository) SearchEmployees(ctx context.Context, query SearchQuery) ([]Employee, int, error) {
	where, args := employeeWhere(query)
	return queryEmployees(ctx, r.db, where, args, query.Page, query.Size)
}

func (r *Repository) SearchClients(ctx context.Context, query SearchQuery) ([]Client, int, error) {
	where, args := clientWhere(query)
	return queryClients(ctx, r.db, where, args, query.Page, query.Size)
}

func (r *Repository) CreateEmployee(ctx context.Context, req EmployeeCreateRequest, permissions []string) (Employee, error) {
	dob, err := time.Parse("2006-01-02", req.DatumRodjenja)
	if err != nil {
		return Employee{}, ErrBadRequest
	}
	active := true
	if req.Aktivan != nil {
		active = *req.Aktivan
	}
	role := defaultString(req.Role, "BASIC")
	tx, err := r.db.Begin(ctx)
	if err != nil {
		return Employee{}, err
	}
	defer tx.Rollback(ctx)

	var id int64
	err = tx.QueryRow(ctx, `
		INSERT INTO employees(ime, prezime, datum_rodjenja, pol, email, broj_telefona, adresa,
		                      username, pozicija, departman, aktivan, role, created_at, updated_at)
		VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,now(),now())
		RETURNING id`,
		req.Ime, req.Prezime, dob, req.Pol, req.Email, nilIfBlank(req.BrojTelefona), nilIfBlank(req.Adresa),
		req.Username, req.Pozicija, req.Departman, active, role).Scan(&id)
	if err != nil {
		return Employee{}, mapPgError(err)
	}
	for _, permission := range permissions {
		_, _ = tx.Exec(ctx, `INSERT INTO zaposlen_permissions(zaposlen_id, permission) VALUES ($1, $2) ON CONFLICT DO NOTHING`, id, permission)
	}
	if err := tx.Commit(ctx); err != nil {
		return Employee{}, err
	}
	return r.EmployeeByID(ctx, id)
}

func (r *Repository) UpdateEmployee(ctx context.Context, id int64, req EmployeeUpdateRequest) (Employee, error) {
	current, err := r.EmployeeByID(ctx, id)
	if err != nil {
		return Employee{}, err
	}
	_, err = r.db.Exec(ctx, `
		UPDATE employees
		   SET ime = $2, prezime = $3, email = $4, broj_telefona = $5, adresa = $6,
		       pozicija = $7, departman = $8, aktivan = $9, role = $10, updated_at = now()
		 WHERE id = $1 AND deleted = false`,
		id,
		valueOr(req.Ime, current.Ime), valueOr(req.Prezime, current.Prezime), valueOr(req.Email, current.Email),
		ptrValueOr(req.BrojTelefona, current.BrojTelefona), ptrValueOr(req.Adresa, current.Adresa),
		valueOr(req.Pozicija, current.Pozicija), valueOr(req.Departman, current.Departman),
		boolValueOr(req.Aktivan, current.Aktivan), valueOr(req.Role, current.Role))
	if err != nil {
		return Employee{}, mapPgError(err)
	}
	return r.EmployeeByID(ctx, id)
}

func (r *Repository) SoftDeleteEmployee(ctx context.Context, id int64) error {
	cmd, err := r.db.Exec(ctx, `UPDATE employees SET deleted = true, updated_at = now() WHERE id = $1 AND deleted = false`, id)
	if err != nil {
		return err
	}
	if cmd.RowsAffected() == 0 {
		return ErrNotFound
	}
	return nil
}

func (r *Repository) CreateClient(ctx context.Context, req ClientCreateRequest, permissions []string) (Client, error) {
	role := defaultString(req.Role, "CLIENT_BASIC")
	encryptedJMBG, err := r.jmbg.Encrypt(req.JMBG)
	if err != nil {
		return Client{}, err
	}
	tx, err := r.db.Begin(ctx)
	if err != nil {
		return Client{}, err
	}
	defer tx.Rollback(ctx)

	var id int64
	err = tx.QueryRow(ctx, `
		INSERT INTO clients(ime, prezime, datum_rodjenja, pol, email, broj_telefona, adresa,
		                    jmbg_encrypted, aktivan, role, created_at, updated_at)
		VALUES ($1,$2,$3,$4,$5,$6,$7,$8,false,$9,now(),now())
		RETURNING id`,
		req.Ime, req.Prezime, req.DatumRodjenja, req.Pol, req.Email, normalizePhone(req.BrojTelefona),
		nilIfBlank(req.Adresa), nilIfBlank(encryptedJMBG), role).Scan(&id)
	if err != nil && isUndefinedColumn(err) {
		err = tx.QueryRow(ctx, `
			INSERT INTO clients(ime, prezime, datum_rodjenja, pol, email, broj_telefona, adresa,
			                    jmbg, aktivan, role, created_at, updated_at)
			VALUES ($1,$2,$3,$4,$5,$6,$7,$8,false,$9,now(),now())
			RETURNING id`,
			req.Ime, req.Prezime, req.DatumRodjenja, req.Pol, req.Email, normalizePhone(req.BrojTelefona),
			nilIfBlank(req.Adresa), req.JMBG, role).Scan(&id)
	}
	if err != nil {
		return Client{}, mapPgError(err)
	}
	for _, permission := range permissions {
		_, _ = tx.Exec(ctx, `INSERT INTO client_permissions(client_id, permission) VALUES ($1, $2) ON CONFLICT DO NOTHING`, id, permission)
	}
	if err := tx.Commit(ctx); err != nil {
		return Client{}, err
	}
	return r.ClientByID(ctx, id)
}

func (r *Repository) UpdateClient(ctx context.Context, id int64, req ClientUpdateRequest) (Client, error) {
	current, err := r.ClientByID(ctx, id)
	if err != nil {
		return Client{}, err
	}
	_, err = r.db.Exec(ctx, `
		UPDATE clients
		   SET ime = $2, prezime = $3, email = $4, broj_telefona = $5, adresa = $6, role = $7, updated_at = now()
		 WHERE id = $1 AND deleted = false`,
		id, valueOr(req.Ime, current.Ime), valueOr(req.Prezime, current.Prezime), valueOr(req.Email, current.Email),
		ptrValueOr(req.BrojTelefona, current.BrojTelefona), ptrValueOr(req.Adresa, current.Adresa),
		valueOr(req.Role, current.Role))
	if err != nil {
		return Client{}, mapPgError(err)
	}
	return r.ClientByID(ctx, id)
}

func (r *Repository) AddClientMarginPermission(ctx context.Context, id int64) error {
	_, err := r.db.Exec(ctx, `INSERT INTO client_permissions(client_id, permission) VALUES ($1, 'MARGIN_TRADE') ON CONFLICT DO NOTHING`, id)
	return err
}

func (r *Repository) SoftDeleteClient(ctx context.Context, id int64) error {
	cmd, err := r.db.Exec(ctx, `UPDATE clients SET deleted = true, updated_at = now() WHERE id = $1 AND deleted = false`, id)
	if err != nil {
		return err
	}
	if cmd.RowsAffected() == 0 {
		return ErrNotFound
	}
	return nil
}

type SearchQuery struct {
	Ime       string
	Prezime   string
	Email     string
	Pozicija  string
	Departman string
	Query     string
	Page      int
	Size      int
}

type scanner interface {
	Scan(dest ...any) error
}

func scanEmployee(row scanner) (Employee, error) {
	var employee Employee
	err := row.Scan(&employee.ID, &employee.Ime, &employee.Prezime, &employee.DatumRodjenja, &employee.Pol,
		&employee.Email, &employee.BrojTelefona, &employee.Adresa, &employee.Username, &employee.PasswordHash,
		&employee.Pozicija, &employee.Departman, &employee.Aktivan, &employee.Role, &employee.FailedLoginAttempts,
		&employee.LockedUntil, &employee.CreatedAt, &employee.UpdatedAt)
	return employee, mapNotFound(err)
}

func scanClient(row scanner) (Client, error) {
	var client Client
	err := row.Scan(&client.ID, &client.Ime, &client.Prezime, &client.DatumRodjenja, &client.Pol,
		&client.Email, &client.BrojTelefona, &client.Adresa, &client.PasswordHash, &client.JMBG,
		&client.JMBGEncrypted, &client.Aktivan, &client.Role, &client.CreatedAt, &client.UpdatedAt)
	return client, mapNotFound(err)
}

func queryEmployees(ctx context.Context, db pgxPool, where string, args []any, page, size int) ([]Employee, int, error) {
	var total int
	if err := db.QueryRow(ctx, `SELECT COUNT(*) FROM employees WHERE deleted = false`+where, args...).Scan(&total); err != nil {
		return nil, 0, err
	}
	args = append(args, size, page*size)
	rows, err := db.Query(ctx, `
		SELECT id, ime, prezime, datum_rodjenja, pol, email, broj_telefona, adresa,
		       username, password, pozicija, departman, aktivan, role,
		       failed_login_attempts, locked_until, created_at, updated_at
		  FROM employees
		 WHERE deleted = false`+where+`
		 ORDER BY prezime, ime
		 LIMIT $`+itoa(len(args)-1)+` OFFSET $`+itoa(len(args)), args...)
	if err != nil {
		return nil, 0, err
	}
	defer rows.Close()
	var employees []Employee
	for rows.Next() {
		employee, err := scanEmployee(rows)
		if err != nil {
			return nil, 0, err
		}
		employees = append(employees, employee)
	}
	return employees, total, rows.Err()
}

func queryClients(ctx context.Context, db pgxPool, where string, args []any, page, size int) ([]Client, int, error) {
	var total int
	if err := db.QueryRow(ctx, `SELECT COUNT(*) FROM clients WHERE deleted = false`+where, args...).Scan(&total); err != nil {
		return nil, 0, err
	}
	args = append(args, size, page*size)
	rows, err := db.Query(ctx, `
		SELECT id, ime, prezime, datum_rodjenja, pol, email, broj_telefona, adresa,
		       password, NULL::text AS jmbg, jmbg_encrypted, aktivan, role, created_at, updated_at
		  FROM clients
		 WHERE deleted = false`+where+`
		 ORDER BY prezime, ime
		 LIMIT $`+itoa(len(args)-1)+` OFFSET $`+itoa(len(args)), args...)
	if err != nil && isUndefinedColumn(err) {
		rows, err = db.Query(ctx, `
			SELECT id, ime, prezime, datum_rodjenja, pol, email, broj_telefona, adresa,
			       password, jmbg, NULL::text AS jmbg_encrypted, aktivan, role, created_at, updated_at
			  FROM clients
			 WHERE deleted = false`+where+`
			 ORDER BY prezime, ime
			 LIMIT $`+itoa(len(args)-1)+` OFFSET $`+itoa(len(args)), args...)
	}
	if err != nil {
		return nil, 0, err
	}
	defer rows.Close()
	var clients []Client
	for rows.Next() {
		client, err := scanClient(rows)
		if err != nil {
			return nil, 0, err
		}
		clients = append(clients, client)
	}
	return clients, total, rows.Err()
}

func employeeWhere(query SearchQuery) (string, []any) {
	var clauses []string
	var args []any
	addLike := func(column, value string) {
		if strings.TrimSpace(value) == "" {
			return
		}
		args = append(args, "%"+strings.ToLower(strings.TrimSpace(value))+"%")
		clauses = append(clauses, "lower("+column+") LIKE $"+itoa(len(args)))
	}
	addLike("ime", query.Ime)
	addLike("prezime", query.Prezime)
	addLike("email", query.Email)
	addLike("pozicija", query.Pozicija)
	addLike("departman", query.Departman)
	if strings.TrimSpace(query.Query) != "" {
		args = append(args, "%"+strings.ToLower(strings.TrimSpace(query.Query))+"%")
		n := itoa(len(args))
		clauses = append(clauses, "(lower(ime) LIKE $"+n+" OR lower(prezime) LIKE $"+n+" OR lower(email) LIKE $"+n+" OR lower(pozicija) LIKE $"+n+")")
	}
	if len(clauses) == 0 {
		return "", args
	}
	return " AND " + strings.Join(clauses, " AND "), args
}

func clientWhere(query SearchQuery) (string, []any) {
	var clauses []string
	var args []any
	addLike := func(column, value string) {
		if strings.TrimSpace(value) == "" {
			return
		}
		args = append(args, "%"+strings.ToLower(strings.TrimSpace(value))+"%")
		clauses = append(clauses, "lower("+column+") LIKE $"+itoa(len(args)))
	}
	addLike("ime", query.Ime)
	addLike("prezime", query.Prezime)
	addLike("email", query.Email)
	if strings.TrimSpace(query.Query) != "" {
		args = append(args, "%"+strings.ToLower(strings.TrimSpace(query.Query))+"%")
		n := itoa(len(args))
		clauses = append(clauses, "(lower(ime) LIKE $"+n+" OR lower(prezime) LIKE $"+n+" OR lower(email) LIKE $"+n+")")
	}
	if len(clauses) == 0 {
		return "", args
	}
	return " AND " + strings.Join(clauses, " AND "), args
}

func mapNotFound(err error) error {
	if err == nil {
		return nil
	}
	if errors.Is(err, pgx.ErrNoRows) {
		return ErrNotFound
	}
	return err
}

func mapPgError(err error) error {
	var pgErr *pgconn.PgError
	if errors.As(err, &pgErr) && pgErr.Code == "23505" {
		return ErrDuplicate
	}
	return err
}

func isUndefinedColumn(err error) bool {
	var pgErr *pgconn.PgError
	return errors.As(err, &pgErr) && pgErr.Code == "42703"
}

func nilIfBlank(value string) any {
	value = strings.TrimSpace(value)
	if value == "" {
		return nil
	}
	return value
}

func normalizePhone(value string) any {
	value = strings.TrimSpace(value)
	if value == "" {
		return nil
	}
	if strings.HasPrefix(value, "+") {
		return value
	}
	if strings.HasPrefix(value, "00") {
		return "+" + strings.TrimPrefix(value, "00")
	}
	if strings.HasPrefix(value, "0") {
		return "+381" + strings.TrimPrefix(value, "0")
	}
	return "+" + value
}

func defaultString(value, fallback string) string {
	value = strings.TrimSpace(value)
	if value == "" {
		return fallback
	}
	return value
}

func valueOr(value *string, fallback string) string {
	if value == nil {
		return fallback
	}
	return *value
}

func ptrValueOr(value *string, fallback *string) any {
	if value == nil {
		return fallback
	}
	return nilIfBlank(*value)
}

func boolValueOr(value *bool, fallback bool) bool {
	if value == nil {
		return fallback
	}
	return *value
}

func itoa(value int) string {
	if value == 0 {
		return "0"
	}
	var buf [20]byte
	i := len(buf)
	for value > 0 {
		i--
		buf[i] = byte('0' + value%10)
		value /= 10
	}
	return string(buf[i:])
}
