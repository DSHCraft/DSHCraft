/*
 * DSHCraft direct HMCL fork
 * Copyright (C) 2026 DSHCraft contributors
 *
 * This file is licensed under the GNU General Public License, version 3 or later.
 */
package org.jackhuang.hmcl.agent;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/// Maintains isolated DSH overlays for HTTP and local stdio MCP servers.
@NotNullByDefault
public final class AgentMcpService {
    /// Header that distinguishes launcher output from user-authored DSH patches.
    private static final String MANAGED_PREFIX = "# DSHCraft managed MCP SHA-256: ";
    /// JSON quoting is also valid YAML double-quoted scalar syntax.
    private static final Gson JSON = new Gson();
    /// Explicit stdio descriptor prefix used in the existing MCP location field.
    private static final String STDIO_PREFIX = "stdio:";

    /// A validated local executable and its direct arguments, never a shell command line.
    @NotNullByDefault
    private record StdioCommand(String command, @Unmodifiable List<String> args) {
    }

    /// Prevents instantiation of the patch service.
    private AgentMcpService() {
    }

    /// Validates and writes the complete MCP overlay for one isolated DSH_HOME.
    /// An externally edited launcher patch is never overwritten or deleted.
    public static synchronized @Nullable Path ensurePatch(Path dshHome,
                                                            @Unmodifiable List<AgentExtension> enabled)
            throws IOException {
        return ensurePatch(dshHome, enabled, Set.of());
    }

