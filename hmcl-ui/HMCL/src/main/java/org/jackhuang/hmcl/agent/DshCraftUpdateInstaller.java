/*
 * DSHCraft direct HMCL fork
 * Copyright (C) 2026 DSHCraft contributors
 *
 * This file is licensed under the GNU General Public License, version 3 or later.
 */
package org.jackhuang.hmcl.agent;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.PublicKey;

/// Replaces an offline launcher file only after rechecking signed release bytes and keeps a rollback copy.
@NotNullByDefault
public final class DshCraftUpdateInstaller {
    /// The installed target and its retained pre-update backup.
    @NotNullByDefault
    public record Installation(Path target, Path backup) {
    }

    /// Operation hook used only to exercise rollback after the original file has moved.
    @FunctionalInterface
    interface AfterBackup {
        /// Runs between the target backup and new-file move.
        void run() throws IOException;
    }

    /// Prevents instantiation of the installer.
    private DshCraftUpdateInstaller() {
    }

    /// Replaces a file after the current launcher process has exited; production requires its embedded key.
    public static Installation replaceOffline(Path candidate, Path target,
                                              byte @Unmodifiable [] manifestBytes, String signatureBase64)
            throws IOException {
        return replaceOffline(candidate, target, manifestBytes, signatureBase64,
                DshCraftSignedUpdate.loadEmbeddedPublicKey(), () -> { });
    }

    /// Uses a supplied key and hook for disposable tests, never for production update entrypoints.
    static Installation replaceOffline(Path candidate, Path target,
                                       byte @Unmodifiable [] manifestBytes, String signatureBase64,
                                       PublicKey publicKey, AfterBackup afterBackup) throws IOException {
        DshCraftSignedUpdate.Manifest manifest = DshCraftSignedUpdate.verifyManifest(
                manifestBytes, signatureBase64, publicKey);
        DshCraftSignedUpdate.verifyArtifact(candidate, manifest);
        Path absoluteTarget = target.toAbsolutePath().normalize();
        Path parent = absoluteTarget.getParent();
        if (parent == null || !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(parent)
                || !Files.isRegularFile(absoluteTarget, LinkOption.NOFOLLOW_LINKS)
                || absoluteTarget.equals(candidate.toAbsolutePath().normalize())) {
            throw new IOException("DSHCraft update target must be a separate regular launcher file.");
        }

        Path staged = Files.createTempFile(parent, ".dshcraft-install-", ".part");
        @Nullable Path backupDirectory = null;
        @Nullable Path backup = null;
        boolean targetMoved = false;
        boolean installed = false;
        try {
            Files.copy(candidate, staged, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            DshCraftSignedUpdate.verifyArtifact(staged, manifest);
            backupDirectory = Files.createTempDirectory(parent, ".dshcraft-backup-");
            backup = backupDirectory.resolve(absoluteTarget.getFileName());
            Files.move(absoluteTarget, backup);
            targetMoved = true;
            afterBackup.run();
            Files.move(staged, absoluteTarget);
            installed = true;
            return new Installation(absoluteTarget, backup);
        } catch (IOException | RuntimeException error) {
            if (targetMoved && backup != null) {
                try {
                    Files.move(backup, absoluteTarget, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException rollbackError) {
                    error.addSuppressed(rollbackError);
                }
            }
            throw error;
        } finally {
            Files.deleteIfExists(staged);
            if (!installed && backupDirectory != null) {
                try {
                    Files.deleteIfExists(backupDirectory);
                } catch (IOException ignored) {
                    // Keep the directory if rollback left a recoverable backup inside it.
                }
            }
        }
    }

    /// Restores a retained pre-update file if launching the newly installed file fails.
    public static void restoreBackup(Installation installation) throws IOException {
        Path backup = installation.backup().toAbsolutePath().normalize();
        Path target = installation.target().toAbsolutePath().normalize();
        if (!Files.isRegularFile(backup, LinkOption.NOFOLLOW_LINKS)
                || !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)
                || !backup.getFileName().equals(target.getFileName())) {
            throw new IOException("DSHCraft update backup or target is invalid.");
        }
        Files.move(backup, target, StandardCopyOption.REPLACE_EXISTING);
    }
}
