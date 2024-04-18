import {Construct} from "constructs";
import {Environment} from "../common/environments";
import {getErrorRecipients} from "../recipients";
import {Monitor} from "../common/monitor";
import {Comparator, metric_sum_query, trace_analytics_count_query} from "../common/queries";

export class ShopApiTaxRefundRequestJobMonitors extends Construct {
    constructor(scope: Construct, environment: Environment) {
        const job = 'web-shop-api-tax-refund-request-job';
        super(scope, `${job}_${environment}`);
        const trace_alert_config = {
            recipients: getErrorRecipients(environment),
            type: "trace-analytics alert",
            monitorThresholds: {
                critical: "3",
                warning: "1",
            },
        }

        const window = "5m";
        const serviceName = `${job}`;
        const common_query = `service:${serviceName} env:${environment}`;

        const error_query = `${common_query} status:error`;
        const warn_query = `$${common_query} status:warn`;
        const tags = [serviceName,`env:${environment}`];

        new Monitor(this, "service_error_rate_high", {
            query: trace_analytics_count_query(
                `${error_query}`,
                window,
                trace_alert_config.monitorThresholds.critical,
            ),
            name: `${job} Error rate too high`,
            message:
            `${job}: throughput deviated too much from its usual value.`,
            tags: tags,
            ...trace_alert_config,
        });

        new Monitor(this, "service_warning_rate_high", {
            query: trace_analytics_count_query(
                `${warn_query}`,
                window,
                trace_alert_config.monitorThresholds.critical,
            ),
            name: `${job} Warning rate too high`,
            message:
            `${job}: throughput deviated too much from its usual value.`,
            tags: tags,
            ...trace_alert_config,
        });

        const metricsHitsQuery = `sum:trace.TaxRefundRequestUsecase_run.hits{service:${serviceName},env:${environment}}.as_count()`;
        const executionRateWindow = "24h";
        const executionRateConfig = {
            recipients: getErrorRecipients(environment),
            type: "metric alert",
            monitorThresholds: {
                critical: "1",
                warning: "6",
            },
        }
        new Monitor(this, "execution_rate_too_low", {
            query: metric_sum_query(
                metricsHitsQuery,
                executionRateWindow,
                executionRateConfig.monitorThresholds.critical,
                Comparator.Below
            ),
            name: `${job} Execution rate too low`,
            message:
            `${job} Periodic job's execution rate is too low.`,
            tags: tags,
            ...executionRateConfig,
        });

        const commitEndpoint = "/sq-bitkey-dmz_ingress/bitkey-web/taxinator/services/squareup.taxinator.service.TaxinatorService/ComputeAndCommitTax"
        new Monitor(this, "report_return_tax_api_error_rate_too_high", {
            query: trace_analytics_count_query(
                `${error_query} @http.method:POST @http.path_group:"${commitEndpoint}"`,
                window,
                trace_alert_config.monitorThresholds.critical,
            ),
            name: `${job}: Taxinator Tax Refund Report Error rate too high`,
            message:
            `${job}: throughput deviated too much from its usual value.`,
            tags: tags,
            ...trace_alert_config,
        });
    }
}