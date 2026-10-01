/*
 * DShCraft direct HMCL fork
 * Copyright (C) 2026 DShCraft contributors
 *
 * This file is licensed under the GNU General Public License, version 3 or later.
 */
package org.jackhuang.hmcl.agent;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

/// Browser-equivalent network discovery for provider models, the official DSh npm package catalog,
/// and optional user-supplied extension catalogs.
@NotNullByDefault
public final class AgentNetworkService {
    /// Official npm registry package queried by the launcher.
    public static final String DSH_PACKAGE = "@deepseek-ai/dsh";
    /// Official npm registry retained as the default and recovery source.
    public static final String OFFICIAL_REGISTRY = "https://registry.npmjs.org";
    /// Shared HTTP client with bounded connection latency.
    private static final HttpClient HTTP = createHttpClient();
    /// Provider model responses are metadata and must not consume unbounded launcher memory.
    private static final int MAX_PROVIDER_RESPONSE_BYTES = 1 * 1024 * 1024;
    /// npm metadata may contain many versions but remains bounded before JSON parsing.
    private static final int MAX_NPM_RESPONSE_BYTES = 8 * 1024 * 1024;
    /// Extension catalogs are intentionally smaller than npm package metadata.
    private static final int MAX_EXTENSION_RESPONSE_BYTES = 4 * 1024 * 1024;
    /// Optional official packages documented for plugin installation, excluding DSH's internal packages.
    private static final Set<String> OFFICIAL_OPTIONAL_PLUGINS = Set.of(
            "@deepseek-ai/dsh-subagent-codex", "@deepseek-ai/dsh-subagent-claude-code",
            "@deepseek-ai/dsh-experimental-agent-team-web-profile",
            "@deepseek-ai/dsh-experimental-agent-team-profile",
            "@deepseek-ai/dsh-experimental-voice-input-bundle",
            "@deepseek-ai/dsh-experimental-auto-review",
            "@deepseek-ai/dsh-experimental-schedule-bundle");

    /// Prevents construction of this utility class.
    private AgentNetworkService() {
    }

