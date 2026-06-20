package com.restprovider.integration;

import com.restprovider.controllers.CosmosDBController;
import com.restprovider.core.ControllerDispatcher;
import com.restprovider.core.ControllerRegistry;
import com.restprovider.domain.security.PasscodeValidator;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CosmosDBControllerIntegrationTest {
    private ControllerDispatcher dispatcher;

    @BeforeEach
    void setup() {
        PasscodeValidator validator = passCode -> "valid-passcode".equals(passCode);
        CosmosDBController.CommandRunner runner = (command, args) -> {
            if (args.contains("database list")) {
                return "[{\"name\":\"db1\"},{\"name\":\"db2\"}]";
            }
            if (args.contains("container list") && args.contains("db1")) {
                return "[{\"name\":\"orders\"},{\"name\":\"users\"}]";
            }
            if (args.contains("container list") && args.contains("db2")) {
                return "[{\"name\":\"events\"}]";
            }
            if (args.contains("sql query")) {
                return "[{\"id\":\"1\",\"status\":\"active\"}]";
            }
            return "{}";
        };

        ControllerRegistry registry = new ControllerRegistry();
        registry.register(new CosmosDBController(validator, runner));
        registry.setControllerEnabled("CosmosDB", true);
        dispatcher = new ControllerDispatcher(registry);
    }

    @Test
    void shouldRejectWithoutPasscode() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/api/cosmosdb/databases");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(401, response.getCode());
    }

    @Test
    void shouldReturnDatabaseList() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/api/cosmosdb/databases");
        request.addHeader("passCode", "valid-passcode");
        request.addHeader("projectName", "cosmos_proj");
        request.addHeader("testcaseName", "tc1");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(200, response.getCode());
        String body = TestResponseUtil.body(response);
        Assertions.assertTrue(body.contains("\"databases\""));
        Assertions.assertTrue(body.contains("db1"));
        Assertions.assertTrue(body.contains("db2"));
    }

    @Test
    void shouldReturnMatchingDatabaseAndContainer() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/api/cosmosdb/database/container/match");
        request.addHeader("passCode", "valid-passcode");
        request.addHeader("projectName", "cosmos_proj");
        request.addHeader("testcaseName", "tc1");
        request.addHeader("filterForContainer", "order");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(200, response.getCode());
        Assertions.assertTrue(TestResponseUtil.body(response).contains("db1"));
        Assertions.assertTrue(TestResponseUtil.body(response).contains("orders"));
    }

    @Test
    void shouldSupportQueryParametersForDatabasesRoute() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest(
                "GET",
                "/api/cosmosdb/databases?passCode=valid-passcode&projectName=cosmos_proj&testcaseName=tc1");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(200, response.getCode());
        Assertions.assertTrue(TestResponseUtil.body(response).contains("\"databases\""));
    }

    @Test
    void shouldReturnBadRequestWhenQueryMissingRequiredFields() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/api/cosmosdb");
        request.addHeader("passCode", "valid-passcode");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(400, response.getCode());
        Assertions.assertTrue(TestResponseUtil.body(response).contains("Missing required parameter"));
    }
}
