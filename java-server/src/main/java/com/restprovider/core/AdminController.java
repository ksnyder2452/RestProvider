package com.restprovider.core;

import com.restprovider.util.JsonUtil;
import java.io.IOException;
import java.util.List;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.entity.StringEntity;

public class AdminController {
    private final ControllerRegistry registry;

    public AdminController(ControllerRegistry registry) {
        this.registry = registry;
    }

    public boolean canHandle(String path) {
        return path != null && path.startsWith("/admin/controllers");
    }

    public void handle(ClassicHttpRequest request, ClassicHttpResponse response) throws IOException, HttpException {
        String requestUri = HttpRequestUtil.requestUri(request);
        String path = HttpRequestUtil.pathOnly(requestUri);
        if ("GET".equalsIgnoreCase(request.getMethod()) && "/admin/controllers".equals(path)) {
            respondControllerList(response);
            return;
        }

        if (path.startsWith("/admin/controllers/") && "PUT".equalsIgnoreCase(request.getMethod())) {
            String tail = path.substring("/admin/controllers/".length());
            String[] parts = tail.split("/");
            if (parts.length >= 2 && "enabled".equalsIgnoreCase(parts[1])) {
                String controllerName = parts[0];
                boolean targetState = HttpRequestUtil.queryBoolean(requestUri, "value", true);
                boolean updated = registry.setControllerEnabled(controllerName, targetState);
                if (updated) {
                    response.setCode(HttpStatus.SC_OK);
                    response.setEntity(new StringEntity(
                            "{\"controller\":\"" + JsonUtil.escape(controllerName)
                                    + "\",\"enabled\":" + targetState + "}",
                            ContentType.APPLICATION_JSON));
                } else {
                    response.setCode(HttpStatus.SC_NOT_FOUND);
                    response.setEntity(new StringEntity(
                            "{\"error\":\"Unknown controller\"}", ContentType.APPLICATION_JSON));
                }
                return;
            }
        }

        response.setCode(HttpStatus.SC_BAD_REQUEST);
        response.setEntity(new StringEntity(
                "{\"error\":\"Use GET /admin/controllers or PUT /admin/controllers/{name}/enabled?value=true|false\"}",
                ContentType.APPLICATION_JSON));
    }

    private void respondControllerList(ClassicHttpResponse response) {
        List<String> names = registry.getControllerNames();
        StringBuilder json = new StringBuilder();
        json.append("{\"controllers\":[");
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            if (i > 0) {
                json.append(",");
            }
            json.append("{\"name\":\"")
                    .append(JsonUtil.escape(name))
                    .append("\",\"enabled\":")
                    .append(registry.isEnabled(name))
                    .append("}");
        }
        json.append("]}");

        response.setCode(HttpStatus.SC_OK);
        response.setEntity(new StringEntity(json.toString(), ContentType.APPLICATION_JSON));
    }
}
