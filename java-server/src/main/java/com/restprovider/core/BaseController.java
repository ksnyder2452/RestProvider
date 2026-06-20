package com.restprovider.core;

import com.restprovider.util.JsonUtil;
import java.io.IOException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.entity.StringEntity;

public class BaseController implements BridgeController {
    protected final Logger logger;
    private final String name;

    public BaseController(String name) {
        this.logger = LogManager.getLogger(getClass());
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void handle(ClassicHttpRequest request, ClassicHttpResponse response, String subPath)
            throws IOException, HttpException {
        logger.debug("Fallback handler served for controller={} method={} path={} subPath={}",
            name, request.getMethod(), request.getPath(), subPath);

        response.setCode(HttpStatus.SC_OK);
        String json = "{"
                + "\"controller\":\"" + JsonUtil.escape(name) + "\"," 
                + "\"enabled\":true,"
                + "\"method\":\"" + JsonUtil.escape(request.getMethod()) + "\"," 
                + "\"path\":\"" + JsonUtil.escape(request.getPath()) + "\"," 
                + "\"subPath\":\"" + JsonUtil.escape(subPath) + "\"," 
                + "\"message\":\"Controller is active in the Java server\""
                + "}";
        response.setEntity(new StringEntity(json, ContentType.APPLICATION_JSON));
    }
}
