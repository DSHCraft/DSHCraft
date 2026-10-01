/*
 * DSHCraft contributors, GPL-3.0-or-later.
 */
package org.jackhuang.hmcl.agent;

import com.sun.net.httpserver.HttpServer;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/// Exercises the real metadata request against deterministic local transport failures.
@NotNullByDefault
public final class AgentCatalogRecoveryTest {
    /// Certificate errors and invalid metadata are never classified as transient network resets.
    @Test
    public void preservesTlsAndValidationFailures() {
        assertFalse(AgentNetworkService.isTransientTransportFailure(new javax.net.ssl.SSLHandshakeException("fixture")));
        assertFalse(AgentNetworkService.isTransientTransportFailure(new IOException("malformed JSON")));
        assertTrue(AgentNetworkService.isTransientTransportFailure(new IOException("transport", new java.net.SocketException("Connection reset"))));
    }

    /// Repeated transport errors have a finite request budget.
    @Test
    public void stopsAfterThreeInterruptedResponses() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            calls.incrementAndGet();
            exchange.sendResponseHeaders(200, 100);
            try (var output = exchange.getResponseBody()) { output.write(1); }
            finally { exchange.close(); }
        });
        server.start();
        try {
            assertThrows(IOException.class, () -> AgentNetworkService.fetchDshCatalog("http://127.0.0.1:" + server.getAddress().getPort()));
            assertEquals(3, calls.get());
        } finally { server.stop(0); }
    }
    /// A dropped response body is retried before failing an otherwise recoverable Core lookup.
    @Test
    public void recoversFromTruncatedResponse() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            int call = calls.incrementAndGet();
            byte[] body = "{\"dist-tags\":{\"latest\":\"0.1.5\"},\"versions\":{\"0.1.5\":{}},\"time\":{}}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, call == 1 ? body.length + 100 : body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
            finally { exchange.close(); }
        });
        server.start();
        try {
            assertEquals("0.1.5", AgentNetworkService.fetchDshCatalog("http://127.0.0.1:" + server.getAddress().getPort()).latest());
            assertEquals(2, calls.get());
        } finally { server.stop(0); }
    }

    /// An HTTP rejection is actionable and must not be hidden by retries.
    @Test
    public void doesNotRetryHttpRejection() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            calls.incrementAndGet();
            exchange.sendResponseHeaders(403, -1);
            exchange.close();
        });
        server.start();
        try {
            assertThrows(IOException.class, () -> AgentNetworkService.fetchDshCatalog("http://127.0.0.1:" + server.getAddress().getPort()));
            assertEquals(1, calls.get());
        } finally { server.stop(0); }
    }
}
