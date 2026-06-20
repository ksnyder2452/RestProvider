package com.restprovider.domain.azure.service;

import com.restprovider.domain.azure.dto.AzureCommandRequest;
import com.restprovider.domain.azure.dto.AzureCommandResponse;
import com.restprovider.domain.common.service.ProcessExecutionService;
import org.apache.hc.core5.http.HttpStatus;

public class AzureCliService implements AzureService {
    private final ProcessExecutionService processExecutionService;

    public AzureCliService(ProcessExecutionService processExecutionService) {
        this.processExecutionService = processExecutionService;
    }

    @Override
    public AzureCommandResponse setExtensionsNoPrompt(AzureCommandRequest request) {
        String result = processExecutionService.run(
                "az",
                "config set extension.use_dynamic_install=yes_without_prompt",
                request.getContext().getStepName(),
                request.getContext().getProjectName());
        return new AzureCommandResponse(result, HttpStatus.SC_OK);
    }

    @Override
    public AzureCommandResponse checkLogin(AzureCommandRequest request) {
        String result = processExecutionService.run(
                "az",
                "account show -o jsonc",
                request.getContext().getStepName(),
                request.getContext().getProjectName());
        int statusCode = result.contains("\"state\": \"Enabled\"")
                ? HttpStatus.SC_OK
                : HttpStatus.SC_NOT_ACCEPTABLE;
        return new AzureCommandResponse(result, statusCode);
    }
}
