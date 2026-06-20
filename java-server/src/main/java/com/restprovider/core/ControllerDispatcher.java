package com.restprovider.core;

import java.io.IOException;
import java.util.Locale;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.protocol.HttpContext;

public class ControllerDispatcher implements HttpRequestHandler {
    private static final String API_PREFIX = "/api/";
    private static final Logger LOGGER = LogManager.getLogger(ControllerDispatcher.class);

    private final ControllerRegistry registry;
    private final AdminController adminController;

    public ControllerDispatcher(ControllerRegistry registry) {
        this.registry = registry;
        this.adminController = new AdminController(registry);
    }

    @Override
    public void handle(ClassicHttpRequest request, ClassicHttpResponse response, HttpContext context)
            throws IOException, HttpException {
        String requestUri = HttpRequestUtil.requestUri(request);
        String path = HttpRequestUtil.pathOnly(requestUri);
        if (path == null) {
            LOGGER.warn("Received request with missing path");
            respond(response, HttpStatus.SC_BAD_REQUEST, "{\"error\":\"Missing path\"}");
            return;
        }

        if (adminController.canHandle(path)) {
            adminController.handle(request, response);
            return;
        }

        if (!path.toLowerCase(Locale.ROOT).startsWith(API_PREFIX)) {
            LOGGER.debug("Rejected non-api path: {}", path);
            respond(response, HttpStatus.SC_NOT_FOUND, "{\"error\":\"Use /api/{controller}\"}");
            return;
        }

        String route = path.substring(API_PREFIX.length());
        String[] split = route.split("/", 2);
        String controllerName = split[0];
        String subPath = split.length > 1 ? split[1] : "";

        if (!registry.isRegistered(controllerName)) {
            LOGGER.warn("Unknown controller requested: {}", controllerName);
            respond(response, HttpStatus.SC_NOT_FOUND, "{\"error\":\"Unknown controller\"}");
            return;
        }
        if (!registry.isEnabled(controllerName)) {
            LOGGER.info("Blocked disabled controller request: {}", controllerName);
            respond(response, HttpStatus.SC_FORBIDDEN, "{\"error\":\"Controller disabled\"}");
            return;
        }

        BridgeController controller = registry.getController(controllerName);
        controller.handle(request, response, subPath);
    }

    private void respond(ClassicHttpResponse response, int code, String body) {
        response.setCode(code);
        response.setEntity(new StringEntity(body, ContentType.APPLICATION_JSON));
    }
}
