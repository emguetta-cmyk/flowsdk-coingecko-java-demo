package com.getflowsdk.demo.coingecko;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A tiny HTTP server on the loopback interface, built on the JDK's own {@code HttpServer}. Every request
 * is answered with the same canned status and body, and recorded so tests can inspect what the SDK sent.
 * No network access is needed.
 */
final class FakeServer implements AutoCloseable {

    /** What the SDK sent. */
    record Received(String method, String path, String rawPath, String rawQuery,
            Map<String, List<String>> headers) {

        /** First value of a header, case-insensitive, or {@code null}. */
        String header(String name) {
            for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(name)) {
                    return entry.getValue().get(0);
                }
            }
            return null;
        }

        /** The query string decoded into name/value pairs. */
        Map<String, String> query() {
            Map<String, String> result = new LinkedHashMap<>();
            if (rawQuery == null || rawQuery.isEmpty()) {
                return result;
            }
            for (String pair : rawQuery.split("&")) {
                int eq = pair.indexOf('=');
                String name = eq < 0 ? pair : pair.substring(0, eq);
                String value = eq < 0 ? "" : pair.substring(eq + 1);
                result.put(URLDecoder.decode(name, StandardCharsets.UTF_8), URLDecoder.decode(value, StandardCharsets.UTF_8));
            }
            return result;
        }
    }

    private final HttpServer server;
    private final List<Received> requests = new CopyOnWriteArrayList<>();
    private volatile int status = 200;
    private volatile String body = "{}";

    FakeServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    /** Sets the answer returned to every following request. */
    FakeServer respond(int status, String json) {
        this.status = status;
        this.body = json;
        return this;
    }

    /** Base URL to give to the SDK builder, with the same path prefix as the real server. */
    String baseUrl() {
        return "http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort() + "/api/v3";
    }

    /** The last request received. */
    Received last() {
        if (requests.isEmpty()) {
            throw new AssertionError("the fake server received no request");
        }
        return requests.get(requests.size() - 1);
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            requests.add(new Received(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                    exchange.getRequestURI().getRawPath(), exchange.getRequestURI().getRawQuery(), Map.copyOf(exchange.getRequestHeaders())));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                exchange.getResponseBody().write(bytes);
            }
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
