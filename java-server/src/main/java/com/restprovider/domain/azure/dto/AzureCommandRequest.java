package com.restprovider.domain.azure.dto;

import com.restprovider.domain.common.dto.RequestContext;

public class AzureCommandRequest {
    private final RequestContext context;
    private final String passCode;

    public AzureCommandRequest(RequestContext context, String passCode) {
        this.context = context;
        this.passCode = passCode;
    }

    public RequestContext getContext() {
        return context;
    }

    public String getPassCode() {
        return passCode;
    }
}
