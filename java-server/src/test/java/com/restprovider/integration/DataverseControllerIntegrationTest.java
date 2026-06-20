package com.restprovider.integration;

import com.restprovider.controllers.DataverseController;
import com.restprovider.core.ControllerDispatcher;
import com.restprovider.core.ControllerRegistry;
import com.restprovider.domain.security.PasscodeValidator;
import java.util.Locale;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DataverseControllerIntegrationTest {
    private ControllerDispatcher dispatcher;

    @BeforeEach
    void setup() {
        PasscodeValidator validator = pass -> "valid-passcode".equals(pass);
        DataverseController.CommandRunner runner = (command, args) -> {
            String normalized = args.toLowerCase(Locale.ROOT);
            if (normalized.contains("select")) {
                return "alpha | beta\ngamma | delta\n";
            }
            if (normalized.contains("ddl")) {
                return "0";
            }
            return "1";
        };

        ControllerRegistry registry = new ControllerRegistry();
        registry.register(new DataverseController(validator, runner));
        registry.setControllerEnabled("Dataverse", true);
        dispatcher = new ControllerDispatcher(registry);
    }

    @Test
    void shouldRejectWithoutPasscode() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/api/dataverse");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(401, response.getCode());
        Assertions.assertTrue(TestResponseUtil.body(response).contains("Passcode failure"));
    }

    @Test
    void shouldRunQueryAndReturnRowCount() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/api/dataverse");
        request.addHeader("passCode", "valid-passcode");
        request.addHeader("projectName", "ProjDV");
        request.addHeader("testcaseName", "CaseDV");
        request.addHeader("sql_statement", "SELECT * FROM account");
        request.addHeader("dv_environment", "sample-env");
        request.addHeader("dv_user", "user");
        request.addHeader("dv_password", "pwd");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(200, response.getCode());
        Assertions.assertTrue(TestResponseUtil.body(response).contains("\"RowCount\":\"2\""));
    }

    @Test
    void shouldRunDdlAndReturnRowCount() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest("PUT", "/api/dataverse/ddl");
        request.addHeader("passCode", "valid-passcode");
        request.addHeader("projectName", "ProjDV");
        request.addHeader("testcaseName", "CaseDV");
        request.addHeader("sql_statement", "DDL TEST");
        request.addHeader("dv_environment", "sample-env");
        request.addHeader("dv_user", "user");
        request.addHeader("dv_password", "pwd");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(200, response.getCode());
        Assertions.assertTrue(TestResponseUtil.body(response).contains("\"RowCount\":\"0\""));
    }

    @Test
    void shouldSupportQueryStringInputsForQueryRoute() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest(
                "GET",
                "/api/dataverse/query?passCode=valid-passcode&projectName=ProjDV&testcaseName=CaseDV"
                        + "&sql=SELECT%20*%20FROM%20account&dv_environment=sample-env"
                        + "&dv_user=user&dv_password=pwd");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(200, response.getCode());
        Assertions.assertTrue(TestResponseUtil.body(response).contains("\"RowCount\":\"2\""));
    }

    @Test
    void shouldReturnBadRequestWhenSqlMissing() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest("PUT", "/api/dataverse/ddl");
        request.addHeader("passCode", "valid-passcode");
        request.addHeader("dv_environment", "sample-env");
        request.addHeader("dv_user", "user");
        request.addHeader("dv_password", "pwd");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(400, response.getCode());
        Assertions.assertTrue(TestResponseUtil.body(response).contains("Missing required parameter"));
    }
}
