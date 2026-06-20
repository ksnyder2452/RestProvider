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
        RequestContext context = new RequestContext(
                HttpRequestUtil.headerValue(request, "stepName"),
                HttpRequestUtil.headerValue(request, "projectName"));
        String passCode = HttpRequestUtil.headerValue(request, "passCode");
        if (!passcodeValidator.isValid(passCode)) {
            response.setCode(HttpStatus.SC_UNAUTHORIZED);
            response.setEntity(new StringEntity("{\"passCodeResult\":\"Passcode failure\"}",
                    ContentType.APPLICATION_JSON));
            return;
        }

        AzureCommandRequest commandRequest = new AzureCommandRequest(context, passCode);
        AzureCommandResponse commandResponse;
        if ("POST".equalsIgnoreCase(request.getMethod())
                && "az/extensions/config".equalsIgnoreCase(subPath)) {
            commandResponse = azureService.setExtensionsNoPrompt(commandRequest);
            respondWithResult(response, commandResponse);
            return;
        }

        if ("GET".equalsIgnoreCase(request.getMethod())
                && "check/azurecli".equalsIgnoreCase(subPath)) {
            commandResponse = azureService.checkLogin(commandRequest);
            respondWithResult(response, commandResponse);
            return;
        }

        response.setCode(HttpStatus.SC_NOT_FOUND);
        response.setEntity(new StringEntity("{\"error\":\"Unknown Azure route\"}", ContentType.APPLICATION_JSON));
    }

    private void respondWithResult(ClassicHttpResponse response, AzureCommandResponse commandResponse) {
        response.setCode(commandResponse.getStatusCode());
        response.setEntity(new StringEntity(
                "{\"result\":\"" + JsonUtil.escape(commandResponse.getResult()) + "\"}",
                ContentType.APPLICATION_JSON));
    }
}
