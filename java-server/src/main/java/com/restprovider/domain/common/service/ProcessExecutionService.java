package com.restprovider.domain.common.service;

public interface ProcessExecutionService {
    String run(String command, String arguments, String stepName, String projectName);
}
