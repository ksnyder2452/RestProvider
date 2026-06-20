package com.restprovider.controllers;

import com.restprovider.core.BridgeController;
import com.restprovider.core.HttpRequestUtil;
import com.restprovider.domain.azure.dto.AzureCommandRequest;
import com.restprovider.domain.azure.dto.AzureCommandResponse;
import com.restprovider.domain.azure.service.AzureCliService;
import com.restprovider.domain.azure.service.AzureService;
import com.restprovider.domain.common.dto.RequestContext;
import com.restprovider.domain.common.service.ShellProcessExecutionService;
import com.restprovider.domain.security.EnvPasscodeValidator;
import com.restprovider.domain.security.PasscodeValidator;
import com.restprovider.util.JsonUtil;
import java.io.IOException;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.entity.StringEntity;

public class AzureController implements BridgeController {
    private static final Logger logger = LogManager.getLogger(AzureController.class);
    private final AzureService azureService;
    private final PasscodeValidator passcodeValidator;

    public AzureController() {
        this(new AzureCliService(new ShellProcessExecutionService()), new EnvPasscodeValidator());
    }

    public AzureController(AzureService azureService, PasscodeValidator passcodeValidator) {
        this.azureService = azureService;
        this.passcodeValidator = passcodeValidator;
    }

    @Override
    public String getName() {
        return "Azure";
    }

    @Override
    public void handle(ClassicHttpRequest request, ClassicHttpResponse response, String subPath)
            throws IOException, HttpException {
        logger.debug("Handling request controller={} method={} subPath={}", getName(), request.getMethod(), subPath);
        String route = normalizeRoute(subPath);
        Map<String, String> query = HttpRequestUtil.queryParams(HttpRequestUtil.requestUri(request));
        RequestContext context = new RequestContext(
            readValue(request, query, "stepName", "step"),
            readValue(request, query, "projectName", "project"));
        String passCode = readValue(request, query, "passCode", "passcode");
        if (!passcodeValidator.isValid(passCode)) {
            response.setCode(HttpStatus.SC_UNAUTHORIZED);
            response.setEntity(new StringEntity("{\"passCodeResult\":\"Passcode failure\"}",
                    ContentType.APPLICATION_JSON));
            return;
        }

        AzureCommandRequest commandRequest = new AzureCommandRequest(context, passCode);
        AzureCommandResponse commandResponse;
        if ("POST".equalsIgnoreCase(request.getMethod())
                && "az/extensions/config".equalsIgnoreCase(route)) {
            commandResponse = azureService.setExtensionsNoPrompt(commandRequest);
            respondWithResult(response, commandResponse);
            return;
        }

        if ("GET".equalsIgnoreCase(request.getMethod())
                && "check/azurecli".equalsIgnoreCase(route)) {
            commandResponse = azureService.checkLogin(commandRequest);
            respondWithResult(response, commandResponse);
            return;
        }

        if (("GET".equalsIgnoreCase(request.getMethod()) || "POST".equalsIgnoreCase(request.getMethod()))
                && "az/command".equalsIgnoreCase(route)) {
            String arguments = readValue(request, query, "azCommand", "command", "arguments", "args");
            commandResponse = azureService.runCommand(commandRequest, arguments);
            respondWithResult(response, commandResponse);
            return;
        }

        response.setCode(HttpStatus.SC_NOT_FOUND);
        response.setEntity(new StringEntity("{\"error\":\"Unknown Azure route\"}", ContentType.APPLICATION_JSON));
    }

    private static String normalizeRoute(String subPath) {
        String route = subPath == null ? "" : subPath;
        if (route.startsWith("azure/")) {
            route = route.substring("azure/".length());
        }
        return route;
    }

    private static String readValue(ClassicHttpRequest request, Map<String, String> query, String... keys) {
        for (String key : keys) {
            String headerValue = HttpRequestUtil.headerValue(request, key);
            if (headerValue != null && !headerValue.isBlank()) {
                return headerValue;
            }
        }
        for (String key : keys) {
            for (Map.Entry<String, String> entry : query.entrySet()) {
                if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(key)) {
                    String value = entry.getValue();
                    if (value != null && !value.isBlank()) {
                        return value;
                    }
                }
            }
        }
        return "";
    }

    private void respondWithResult(ClassicHttpResponse response, AzureCommandResponse commandResponse) {
        response.setCode(commandResponse.getStatusCode());
        response.setEntity(new StringEntity(
                "{\"result\":\"" + JsonUtil.escape(commandResponse.getResult()) + "\"}",
                ContentType.APPLICATION_JSON));
    }
}
