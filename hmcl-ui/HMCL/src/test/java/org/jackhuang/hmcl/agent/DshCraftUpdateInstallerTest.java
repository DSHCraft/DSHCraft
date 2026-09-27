/*
 * DSHCraft direct HMCL fork
 * Copyright (C) 2026 DSHCraft contributors
 *
 * This file is licensed under the GNU General Public License, version 3 or later.
 */
package org.jackhuang.hmcl.agent;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.util.Base64;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises signed offline replacement and rollback only in disposable directories.
@NotNullByDefault
public class DshCraftUpdateInstallerTest {
    /// Test-only launcher directory, never the running application.
    @TempDir
    Path temporary;

    /// A valid signed candidate replaces the old file while retaining a recovery copy.
    @Test
    public void installsVerifiedBytesAndRetainsBackup() throws Exception {
        KeyPair keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        Path candidate = temporary.resolve("downloaded.jar");
        Path target = temporary.resolve("DSHCraft.jar");
        Files.writeString(candidate, "new signed launcher", StandardCharsets.UTF_8);
        Files.writeString(target, "old launcher", StandardCharsets.UTF_8);
        byte[] document = manifest(sha256(Files.readAllBytes(candidate)));
        DshCraftUpdateInstaller.Installation result = DshCraftUpdateInstaller.replaceOffline(
                candidate, target, document, sign(document, keys), keys.getPublic(), () -> { });
        assertEquals("new signed launcher", Files.readString(result.target(), StandardCharsets.UTF_8));
        assertEquals("old launcher", Files.readString(result.backup(), StandardCharsets.UTF_8));
        assertTrue(Files.exists(candidate));
    }

    /// A failure after moving the original restores it before exposing any new file.
    @Test
    public void restoresTargetWhenInstallationFails() throws Exception {
        KeyPair keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        Path candidate = temporary.resolve("downloaded.jar");
        Path target = temporary.resolve("DSHCraft.jar");
        Files.writeString(candidate, "new signed launcher", StandardCharsets.UTF_8);
        Files.writeString(target, "old launcher", StandardCharsets.UTF_8);
        byte[] document = manifest(sha256(Files.readAllBytes(candidate)));
        assertThrows(IOException.class, () -> DshCraftUpdateInstaller.replaceOffline(
                candidate, target, document, sign(document, keys), keys.getPublic(),
                () -> { throw new IOException("forced fixture failure"); }));
        assertEquals("old launcher", Files.readString(target, StandardCharsets.UTF_8));
        try (var entries = Files.list(temporary)) {
            assertFalse(entries.anyMatch(path -> path.getFileName().toString().startsWith(".dshcraft-")));
        }
    }

    /// Invalid signatures cannot mutate the existing launcher file.
    @Test
    public void rejectsUnsignedCandidateBeforeAnyReplacement() throws Exception {
        KeyPair keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        Path candidate = temporary.resolve("downloaded.jar");
        Path target = temporary.resolve("DSHCraft.jar");
        Files.writeString(candidate, "new signed launcher", StandardCharsets.UTF_8);
        Files.writeString(target, "old launcher", StandardCharsets.UTF_8);
        byte[] document = manifest(sha256(Files.readAllBytes(candidate)));
        byte[] altered = new String(document, StandardCharsets.UTF_8).replace("1.3.1", "1.3.2")
                .getBytes(StandardCharsets.UTF_8);
        assertThrows(IOException.class, () -> DshCraftUpdateInstaller.replaceOffline(
                candidate, target, altered, sign(document, keys), keys.getPublic(), () -> { }));
        assertEquals("old launcher", Files.readString(target, StandardCharsets.UTF_8));
    }

    /// The helper starts the installed file only after signed replacement succeeds.
    @Test
    public void handoffStartsInstalledTarget() throws Exception {
        KeyPair keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        Path candidate = temporary.resolve("downloaded.jar");
        Path target = temporary.resolve("DSHCraft.jar");
        Files.writeString(candidate, "new signed launcher", StandardCharsets.UTF_8);
        Files.writeString(target, "old launcher", StandardCharsets.UTF_8);
        byte[] document = manifest(sha256(Files.readAllBytes(candidate)));
        AtomicBoolean started = new AtomicBoolean();
        DshCraftUpdateHandoff.applyVerified(candidate, target, document, sign(document, keys),
                keys.getPublic(), installed -> {
                    assertEquals("new signed launcher", Files.readString(installed, StandardCharsets.UTF_8));
                    started.set(true);
                });
        assertTrue(started.get());
    }

