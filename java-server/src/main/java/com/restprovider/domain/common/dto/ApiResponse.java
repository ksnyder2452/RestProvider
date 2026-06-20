package com.restprovider.domain.common.dto;

import com.restprovider.util.JsonUtil;

public class ApiResponse {
    private final String key;
    private final String value;

    public ApiResponse(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public String toJson() {
        return "{\"" + JsonUtil.escape(key) + "\":\"" + JsonUtil.escape(value) + "\"}";
    }
}
