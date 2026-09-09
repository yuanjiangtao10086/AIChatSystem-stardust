package com.example.stardust_springboot.common.logging;

import org.slf4j.MDC;

public final class RequestContext {

    public static final String REQUEST_ID_MDC_KEY = "requestId";
    public static final String TRACE_ID_MDC_KEY = "traceId";

    private RequestContext() {
    }

    public static String requestId() {
        return MDC.get(REQUEST_ID_MDC_KEY);
    }
}
