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
        dispatcher = new ControllerDispatcher(registry);
    }

    @Test
    void shouldCreateIncrementAndDeleteSequence() throws Exception {
        String project = "seqtest_" + System.nanoTime();

        BasicClassicHttpRequest create = new BasicClassicHttpRequest("POST", "/api/sequence");
        create.addHeader("projectName", project);
        create.addHeader("sequenceName", "order_id");
        create.addHeader("recreateIfExists", "YES");
        create.addHeader("minVal", "100");
        create.addHeader("incrementBy", "5");
        BasicClassicHttpResponse createResponse = new BasicClassicHttpResponse(200);
        dispatcher.handle(create, createResponse, TestHttpContexts.newContext());
        Assertions.assertEquals(200, createResponse.getCode());

        BasicClassicHttpRequest curr = new BasicClassicHttpRequest("GET", "/api/sequence/currval");
        curr.addHeader("projectName", project);
        curr.addHeader("sequenceName", "order_id");
        BasicClassicHttpResponse currResponse = new BasicClassicHttpResponse(200);
        dispatcher.handle(curr, currResponse, TestHttpContexts.newContext());
        Assertions.assertEquals("100", TestResponseUtil.body(currResponse));

        BasicClassicHttpRequest next = new BasicClassicHttpRequest("GET", "/api/sequence/nextval");
        next.addHeader("projectName", project);
        next.addHeader("sequenceName", "order_id");
        BasicClassicHttpResponse nextResponse = new BasicClassicHttpResponse(200);
        dispatcher.handle(next, nextResponse, TestHttpContexts.newContext());
        Assertions.assertEquals("105", TestResponseUtil.body(nextResponse));

        BasicClassicHttpRequest currAgain = new BasicClassicHttpRequest("GET", "/api/sequence/currval");
        currAgain.addHeader("projectName", project);
        currAgain.addHeader("sequenceName", "order_id");
        BasicClassicHttpResponse currAgainResponse = new BasicClassicHttpResponse(200);
        dispatcher.handle(currAgain, currAgainResponse, TestHttpContexts.newContext());
        Assertions.assertEquals("105", TestResponseUtil.body(currAgainResponse));

        BasicClassicHttpRequest delete = new BasicClassicHttpRequest("DELETE", "/api/sequence");
        delete.addHeader("projectName", project);
        delete.addHeader("sequenceName", "order_id");
        BasicClassicHttpResponse deleteResponse = new BasicClassicHttpResponse(200);
        dispatcher.handle(delete, deleteResponse, TestHttpContexts.newContext());
        Assertions.assertEquals(200, deleteResponse.getCode());
    }
}