    /// A failure to launch the newly installed file restores the prior file.
    @Test
    public void handoffRollsBackWhenRestartFails() throws Exception {
        KeyPair keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        Path candidate = temporary.resolve("downloaded.jar");
        Path target = temporary.resolve("DSHCraft.jar");
        Files.writeString(candidate, "new signed launcher", StandardCharsets.UTF_8);
        Files.writeString(target, "old launcher", StandardCharsets.UTF_8);
        byte[] document = manifest(sha256(Files.readAllBytes(candidate)));
        assertThrows(IOException.class, () -> DshCraftUpdateHandoff.applyVerified(
                candidate, target, document, sign(document, keys), keys.getPublic(),
                installed -> { throw new IOException("fixture start failure"); }));
        assertEquals("old launcher", Files.readString(target, StandardCharsets.UTF_8));
    }

    /// The parent stages a copy of its trusted launcher and exact signed feed before spawning a helper.
    @Test
    public void stagesTrustedHelperWithSignedFiles() throws Exception {
        KeyPair keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        Path current = temporary.resolve("DSHCraft-current.exe");
        Path candidate = temporary.resolve("DSHCraft-next.exe");
        Files.writeString(current, "trusted current launcher", StandardCharsets.UTF_8);
        Files.writeString(candidate, "signed next launcher", StandardCharsets.UTF_8);
        byte[] document = manifest(sha256(Files.readAllBytes(candidate)));
        String signature = sign(document, keys);
        DshCraftSignedUpdate.SignedFeed feed = new DshCraftSignedUpdate.SignedFeed(
                DshCraftSignedUpdate.verifyManifest(document, signature, keys.getPublic()), document, signature);
        AtomicBoolean started = new AtomicBoolean();
        Path handoff = DshCraftUpdateHandoff.stageAndStartHelper(current, candidate, feed,
                keys.getPublic(), temporary, (helper, arguments) -> {
                    assertEquals("trusted current launcher", Files.readString(helper, StandardCharsets.UTF_8));
                    assertEquals("--apply-dshcraft-update", arguments.get(0));
                    assertEquals(candidate.toAbsolutePath().toString(), arguments.get(1));
                    assertEquals(current.toAbsolutePath().toString(), arguments.get(2));
                    assertEquals(6, arguments.size());
                    started.set(true);
                });
        assertTrue(started.get());
        assertEquals(new String(document, StandardCharsets.UTF_8),
                Files.readString(handoff.resolve("manifest.json"), StandardCharsets.UTF_8));
        assertEquals(signature, Files.readString(handoff.resolve("manifest.json.sig"), StandardCharsets.US_ASCII));
    }

    /// A helper-spawn failure removes only its newly staged files.
    @Test
    public void failedHelperStartCleansStaging() throws Exception {
        KeyPair keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        Path current = temporary.resolve("DSHCraft-current.exe");
        Path candidate = temporary.resolve("DSHCraft-next.exe");
        Files.writeString(current, "trusted current launcher", StandardCharsets.UTF_8);
        Files.writeString(candidate, "signed next launcher", StandardCharsets.UTF_8);
        byte[] document = manifest(sha256(Files.readAllBytes(candidate)));
        String signature = sign(document, keys);
        DshCraftSignedUpdate.SignedFeed feed = new DshCraftSignedUpdate.SignedFeed(
                DshCraftSignedUpdate.verifyManifest(document, signature, keys.getPublic()), document, signature);
        assertThrows(IOException.class, () -> DshCraftUpdateHandoff.stageAndStartHelper(
                current, candidate, feed, keys.getPublic(), temporary,
                (helper, arguments) -> { throw new IOException("fixture spawn failure"); }));
        try (var entries = Files.list(temporary)) {
            assertFalse(entries.anyMatch(path -> path.getFileName().toString()
                    .startsWith("dshcraft-update-handoff-")));
        }
        assertEquals("trusted current launcher", Files.readString(current, StandardCharsets.UTF_8));
    }

    /// Constructs a matching pinned release manifest for disposable bytes.
    private static byte[] manifest(String hash) {
        return ("""
                {"version":"1.3.1",\
                 "artifactUrl":"https://github.com/DSHCraft/DSHCraft/releases/download/v1.3.1/DSHCraft-1.3.1.jar",\
                 "sha256":"%s",\
                 "releaseNotesUrl":"https://github.com/DSHCraft/DSHCraft/releases/tag/v1.3.1"}
                """.formatted(hash)).getBytes(StandardCharsets.UTF_8);
    }

    /// Signs fixture JSON only with a disposable private key.
    private static String sign(byte @Unmodifiable [] document, KeyPair keys) throws Exception {
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(keys.getPrivate());
        signer.update(document);
        return Base64.getEncoder().encodeToString(signer.sign());
    }

    /// Produces the manifest's artifact hash from fixture bytes.
    private static String sha256(byte @Unmodifiable [] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
