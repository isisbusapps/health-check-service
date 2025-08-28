package com.stfc.UserOffice.service;

import com.stfc.UserOffice.clients.UOWS;
import com.stfc.UserOffice.clients.Visits;
import com.stfc.UserOffice.dto.Status;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.quarkus.scheduler.Scheduled;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.resteasy.reactive.RestResponse;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

@ApplicationScoped
public class HealthChecker {

    private static final Logger LOGGER = Logger.getLogger(HealthChecker.class.getName());

    @RestClient
    UOWS uows;

    @RestClient
    Visits visits;

    @Inject
    MeterRegistry registry;

    private final AtomicInteger uowsGauge = new AtomicInteger(0);
    private final AtomicInteger visitsGauge = new AtomicInteger(0);

    @PostConstruct
    void init() {
        registry.gauge("service.status", Tags.of("service", "uows"), uowsGauge, AtomicInteger::get);
        registry.gauge("service.status", Tags.of("service", "visits"), visitsGauge, AtomicInteger::get);
    }

    @Scheduled(every = "10s")
    public void check() {
        try (RestResponse<Status> restResponse = uows.checkUOWS()) {
            if (restResponse.getStatus() != 200) {
                uowsGauge.set(0);
                LOGGER.info("UOWS is DOWN");
                return;
            }
            uowsGauge.set(1);
            LOGGER.info("UOWS is UP");
        } catch (Exception e) {
            uowsGauge.set(0);
            LOGGER.info("UOWS is DOWN");
        }

        try (RestResponse<Status> restResponse = visits.checkVisits()) {
            if (restResponse.getStatus() != 200) {
                visitsGauge.set(0);
                LOGGER.info("Visits is DOWN");
                return;
            }
            visitsGauge.set(1);
            LOGGER.info("visits is UP");
        } catch (Exception e) {
            visitsGauge.set(0);
            LOGGER.info("visits is DOWN");
        }
    }
}