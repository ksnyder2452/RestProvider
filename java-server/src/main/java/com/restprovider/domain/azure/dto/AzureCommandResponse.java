package com.restprovider.domain.azure.dto;

public class AzureCommandResponse {
    private final String result;
    private final int statusCode;

    public AzureCommandResponse(String result, int statusCode) {
        this.result = result;
        this.statusCode = statusCode;
    }

    public String getResult() {
        return result;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
