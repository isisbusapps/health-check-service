package com.stfc.UserOffice.service;

import com.stfc.UserOffice.clients.UOWS;
import com.stfc.UserOffice.dto.Status;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.Objects;
import java.util.logging.Logger;

@ApplicationScoped
public class HealthChecker {

    private static final Logger LOGGER = Logger.getLogger(HealthChecker.class.getName());

    @RestClient
    UOWS uows;

    private Boolean up;

    public Boolean isUp() {
        return up;
    }

    @Scheduled(every = "10s")
    public void check() {
        Status status = uows.checkUOWS();
        up = Objects.equals(status.status(), "UP");
        LOGGER.info("UOWS status: " + status.status());
    }
}
