/*
 * DSHCraft direct HMCL fork
 * Copyright (C) 2026 DSHCraft contributors
 *
 * This file is licensed under the GNU General Public License, version 3 or later.
 */
package org.jackhuang.hmcl.agent;

import org.jackhuang.hmcl.upgrade.UpdateHandler;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.PublicKey;
import java.time.Duration;
import java.util.List;

/// Runs in a trusted copy of the old launcher after its parent exits, then installs a signed new file.
@NotNullByDefault
public final class DshCraftUpdateHandoff {
    /// Maximum time a helper waits for the original launcher process to release its file.
    private static final Duration PARENT_EXIT_TIMEOUT = Duration.ofSeconds(30);

    /// Starts an installed launcher; substitutable only in disposable tests.
    @FunctionalInterface
    interface Starter {
        /// Starts the installed target and reports a launch failure.
        void start(Path target) throws IOException;
    }

    /// Launches a trusted copy of the current binary with update-only arguments.
    @FunctionalInterface
    interface HelperStarter {
        /// Starts the helper process from a copied launcher and argument list.
        void start(Path helper, @Unmodifiable List<String> arguments) throws IOException;
    }

    /// Prevents instantiation of the handoff helper.
    private DshCraftUpdateHandoff() {
    }

    /// Starts a trusted helper copy; the caller must then save state and exit its current process.
    public static Path stageAndStartHelper(Path currentLauncher, Path candidate,
                                           DshCraftSignedUpdate.SignedFeed signedFeed) throws IOException {
        return stageAndStartHelper(currentLauncher, candidate, signedFeed,
                DshCraftSignedUpdate.loadEmbeddedPublicKey(),
                Path.of(System.getProperty("java.io.tmpdir")),
                (helper, arguments) -> UpdateHandler.startJava(helper, arguments.toArray(String[]::new)));
    }

    /// Validates all signed inputs before creating handoff files or launching a test helper.
    static Path stageAndStartHelper(Path currentLauncher, Path candidate,
                                    DshCraftSignedUpdate.SignedFeed signedFeed,
                                    PublicKey trustedKey, Path stagingParent, HelperStarter starter) throws IOException {
        DshCraftSignedUpdate.Manifest verified = DshCraftSignedUpdate.verifyManifest(
                signedFeed.manifestBytes(), signedFeed.signatureBase64(), trustedKey);
        DshCraftSignedUpdate.verifyArtifact(candidate, verified);
        Path current = currentLauncher.toAbsolutePath().normalize();
        if (!Files.isRegularFile(current, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Current DSHCraft launcher file is unavailable for update handoff.");
        }
        if (current.equals(candidate.toAbsolutePath().normalize())
                || !Files.isDirectory(stagingParent, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(stagingParent)) {
            throw new IOException("DSHCraft update staging directory or candidate is invalid.");
        }
        String name = current.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        if (!name.endsWith(".exe") && !name.endsWith(".jar")) {
            throw new IOException("DSHCraft update helper requires an EXE or JAR launcher.");
        }
        String suffix = name.endsWith(".exe") ? ".exe" : ".jar";
        Path directory = Files.createTempDirectory(stagingParent, "dshcraft-update-handoff-");
        Path helper = directory.resolve("DSHCraft-update-helper" + suffix);
        Path document = directory.resolve("manifest.json");
        Path signature = directory.resolve("manifest.json.sig");
        boolean launched = false;
        try {
            Files.copy(current, helper, StandardCopyOption.COPY_ATTRIBUTES);
            Files.write(document, signedFeed.manifestBytes(), StandardOpenOption.CREATE_NEW);
            Files.writeString(signature, signedFeed.signatureBase64(), StandardCharsets.US_ASCII,
                    StandardOpenOption.CREATE_NEW);
            starter.start(helper, List.of("--apply-dshcraft-update", candidate.toAbsolutePath().normalize().toString(),
                    current.toString(), document.toString(), signature.toString(),
                    Long.toString(ProcessHandle.current().pid())));
            launched = true;
            return directory;
        } finally {
            if (!launched) {
                Files.deleteIfExists(signature);
                Files.deleteIfExists(document);
                Files.deleteIfExists(helper);
                Files.deleteIfExists(directory);
            }
        }
    }

    /// Handles the product-specific helper command with only the embedded DSHCraft public key.
    public static void apply(Path candidate, Path target, Path manifestFile, Path signatureFile, long parentPid)
            throws IOException, InterruptedException {
        PublicKey trustedKey = DshCraftSignedUpdate.loadEmbeddedPublicKey();
        byte[] document = readBounded(manifestFile, 16 * 1024);
        String signature = new String(readBounded(signatureFile, 256), StandardCharsets.US_ASCII);
        DshCraftSignedUpdate.Manifest manifest = DshCraftSignedUpdate.verifyManifest(
                document, signature, trustedKey);
        DshCraftSignedUpdate.verifyArtifact(candidate, manifest);
        awaitParentExit(parentPid);
        applyVerified(candidate, target, document, signature, trustedKey,
                installed -> UpdateHandler.startJava(installed));
    }

    /// Installs signed bytes and restores the backup when the new process cannot start.
    static void applyVerified(Path candidate, Path target, byte @Unmodifiable [] document,
                              String signature, PublicKey trustedKey, Starter starter) throws IOException {
        DshCraftUpdateInstaller.Installation installed = DshCraftUpdateInstaller.replaceOffline(
                candidate, target, document, signature, trustedKey, () -> { });
        try {
            starter.start(installed.target());
        } catch (IOException error) {
            try {
                DshCraftUpdateInstaller.restoreBackup(installed);
            } catch (IOException rollbackError) {
                error.addSuppressed(rollbackError);
            }
            throw error;
        }
    }

    /// Waits for the original process rather than racing its Windows executable file lock.
    private static void awaitParentExit(long parentPid) throws IOException, InterruptedException {
        if (parentPid <= 0 || parentPid == ProcessHandle.current().pid()) {
            throw new IOException("DSHCraft update parent PID is invalid.");
        }
        long deadline = System.nanoTime() + PARENT_EXIT_TIMEOUT.toNanos();
        while (ProcessHandle.of(parentPid).map(ProcessHandle::isAlive).orElse(false)) {
            if (System.nanoTime() >= deadline) {
                throw new IOException("DSHCraft update parent did not exit in time.");
            }
            Thread.sleep(100);
        }
    }

    /// Reads only a regular bounded handoff file; links and oversized files are rejected.
    private static byte @Unmodifiable [] readBounded(Path file, int maximum) throws IOException {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                || Files.size(file) == 0 || Files.size(file) > maximum) {
            throw new IOException("DSHCraft update handoff file is missing or oversized.");
        }
        return Files.readAllBytes(file);
    }
}