    /// Uses the operating-system trust roots on Windows while retaining normal TLS certificate checks.
    private static HttpClient createHttpClient() {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL);
        if (System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win")) {
            try {
                KeyStore roots = KeyStore.getInstance("Windows-ROOT");
                roots.load(null, null);
                TrustManagerFactory trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                trust.init(roots);
                SSLContext context = SSLContext.getInstance("TLS");
                context.init(null, trust.getTrustManagers(), null);
                builder.sslContext(context);
            } catch (GeneralSecurityException | IOException error) {
                throw new IllegalStateException("Cannot initialize Windows certificate trust for DSH network requests", error);
            }
        }
        return builder.build();
    }

    /// Queries an OpenAI-compatible provider's `/models` endpoint, trying `/v1/models` as a fallback.
    public static List<String> discoverModels(AgentProvider provider) throws IOException, InterruptedException {
        String base = normalizeBaseUrl(provider.baseUrlProperty().get());
        if (base.isEmpty()) {
            throw new IOException("Provider Base URL is empty");
        }
        List<String> urls = new ArrayList<>();
        urls.add(base + "/models");
        if (!base.endsWith("/v1")) {
            urls.add(base + "/v1/models");
        }
        String secret = AgentSecretStore.resolve(provider);
        IOException last = null;
        for (String url : urls) {
            try {
                HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(20))
                        .header("Accept", "application/json")
                        .header("User-Agent", "DShCraft-Agent-Launcher")
                        .GET();
                if (secret != null && !secret.isBlank()) {
                    if (provider.typeProperty().get().toLowerCase(java.util.Locale.ROOT).contains("anthropic")) {
                        builder.header("x-api-key", secret.trim());
                        builder.header("anthropic-version", "2023-06-01");
                    } else {
                        builder.header("Authorization", "Bearer " + secret.trim());
                    }
                }
                HttpResponse<InputStream> response = HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
                try (InputStream body = response.body()) {
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        throw new IOException("Provider returned HTTP " + response.statusCode() + " for " + url);
                    }
                    List<String> models = parseModels(readBounded(body, MAX_PROVIDER_RESPONSE_BYTES,
                            "Provider model response"));
                    if (!models.isEmpty()) return models;
                    last = new IOException("Provider returned no model IDs from " + url);
                }
            } catch (IllegalArgumentException | IOException e) {
                last = e instanceof IOException io ? io : new IOException("Invalid provider URL: " + url, e);
            }
        }
        throw last == null ? new IOException("Unable to discover provider models") : last;
    }

    /// Fetches the real npm metadata for `@deepseek-ai/dsh`, including dist-tags and recent published versions.
    public static DshCatalog fetchDshCatalog() throws IOException, InterruptedException {
        return fetchDshCatalog(OFFICIAL_REGISTRY);
    }

    /// Fetches published DSH versions from a validated npm-compatible registry.
    public static DshCatalog fetchDshCatalog(String registry) throws IOException, InterruptedException {
        String packagePath = URLEncoder.encode(DSH_PACKAGE, StandardCharsets.UTF_8).replace("%40", "@");
        URI uri = URI.create(validateRegistryUrlForCommand(registry) + "/" + packagePath);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/json")
                .header("User-Agent", "DShCraft-Agent-Launcher")
                .GET()
                .build();
        String responseBody = fetchCoreCatalogBody(request);
        JsonObject root;
        try {
            root = JsonParser.parseString(responseBody).getAsJsonObject();
        } catch (RuntimeException e) {
            throw new IOException("npm registry returned malformed JSON", e);
        }
        JsonObject tags = object(root, "dist-tags");
        JsonObject versions = object(root, "versions");
        JsonObject times = object(root, "time");
        List<CoreRelease> releases = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : versions.entrySet()) {
            String version = entry.getKey();
            JsonObject metadata = entry.getValue().isJsonObject() ? entry.getValue().getAsJsonObject() : new JsonObject();
            String publishedAt = string(times, version);
            String deprecated = string(metadata, "deprecated");
            releases.add(new CoreRelease(version, publishedAt, deprecated));
        }
        releases.sort(Comparator.comparing(CoreRelease::publishedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed()
                .thenComparing(CoreRelease::version, Comparator.reverseOrder()));
        if (releases.size() > 120) {
            releases = new ArrayList<>(releases.subList(0, 120));
        }
        return new DshCatalog(
                string(tags, "latest"),
                string(tags, "next"),
                string(tags, "alpha"),
                List.copyOf(releases));
    }

    /// Retries only idempotent Core metadata reads after transport interruptions, at most three times.
    private static String fetchCoreCatalogBody(HttpRequest request) throws IOException, InterruptedException {
        for (int attempt = 0; ; attempt++) {
            try {
                HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
                try (InputStream body = response.body()) {
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        throw new IOException("npm registry returned HTTP " + response.statusCode());
                    }
                    return readBounded(body, MAX_NPM_RESPONSE_BYTES, "npm registry response");
                }
            } catch (IOException failure) {
                if (attempt >= 2 || !isTransientTransportFailure(failure)) throw failure;
                Thread.sleep(200L * (attempt + 1));
            }
        }
    }

    /// Distinguishes truncated/reset/timed-out transport from HTTP, TLS and metadata validation errors.
    static boolean isTransientTransportFailure(Throwable failure) {
        for (@Nullable Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof javax.net.ssl.SSLException) return false;
        }
        for (@Nullable Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof java.net.SocketException || cause instanceof java.io.EOFException
                    || cause instanceof java.net.http.HttpTimeoutException) return true;
        }
        return false;
    }

    /// Resolves a known npm registry choice or validates a custom HTTP(S) registry root.
    public static String validateRegistry(String source, String customUrl) throws IOException {
        return switch (source == null ? "" : source) {
            case "official" -> OFFICIAL_REGISTRY;
            case "npmmirror" -> "https://registry.npmmirror.com";
            case "huawei" -> "https://repo.huaweicloud.com/repository/npm";
            case "tencent" -> "https://mirrors.cloud.tencent.com/npm";
            case "custom" -> validateRegistryUrlForCommand(customUrl);
            default -> throw new IOException("Unknown npm registry source");
        };
    }

    /// Ensures a registry is a plain HTTP(S) origin/path without embedded credentials or query data.
    public static String validateRegistryUrlForCommand(String value) throws IOException {
        URI uri;
        try {
            uri = URI.create(value == null ? "" : value.trim());
        } catch (IllegalArgumentException error) {
            throw new IOException("Invalid npm registry URL", error);
        }
        String scheme = uri.getScheme();
        if (!("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))
                || uri.getHost() == null || uri.getUserInfo() != null || uri.getRawQuery() != null
                || uri.getRawFragment() != null) {
            throw new IOException("npm registry must be an HTTP(S) URL without credentials, query, or fragment");
        }
        String normalized = uri.toASCIIString();
        while (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        return normalized;
    }

    /// Fetches a browser-style Plugin/MCP/Skill catalog from a user-supplied HTTP(S) JSON endpoint.
    public static List<ExtensionCatalogEntry> fetchExtensionCatalog(String url) throws IOException, InterruptedException {
        URI uri;
        try {
            uri = URI.create(url == null ? "" : url.trim());
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid extension catalog URL", e);
        }
        if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))) {
            throw new IOException("Extension catalog URL must use HTTP or HTTPS");
        }
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/json")
                .header("User-Agent", "DShCraft-Agent-Launcher")
                .GET()
                .build();
        HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
        String responseBody;
        try (InputStream body = response.body()) {
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("Extension catalog returned HTTP " + response.statusCode());
            }
            responseBody = readBounded(body, MAX_EXTENSION_RESPONSE_BYTES, "Extension catalog response");
        }
        JsonElement root;
        try {
            root = JsonParser.parseString(responseBody);
        } catch (RuntimeException e) {
            throw new IOException("Extension catalog returned malformed JSON", e);
        }
        JsonArray entries;
        if (root.isJsonArray()) {
            entries = root.getAsJsonArray();
        } else if (root.isJsonObject() && root.getAsJsonObject().get("plugins") instanceof JsonArray plugins) {
            entries = plugins;
        } else {
            throw new IOException("Extension catalog must be an array or an object containing a plugins array");
        }
        List<ExtensionCatalogEntry> result = new ArrayList<>();
        Set<String> ids = new LinkedHashSet<>();
        for (JsonElement element : entries) {
            if (!element.isJsonObject()) continue;
            JsonObject object = element.getAsJsonObject();
            String id = string(object, "id").trim();
            String name = string(object, "name").trim();
            if (id.isEmpty() || name.isEmpty() || !ids.add(id)) continue;
            String kind = string(object, "kind");
            if (kind.isBlank()) kind = string(object, "type");
            if (kind.isBlank()) kind = "Plugin";
            String packageSpec = string(object, "packageSpec");
            if (packageSpec.isBlank()) packageSpec = string(object, "location");
            boolean enabled = booleanValue(object, "enabled", false);
            result.add(new ExtensionCatalogEntry(id, name, kind, packageSpec, enabled));
        }
        return List.copyOf(result);
    }

    /// Searches npm for DSH Plugin/Bundle packages and returns version-pinned install specs.
    public static NpmSearchResult searchNpmPlugins(String query, String registry, int offset)
            throws IOException, InterruptedException {
        String text = query == null ? "" : query.trim();
        if (text.isBlank() || text.length() > 120) throw new IOException("Plugin search text is invalid");
        if (offset < 0 || offset > 1000) throw new IOException("Plugin search offset is invalid");
        String base = validateRegistryUrlForCommand(registry);
        URI uri = URI.create(base + "/-/v1/search?text="
                + URLEncoder.encode(text, StandardCharsets.UTF_8) + "&size=40&from=" + offset);
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20))
                .header("Accept", "application/json")
                .header("User-Agent", "DShCraft-Agent-Launcher")
                .GET().build();
        HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
        String body;
        try (InputStream input = response.body()) {
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("npm plugin search returned HTTP " + response.statusCode());
            }
            body = readBounded(input, MAX_EXTENSION_RESPONSE_BYTES, "npm plugin search response");
        }
        return parseNpmSearch(body, offset);
    }

    /// Parses a bounded npm search response for validation and fixture tests.
    static NpmSearchResult parseNpmSearch(String body, int offset) throws IOException {
        JsonObject root;
        try {
            root = JsonParser.parseString(body).getAsJsonObject();
        } catch (RuntimeException error) {
            throw new IOException("npm plugin search returned malformed JSON", error);
        }
        try {
            JsonElement objectsElement = root.get("objects");
            if (!(objectsElement instanceof JsonArray objects)) return new NpmSearchResult(List.of(), 0);
            int total = root.has("total") && root.get("total").isJsonPrimitive()
                    ? root.get("total").getAsInt() : offset + objects.size();
            List<NpmPlugin> result = new ArrayList<>();
            Set<String> names = new LinkedHashSet<>();
            for (JsonElement element : objects) {
                if (!element.isJsonObject()) continue;
                JsonElement packageElement = element.getAsJsonObject().get("package");
                if (!(packageElement instanceof JsonObject packageObject)) continue;
                String name = string(packageObject, "name").trim();
                String version = string(packageObject, "version").trim();
                if (!validNpmName(name) || !version.matches("[0-9A-Za-z.+-]{1,100}")
                        || !names.add(name) || DSH_PACKAGE.equals(name)) continue;
                JsonElement keywordsElement = packageObject.get("keywords");
                boolean official = OFFICIAL_OPTIONAL_PLUGINS.contains(name);
                if (name.startsWith("@deepseek-ai/") && !official) continue;
                boolean dshKeyword = false;
                if (keywordsElement instanceof JsonArray keywords) {
                    for (JsonElement keyword : keywords) {
                        String value = keyword.isJsonPrimitive() ? keyword.getAsString().toLowerCase(Locale.ROOT) : "";
                        if (Set.of("dsh", "dsh-plugin", "deepseek-harness", "deepseek-harness-plugin").contains(value)) {
                            dshKeyword = true;
                        }
                    }
                }
                if (!official && !dshKeyword) continue;
                result.add(new NpmPlugin(name, version, string(packageObject, "description"), name + "@" + version));
            }
            return new NpmSearchResult(List.copyOf(result), Math.max(total, 0));
        } catch (RuntimeException error) {
            throw new IOException("npm plugin search returned malformed package data", error);
        }
    }

    /// Reads published versions for one npm package without installing or caching it.
    public static List<String> fetchPluginVersions(String name, String registry)
            throws IOException, InterruptedException {
        if (!validNpmName(name)) throw new IOException("Invalid npm plugin name");
        String packagePath = URLEncoder.encode(name, StandardCharsets.UTF_8).replace("%40", "@");
        URI uri = URI.create(validateRegistryUrlForCommand(registry) + "/" + packagePath);
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20))
                .header("Accept", "application/json")
                .header("User-Agent", "DShCraft-Agent-Launcher").GET().build();
        HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
        String body;
        try (InputStream input = response.body()) {
            if (response.statusCode() < 200 || response.statusCode() >= 300)
                throw new IOException("npm package metadata returned HTTP " + response.statusCode());
            body = readBounded(input, MAX_NPM_RESPONSE_BYTES, "npm package metadata");
        }
        return parsePluginVersions(body, name);
    }

    /// Selects published versions that declare a DSH Bundle patch; legacy official Bundles remain eligible.
    static List<String> parsePluginVersions(String body, String name) throws IOException {
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            JsonObject versions = object(root, "versions");
            List<String> result = new ArrayList<>();
            for (Map.Entry<String, JsonElement> entry : versions.entrySet()) {
                String version = entry.getKey();
                if (!version.matches("[0-9A-Za-z.+-]{1,100}") || !entry.getValue().isJsonObject()) continue;
                JsonObject metadata = entry.getValue().getAsJsonObject();
                JsonObject dsh = object(metadata, "dsh");
                JsonObject bundle = object(dsh, "bundle");
                if (OFFICIAL_OPTIONAL_PLUGINS.contains(name) || !string(bundle, "patch").isBlank())
                    result.add(version);
            }
            java.util.Collections.reverse(result);
            return List.copyOf(result);
        } catch (RuntimeException error) {
            throw new IOException("npm package metadata returned malformed JSON", error);
        }
    }

    /// Accepts ordinary scoped and unscoped npm package names for registry URLs and DSH plugin commands.
    private static boolean validNpmName(String name) {
        return name != null && name.length() <= 214
                && name.matches("(?:@[a-z0-9][a-z0-9._-]*/)?[a-z0-9][a-z0-9._-]*");
    }

    /// Reads UTF-8 response data with a byte limit before allocating a JSON string.
    private static String readBounded(InputStream input, int maximum, String label) throws IOException {
        byte[] contents = input.readNBytes(maximum + 1);
        if (contents.length > maximum) throw new IOException(label + " is too large");
        return new String(contents, StandardCharsets.UTF_8);
    }

    /// Parses common OpenAI-compatible model-list response shapes into sorted unique IDs.
    private static List<String> parseModels(String json) throws IOException {
        JsonObject root;
        try {
            root = JsonParser.parseString(json).getAsJsonObject();
        } catch (RuntimeException e) {
            throw new IOException("Provider returned malformed model JSON", e);
        }
        Set<String> models = new LinkedHashSet<>();
        JsonElement dataElement = root.get("data");
        if (dataElement instanceof JsonArray data) {
            collectModelArray(models, data);
        }
        JsonElement modelsElement = root.get("models");
        if (modelsElement instanceof JsonArray array) {
            collectModelArray(models, array);
        } else if (modelsElement instanceof JsonObject object) {
            models.addAll(object.keySet());
        }
        return models.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    /// Extracts `id` fields from one model metadata array.
    private static void collectModelArray(Set<String> target, JsonArray array) {
        for (JsonElement element : array) {
            if (element.isJsonObject()) {
                String id = string(element.getAsJsonObject(), "id");
                if (!id.isBlank()) {
                    target.add(id);
                }
            } else if (element.isJsonPrimitive()) {
                String id = element.getAsString();
                if (!id.isBlank()) {
                    target.add(id);
                }
            }
        }
    }

    /// Normalizes a Base URL while preserving any explicit path prefix.
    private static String normalizeBaseUrl(String value) {
        if (value == null) return "";
        String base = value.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }

    /// Returns an object member or an empty object when the response omitted it.
    private static JsonObject object(JsonObject root, String key) {
        JsonElement element = root.get(key);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
    }

    /// Returns a string member or an empty string when absent.
    private static String string(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return "";
        }
        try {
            return element.getAsString();
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    /// Returns a boolean member or a fallback when absent or malformed.
    private static boolean booleanValue(JsonObject object, String key, boolean fallback) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) return fallback;
        try {
            return element.getAsBoolean();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    /// One importable browser-style extension catalog entry.
    public record ExtensionCatalogEntry(String id, String name, String kind, String packageSpec, boolean enabled) {
    }

    /// One npm package result shown by the DSH Plugin market.
    public record NpmPlugin(String name, String version, String description, String packageSpec) {
    }

    /// One remote search page and the total number of raw npm results.
    public record NpmSearchResult(List<NpmPlugin> plugins, int total) {
    }

    /// Immutable npm catalog summary suitable for the HMCL dialog layer.
    public record DshCatalog(String latest, String next, String alpha, List<CoreRelease> releases) {
    }

    /// One published DSh npm version.
    public record CoreRelease(String version, String publishedAt, String deprecated) {
    }
}
