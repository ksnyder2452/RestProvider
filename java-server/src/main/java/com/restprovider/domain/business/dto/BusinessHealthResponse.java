package com.restprovider.domain.business.dto;

public class BusinessHealthResponse {
    private final String status;
    private final String detail;

    public BusinessHealthResponse(String status, String detail) {
        this.status = status;
        this.detail = detail;
    }

    public String getStatus() {
        return status;
    }

    public String getDetail() {
        return detail;
    }
}
