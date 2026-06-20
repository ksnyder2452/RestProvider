package com.restprovider.integration;

import java.io.IOException;
import java.net.ServerSocket;

public final class TestPortUtil {
    private TestPortUtil() {
    }

    public static int findAvailablePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to allocate test port", e);
        }
    }
}
