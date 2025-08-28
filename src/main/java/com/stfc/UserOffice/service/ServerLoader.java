package com.stfc.UserOffice.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stfc.UserOffice.dto.Server;
import io.quarkus.arc.log.LoggerName;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@ApplicationScoped
public class ServerLoader {

    @LoggerName("ServerLoader")
    Logger serverLoaderLogger;

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "servers.file", defaultValue = "servers.json")
    String serversFilePath;

    private List<Server> server;

    void onStart(@Observes StartupEvent ev) {
        Path path = Path.of(serversFilePath);
        serverLoaderLogger.infof("Loading servers from file: %s", path.toAbsolutePath());
        try (InputStream s = Files.newInputStream(path)) {
            server = objectMapper.readValue(s, new TypeReference<>() {
            });
        } catch (IOException e) {
            serverLoaderLogger.fatalf("Failed to load servers file at %s: %s", path.toAbsolutePath(), e.getMessage());
            throw new RuntimeException("Failed to load servers file: " + path.toAbsolutePath(), e);
        }
    }

    public List<Server> getServers() {
        return server;
    }
}
