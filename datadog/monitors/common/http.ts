import { Monitor, MonitorConfig, MonitorMonitorThresholds } from "./monitor";
import { Construct } from "constructs";

type BaseConfig = Omit<MonitorConfig, "query" | "type">;

interface ErrorRateHighMonitorConfig extends BaseConfig {
  tags: string[],
  window?: string,
  monitorThresholds: MonitorMonitorThresholds,
}

const errorRateHighMonitorConfigDefaults: Partial<ErrorRateHighMonitorConfig> = {
  window: "last_10m",
}

/**
 * ErrorRateHighMonitor creates a monitor that alerts if the total number of errors
 * within a time window exceeds a threshold.
 */
export class ErrorRateHighMonitor extends Construct {
  constructor(scope: Construct, id: string, config: ErrorRateHighMonitorConfig) {
    super(scope, id);

    config = {
      ...errorRateHighMonitorConfigDefaults,
      ...config,
    }
    const {
      tags,
      window,
      ...monitorConfig
    } = config
    const tagstring = tags.join(",");

    new Monitor(this, `monitor`, {
      query: `sum(${window}): (
        sum:trace.web.request.errors{${tagstring}}.as_count()
        / sum:trace.web.request.hits{${tagstring}}.as_count()
      ) > ${config.monitorThresholds?.critical}`,
      ...monitorConfig,
      type: "query alert",
      tags: tags,
    });
  }
}

interface AverageLatencyHighMonitorConfig extends BaseConfig {
  tags: string[],
  window?: string,
  monitorThresholds: MonitorMonitorThresholds,
}

const averageLatencyHighMonitorConfigDefaults: Partial<AverageLatencyHighMonitorConfig> = {
  window: "last_10m",
}

/**
 * AverageLatencyHighMonitor creates a monitor that alerts if the average latency within
 * a window exceeds the threshold.
 */
export class AverageLatencyHighMonitor extends Construct {
  constructor(scope: Construct, id: string, config: AverageLatencyHighMonitorConfig) {
    super(scope, id);

    config = {
      ...averageLatencyHighMonitorConfigDefaults,
      ...config,
    }
    const {
      tags,
      window,
      ...monitorConfig
    } = config
    const tagstring = tags.join(",");

    new Monitor(this, `avg_latency_high`, {
      query: `sum(${window}): (
        sum:trace.web.request.duration{${tagstring}}.rollup(sum).fill(zero)
        / sum:trace.web.request.hits{${tagstring}}
      ) > ${config.monitorThresholds?.critical}`,
      ...monitorConfig,
      type: "query alert",
      tags: tags,
    });
  }
}

interface PercentileLatencyHighMonitorConfig extends BaseConfig {
  percentile: number,
  tags: string[],
  window?: string,
  monitorThresholds: MonitorMonitorThresholds,
}

const percentileLatencyHighMonitorConfigDefaults: Partial<PercentileLatencyHighMonitorConfig> = {
  window: "last_10m",
}

/**
 * PercentileLatencyHighMonitor creates a monitor that alerts if the percentile latency within
 * a window exceeds the threshold.
 */
export class PercentileLatencyHighMonitor extends Construct {
  constructor(scope: Construct, id: string, config: PercentileLatencyHighMonitorConfig) {
    super(scope, id);

    config = {
      ...percentileLatencyHighMonitorConfigDefaults,
      ...config,
    }
    const {
      tags,
      window,
      percentile,
      ...monitorConfig
    } = config
    const tagstring = tags.join(",");

    if (!Number.isInteger(percentile) || percentile < 0) {
      throw new Error(`percentile must be a position integer but was ${percentile}`)
    }

    new Monitor(this, `avg_latency_high`, {
      query:
        `percentile(${window}):p${percentile}:trace.web.request{${tagstring}} > ${config.monitorThresholds?.critical}`,
      ...monitorConfig,
      type: "query alert",
      tags: tags,
    });
  }
}

interface HttpStatusConfig {
  status: "1xx" | "2xx" | "3xx" | "4xx" | "5xx",
  group: string,
  environment: string,
  tags: string[],
  window?: string,
  rateThreshold: string,
  countThreshold: string,
  recipients: string[],
  dataDogLink?: string,
}

const httpStatusConfigDefaults: Partial<HttpStatusConfig> = {
  window: "30m",
}

/**
 * HttpStatusCompositeMonitor creates a rate monitor, a count monitor, and a composite monitor,
 * the latter of which alerts if a given http status code rate && absolute count exceed their
 * thresholds in a rolling 30-minute window
 */
export class HttpStatusCompositeMonitor extends Construct {
  constructor(scope: Construct, id: string, config: HttpStatusConfig) {
    super(scope, id);

    config = {
      ...httpStatusConfigDefaults,
      ...config,
    }
    const {
      status,
      group,
      environment,
      window,
      tags,
      rateThreshold,
      countThreshold,
      recipients,
      dataDogLink,
    } = config

    // https://docs.datadoghq.com/monitors/guide/as-count-in-monitor-evaluations/
    // This query represents the $window rolling rate of requests of status $status
    // (# of responses of status $status in $window / # of responses in $window)
    let rateMonitor = new Monitor(this, 'http_status_rate_high', {
      query:
        `sum(last_${window}):
             default_zero(sum:bitkey.http.response{${[`status:${status}`, `env:${environment}`].concat(tags).join(",")}}.as_count() /
             sum:bitkey.http.response{${[`env:${environment}`].concat(tags).join(",")}}.as_count())
         > ${rateThreshold}`,
      name: `[${group}] high ${status} http status rate on env:${environment} (Composite 1/2)`,
      message: `${status} http status rate is too high.`,
      monitorThresholds: {
        critical: rateThreshold,
      },
      type: "query alert",
      tags,
      recipients: [],
    });

    let countMonitor = new Monitor(this, 'http_status_count_high', {
      query:
        `sum(last_${window}):
             sum:bitkey.http.response{${[`status:${status}`, `env:${environment}`].concat(tags).join(",")}}.as_count()
         > ${countThreshold}`,
      name: `[${group}] high ${status} http status count on env:${environment} (Composite 2/2)`,
      message: `${status} http status count is too high.`,
      monitorThresholds: {
        critical: countThreshold,
      },
      type: "query alert",
      tags,
      recipients: [],
    });

    new Monitor(this, 'http_status_composite', {
      query: `${rateMonitor.id} && ${countMonitor.id}`,
      name: `[${group}] ${status} http status thresholds exceeded on env:${environment} (Composite)`,
      message: `${status} http status thresholds exceeded.`,
      type: "composite",
      tags,
      dataDogLink,
      recipients,
    });
  }
}
