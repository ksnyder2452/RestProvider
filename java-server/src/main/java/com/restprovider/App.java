package com.restprovider;

import com.restprovider.core.ControllerRegistry;
import com.restprovider.core.DefaultRegistryFactory;
import com.restprovider.core.RestProviderServer;
import java.util.concurrent.CountDownLatch;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class App {
    private static final Logger LOGGER = LogManager.getLogger(App.class);

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("RESTPROVIDER_PORT", "8080"));

        ControllerRegistry registry = DefaultRegistryFactory.createDefaultRegistry();
        RestProviderServer server = new RestProviderServer(port, registry);
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        server.start();
        LOGGER.info("RestProvider Java Server started on port {}", port);

        try {
            new CountDownLatch(1).await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.warn("Main thread interrupted", e);
        }
    }
}