package com.restprovider.integration;

import com.restprovider.controllers.JavadocController;
import com.restprovider.core.ControllerDispatcher;
import com.restprovider.core.ControllerRegistry;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JavadocControllerIntegrationTest {
    private ControllerDispatcher dispatcher;
    private Path docsRoot;

    @BeforeEach
    void setup() throws Exception {
        docsRoot = Files.createTempDirectory("javadocs_");
        Files.createDirectories(docsRoot);
        Files.writeString(docsRoot.resolve("index.html"), "<html><body>API Docs</body></html>", StandardCharsets.UTF_8);

        JavadocController.CommandRunner runner = (command, args) -> "javadocs generated";
        ControllerRegistry registry = new ControllerRegistry();
        registry.register(new JavadocController(runner, docsRoot));
        registry.setControllerEnabled("Javadoc", true);
        dispatcher = new ControllerDispatcher(registry);
    }

    @Test
    void shouldGenerateJavadocs() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/api/javadoc/generate");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(200, response.getCode());
        String body = TestResponseUtil.body(response);
        Assertions.assertTrue(body.contains("Javadocs generated"));
        Assertions.assertTrue(body.contains("/api/javadoc/docs"));
    }

    @Test
    void shouldServeJavadocsIndex() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/api/javadoc/docs");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(200, response.getCode());
        Assertions.assertTrue(TestResponseUtil.body(response).contains("API Docs"));
    }

    @Test
    void shouldRejectPathTraversal() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/api/javadoc/docs/../secret.txt");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(400, response.getCode());
        Assertions.assertTrue(TestResponseUtil.body(response).contains("Invalid documentation path"));
    }
}
