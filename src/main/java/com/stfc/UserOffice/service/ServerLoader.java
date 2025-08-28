package com.stfc.UserOffice.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stfc.UserOffice.dto.Server;
import io.quarkus.arc.log.LoggerName;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@ApplicationScoped
public class ServerLoader {

    @LoggerName("ServerLoader")
    Logger serverLoaderLogger;

    @Inject
    ObjectMapper objectMapper;

    private List<Server> server;

    void onStart(@Observes StartupEvent ev) {
        try (InputStream s = ServerLoader.class.getResourceAsStream("/server.json")) {
            if (s == null) {
                serverLoaderLogger.fatal("server.json not found");
                throw new IOException("server.json not found");
            }
            server = objectMapper.readValue(s, new TypeReference<>() {
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public List<Server> getServers() {
        return server;
    }
}
