package com.restprovider.integration;

import com.restprovider.controllers.BrowserStackController;
import com.restprovider.core.ControllerDispatcher;
import com.restprovider.core.ControllerRegistry;
import com.restprovider.domain.security.PasscodeValidator;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BrowserStackControllerIntegrationTest {
    private ControllerDispatcher dispatcher;

    @BeforeEach
    void setup() {
        PasscodeValidator validator = passCode -> "valid-passcode".equals(passCode);
        BrowserStackController.HttpInvoker invoker = (method, endpoint, user, key) ->
                "{\"endpoint\":\"" + endpoint + "\",\"status\":\"ok\"}";

        ControllerRegistry registry = new ControllerRegistry();
        registry.register(new BrowserStackController(validator, invoker));
        registry.setControllerEnabled("BrowserStack", true);
        dispatcher = new ControllerDispatcher(registry);
    }

    @Test
    void shouldRejectWithoutPasscode() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/api/browserstack/build/list");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(401, response.getCode());
        Assertions.assertTrue(TestResponseUtil.body(response).contains("Passcode failure"));
    }

    @Test
    void shouldReturnBuildListAndWriteOutput() throws Exception {
        String project = "bs_" + System.nanoTime();
        BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/api/browserstack/build/list");
        request.addHeader("passCode", "valid-passcode");
        request.addHeader("projectName", project);
        request.addHeader("testcaseName", "Case A");
        request.addHeader("numberBuilds", "3");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(200, response.getCode());
        String body = TestResponseUtil.body(response);
        Assertions.assertTrue(body.contains("builds.json?limit=3"));

        Path output = Path.of(System.getProperty("user.dir"), "data_files", "temp", project,
                "Case_A_browserstack_buildlist_result.txt");
        Assertions.assertTrue(Files.exists(output));
    }

    @Test
    void shouldHandleAppiumSessionDetailsRoute() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/api/browserstack/appium/session/details");
        request.addHeader("passCode", "valid-passcode");
        request.addHeader("projectName", "proj2");
        request.addHeader("testcaseName", "CaseB");
        request.addHeader("sessionId", "session-55");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(200, response.getCode());
        Assertions.assertTrue(TestResponseUtil.body(response).contains("sessions/session-55.json"));
    }

    @Test
    void shouldSupportQueryParametersForBuildList() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest(
                "GET",
                "/api/browserstack/build/list?passCode=valid-passcode&numberBuilds=7&status=running");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(200, response.getCode());
        String body = TestResponseUtil.body(response);
        Assertions.assertTrue(body.contains("builds.json?limit=7&status=running"));
    }

    @Test
    void shouldHandleBuildDetailsRoute() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/api/browserstack/build/details");
        request.addHeader("passCode", "valid-passcode");
        request.addHeader("buildId", "build-77");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(200, response.getCode());
        Assertions.assertTrue(TestResponseUtil.body(response).contains("builds/build-77.json"));
    }

    @Test
    void shouldHandleAppiumSessionListRoute() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest(
                "GET",
                "/api/browserstack/appium/session/list?passCode=valid-passcode&buildId=app-build-42");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(200, response.getCode());
        Assertions.assertTrue(TestResponseUtil.body(response).contains("builds/app-build-42/sessions.json"));
    }
}
