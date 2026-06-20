package com.restprovider.domain.business.service;

import com.restprovider.domain.business.dto.BusinessHealthResponse;

public class DefaultBusinessService implements BusinessService {
    @Override
    public BusinessHealthResponse health() {
        return new BusinessHealthResponse("UP", "Business domain services are reachable");
    }
}
