package com.restprovider.domain.azure.service;

import com.restprovider.domain.azure.dto.AzureCommandRequest;
import com.restprovider.domain.azure.dto.AzureCommandResponse;

public interface AzureService {
    AzureCommandResponse setExtensionsNoPrompt(AzureCommandRequest request);

    AzureCommandResponse checkLogin(AzureCommandRequest request);

    AzureCommandResponse runCommand(AzureCommandRequest request, String commandArguments);
}
