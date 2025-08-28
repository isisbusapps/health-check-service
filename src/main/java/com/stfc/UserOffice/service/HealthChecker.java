package com.stfc.UserOffice.service;

import com.stfc.UserOffice.dto.Server;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.quarkus.scheduler.Scheduled;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@ApplicationScoped
public class HealthChecker {

    private static final Logger LOGGER = Logger.getLogger(HealthChecker.class.getName());

    @Inject
    MeterRegistry registry;

    @Inject
    ServerLoader serverLoader;

    private final Map<String, AtomicInteger> gauges = new HashMap<>();
    private final Map<String, AtomicInteger> localGauges = new HashMap<>();
    private final Map<String, AtomicInteger> combinedGauges = new HashMap<>();
    private volatile List<Server> servers;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    @PostConstruct
    void init() {
        servers = serverLoader.getServers();
        if (servers == null || servers.isEmpty()) {
            LOGGER.warn("No servers configured for health check");
        }
        for (Server s : servers) {
            String name = s.name == null ? "unknown" : s.name;
            AtomicInteger defaultGauge = new AtomicInteger(0);
            gauges.put(name, defaultGauge);
            registry.gauge("service.status.default", Tags.of("service", name.toLowerCase()), defaultGauge, AtomicInteger::get);

            if (s.localUrl != null && !s.localUrl.isBlank()) {
                AtomicInteger localGauge = new AtomicInteger(0);
                localGauges.put(name, localGauge);
                registry.gauge("service.status.local", Tags.of("service", name.toLowerCase()), localGauge, AtomicInteger::get);

                AtomicInteger combinedGauge = new AtomicInteger(0);
                combinedGauges.put(name, combinedGauge);
                registry.gauge("service.status.combined", Tags.of("service", name.toLowerCase()), combinedGauge, AtomicInteger::get);
            }
        }
    }

    @Scheduled(every = "10s")
    public void check() {
        for (Server s : servers) {
            String name = s.name == null ? "unknown" : s.name;
            AtomicInteger defaultGauge = gauges.get(name);
            String defaultUrl = s.url;
            boolean defaultOk = false;

            if (defaultUrl == null || defaultUrl.isBlank()) {
                defaultGauge.set(0);
                LOGGER.infof("%s default is DOWN (no URL)", name);
            } else {
                defaultOk = performCheck(defaultUrl, name + " default");
                defaultGauge.set(defaultOk ? 1 : 0);
            }

            String localUrl = s.localUrl;
            AtomicInteger localGauge = localGauges.get(name);
            AtomicInteger combinedGauge = combinedGauges.get(name);

            if (localGauge != null) { // means localUrl configured during init
                boolean localOk = false;
                if (localUrl == null || localUrl.isBlank()) {
                    localGauge.set(0);
                    LOGGER.infof("%s local is DOWN (no local URL)", name);
                } else {
                    localOk = performCheck(localUrl, name + " local");
                    localGauge.set(localOk ? 1 : 0);
                }
                if (combinedGauge != null) {
                    combinedGauge.set((defaultOk && localOk) ? 1 : 0);
                }
            }
        }
    }

    private boolean performCheck(String url, String label) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<Void> resp = httpClient.send(req, HttpResponse.BodyHandlers.discarding());
            if (resp.statusCode() == 200) {
                LOGGER.infof("%s is UP", label);
                return true;
            } else {
                LOGGER.infof("%s is DOWN (status %d)", label, resp.statusCode());
                return false;
            }
        } catch (Exception e) {
            LOGGER.infof("%s is DOWN (%s)", label, e.getMessage());
            return false;
        }
    }
}