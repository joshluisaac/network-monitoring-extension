package com.appdynamics.extensions.network;

import com.appdynamics.extensions.AMonitorTaskRunnable;
import com.appdynamics.extensions.MetricWriteHelper;
import com.appdynamics.extensions.conf.MonitorContextConfiguration;
import com.appdynamics.extensions.logging.ExtensionsLoggerFactory;
import com.appdynamics.extensions.metrics.Metric;
import com.appdynamics.extensions.network.config.ScriptFile;
import com.appdynamics.extensions.network.input.Stat;
import org.slf4j.Logger;

import java.util.*;

import static com.appdynamics.extensions.network.NetworkConstants.DEFAULT_SCRIPT_TIMEOUT_IN_SEC;

public class NetworkMonitorTask implements AMonitorTaskRunnable {
    private static final Logger logger = ExtensionsLoggerFactory.getLogger(NetworkMonitorTask.class);

    private MonitorContextConfiguration monitorContextConfiguration;
    private MetricWriteHelper metricWriteHelper;
    public NetworkMonitorTask(MonitorContextConfiguration contextConfiguration, MetricWriteHelper metricWriteHelper) {
        this.monitorContextConfiguration = contextConfiguration;
        this.metricWriteHelper = metricWriteHelper;
    }

    @Override
    public void onTaskComplete() {
        logger.info("Network Monitoring Extension Task completed");
    }

    @Override
    public void run() {
        Map<String, ?> config = monitorContextConfiguration.getConfigYml();
        collectAndPrintMetrics(config);
    }

    private void collectAndPrintMetrics(Map<String, ?> config) {
        ScriptMetrics scriptMetrics = getScriptMetrics(config);
        Set<String> networkInterfaces = new HashSet<>((ArrayList<String>)config.get("networkInterfaces"));
        SigarMetrics sigarMetrics = new SigarMetrics(networkInterfaces);

        Stat.Stats statsFromMetricsXml = (Stat.Stats) monitorContextConfiguration.getMetricsXml();
        String metricPrefix = monitorContextConfiguration.getMetricPrefix();
        NetworkMetricsCollector metricsCollector = new NetworkMetricsCollector
                (sigarMetrics, scriptMetrics, networkInterfaces, statsFromMetricsXml, metricPrefix);

        uploadMetrics(metricsCollector.collectMetrics());
    }

    private ScriptMetrics getScriptMetrics(Map<String, ?> config) {
        ScriptMetrics scriptMetrics = null;

        if ((Boolean) config.get("overrideMetricsUsingScriptFile")) {
            logger.debug("Override metrics using script is enabled, attempting to retrieve metrics from script...");

            try {
                ScriptMetricsExecutor scriptMetricsExecutor = new ScriptMetricsExecutor();
                scriptMetrics = scriptMetricsExecutor.executeAndCollectScriptMetrics(
                        (List<ScriptFile>)config.get("scriptFiles"), getScriptTimeout(config));

            } catch (Exception ex) {
                logger.error("Unfortunately an error has occurred while fetching metrics from script", ex);
            }
        }

        return scriptMetrics != null ? scriptMetrics : new ScriptMetrics();
    }

    private long getScriptTimeout(Map<String, ?> config) {
        int scriptTimeout = (Integer) config.get("scriptTimeoutInSec");
        return scriptTimeout > 0 ? scriptTimeout : DEFAULT_SCRIPT_TIMEOUT_IN_SEC;
    }

    private void uploadMetrics(List<Metric> metrics) {

        metricWriteHelper.transformAndPrintMetrics(metrics);
    }
}
