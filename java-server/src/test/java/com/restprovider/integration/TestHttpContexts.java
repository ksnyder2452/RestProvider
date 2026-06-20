package com.restprovider.integration;

import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;

final class TestHttpContexts {
    private TestHttpContexts() {
    }

    static HttpContext newContext() {
        return HttpCoreContext.create();
    }
}