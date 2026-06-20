package com.restprovider.core;

import java.io.IOException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;

public class RestProviderServer {
    private static final Logger LOGGER = LogManager.getLogger(RestProviderServer.class);
    private final HttpServer server;

    public RestProviderServer(int port, ControllerRegistry registry) {
        this.server = ServerBootstrap.bootstrap()
                .setListenerPort(port)
                .register("*", new ControllerDispatcher(registry))
                .create();
    }

    public void start() throws IOException {
        LOGGER.info("Starting RestProvider HTTP server");
        server.start();
    }

    public void stop() {
        LOGGER.info("Stopping RestProvider HTTP server");
        server.close();
    }
}
