/*
 * DSHCraft direct HMCL fork
 * Copyright (C) 2026 DSHCraft contributors
 *
 * This file is licensed under the GNU General Public License, version 3 or later.
 */
package org.jackhuang.hmcl.agent;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/// Verifies staged Java/HMCL `.dshpack` Core/Mod installation and cleanup.
@NotNullByDefault
public class AgentPackServiceTest {
    /// Disposable runtime and instance root for real Profile operations.
    @TempDir
    Path temporary;

    /// A real external dependency is installed before a Pack instance becomes visible to the repository.
    @Test
    public void installsPreparedPackIntoIsolatedProfile() throws Exception {
        String configured = System.getenv("DSHCRAFT_E2E_RUNTIME_ROOT");
        assumeTrue(configured != null && !configured.isBlank(), "Set DSHCRAFT_E2E_RUNTIME_ROOT for real Pack E2E");
        AgentInstance instance = instance("pack-success", "is-number@7.0.0");
        AgentExtension extension = new AgentExtension("fixture", "Fixture", "Plugin", "is-number@7.0.0", false);
        AgentPackService.PreparedPack prepared = new AgentPackService.PreparedPack(instance, java.util.List.of(extension),
                java.util.List.of("is-number@7.0.0"));
        String version = AgentPackService.installPrepared(prepared, Path.of(configured), temporary.resolve("instances"));
        Path packageFile = temporary.resolve("instances").resolve("pack-success").resolve("dsh-home")
                .resolve("profiles").resolve("web").resolve("package.json");
        assertTrue(Files.isRegularFile(packageFile));
        assertTrue(DshModService.installedPackages(packageFile.getParent().getParent().getParent(), "web")
                .contains("is-number"));
        assertTrue(version.matches("\\d+\\.\\d+\\.\\d+.*"));
    }

    /// A failed dependency install removes the staged instance directory and cannot leave a partial Pack.
    @Test
    public void failedPreparedPackRemovesStagedInstance() throws Exception {
        String configured = System.getenv("DSHCRAFT_E2E_RUNTIME_ROOT");
        assumeTrue(configured != null && !configured.isBlank(), "Set DSHCRAFT_E2E_RUNTIME_ROOT for real Pack E2E");
        AgentInstance instance = instance("pack-failure", "is-number@999999.0.0");
        AgentPackService.PreparedPack prepared = new AgentPackService.PreparedPack(instance, java.util.List.of(),
                java.util.List.of("is-number@999999.0.0"));
        assertThrows(IOException.class, () -> AgentPackService.installPrepared(prepared, Path.of(configured), temporary.resolve("instances")));
        assertFalse(Files.exists(temporary.resolve("instances").resolve("pack-failure")));
    }

    /// Local and credential-bearing specs are rejected before a Pack can stage a runtime.
    @Test
    public void rejectsNonPortablePackageSpecs() {
        assertThrows(IOException.class, () -> AgentPackService.portablePackageSpec("file:C:/secret/package"));
        assertThrows(IOException.class, () -> AgentPackService.portablePackageSpec("https://user:secret@example.test/mod"));
    }

    /// The default export selection includes instance metadata but never a Provider or API key value.
    @Test
    public void defaultExportSelectionIsPortableAndSecretFree() throws Exception {
        AgentInstance instance = instance("portable", "");
        AgentPackService.ExportOptions options = AgentPackService.ExportOptions.defaults(instance);
        assertTrue(options.includeCore());
        assertTrue(options.includeProfile());
        assertTrue(options.includeModel());
        assertTrue(options.includeExtensions());
        assertFalse(options.includeProvider());
        String json = org.jackhuang.hmcl.util.gson.JsonUtils.GSON.toJson(options);
        assertFalse(json.contains("apiKey"));
        assertFalse(json.contains("secret"));
    }

    /// Discarding a failed commit removes only its generated directory, not another managed instance.
    @Test
    public void failedCommitCleanupPreservesSibling() throws Exception {
        Path root = temporary.resolve("instances");
        Path target = root.resolve("pack-failure");
        Path sibling = root.resolve("another-instance");
        Files.createDirectories(target.resolve("dsh-home"));
        Files.createDirectories(sibling);
        Files.writeString(target.resolve("dsh-home").resolve("partial.txt"), "partial");
        Files.writeString(sibling.resolve("keep.txt"), "keep");

        AgentPackService.PreparedPack prepared = new AgentPackService.PreparedPack(
                instance("pack-failure", ""), java.util.List.of(), java.util.List.of());
        AgentPackService.discardPrepared(prepared, root);
        assertFalse(Files.exists(target));
        assertTrue(Files.isRegularFile(sibling.resolve("keep.txt")));
    }

    /// A crafted ID cannot make Pack cleanup target a sibling through path traversal.
    @Test
    public void cleanupRejectsTraversalInstanceId() throws Exception {
        Path root = temporary.resolve("instances");
        Path sibling = root.resolve("another-instance");
        Files.createDirectories(sibling);
        Files.writeString(sibling.resolve("keep.txt"), "keep");
        AgentPackService.PreparedPack prepared = new AgentPackService.PreparedPack(
                instance("pack-failure/../another-instance", ""), java.util.List.of(), java.util.List.of());
        assertThrows(IOException.class, () -> AgentPackService.discardPrepared(prepared, root));
        assertTrue(Files.isRegularFile(sibling.resolve("keep.txt")));
    }

    /// Creates one valid DSH Web instance fixture for staged Pack operations.
    private static AgentInstance instance(String id, String spec) {
        return new AgentInstance(id, id, "default", "", "0.1.5-rc.2", "web", "web", "3080", "fixture-model", "fixture", "dsh", "", "");
    }
}
