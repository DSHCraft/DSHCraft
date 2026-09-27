/*
 * DSHCraft direct HMCL fork
 * Copyright (C) 2026 DSHCraft contributors
 *
 * This file is licensed under the GNU General Public License, version 3 or later.
 */
package org.jackhuang.hmcl.agent;

import com.sun.net.httpserver.HttpServer;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/// Exercises disposable Provider credentials in the real Windows Credential Manager.
@NotNullByDefault
public class AgentSecretStoreTest {
    /// Saves, reads and deletes a random key without logging or persisting it in Launcher files.
    @Test
    public void storesAndDeletesDisposableWindowsCredential() throws Exception {
        assumeTrue(System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win"));
        String id = "credential-test-" + UUID.randomUUID();
        String secret = UUID.randomUUID() + "-" + UUID.randomUUID();
        try {
            AgentSecretStore.set(id, secret);
            assertTrue(AgentSecretStore.has(id));
            assertTrue(secret.equals(AgentSecretStore.get(id)), "Credential Manager returned a different key");
        } finally {
            AgentSecretStore.delete(id);
        }
        assertFalse(AgentSecretStore.has(id));
    }

    /// Refuses malformed Provider IDs before calling native Credential Manager APIs.
    @Test
    public void rejectsUnsafeProviderId() {
        assertThrows(IOException.class, () -> AgentSecretStore.get("../outside"));
    }

    /// MCP tokens use a separate OS target and cannot replace a Provider key with the same ID.
    @Test
    public void separatesMcpAndProviderCredentials() throws Exception {
        assumeTrue(AgentSecretStore.isSupported());
        String id = "mcp-" + UUID.randomUUID().toString().substring(0, 12);
        String providerKey = "provider-" + UUID.randomUUID();
        String mcpToken = "mcp-" + UUID.randomUUID();
        try {
            AgentSecretStore.set(id, providerKey);
            AgentSecretStore.setMcp(id, mcpToken);
            assertTrue(providerKey.equals(AgentSecretStore.get(id)));
            assertTrue(mcpToken.equals(AgentSecretStore.getMcp(id)));
            AgentSecretStore.deleteMcp(id);
            assertFalse(AgentSecretStore.hasMcp(id));
            assertTrue(providerKey.equals(AgentSecretStore.get(id)));
        } finally {
            AgentSecretStore.deleteMcp(id);
            AgentSecretStore.delete(id);
        }
    }

    /// Model discovery reads the OS key and sends the Anthropic-specific headers to a loopback fixture.
    @Test
    public void storedKeyAuthenticatesAnthropicModelDiscovery() throws Exception {
        assumeTrue(AgentSecretStore.isSupported());
        String id = "credential-model-" + UUID.randomUUID();
        String secret = UUID.randomUUID() + "-" + UUID.randomUUID();
        AtomicBoolean authenticated = new AtomicBoolean(false);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/models", exchange -> {
            authenticated.set(secret.equals(exchange.getRequestHeaders().getFirst("x-api-key"))
                    && "2023-06-01".equals(exchange.getRequestHeaders().getFirst("anthropic-version")));
            byte[] body = "{\"data\":[{\"id\":\"fixture-model\"}]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();
        try {
            AgentSecretStore.set(id, secret);
            AgentProvider provider = new AgentProvider(id, "Fixture", "Anthropic-compatible",
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/v1", "fixture-model", "NO_FIXTURE_KEY");
            assertTrue(AgentNetworkService.discoverModels(provider).equals(List.of("fixture-model")));
            assertTrue(authenticated.get(), "Provider request did not use the saved OS credential");
        } finally {
            server.stop(0);
            AgentSecretStore.delete(id);
        }
    }
}
