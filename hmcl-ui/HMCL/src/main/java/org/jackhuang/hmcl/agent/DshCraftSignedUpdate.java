/*
 * DSHCraft direct HMCL fork
 * Copyright (C) 2026 DSHCraft contributors
 *
 * This file is licensed under the GNU General Public License, version 3 or later.
 */
package org.jackhuang.hmcl.agent;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.time.Duration;

/// Verifies a DSHCraft release manifest and artifact independently of HMCL's updater trust root.
@NotNullByDefault
public final class DshCraftSignedUpdate {
    /// Embedded Ed25519 public key required before production update checks can run.
    private static final String PUBLIC_KEY_RESOURCE = "/assets/dshcraft_update_publickey.der";
    /// Maximum accepted signed JSON document size.
    private static final int MAX_MANIFEST_BYTES = 16 * 1024;
    /// Maximum accepted launcher artifact size.
    private static final long MAX_ARTIFACT_BYTES = 128L * 1024 * 1024;
    /// Fixed GitHub Release assets; no user-supplied updater feed is trusted by default.
    private static final URI MANIFEST_URI = URI.create(
            "https://github.com/DSHCraft/DSHCraft/releases/latest/download/dshcraft-update.json");
    /// Detached signature for the exact JSON bytes above.
    private static final URI SIGNATURE_URI = URI.create(
            "https://github.com/DSHCraft/DSHCraft/releases/latest/download/dshcraft-update.json.sig");
    /// TLS-validated HTTP client that permits HTTPS release-asset redirects.
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.NORMAL).build();
    /// Exact required keys prevent a feed from silently changing meaning.
    private static final @Unmodifiable Set<String> MANIFEST_KEYS =
            Set.of("version", "artifactUrl", "sha256", "releaseNotesUrl");

    /// The authenticated download location and SHA-256 for one release.
    @NotNullByDefault
    public record Manifest(String version, URI artifactUrl, String sha256, URI releaseNotesUrl) {
    }

    /// A manifest plus the exact signed bytes required by the offline helper.
    @NotNullByDefault
    public record SignedFeed(Manifest manifest, byte @Unmodifiable [] manifestBytes, String signatureBase64) {
        /// Defensively copies authenticated bytes before a caller can retain them.
        public SignedFeed {
            manifestBytes = manifestBytes.clone();
        }

        /// Returns a copy so callers cannot alter the stored signed document.
        @Override
        public byte @Unmodifiable [] manifestBytes() {
            return manifestBytes.clone();
        }
    }

    /// Prevents instantiation of the verifier.
    private DshCraftSignedUpdate() {
    }

    /// Loads the DSHCraft production trust root; absence deliberately disables updates.
    public static PublicKey loadEmbeddedPublicKey() throws IOException {
        try (InputStream input = DshCraftSignedUpdate.class.getResourceAsStream(PUBLIC_KEY_RESOURCE)) {
            if (input == null) throw new IOException("DSHCraft update public key is not configured.");
            byte[] encoded = input.readNBytes(4097);
            if (encoded.length == 0 || encoded.length > 4096) {
                throw new IOException("DSHCraft update public key has an invalid size.");
            }
            try {
                return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(encoded));
            } catch (GeneralSecurityException error) {
                throw new IOException("DSHCraft update public key is invalid.", error);
            }
        }
    }

    /// Fetches the current DSHCraft feed only after a production public key is available.
    public static Manifest fetchLatestManifest() throws IOException, InterruptedException {
        return fetchLatestSignedFeed().manifest();
    }

    /// Fetches a verified feed while retaining exact signed bytes for a separate install process.
    public static SignedFeed fetchLatestSignedFeed() throws IOException, InterruptedException {
        return fetchSignedFeed(MANIFEST_URI, SIGNATURE_URI, loadEmbeddedPublicKey());
    }

    /// Retrieves bounded raw feed bytes before verifying their detached signature.
    static Manifest fetchSignedManifest(URI manifestUri, URI signatureUri, PublicKey publicKey)
            throws IOException, InterruptedException {
        return fetchSignedFeed(manifestUri, signatureUri, publicKey).manifest();
    }

    /// Fetches and verifies the complete feed for loopback tests and the production wrapper.
    static SignedFeed fetchSignedFeed(URI manifestUri, URI signatureUri, PublicKey publicKey)
            throws IOException, InterruptedException {
        byte[] document = fetchBounded(manifestUri, MAX_MANIFEST_BYTES);
        String signature = new String(fetchBounded(signatureUri, 256), StandardCharsets.US_ASCII);
        return new SignedFeed(verifyManifest(document, signature, publicKey), document, signature);
    }

    /// Downloads a signed-manifest artifact into a fresh destination only after hash verification.
    public static Path downloadVerifiedArtifact(Manifest manifest, Path destination)
            throws IOException, InterruptedException {
        return downloadVerifiedArtifact(manifest.artifactUrl(), manifest, destination);
    }

    /// Uses an explicit URI only for same-package loopback tests; production uses the signed URL.
    static Path downloadVerifiedArtifact(URI downloadUri, Manifest manifest, Path destination)
            throws IOException, InterruptedException {
        Path absolute = destination.toAbsolutePath().normalize();
        Path parent = absolute.getParent();
        if (parent == null || !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(parent) || Files.exists(absolute, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("DSHCraft update destination must be a new file in a regular directory.");
        }
        Path staged = Files.createTempFile(parent, ".dshcraft-update-", ".part");
        try {
            HttpRequest request = HttpRequest.newBuilder(downloadUri).timeout(Duration.ofMinutes(3)).GET().build();
            HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream input = response.body()) {
                if (response.statusCode() != 200) {
                    throw new IOException("DSHCraft update artifact returned HTTP " + response.statusCode());
                }
                try (OutputStream output = Files.newOutputStream(staged)) {
                    byte[] buffer = new byte[64 * 1024];
                    long total = 0;
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        total += count;
                        if (total > MAX_ARTIFACT_BYTES) throw new IOException("DSHCraft update artifact is too large.");
                        output.write(buffer, 0, count);
                    }
                }
            }
            verifyArtifact(staged, manifest);
            return Files.move(staged, absolute);
        } finally {
            Files.deleteIfExists(staged);
        }
    }

    /// Reads a small HTTP response without allowing an unbounded manifest or signature allocation.
    private static byte @Unmodifiable [] fetchBounded(URI uri, int maximum)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30)).GET().build();
        HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream input = response.body()) {
            if (response.statusCode() != 200) {
                throw new IOException("DSHCraft update feed returned HTTP " + response.statusCode());
            }
            byte[] contents = input.readNBytes(maximum + 1);
            if (contents.length == 0 || contents.length > maximum) {
                throw new IOException("DSHCraft update feed response has an invalid size.");
            }
            return contents;
        }
    }

    /// Verifies an Ed25519 signature over the exact UTF-8 manifest bytes before parsing any URL.
    public static Manifest verifyManifest(byte @Unmodifiable [] manifestBytes, String signatureBase64,
                                          PublicKey publicKey)
            throws IOException {
        if (manifestBytes.length == 0 || manifestBytes.length > MAX_MANIFEST_BYTES) {
            throw new IOException("DSHCraft update manifest has an invalid size.");
        }
        byte[] signatureBytes;
        try {
            signatureBytes = Base64.getDecoder().decode(signatureBase64.trim());
        } catch (IllegalArgumentException error) {
            throw new IOException("DSHCraft update signature is not Base64.", error);
        }
        if (signatureBytes.length != 64) throw new IOException("DSHCraft update signature has an invalid size.");
        try {
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(publicKey);
            verifier.update(manifestBytes);
            if (!verifier.verify(signatureBytes)) throw new IOException("DSHCraft update signature is invalid.");
        } catch (GeneralSecurityException error) {
            throw new IOException("DSHCraft update signature verification failed.", error);
        }
        return parseManifest(new String(manifestBytes, StandardCharsets.UTF_8));
    }

    /// Parses only the signed DSHCraft GitHub release schema and rejects unknown fields.
    private static Manifest parseManifest(String json) throws IOException {
        try {
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) throw new IOException("DSHCraft update manifest must be an object.");
            JsonObject object = parsed.getAsJsonObject();
            if (!object.keySet().equals(MANIFEST_KEYS)) {
                throw new IOException("DSHCraft update manifest has missing or unknown fields.");
            }
            String version = stringField(object, "version");
            if (!version.matches("[0-9]+\\.[0-9]+\\.[0-9]+(?:-[A-Za-z0-9.-]+)?")) {
                throw new IOException("DSHCraft update version is invalid.");
            }
            String sha256 = stringField(object, "sha256");
            if (!sha256.matches("[0-9a-fA-F]{64}")) throw new IOException("DSHCraft update SHA-256 is invalid.");
            URI artifact = releaseUri(stringField(object, "artifactUrl"),
                    "/DSHCraft/DSHCraft/releases/download/v" + version + "/");
            if (artifact.getPath().endsWith("/")) {
                throw new IOException("DSHCraft update artifact URL has no file name.");
            }
            URI notes = releaseUri(stringField(object, "releaseNotesUrl"),
                    "/DSHCraft/DSHCraft/releases/tag/v" + version);
            if (!notes.getPath().equals("/DSHCraft/DSHCraft/releases/tag/v" + version)) {
                throw new IOException("DSHCraft release-notes URL does not match the version.");
            }
            return new Manifest(version, artifact, sha256.toLowerCase(Locale.ROOT), notes);
        } catch (com.google.gson.JsonParseException | IllegalStateException error) {
            throw new IOException("DSHCraft update manifest is malformed.", error);
        }
    }

    /// Reads a required JSON string without coercing numbers or booleans.
    private static String stringField(JsonObject object, String field) throws IOException {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IOException("DSHCraft update field is not a string: " + field);
        }
        return value.getAsString();
    }

    /// Restricts signed release URLs to the DSHCraft repository and exact release tag.
    private static URI releaseUri(String value, String pathPrefix) throws IOException {
        try {
            URI uri = new URI(value);
            if (!"https".equals(uri.getScheme()) || !"github.com".equals(uri.getHost())
                    || uri.getPort() != -1 || uri.getUserInfo() != null
                    || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || !uri.getPath().startsWith(pathPrefix)) {
                throw new IOException("DSHCraft update URL must use the matching GitHub release.");
            }
            return uri;
        } catch (URISyntaxException error) {
            throw new IOException("DSHCraft update URL is invalid.", error);
        }
    }

    /// Verifies the downloaded release file against the hash authenticated by the signed manifest.
    public static void verifyArtifact(Path artifact, Manifest manifest) throws IOException {
        if (!Files.isRegularFile(artifact, LinkOption.NOFOLLOW_LINKS)
                || Files.size(artifact) == 0 || Files.size(artifact) > MAX_ARTIFACT_BYTES) {
            throw new IOException("DSHCraft update artifact is missing or has an invalid size.");
        }
        try (InputStream input = Files.newInputStream(artifact)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[64 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
            byte[] expected = HexFormat.of().parseHex(manifest.sha256());
            if (!MessageDigest.isEqual(digest.digest(), expected)) {
                throw new IOException("DSHCraft update artifact SHA-256 mismatch.");
            }
        } catch (GeneralSecurityException error) {
            throw new IOException("SHA-256 is unavailable for DSHCraft update verification.", error);
        }
    }
}
