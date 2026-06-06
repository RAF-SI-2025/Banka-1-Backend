module banka1/market-service-go

go 1.25.0

replace banka1/go-platform => ../go-platform

require (
	banka1/go-platform v0.0.0-00010101000000-000000000000
	github.com/golang-jwt/jwt/v5 v5.3.1
	github.com/jackc/pgx/v5 v5.9.2
	github.com/redis/go-redis/v9 v9.5.1
	github.com/robfig/cron/v3 v3.0.1
	github.com/shopspring/decimal v1.4.0
	github.com/stretchr/testify v1.11.1
	google.golang.org/grpc v1.81.1
	google.golang.org/protobuf v1.36.11
)

require (
	github.com/cespare/xxhash/v2 v2.3.0 // indirect
	github.com/davecgh/go-spew v1.1.1 // indirect
	github.com/dgryski/go-rendezvous v0.0.0-20200823014737-9f7001d12a5f // indirect
	github.com/jackc/pgpassfile v1.0.0 // indirect
	github.com/jackc/pgservicefile v0.0.0-20240606120523-5a60cdf6a761 // indirect
	github.com/jackc/puddle/v2 v2.2.2 // indirect
	github.com/kr/text v0.2.0 // indirect
	github.com/pashagolub/pgxmock/v4 v4.9.0 // indirect
	github.com/pmezard/go-difflib v1.0.0 // indirect
	github.com/rabbitmq/amqp091-go v1.11.0 // indirect
	github.com/rogpeppe/go-internal v1.15.0 // indirect
	golang.org/x/net v0.52.0 // indirect
	golang.org/x/sync v0.20.0 // indirect
	golang.org/x/sys v0.42.0 // indirect
	golang.org/x/text v0.35.0 // indirect
	google.golang.org/genproto/googleapis/rpc v0.0.0-20260401024825-9d38bb4040a9 // indirect
	gopkg.in/yaml.v3 v3.0.1 // indirect
)
