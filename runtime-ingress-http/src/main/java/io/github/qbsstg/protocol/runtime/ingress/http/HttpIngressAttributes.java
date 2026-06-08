package io.github.qbsstg.protocol.runtime.ingress.http;

public final class HttpIngressAttributes {

    public static final String LISTENER_NAME = "http.listener.name";
    public static final String METHOD = "http.method";
    public static final String PATH = "http.path";
    public static final String QUERY = "http.query";
    public static final String CONTENT_TYPE = "http.contentType";
    public static final String REMOTE_ADDRESS = "http.remoteAddress";
    public static final String REQUEST_ID = "http.requestId";
    public static final String RESPONSE_MODE = "http.responseMode";
    public static final String SOURCE_ID_MODE = "http.sourceIdMode";
    public static final String SOURCE_NAMESPACE = "http.source.namespace";
    public static final String SOURCE_VALUE = "http.source.value";
    public static final String PROTOCOL = "http.protocol";

    private HttpIngressAttributes() {
    }
}
