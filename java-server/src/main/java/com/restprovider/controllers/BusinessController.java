package com.restprovider.controllers;

import com.restprovider.core.BridgeController;
import com.restprovider.domain.business.dto.BusinessHealthResponse;
import com.restprovider.domain.business.service.BusinessService;
import com.restprovider.domain.business.service.DefaultBusinessService;
import com.restprovider.util.JsonUtil;
import java.io.IOException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.entity.StringEntity;

public class BusinessController implements BridgeController {
    private static final Logger logger = LogManager.getLogger(BusinessController.class);
    private final BusinessService businessService;

    public BusinessController() {
        this(new DefaultBusinessService());
    }

    public BusinessController(BusinessService businessService) {
        this.businessService = businessService;
    }

    @Override
    public String getName() {
        return "Business";
    }

    @Override
    public void handle(ClassicHttpRequest request, ClassicHttpResponse response, String subPath)
            throws IOException, HttpException {
        logger.debug("Handling request controller={} method={} subPath={}", getName(), request.getMethod(), subPath);
        String route = normalizeRoute(subPath);

        if ("GET".equalsIgnoreCase(request.getMethod()) && "health".equalsIgnoreCase(route)) {
            BusinessHealthResponse health = businessService.health();
            respondJson(response, HttpStatus.SC_OK,
                    "{\"status\":\"" + JsonUtil.escape(health.getStatus()) + "\","
                            + "\"detail\":\"" + JsonUtil.escape(health.getDetail()) + "\"}");
            return;
        }

        // Customer customization template:
        // Add deployment-specific business routes here without changing project structure.
        // Example:
        // if ("GET".equalsIgnoreCase(request.getMethod()) && "my/custom/route".equalsIgnoreCase(route)) {
        //     respondJson(response, HttpStatus.SC_OK, "{\"result\":\"custom response\"}");
        //     return;
        // }

        respondJson(response, HttpStatus.SC_NOT_FOUND,
                "{\"error\":\"Unknown Business route\","
                        + "\"hint\":\"Replace BusinessController with custom business API routes for your deployment\"}");
    }

    private static String normalizeRoute(String subPath) {
        String route = subPath == null ? "" : subPath;
        if (route.startsWith("business/")) {
            route = route.substring("business/".length());
        }
        if ("business".equalsIgnoreCase(route)) {
            return "";
        }
        return route;
    }

    private static void respondJson(ClassicHttpResponse response, int code, String body) {
        response.setCode(code);
        response.setEntity(new StringEntity(body, ContentType.APPLICATION_JSON));
    }
}
