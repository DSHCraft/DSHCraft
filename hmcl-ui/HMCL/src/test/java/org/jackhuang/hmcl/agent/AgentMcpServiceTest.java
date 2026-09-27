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
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/// Checks the launcher-owned MCP overlay before DSH consumes it.
@NotNullByDefault
public class AgentMcpServiceTest {
    /// Disposable isolated DSH_HOME for each test.
    @TempDir
    Path temporary;

    /// A server overlay can be created and removed without touching another instance.
    @Test
    public void writesOnlyTheSelectedInstance() throws Exception {
        AgentExtension server = new AgentExtension("local-mcp", "Local MCP", "MCP", "http://127.0.0.1:3000/mcp", false);
        Path first = temporary.resolve("first");
        Path second = temporary.resolve("second");
        Path patch = AgentMcpService.ensurePatch(first, List.of(server));
        assertNotNull(patch);
        String contents = Files.readString(patch);
        assertTrue(contents.contains("@deepseek-ai/dsh-mcp-client"));
        assertTrue(contents.contains("streamable-http"));
        assertTrue(contents.contains("http://127.0.0.1:3000/mcp"));
        assertFalse(Files.exists(second.resolve("dshcraft-mcp.patch.yml")));
        AgentMcpService.ensurePatch(first, List.of());
        assertFalse(Files.exists(patch));
    }

    /// URLs with an embedded key or URL query cannot enter launcher state or the patch.
    @Test
    public void rejectsCredentialBearingUrls() {
        assertThrows(IOException.class, () -> AgentMcpService.validateEndpoint("https://user:token@example.com/mcp"));
        assertThrows(IOException.class, () -> AgentMcpService.validateEndpoint("https://example.com/mcp?key=secret"));
        assertThrows(IOException.class, () -> AgentMcpService.validateEndpoint("file:///tmp/server"));
    }

    /// Authenticated overlays refer only to an environment variable, never a saved token value.
    @Test
    public void bearerHeaderContainsOnlyEnvironmentReference() throws Exception {
        AgentExtension server = new AgentExtension("secure-mcp", "Secure MCP", "MCP", "https://example.com/mcp", false);
        Path patch = AgentMcpService.ensurePatch(temporary, List.of(server), Set.of(server.getId()));
        assertNotNull(patch);
        String body = Files.readString(patch);
        assertTrue(body.contains("Authorization: !!js"));
        assertTrue(body.contains("process.env." + AgentMcpService.environmentVariable(server.getId())));
        assertFalse(body.contains("fixture-secret"));
    }

    /// A local stdio descriptor becomes DSH's command/args transport without shell interpolation.
    @Test
    public void writesStdioCommandWithoutSecrets() throws Exception {
        AgentExtension server = new AgentExtension("local-stdio", "Local stdio", "MCP",
                "stdio:{\"command\":\"npx\",\"args\":[\"-y\",\"example-mcp\"]}", false);
        Path patch = AgentMcpService.ensurePatch(temporary, List.of(server));
        assertNotNull(patch);
        String body = Files.readString(patch);
        assertTrue(body.contains("transport: stdio"));
        assertTrue(body.contains("command: \"npx\""));
        assertTrue(body.contains("args: [\"-y\",\"example-mcp\"]"));
        assertFalse(body.contains("Authorization"));
        assertThrows(IOException.class,
                () -> AgentMcpService.ensurePatch(temporary, List.of(server), Set.of(server.getId())));
    }

    /// Stdio descriptors cannot smuggle an environment map or malformed command into launcher state.
    @Test
    public void rejectsUnsafeStdioDescriptor() {
        AgentExtension server = new AgentExtension("bad-stdio", "Bad stdio", "MCP",
                "stdio:{\"command\":\"node\",\"env\":{\"TOKEN\":\"secret\"}}", false);
        assertThrows(IOException.class, () -> AgentMcpService.ensurePatch(temporary, List.of(server)));
        server.locationProperty().set("stdio:{\"args\":[\"-y\"]}");
        assertThrows(IOException.class, () -> AgentMcpService.ensurePatch(temporary, List.of(server)));
    }

    /// User edits to a generated patch cannot be silently overwritten.
    @Test
    public void refusesExternallyEditedPatch() throws Exception {
        AgentExtension server = new AgentExtension("local-mcp", "Local MCP", "MCP", "http://127.0.0.1:3000/mcp", false);
        Path patch = AgentMcpService.ensurePatch(temporary, List.of(server));
        assertNotNull(patch);
        Files.writeString(patch, Files.readString(patch) + "# external edit\n");
        assertThrows(IOException.class, () -> AgentMcpService.ensurePatch(temporary, List.of(server)));
        assertThrows(IOException.class, () -> AgentMcpService.ensurePatch(temporary, List.of()));
    }

