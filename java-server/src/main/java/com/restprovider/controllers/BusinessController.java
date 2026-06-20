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
        if ("GET".equalsIgnoreCase(request.getMethod()) && "health".equalsIgnoreCase(subPath)) {
            BusinessHealthResponse health = businessService.health();
            response.setCode(HttpStatus.SC_OK);
            response.setEntity(new StringEntity(
                    "{\"status\":\"" + JsonUtil.escape(health.getStatus()) + "\","
                            + "\"detail\":\"" + JsonUtil.escape(health.getDetail()) + "\"}",
                    ContentType.APPLICATION_JSON));
            return;
        }

        response.setCode(HttpStatus.SC_NOT_FOUND);
        response.setEntity(new StringEntity("{\"error\":\"Unknown Business route\"}", ContentType.APPLICATION_JSON));
    }
}
