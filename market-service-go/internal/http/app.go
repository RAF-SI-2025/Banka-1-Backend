package http

import (
	"context"
	"log/slog"
	"time"

	"banka1/market-service-go/internal/fx"
	"banka1/market-service-go/internal/market"
	"banka1/market-service-go/internal/platform"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/robfig/cron/v3"
)

type App struct {
	Config         platform.Config
	Logger         *slog.Logger
	MarketRepo     ListingTypeResolver
	FXRepo         *fx.Repository
	MarketService  MarketServicePort
	FXService      FXServicePort
	PriceFeed      PriceFeedPort
	EventPublisher platform.EventPublisher
	schedulerClose func()
}

func NewApp(ctx context.Context, cfg platform.Config, db *pgxpool.Pool, logger *slog.Logger) (*App, error) {
	marketRepo := market.NewRepository(db)
	fxRepo := fx.NewRepository(db)
	fxService := fx.NewService(cfg, fxRepo)
	marketService := market.NewService(cfg, marketRepo, fxService, logger)
	priceFeed := market.NewPriceFeedService(cfg, logger)
	eventPublisher, err := platform.NewRabbitPublisher(ctx, logger)
	if err != nil {
		return nil, err
	}
	marketService.SetPriceAlertPublisher(market.NewRabbitPriceAlertPublisher(eventPublisher))
	app := &App{
		Config:         cfg,
		Logger:         logger,
		MarketRepo:     marketRepo,
		FXRepo:         fxRepo,
		MarketService:  marketService,
		FXService:      fxService,
		PriceFeed:      priceFeed,
		EventPublisher: eventPublisher,
	}
	scheduler := cron.New(cron.WithSeconds())
	if cfg.Stock.RefreshEnabled {
		ticker := time.NewTicker(cfg.Stock.RefreshInterval)
		stopCh := make(chan struct{})
		go func() {
			for {
				select {
				case <-ctx.Done():
					return
				case <-stopCh:
					return
				case <-ticker.C:
					_ = marketService.RefreshOpenListings(context.Background())
				}
			}
		}()
		app.schedulerClose = func() {
			close(stopCh)
			ticker.Stop()
			stopCtx := scheduler.Stop()
			<-stopCtx.Done()
		}
	} else {
		app.schedulerClose = func() {
			stopCtx := scheduler.Stop()
			<-stopCtx.Done()
		}
	}
	if cfg.FX.FetchCron != "" {
		_, _ = scheduler.AddFunc(cfg.FX.FetchCron, func() {
			_, _ = fxService.FetchAndStoreDailyRates(context.Background())
		})
	}
	scheduler.Start()
	if cfg.FX.FetchOnStartup {
		_, _ = fxService.FetchAndStoreDailyRates(ctx)
	}
	return app, nil
}

func (a *App) Close() {
	if a.schedulerClose != nil {
		a.schedulerClose()
	}
	if a.EventPublisher != nil {
		a.EventPublisher.Close()
	}
}