    /// The installed DSH Core accepts the generated overlay as a real MCP client row.
    @Test
    public void installedCoreComposesMcpOverlay() throws Exception {
        String runtime = System.getenv("DSHCRAFT_E2E_RUNTIME_ROOT");
        assumeTrue(runtime != null && !runtime.isBlank(), "Set DSHCRAFT_E2E_RUNTIME_ROOT for Core composition E2E");
        Path cli = DshModService.installedCoreCli(Path.of(runtime), "0.1.5-rc.2");
        AgentExtension server = new AgentExtension("local-mcp", "Local MCP", "MCP", "http://127.0.0.1:3000/mcp", false);
        AgentExtension stdio = new AgentExtension("local-stdio", "Local stdio", "MCP",
                "stdio:{\"command\":\"node\",\"args\":[\"server.mjs\"]}", false);
        Path home = temporary.resolve("e2e-home");
        Path patch = AgentMcpService.ensurePatch(home, List.of(server, stdio), Set.of(server.getId()));
        assertNotNull(patch);
        ProcessBuilder builder = new ProcessBuilder(DshModService.managedNodeExecutable(), cli.toString(),
                "--profile", "mcp-check", "--from-default-profile", "web",
                "--patch", patch.toString(), "--dump-config");
        builder.environment().put("DSH_HOME", home.toString());
        builder.environment().put(AgentMcpService.environmentVariable(server.getId()), "dshcraft-fixture-token");
        builder.directory(temporary.toFile());
        builder.redirectErrorStream(true);
        Path outputFile = temporary.resolve("dsh-dump-config.txt");
        builder.redirectOutput(outputFile.toFile());
        DshModService.configureWindowsModuleFallback(builder, cli);
        Process process = builder.start();
        if (!process.waitFor(60, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            process.waitFor(10, TimeUnit.SECONDS);
            throw new IOException("DSH dump-config timed out: " + Files.readString(outputFile, StandardCharsets.UTF_8));
        }
        String output = Files.readString(outputFile, StandardCharsets.UTF_8);
        assertTrue(process.exitValue() == 0, output);
        assertTrue(output.contains("dshcraft-mcp-local-mcp"), output);
        assertTrue(output.contains("http://127.0.0.1:3000/mcp"), output);
        assertTrue(output.contains("transport: stdio"), output);
        assertTrue(output.contains("command: node"), output);
        assertTrue(output.contains("process.env." + AgentMcpService.environmentVariable(server.getId())), output);
        assertFalse(output.contains("dshcraft-fixture-token"), "Core config dump must not disclose the token value");
    }

    /// A live isolated DSH process sends the OS-style token environment reference as an HTTP header.
    @Test
    public void installedCoreSendsBearerHeaderToMcpServer() throws Exception {
        String runtime = System.getenv("DSHCRAFT_E2E_RUNTIME_ROOT");
        assumeTrue(runtime != null && !runtime.isBlank(), "Set DSHCRAFT_E2E_RUNTIME_ROOT for Core MCP E2E");
        Path cli = DshModService.installedCoreCli(Path.of(runtime), "0.1.5-rc.2");
        CountDownLatch request = new CountDownLatch(1);
        AtomicReference<String> authorization = new AtomicReference<>("");
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/mcp", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            request.countDown();
            byte[] response = "Fixture MCP endpoint".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(503, response.length);
            try (var output = exchange.getResponseBody()) {
                output.write(response);
            }
        });
        server.start();
        Process process = null;
        try {
            int webPort;
            try (ServerSocket port = new ServerSocket(0)) {
                webPort = port.getLocalPort();
            }
            AgentExtension mcp = new AgentExtension("fixture-mcp", "Fixture MCP", "MCP",
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/mcp", false);
            Path home = temporary.resolve("live-home");
            Path patch = AgentMcpService.ensurePatch(home, List.of(mcp), Set.of(mcp.getId()));
            assertNotNull(patch);
            List<String> command = AgentRepository.launchCommand(cli, "mcp-live", "web",
                    String.valueOf(webPort), false);
            AgentRepository.addProfileOverlay(command, patch);
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.environment().put("DSH_HOME", home.toString());
            builder.environment().put(AgentMcpService.environmentVariable(mcp.getId()), "dshcraft-fixture-token");
            builder.directory(temporary.toFile());
            builder.redirectErrorStream(true);
            Path outputFile = temporary.resolve("dsh-live-output.txt");
            builder.redirectOutput(outputFile.toFile());
            DshModService.configureWindowsModuleFallback(builder, cli);
            process = builder.start();
            assertTrue(request.await(45, TimeUnit.SECONDS),
                    () -> "DSH did not contact the MCP fixture: " + readDiagnostic(outputFile));
            assertTrue("Bearer dshcraft-fixture-token".equals(authorization.get()),
                    "DSH did not send the configured bearer token");
        } finally {
            if (process != null) {
                process.destroyForcibly();
                process.waitFor(10, TimeUnit.SECONDS);
            }
            server.stop(0);
        }
    }

