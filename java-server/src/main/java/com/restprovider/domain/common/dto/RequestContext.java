package com.restprovider.domain.common.dto;

public class RequestContext {
    private final String stepName;
    private final String projectName;

    public RequestContext(String stepName, String projectName) {
        this.stepName = stepName;
        this.projectName = projectName;
    }

    public String getStepName() {
        return stepName;
    }

    public String getProjectName() {
        return projectName;
    }
}