    /// Writes MCP rows with optional bearer-token environment references, never token values.
    public static synchronized @Nullable Path ensurePatch(Path dshHome,
                                                            @Unmodifiable List<AgentExtension> enabled,
                                                            @Unmodifiable Set<String> bearerTokenIds)
            throws IOException {
        rejectLinkedHome(dshHome);
        Path patch = dshHome.resolve("dshcraft-mcp.patch.yml");
        StringBuilder body = new StringBuilder();
        if (enabled.isEmpty()) {
            verifyExisting(patch);
            Files.deleteIfExists(patch);
            return null;
        }
        body.append("- insert:\n");
        for (AgentExtension extension : enabled) {
            String id = extension.getId();
            if (!id.matches("[A-Za-z0-9_-]{1,32}")) {
                throw new IOException("MCP server ID must contain 1-32 letters, digits, '_' or '-'.");
            }
            body.append("    - id: ").append(JSON.toJson("dshcraft-mcp-" + id)).append('\n')
                    .append("      name: '@deepseek-ai/dsh-mcp-client'\n")
                    .append("      config:\n")
                    .append("        serverName: ").append(JSON.toJson(id)).append('\n');
            String location = extension.locationProperty().get();
            if (isStdio(location)) {
                if (bearerTokenIds.contains(id)) {
                    throw new IOException("MCP bearer tokens are supported only for HTTP endpoints.");
                }
                StdioCommand command = parseStdio(location);
                body.append("        transport: stdio\n")
                        .append("        command: ").append(JSON.toJson(command.command())).append('\n')
                        .append("        args: ").append(JSON.toJson(command.args())).append('\n');
            } else {
                URI endpoint = validateEndpoint(location);
                body.append("        transport: streamable-http\n")
                        .append("        url: ").append(JSON.toJson(endpoint.toString())).append('\n');
            }
            if (!isStdio(location) && bearerTokenIds.contains(id)) {
                body.append("        headers:\n")
                        .append("          Authorization: !!js ")
                        .append(JSON.toJson("'Bearer ' + process.env." + environmentVariable(id)))
                        .append('\n');
            }
        }
        String rendered = MANAGED_PREFIX + sha256(body.toString()) + "\n" + body;
        Files.createDirectories(dshHome);
        verifyExisting(patch);
        if (Files.exists(patch, LinkOption.NOFOLLOW_LINKS)
                && rendered.equals(Files.readString(patch, StandardCharsets.UTF_8))) return patch;
        Path staged = Files.createTempFile(dshHome, ".dshcraft-mcp-", ".yml");
        try {
            Files.writeString(staged, rendered, StandardCharsets.UTF_8);
            try {
                Files.move(staged, patch, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(staged, patch, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(staged);
        }
        return patch;
    }

    /// Recognizes an explicit local-command descriptor without executing it.
    public static boolean isStdio(String location) {
        return location != null && location.trim().startsWith(STDIO_PREFIX);
    }

    /// Validates a local server descriptor before accepting the add dialog.
    public static void validateStdioDescriptor(String descriptor) throws IOException {
        parseStdio(STDIO_PREFIX + (descriptor == null ? "" : descriptor.trim()));
    }

    /// Parses a bounded JSON object with only `command` and `args`; no environment or shell is accepted.
    private static StdioCommand parseStdio(String location) throws IOException {
        try {
            JsonElement parsed = JsonParser.parseString(location.trim().substring(STDIO_PREFIX.length()));
            if (!parsed.isJsonObject()) throw new IOException("MCP stdio descriptor must be a JSON object.");
            JsonObject object = parsed.getAsJsonObject();
            for (String key : object.keySet()) {
                if (!"command".equals(key) && !"args".equals(key)) {
                    throw new IOException("MCP stdio descriptor supports only command and args.");
                }
            }
            JsonElement executable = object.get("command");
            if (executable == null || !executable.isJsonPrimitive() || !executable.getAsJsonPrimitive().isString()) {
                throw new IOException("MCP stdio command is required.");
            }
            String command = executable.getAsString().trim();
            if (command.isEmpty() || command.length() > 1024 || containsControl(command)) {
                throw new IOException("MCP stdio command is invalid.");
            }
            List<String> args = new ArrayList<>();
            JsonElement arguments = object.get("args");
            if (arguments != null) {
                if (!arguments.isJsonArray()) throw new IOException("MCP stdio args must be an array.");
                JsonArray array = arguments.getAsJsonArray();
                if (array.size() > 32) throw new IOException("MCP stdio has too many arguments.");
                for (JsonElement argument : array) {
                    if (!argument.isJsonPrimitive() || !argument.getAsJsonPrimitive().isString()
                            || argument.getAsString().length() > 4096 || containsControl(argument.getAsString())) {
                        throw new IOException("MCP stdio contains an invalid argument.");
                    }
                    args.add(argument.getAsString());
                }
            }
            return new StdioCommand(command, List.copyOf(args));
        } catch (com.google.gson.JsonParseException | IllegalStateException error) {
            throw new IOException("MCP stdio descriptor is not valid JSON.", error);
        }
    }

    /// Rejects control characters that could obscure a persisted local command.
    private static boolean containsControl(String value) {
        return value.codePoints().anyMatch(Character::isISOControl);
    }

    /// Derives a collision-resistant, valid environment name without exposing a server ID in logs.
    public static String environmentVariable(String serverId) throws IOException {
        if (serverId == null || !serverId.matches("[A-Za-z0-9_-]{1,32}")) {
            throw new IOException("Invalid MCP server ID for token environment variable.");
        }
        return "DSHCRAFT_MCP_" + sha256(serverId).substring(0, 16).toUpperCase(Locale.ROOT) + "_TOKEN";
    }

    /// Rejects links in the instance-owned part of the DSH_HOME path before writing or deleting.
    private static void rejectLinkedHome(Path dshHome) throws IOException {
        Path current = dshHome.toAbsolutePath().normalize();
        for (int depth = 0; current != null && depth < 4; depth++, current = current.getParent()) {
            if (Files.isSymbolicLink(current)) {
                throw new IOException("Instance DSH_HOME path cannot contain a symbolic link.");
            }
        }
    }

    /// Accepts credential-free Streamable HTTP endpoints only.
    public static URI validateEndpoint(String value) throws IOException {
        try {
            URI uri = new URI(value == null ? "" : value.trim());
            if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getHost().isBlank() || uri.getUserInfo() != null
                    || uri.getRawQuery() != null || uri.getRawFragment() != null) {
                throw new IOException("MCP endpoint must be an HTTP(S) URL without credentials, query or fragment.");
            }
            return uri;
        } catch (URISyntaxException error) {
            throw new IOException("MCP endpoint is not a valid URL.", error);
        }
    }

    /// Refuses to mutate an unowned or edited patch, including symbolic links.
    private static void verifyExisting(Path patch) throws IOException {
        if (!Files.exists(patch, LinkOption.NOFOLLOW_LINKS)) return;
        if (Files.isSymbolicLink(patch)) throw new IOException("Refusing to replace a linked MCP patch.");
        String current = Files.readString(patch, StandardCharsets.UTF_8);
        int end = current.indexOf('\n');
        if (end < 0 || !current.startsWith(MANAGED_PREFIX)
                || !current.substring(0, end).equals(MANAGED_PREFIX + sha256(current.substring(end + 1)))) {
            throw new IOException("DSH MCP patch was edited outside the launcher; refusing to overwrite it.");
        }
    }

    /// Computes a digest for detecting external edits to the launcher-owned patch.
    private static String sha256(String body) throws IOException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(body.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IOException("SHA-256 is unavailable for MCP patch verification.", error);
        }
    }
}
