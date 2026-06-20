package com.restprovider.integration;

import com.restprovider.controllers.SequenceController;
import com.restprovider.core.ControllerDispatcher;
import com.restprovider.core.ControllerRegistry;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.protocol.BasicHttpContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SequenceControllerIntegrationTest {
    private ControllerDispatcher dispatcher;

    @BeforeEach
    void setup() {
        ControllerRegistry registry = new ControllerRegistry();
        registry.register(new SequenceController());
        registry.setControllerEnabled("Sequence", true);
        dispatcher = new ControllerDispatcher(registry);
    }

    @Test
    void shouldCreateIncrementAndDeleteSequence() throws Exception {
        String project = "seqtest_" + System.nanoTime();

        BasicClassicHttpRequest create = new BasicClassicHttpRequest(
            "POST",
            "/api/sequence/create?project=" + project + "&name=order_id&recreate=YES&minVal=100&step=5");
        BasicClassicHttpResponse createResponse = new BasicClassicHttpResponse(200);
        dispatcher.handle(create, createResponse, TestHttpContexts.newContext());
        Assertions.assertEquals(200, createResponse.getCode());

        BasicClassicHttpRequest curr = new BasicClassicHttpRequest(
            "GET",
            "/api/sequence/current?project=" + project + "&name=order_id");
        BasicClassicHttpResponse currResponse = new BasicClassicHttpResponse(200);
        dispatcher.handle(curr, currResponse, TestHttpContexts.newContext());
        Assertions.assertEquals("100", TestResponseUtil.body(currResponse));

        BasicClassicHttpRequest next = new BasicClassicHttpRequest(
            "GET",
            "/api/sequence/next?project=" + project + "&name=order_id");
        BasicClassicHttpResponse nextResponse = new BasicClassicHttpResponse(200);
        dispatcher.handle(next, nextResponse, TestHttpContexts.newContext());
        Assertions.assertEquals("105", TestResponseUtil.body(nextResponse));

        BasicClassicHttpRequest currAgain = new BasicClassicHttpRequest(
            "GET",
            "/api/sequence/current?project=" + project + "&name=order_id");
        BasicClassicHttpResponse currAgainResponse = new BasicClassicHttpResponse(200);
        dispatcher.handle(currAgain, currAgainResponse, TestHttpContexts.newContext());
        Assertions.assertEquals("105", TestResponseUtil.body(currAgainResponse));

        BasicClassicHttpRequest delete = new BasicClassicHttpRequest(
            "DELETE",
            "/api/sequence/delete?project=" + project + "&name=order_id");
        BasicClassicHttpResponse deleteResponse = new BasicClassicHttpResponse(200);
        dispatcher.handle(delete, deleteResponse, TestHttpContexts.newContext());
        Assertions.assertEquals(200, deleteResponse.getCode());
    }

        @Test
        void shouldRejectCreateWithoutSequenceName() throws Exception {
        BasicClassicHttpRequest create = new BasicClassicHttpRequest("POST", "/api/sequence/create?project=test");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(create, response, TestHttpContexts.newContext());

        Assertions.assertEquals(400, response.getCode());
        Assertions.assertTrue(TestResponseUtil.body(response).contains("sequenceName"));
        }
}
