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
            AtomicInteger gauge = new AtomicInteger(0);
            gauges.put(name, gauge);
            registry.gauge("service.status", Tags.of("service", name.toLowerCase()), gauge, AtomicInteger::get);
        }
    }

    @Scheduled(every = "10s")
    public void check() {
        for (Server s : servers) {
            String name = s.name == null ? "unknown" : s.name;
            AtomicInteger gauge = gauges.get(name);
            String url = s.url;
            if (url == null || url.isBlank()) {
                gauge.set(0);
                LOGGER.infof("%s is DOWN (no URL)", name);
                continue;
            }
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(5))
                        .GET()
                        .build();
                HttpResponse<Void> resp = httpClient.send(req, HttpResponse.BodyHandlers.discarding());
                if (resp.statusCode() == 200) {
                    gauge.set(1);
                    LOGGER.infof("%s is UP", name);
                } else {
                    gauge.set(0);
                    LOGGER.infof("%s is DOWN (status %d)", name, resp.statusCode());
                }
            } catch (Exception e) {
                gauge.set(0);
                LOGGER.infof("%s is DOWN (%s)", name, e.getMessage());
            }
        }
    }
}