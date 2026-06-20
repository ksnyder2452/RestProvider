package com.restprovider.core;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.Header;

public final class HttpRequestUtil {
    private HttpRequestUtil() {
    }

    public static String headerValue(ClassicHttpRequest request, String name) {
        Header header = request.getFirstHeader(name);
        return header == null ? "" : header.getValue();
    }

    public static String requestUri(ClassicHttpRequest request) {
        String uri = request.getRequestUri();
        return uri == null ? "" : uri;
    }

    public static String pathOnly(String requestUri) {
        int idx = requestUri.indexOf('?');
        return idx >= 0 ? requestUri.substring(0, idx) : requestUri;
    }

    public static Map<String, String> queryParams(String requestUri) {
        Map<String, String> params = new HashMap<>();
        int idx = requestUri.indexOf('?');
        if (idx < 0 || idx + 1 >= requestUri.length()) {
            return params;
        }

        String query = requestUri.substring(idx + 1);
        for (String token : query.split("&")) {
            if (token.isBlank()) {
                continue;
            }
            String[] pair = token.split("=", 2);
            if (pair.length == 2) {
                params.put(pair[0], pair[1]);
            }
        }
        return params;
    }

    public static boolean queryBoolean(String requestUri, String name, boolean defaultValue) {
        return Optional.ofNullable(queryParams(requestUri).get(name))
                .map(Boolean::parseBoolean)
                .orElse(defaultValue);
    }
}
