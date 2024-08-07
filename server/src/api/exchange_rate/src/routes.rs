use crate::flags::FLAG_USE_CASH_EXCHANGE_RATE_PROVIDER;
use axum::extract::State;
use axum::routing::{get, post};
use axum::{Json, Router};
use errors::ApiError;
use feature_flags::service::Service as FeatureFlagsService;
use http_server::swagger::{SwaggerEndpoint, Url};
use serde::{Deserialize, Serialize};
use time::OffsetDateTime;
use tracing::instrument;
use types::currencies::{
    Currency, CurrencyCode, CurrencyData, FiatCurrency, FiatDisplayConfiguration,
};
use types::exchange_rate::bitstamp::BitstampRateProvider;
use types::exchange_rate::cash::CashAppRateProvider;
use types::exchange_rate::coingecko::RateProvider as CoingeckoRateProvider;
use types::exchange_rate::ExchangeRate;
use types::serde::{deserialize_iso_4217, deserialize_ts_vec};
use utoipa::{OpenApi, ToSchema};

use crate::service::Service as ExchangeRateService;

#[derive(Clone, axum_macros::FromRef)]
pub struct RouteState(pub ExchangeRateService, pub FeatureFlagsService);

impl RouteState {
    pub fn unauthed_router(&self) -> Router {
        Router::new()
            .route("/api/exchange-rates", get(get_supported_price_data))
            .route(
                "/api/exchange-rates/currencies",
                get(get_supported_currencies),
            )
            .with_state(self.to_owned())
    }

    pub fn basic_validation_router(&self) -> Router {
        Router::new()
            .route(
                "/api/exchange-rates/historical",
                post(get_historical_price_data),
            )
            .with_state(self.to_owned())
    }
}
impl From<RouteState> for SwaggerEndpoint {
    fn from(_: RouteState) -> Self {
        (
            Url::new("Exchange Rates", "/docs/exchange-rate/openapi.json"),
            ApiDoc::openapi(),
        )
    }
}

#[derive(OpenApi)]
#[openapi(
    paths(
        get_supported_currencies,
        get_supported_price_data,
        get_historical_price_data,
    ),
    components(
        schemas(SupportedFiatCurrenciesResponse, FiatCurrency, SupportedPriceDataResponse, ExchangeRate, CurrencyData, FiatDisplayConfiguration, HistoricalPriceQuery, HistoricalPriceResponse)
    ),
    tags(
        (name = "Exchange Rates", description = "Exchange Rate Price Data"),
    )
)]
struct ApiDoc;

#[utoipa::path(
    get,
    path = "/api/exchange-rates/currencies",
    responses(
        (status = 200, description = "Retrieved a list of supported fiat currencies", body=SupportedFiatCurrenciesResponse)
    ),
)]
pub async fn get_supported_currencies() -> Result<Json<SupportedFiatCurrenciesResponse>, ApiError> {
    Ok(Json(SupportedFiatCurrenciesResponse {
        supported_currencies: Currency::supported_fiat_currencies(),
    }))
}

#[derive(Serialize, Deserialize, Debug, PartialEq, ToSchema)]
pub struct SupportedFiatCurrenciesResponse {
    pub supported_currencies: Vec<FiatCurrency>,
}

#[instrument(err, skip(exchange_rate_service, feature_flags_service))]
#[utoipa::path(
    get,
    path = "/api/exchange-rates",
    responses(
        (status = 200, description = "Retrieved a list of supported fiat currencies", body=SupportedFiatCurrenciesResponse)
    ),
)]
pub async fn get_supported_price_data(
    State(exchange_rate_service): State<ExchangeRateService>,
    State(feature_flags_service): State<FeatureFlagsService>,
) -> Result<Json<SupportedPriceDataResponse>, ApiError> {
    let use_cash_app_rate = FLAG_USE_CASH_EXCHANGE_RATE_PROVIDER
        .resolver(&feature_flags_service)
        .resolve();

    let exchange_rates = if use_cash_app_rate {
        exchange_rate_service
            .get_latest_rates(CashAppRateProvider::new())
            .await?
    } else {
        exchange_rate_service
            .get_latest_rates(BitstampRateProvider::new())
            .await?
    };

    Ok(Json(SupportedPriceDataResponse { exchange_rates }))
}

#[derive(Serialize, Deserialize, Debug, PartialEq, ToSchema)]
pub struct SupportedPriceDataResponse {
    exchange_rates: Vec<ExchangeRate>,
}

#[instrument(err, skip(exchange_rate_service))]
#[utoipa::path(
    post,
    path = "/api/exchange-rates/historical",
    responses(
        (status = 200, description = "Retrieve price of bitcoin at a specific time for a specific currency.", body=HistoricalPriceResponse)
    ),
)]
pub async fn get_historical_price_data(
    State(exchange_rate_service): State<ExchangeRateService>,
    Json(request): Json<HistoricalPriceQuery>,
) -> Result<Json<HistoricalPriceResponse>, ApiError> {
    let exchange_rates = exchange_rate_service
        .get_historical_rates(
            CoingeckoRateProvider::new(),
            request.currency_code,
            request.timestamps,
        )
        .await?;

    Ok(Json(HistoricalPriceResponse { exchange_rates }))
}

#[derive(Debug, Deserialize, ToSchema)]
pub struct HistoricalPriceQuery {
    #[serde(deserialize_with = "deserialize_iso_4217")]
    currency_code: CurrencyCode,
    #[serde(deserialize_with = "deserialize_ts_vec")]
    timestamps: Vec<OffsetDateTime>,
}

#[derive(Serialize, Deserialize, Debug, PartialEq, ToSchema)]
pub struct HistoricalPriceResponse {
    pub exchange_rates: Vec<ExchangeRate>,
}