    /// A live isolated Core process starts a configured local stdio server and sends initialize.
    @Test
    public void installedCoreConnectsToStdioMcpServer() throws Exception {
        String runtime = System.getenv("DSHCRAFT_E2E_RUNTIME_ROOT");
        assumeTrue(runtime != null && !runtime.isBlank(), "Set DSHCRAFT_E2E_RUNTIME_ROOT for Core MCP E2E");
        Path cli = DshModService.installedCoreCli(Path.of(runtime), "0.1.5-rc.2");
        Path marker = temporary.resolve("stdio-initialize.txt");
        Path fixture = temporary.resolve("stdio-fixture.cjs");
        Files.writeString(fixture, """
                const fs = require('node:fs');
                const readline = require('node:readline');
                const marker = process.argv[2];
                readline.createInterface({ input: process.stdin }).on('line', line => {
                  let request;
                  try { request = JSON.parse(line); } catch { return; }
                  if (request.method === 'initialize') {
                    fs.writeFileSync(marker, String(request.params?.protocolVersion ?? 'unknown'));
                    process.stdout.write(JSON.stringify({ jsonrpc: '2.0', id: request.id,
                      result: { protocolVersion: request.params?.protocolVersion ?? '2025-03-26',
                        capabilities: { tools: {} }, serverInfo: { name: 'dshcraft-stdio-fixture', version: '1' } } }) + '\\n');
                  } else if (request.method === 'tools/list') {
                    process.stdout.write(JSON.stringify({ jsonrpc: '2.0', id: request.id,
                      result: { tools: [] } }) + '\\n');
                  } else if (request.id !== undefined) {
                    process.stdout.write(JSON.stringify({ jsonrpc: '2.0', id: request.id,
                      result: {} }) + '\\n');
                  }
                });
                """, StandardCharsets.UTF_8);
        AgentExtension mcp = new AgentExtension("fixture-stdio", "Fixture stdio", "MCP",
                "stdio:{\"command\":\"" + DshModService.managedNodeExecutable().replace("\\", "\\\\")
                        + "\",\"args\":[\"" + fixture.toString().replace("\\", "\\\\")
                        + "\",\"" + marker.toString().replace("\\", "\\\\") + "\"]}", false);
        Path home = temporary.resolve("stdio-home");
        Path patch = AgentMcpService.ensurePatch(home, List.of(mcp));
        assertNotNull(patch);
        int webPort;
        try (ServerSocket port = new ServerSocket(0)) {
            webPort = port.getLocalPort();
        }
        List<String> command = AgentRepository.launchCommand(cli, "stdio-live", "web",
                String.valueOf(webPort), false);
        AgentRepository.addProfileOverlay(command, patch);
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.environment().put("DSH_HOME", home.toString());
        builder.directory(temporary.toFile());
        builder.redirectErrorStream(true);
        Path outputFile = temporary.resolve("dsh-stdio-output.txt");
        builder.redirectOutput(outputFile.toFile());
        DshModService.configureWindowsModuleFallback(builder, cli);
        Process process = builder.start();
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(45);
            while (!Files.exists(marker) && process.isAlive() && System.nanoTime() < deadline) {
                Thread.sleep(100);
            }
            assertTrue(Files.exists(marker),
                    () -> "DSH did not initialize the stdio MCP fixture: " + readDiagnostic(outputFile));
            assertFalse(Files.readString(marker, StandardCharsets.UTF_8).isBlank());
        } finally {
            process.destroyForcibly();
            process.waitFor(10, TimeUnit.SECONDS);
        }
    }

    /// Reads only bounded fixture diagnostics for a failed live-process assertion.
    private static String readDiagnostic(Path outputFile) {
        try {
            if (!Files.exists(outputFile)) return "No DSH output file";
            String output = Files.readString(outputFile, StandardCharsets.UTF_8);
            return output.substring(0, Math.min(output.length(), 2000))
                    .replaceAll("(?i)(token=)[^\\s&]+", "$1[redacted]");
        } catch (IOException error) {
            return error.getClass().getSimpleName();
        }
    }
}
