/*
 * DSHCraft direct HMCL fork
 * Copyright (C) 2026 DSHCraft contributors
 *
 * This file is licensed under the GNU General Public License, version 3 or later.
 */
package org.jackhuang.hmcl.agent;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.util.Base64;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Tests the fork-specific update trust boundary with disposable signing keys and artifacts.
@NotNullByDefault
public class DshCraftSignedUpdateTest {
    /// Disposable download path for artifact-integrity checks.
    @TempDir
    Path temporary;

    /// A signed manifest authenticates the expected release and artifact bytes.
    @Test
    public void acceptsMatchingSignatureAndArtifact() throws Exception {
        KeyPair keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        Path artifact = temporary.resolve("DSHCraft-1.3.1.jar");
        Files.writeString(artifact, "verified release bytes", StandardCharsets.UTF_8);
        byte[] document = manifest(sha256(Files.readAllBytes(artifact)));
        DshCraftSignedUpdate.Manifest verified = DshCraftSignedUpdate.verifyManifest(
                document, sign(document, keys), keys.getPublic());
        assertEquals("1.3.1", verified.version());
        DshCraftSignedUpdate.verifyArtifact(artifact, verified);
    }

    /// Mutating any signed byte or changing the trust root invalidates the feed.
    @Test
    public void rejectsAlteredManifestAndWrongKey() throws Exception {
        KeyPair keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        KeyPair other = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        byte[] document = manifest("a".repeat(64));
        String signature = sign(document, keys);
        assertThrows(java.io.IOException.class, () -> DshCraftSignedUpdate.verifyManifest(
                new String(document, StandardCharsets.UTF_8).replace("1.3.1", "1.3.2")
                        .getBytes(StandardCharsets.UTF_8), signature, keys.getPublic()));
        assertThrows(java.io.IOException.class, () -> DshCraftSignedUpdate.verifyManifest(
                document, signature, other.getPublic()));
    }

    /// Even a valid signature cannot authorize an unrelated URL or an edited artifact.
    @Test
    public void rejectsUnrelatedReleaseAndArtifactMismatch() throws Exception {
        KeyPair keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        byte[] unrelated = new String(manifest("a".repeat(64)), StandardCharsets.UTF_8)
                .replace("github.com/DSHCraft/DSHCraft", "example.com/DSHCraft/DSHCraft")
                .getBytes(StandardCharsets.UTF_8);
        assertThrows(java.io.IOException.class, () -> DshCraftSignedUpdate.verifyManifest(
                unrelated, sign(unrelated, keys), keys.getPublic()));

        byte[] document = manifest("a".repeat(64));
        DshCraftSignedUpdate.Manifest verified = DshCraftSignedUpdate.verifyManifest(
                document, sign(document, keys), keys.getPublic());
        Path artifact = temporary.resolve("download.jar");
        Files.writeString(artifact, "tampered", StandardCharsets.UTF_8);
        assertThrows(java.io.IOException.class, () -> DshCraftSignedUpdate.verifyArtifact(artifact, verified));
    }

    /// A loopback release feed is fetched, signature-checked, downloaded and hash-checked.
    @Test
    public void fetchesAndVerifiesLoopbackRelease() throws Exception {
        KeyPair keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        byte[] artifactBytes = "loopback release artifact".getBytes(StandardCharsets.UTF_8);
        byte[] document = manifest(sha256(artifactBytes));
        byte[] signature = sign(document, keys).getBytes(StandardCharsets.US_ASCII);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/feed.json", exchange -> respond(exchange, document));
        server.createContext("/feed.json.sig", exchange -> respond(exchange, signature));
        server.createContext("/artifact.jar", exchange -> respond(exchange, artifactBytes));
        server.createContext("/tampered.jar", exchange -> respond(exchange,
                "modified artifact".getBytes(StandardCharsets.UTF_8)));
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            DshCraftSignedUpdate.Manifest verified = DshCraftSignedUpdate.fetchSignedManifest(
                    URI.create(base + "/feed.json"), URI.create(base + "/feed.json.sig"), keys.getPublic());
            Path downloaded = temporary.resolve("verified.jar");
            DshCraftSignedUpdate.downloadVerifiedArtifact(URI.create(base + "/artifact.jar"), verified, downloaded);
            assertEquals("loopback release artifact", Files.readString(downloaded, StandardCharsets.UTF_8));
            Path rejected = temporary.resolve("rejected.jar");
            assertThrows(java.io.IOException.class, () -> DshCraftSignedUpdate.downloadVerifiedArtifact(
                    URI.create(base + "/tampered.jar"), verified, rejected));
            assertFalse(Files.exists(rejected));
        } finally {
            server.stop(0);
        }
    }

    /// Sends fixture bytes without an external network request.
    private static void respond(HttpExchange exchange, byte @Unmodifiable [] body) throws java.io.IOException {
        exchange.sendResponseHeaders(200, body.length);
        try (var output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    /// Creates a minimal pinned GitHub release document for the one test version.
    private static byte[] manifest(String hash) {
        return ("""
                {"version":"1.3.1",\
                 "artifactUrl":"https://github.com/DSHCraft/DSHCraft/releases/download/v1.3.1/DSHCraft-1.3.1.jar",\
                 "sha256":"%s",\
                 "releaseNotesUrl":"https://github.com/DSHCraft/DSHCraft/releases/tag/v1.3.1"}
                """.formatted(hash)).getBytes(StandardCharsets.UTF_8);
    }

    /// Signs exact manifest bytes with a disposable test-only private key.
    private static String sign(byte @Unmodifiable [] document, KeyPair keys) throws Exception {
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(keys.getPrivate());
        signer.update(document);
        return Base64.getEncoder().encodeToString(signer.sign());
    }

    /// Hashes fixture artifact bytes in the same representation as the feed.
    private static String sha256(byte @Unmodifiable [] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
