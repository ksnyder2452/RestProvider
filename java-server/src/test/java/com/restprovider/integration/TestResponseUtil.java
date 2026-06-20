package com.restprovider.integration;

import java.io.IOException;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;

public final class TestResponseUtil {
    private TestResponseUtil() {
    }

    public static String body(ClassicHttpResponse response) {
        if (response.getEntity() == null) {
            return "";
        }
        try {
            return EntityUtils.toString(response.getEntity());
        } catch (IOException | ParseException e) {
            throw new IllegalStateException("Unable to read response body", e);
        }
    }
}
