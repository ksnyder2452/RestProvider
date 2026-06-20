package com.restprovider.integration;

import com.restprovider.controllers.StorageAccountController;
import com.restprovider.core.ControllerDispatcher;
import com.restprovider.core.ControllerRegistry;
import com.restprovider.domain.security.PasscodeValidator;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StorageAccountControllerIntegrationTest {
    private ControllerDispatcher dispatcher;

    @BeforeEach
    void setup() {
        PasscodeValidator validator = passCode -> "valid-passcode".equals(passCode);
        StorageAccountController.CommandRunner runner = (command, args) -> {
            if (args.contains("blob exists") || args.contains("file exists")) {
                return "{\"exists\": true}";
            }
            if (args.contains("metadata show")) {
                return "{\"metadata\":{\"k\":\"v\"}}";
            }
            return "{\"ok\":true}";
        };

        ControllerRegistry registry = new ControllerRegistry();
        registry.register(new StorageAccountController(validator, runner));
        registry.setControllerEnabled("StorageAccount", true);
        dispatcher = new ControllerDispatcher(registry);
    }

    @Test
    void shouldRejectWithoutPasscode() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/api/storageaccount/container/directories");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(401, response.getCode());
        Assertions.assertTrue(TestResponseUtil.body(response).contains("Passcode failure"));
    }

    @Test
    void shouldReturnBlobExistsForDataFileRoute() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/api/storageaccount/datafile");
        request.addHeader("passCode", "valid-passcode");
        request.addHeader("landingFolderName", "results");
        request.addHeader("customFileName", "a.txt");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(200, response.getCode());
        Assertions.assertTrue(TestResponseUtil.body(response).contains("exists"));
    }

    @Test
    void shouldReturnMetadataForMetadataRoute() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/api/storageaccount/datafile/metadata");
        request.addHeader("passCode", "valid-passcode");
        request.addHeader("folderName", "archive");
        request.addHeader("fileName", "sample.txt");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(200, response.getCode());
        Assertions.assertTrue(TestResponseUtil.body(response).contains("metadata"));
    }

    @Test
    void shouldSupportDirectoryAliasAndQueryInputs() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest(
                "GET",
                "/api/storageaccount/directories?passCode=valid-passcode&path=archive");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(200, response.getCode());
        Assertions.assertTrue(TestResponseUtil.body(response).contains("ok"));
    }

    @Test
    void shouldReturnBadRequestWhenDataFileMissingName() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/api/storageaccount/datafile");
        request.addHeader("passCode", "valid-passcode");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(400, response.getCode());
        Assertions.assertTrue(TestResponseUtil.body(response).contains("Missing required parameter"));
    }
}
