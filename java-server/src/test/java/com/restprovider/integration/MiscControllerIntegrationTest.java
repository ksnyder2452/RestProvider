package com.restprovider.integration;

import com.restprovider.controllers.MiscController;
import com.restprovider.core.ControllerDispatcher;
import com.restprovider.core.ControllerRegistry;
import com.restprovider.domain.security.PasscodeValidator;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.protocol.BasicHttpContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MiscControllerIntegrationTest {
    private ControllerDispatcher dispatcher;

    @BeforeEach
    void setup() {
        PasscodeValidator validator = passCode -> "valid-passcode".equals(passCode);
        MiscController.CommandRunner runner = (command, args) -> {
            String cmd = command + " " + args;
            if (cmd.contains("ipinfo.io/ip")) {
                return "10.42.9.4";
            }
            if (command.equalsIgnoreCase("ping")) {
                return "1 packets transmitted, 1 received, 0.0% packet loss";
            }
            return "process-ok";
        };
        Map<String, String> vars = new ConcurrentHashMap<>();
        vars.put("rb_accounts", "acctA,acctB");
        vars.put("publicVar", "publicValue");

        ControllerRegistry registry = new ControllerRegistry();
        registry.register(new MiscController(validator, runner, new Random(1234), vars));
        dispatcher = new ControllerDispatcher(registry);
    }

    @Test
    void shouldValidateVpnNetwork() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/api/misc/check/vpn");
        request.addHeader("expectedNetwork", "10.42");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(200, response.getCode());
        Assertions.assertTrue(TestResponseUtil.body(response).contains("10.42.9.4"));
    }

    @Test
    void shouldRejectProtectedVariableWithoutPasscode() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/api/misc/data/variable");
        request.addHeader("variableName", "publicVar");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(401, response.getCode());
        Assertions.assertTrue(TestResponseUtil.body(response).contains("Passcode failure"));
    }

    @Test
    void shouldGetFilePropertyWhenPasscodeValid() throws Exception {
        String project = "misc_" + System.nanoTime();
        Path folder = Path.of(System.getProperty("user.dir"), "data_files", "temp", project, "cfg");
        Files.createDirectories(folder);
        Files.writeString(folder.resolve("test.properties"), "a=1\nsecret=abc\n", StandardCharsets.UTF_8);

        BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/api/misc/file/property");
        request.addHeader("passCode", "valid-passcode");
        request.addHeader("projectName", project);
        request.addHeader("filePath", "cfg");
        request.addHeader("fileName", "test.properties");
        request.addHeader("propertyName", "secret");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(200, response.getCode());
        Assertions.assertTrue(TestResponseUtil.body(response).contains("\"result\":\"abc\""));
    }

    @Test
    void shouldReturnAccountNames() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/api/misc/account/names");
        request.addHeader("passCode", "valid-passcode");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(200, response.getCode());
        Assertions.assertTrue(TestResponseUtil.body(response).contains("acctA,acctB"));
    }
}
