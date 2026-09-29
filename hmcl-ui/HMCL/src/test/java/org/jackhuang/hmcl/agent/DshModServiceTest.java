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
import java.io.OutputStream;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/// Verifies DSH-native Mod command preparation and isolated Profile operations.
@NotNullByDefault
public class DshModServiceTest {
    /// Test-only isolated DSH home for operations that need real pnpm.
    @TempDir
    Path temporary;

    /// Official optional packages default to the current Core version.
    @Test
    public void pinsOfficialBundles() {
        assertEquals("@deepseek-ai/dsh-subagent-codex@0.1.5-rc.2",
                DshModService.pinOfficial("@deepseek-ai/dsh-subagent-codex", "0.1.5-rc.2"));
        assertEquals("@deepseek-ai/dsh-subagent-codex@0.1.4",
                DshModService.pinOfficial("@deepseek-ai/dsh-subagent-codex@0.1.4", "0.1.5-rc.2"));
        assertEquals("is-number@7.0.0", DshModService.pinOfficial("is-number@7.0.0", "0.1.5-rc.2"));
    }

    /// Deletion uses a package name rather than a version-suffixed install spec.
    @Test
    public void removesByPackageName() {
        assertEquals("@deepseek-ai/dsh-subagent-codex",
                DshModService.packageName("@deepseek-ai/dsh-subagent-codex@0.1.5-rc.2"));
        assertEquals("is-number", DshModService.packageName("is-number@7.0.0"));
    }

    /// Reads the real package identity from a dropped npm archive before installation.
    @Test
    public void readsLocalNpmTarballName() throws Exception {
        Path archive = temporary.resolve("plugin.tgz");
        byte[] manifest = "{\"name\":\"@vendor/dsh-plugin\",\"version\":\"1.0.0\"}"
                .getBytes(StandardCharsets.UTF_8);
        byte[] header = new byte[512];
        byte[] name = "package/package.json".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(name, 0, header, 0, name.length);
        byte[] size = String.format("%011o", manifest.length).getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(size, 0, header, 124, size.length);
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(archive))) {
            output.write(header);
            output.write(manifest);
            output.write(new byte[(512 - manifest.length % 512) % 512]);
            output.write(new byte[1024]);
        }
        assertEquals("@vendor/dsh-plugin", DshModService.packageNameFromTarball(archive));
        assertThrows(IOException.class, () -> DshModService.packageNameFromTarball(
                temporary.resolve("missing.tgz")));
    }

    /// Optional real DSH plugin install from a dropped archive in a disposable Profile.
    @Test
    public void installsRealDroppedPluginArchive() throws Exception {
        String archive = System.getenv("DSHCRAFT_E2E_PLUGIN_TARBALL");
        String runtimes = System.getenv("DSHCRAFT_E2E_RUNTIME_ROOT");
        String coreVersion = System.getenv("DSHCRAFT_E2E_CORE_VERSION");
        assumeTrue(archive != null && runtimes != null && coreVersion != null,
                "Set DSHCRAFT_E2E_PLUGIN_TARBALL, DSHCRAFT_E2E_RUNTIME_ROOT and DSHCRAFT_E2E_CORE_VERSION");
        Path home = temporary.resolve("dropped-plugin-instance/dsh-home");
        String packageName = DshModService.packageNameFromTarball(Path.of(archive));
        DshModService.Result result = DshModService.run(Path.of(runtimes), home, coreVersion,
                "web", archive, DshModService.Action.INSTALL);
        assertEquals(packageName, result.installedPackageName());
        assertTrue(DshModService.installedPackages(home, "web").contains(packageName));
    }

    /// Missing Profiles report no versions, keeping the Mod list usable before first launch.
    @Test
    public void readsInstalledPackageVersions() throws Exception {
        Path home = temporary.resolve("version-home");
        Path packageFile = home.resolve("profiles/web/package.json");
        Files.createDirectories(packageFile.getParent());
        Files.writeString(packageFile, "{\"dependencies\":{\"is-number\":\"7.0.0\"}}");
        assertEquals("7.0.0", DshModService.installedPackageVersions(home, "web").get("is-number"));
        Path installedManifest = home.resolve("profiles/web/node_modules/@vendor/dsh-plugin/package.json");
        Files.createDirectories(installedManifest.getParent());
        Files.writeString(installedManifest, "{\"name\":\"@vendor/dsh-plugin\",\"version\":\"1.2.3\"}");
        Files.writeString(packageFile, "{\"dependencies\":{\"@vendor/dsh-plugin\":\"file:C:/tmp/plugin.tgz\"}}");
        assertEquals("1.2.3", DshModService.installedPackageVersions(home, "web").get("@vendor/dsh-plugin"));
    }

    /// Hostile Profile names and credential-bearing specs are rejected before execution.
    @Test
    public void rejectsUnsafeInputs() {
        for (String name : new String[]{"../web", "CON", "COM1", "web.", ""}) {
            assertThrows(IOException.class, () -> DshModService.validateProfile(name));
        }
        for (String id : new String[]{"../outside", "CON", "com1", "a.b"}) {
            assertThrows(IOException.class, () -> DshModService.validateInstanceId(id));
        }
        assertThrows(IOException.class, () -> DshModService.validateVersion("latest"));
        assertThrows(IOException.class, () ->
                DshModService.validateSpec("https://user:secret@example.test/package"));
        assertThrows(IOException.class, () ->
                DshModService.validateSpec("https://example.test/package?api_key=secret"));
        assertThrows(IOException.class, () ->
                DshModService.installedCoreCli(temporary, "latest"));
        assertThrows(IOException.class, () ->
                DshModService.installedCoreCli(temporary, "0.1.5-rc.2"));
    }

    /// Registry selection accepts known mirrors and safe custom roots while rejecting credential URLs.
    @Test
    public void validatesDownloadRegistries() throws Exception {
        assertEquals("https://registry.npmjs.org",
                AgentNetworkService.validateRegistry("official", ""));
        assertEquals("https://registry.npmmirror.com",
                AgentNetworkService.validateRegistry("npmmirror", ""));
        assertEquals("https://repo.huaweicloud.com/repository/npm",
                AgentNetworkService.validateRegistry("huawei", ""));
        assertEquals("https://mirrors.cloud.tencent.com/npm",
                AgentNetworkService.validateRegistry("tencent", ""));
        assertEquals("https://packages.example.test/npm",
                AgentNetworkService.validateRegistry("custom", "https://packages.example.test/npm/"));
        for (String unsafe : new String[]{"file:///tmp/registry", "https://user:pass@example.test",
                "https://example.test?token=secret", "https://example.test/#fragment"}) {
            assertThrows(IOException.class, () ->
                    AgentNetworkService.validateRegistry("custom", unsafe));
        }
        assertThrows(IOException.class, () -> AgentNetworkService.validateRegistry("unknown", ""));
    }

    /// A launch command preserves the selected Profile and applies its template only when needed.
    @Test
    public void launchesFromManagedCoreWithInstanceProfile() {
        Path cli = temporary.resolve("runtime").resolve("bin.js");
        var web = AgentRepository.launchCommand(cli, "custom", "web", "3080", false);
        assertEquals(DshModService.managedNodeExecutable(), web.get(0));
        assertEquals(cli.toString(), web.get(1));
        assertEquals(java.util.List.of("--profile", "custom", "--from-default-profile", "web", "--port", "3080", "--no-open"),
                web.subList(2, web.size()));
        var existing = AgentRepository.launchCommand(cli, "custom", "web", "3080", true);
        assertFalse(existing.contains("--from-default-profile"));
        var shipped = AgentRepository.launchCommand(cli, "web", "web", "3080", false);
        assertFalse(shipped.contains("--from-default-profile"));
        assertTrue(shipped.contains("--profile"));
    }

    /// Deleting an instance removes only its isolated DSH_HOME and preserves Workspace and neighboring data.
    @Test
    public void deletesOnlyManagedDshHome() throws Exception {
        Path instances = temporary.resolve("instances");
        Path dshHome = instances.resolve("agent-a").resolve("dsh-home");
        Files.createDirectories(dshHome.resolve("profiles/web"));
        Files.writeString(dshHome.resolve("profiles/web/settings.yaml"), "fixture");
        Path workspace = instances.resolve("agent-a").resolve("workspace.txt");
        Files.writeString(workspace, "keep");
        Path neighbor = instances.resolve("agent-b/dsh-home/keep.txt");
        Files.createDirectories(neighbor.getParent());
        Files.writeString(neighbor, "keep");

        AgentRepository.deleteManagedDshHome(instances, "agent-a");

        assertFalse(Files.exists(dshHome));
        assertEquals("keep", Files.readString(workspace));
        assertEquals("keep", Files.readString(neighbor));
        assertThrows(IOException.class, () -> AgentRepository.deleteManagedDshHome(instances, "../outside"));
    }

    /// Local Core selection exposes only complete published-version runtime directories.
    @Test
    public void listsOnlyInstalledCoreVersions() throws Exception {
        Path runtimes = temporary.resolve("core-runtimes");
        Path installedCli = runtimes.resolve("0.1.5-rc.3/node_modules/@deepseek-ai/dsh/lib/bin.js");
        Files.createDirectories(installedCli.getParent());
        Files.writeString(installedCli, "fixture");
        Path incomplete = runtimes.resolve("0.1.6/node_modules/@deepseek-ai/dsh/lib");
        Files.createDirectories(incomplete);
        Path staging = runtimes.resolve(".0.1.7.installing-123");
        Files.createDirectories(staging);
        Files.writeString(staging.resolve("package.json"), "{}");

        assertEquals(java.util.List.of("0.1.5-rc.3"), DshModService.installedCoreVersions(runtimes));
    }

    /// A failed pnpm install cannot publish or leave behind a partial Core runtime.
    @Test
    public void failedCoreInstallRemovesItsStagingDirectory() throws Exception {
        Path runtimes = temporary.resolve("failed-runtimes");
        String previous = System.getProperty("dshcraft.pnpm.executable");
        try {
            System.setProperty("dshcraft.pnpm.executable", "java");
            assertThrows(IOException.class, () ->
                    DshModService.ensureCore(runtimes, "0.0.0-codex-fixture"));
            assertFalse(Files.exists(runtimes.resolve("0.0.0-codex-fixture")));
            try (var entries = Files.list(runtimes)) {
                assertEquals(0, entries.count());
            }
        } finally {
            if (previous == null) System.clearProperty("dshcraft.pnpm.executable");
            else System.setProperty("dshcraft.pnpm.executable", previous);
        }
    }

    /// Installs an actual Core into a disposable directory only when explicitly enabled.
    @Test
    public void installsRealIsolatedCore() throws Exception {
        assumeTrue("true".equals(System.getenv("DSHCRAFT_E2E_INSTALL_CORE")),
                "Set DSHCRAFT_E2E_INSTALL_CORE=true for the real npm installation");
        Path runtimes = temporary.resolve("fresh-runtimes");
        Path installed = DshModService.ensureCore(runtimes, "0.1.5-rc.2");
        assertTrue(Files.isRegularFile(installed.resolve("node_modules")
                .resolve("@deepseek-ai").resolve("dsh").resolve("lib").resolve("bin.js")));
        assertEquals(installed, DshModService.ensureCore(runtimes, "0.1.5-rc.2"));
    }

    /// Optional local recovery check installs a selected Core in the same managed cache used by launch.
    @Test
    public void installsSelectedCoreInLauncherRuntime() throws Exception {
        assumeTrue("true".equals(System.getenv("DSHCRAFT_E2E_INSTALL_SELECTED_CORE")),
                "Set DSHCRAFT_E2E_INSTALL_SELECTED_CORE=true for a real managed-cache install");
        String version = System.getenv("DSHCRAFT_E2E_CORE_VERSION");
        assumeTrue(version != null && !version.isBlank(), "Set DSHCRAFT_E2E_CORE_VERSION");
        Path runtime = DshModService.ensureCore(DshModService.runtimeRoot(), version);
        assertEquals(runtime.resolve("node_modules/@deepseek-ai/dsh/lib/bin.js"),
                DshModService.installedCoreCli(DshModService.runtimeRoot(), version));
    }

    /// Runs the official CLI with a disposable DSH_HOME when a real Core is supplied explicitly.
    @Test
    public void installsUpdatesAndRemovesRealProfileDependency() throws Exception {
        String root = System.getenv("DSHCRAFT_E2E_RUNTIME_ROOT");
        assumeTrue(root != null && !root.isBlank(), "Set DSHCRAFT_E2E_RUNTIME_ROOT for real DSH E2E");
        Path runtimes = Path.of(root);
        Path home = temporary.resolve("isolated-instance").resolve("dsh-home");
        Path packageFile = home.resolve("profiles").resolve("web").resolve("node_modules")
                .resolve("is-number").resolve("package.json");
        assertFalse(Files.exists(packageFile));
        assertTrue(DshModService.run(runtimes, home, "0.1.5-rc.2", "web",
                "is-number@7.0.0", DshModService.Action.INSTALL).restartRequired());
        assertTrue(Files.isRegularFile(packageFile));
        assertTrue(DshModService.installedPackages(home, "web").contains("is-number"));
        DshModService.run(runtimes, home, "0.1.5-rc.2", "web",
                "is-number", DshModService.Action.UPDATE);
        assertTrue(Files.isRegularFile(packageFile));
        DshModService.run(runtimes, home, "0.1.5-rc.2", "web",
                "is-number", DshModService.Action.REMOVE);
        assertFalse(DshModService.installedPackages(home, "web").contains("is-number"));
    }

    /// Boots a disposable Web Profile and verifies it announces a loopback URL.
    @Test
    public void launchesRealIsolatedWebProfile() throws Exception {
        String root = System.getenv("DSHCRAFT_E2E_RUNTIME_ROOT");
        assumeTrue(root != null && !root.isBlank(), "Set DSHCRAFT_E2E_RUNTIME_ROOT for real DSH E2E");
        String requested = System.getenv("DSHCRAFT_E2E_CORE_VERSION");
        String version = requested == null || requested.isBlank() ? "0.1.5-rc.3" : requested;
        Path cli = DshModService.installedCoreCli(Path.of(root), version);
        int port;
        try (ServerSocket available = new ServerSocket(0)) {
            port = available.getLocalPort();
        }
        Path home = temporary.resolve("web-instance").resolve("dsh-home");
        Files.createDirectories(home);
        ProcessBuilder builder = new ProcessBuilder(AgentRepository.launchCommand(
                cli, "web", "web", Integer.toString(port),
                Files.isDirectory(home.resolve("profiles").resolve("web"))));
        builder.directory(home.getParent().toFile());
        builder.redirectErrorStream(true);
        builder.environment().put("DSH_HOME", home.toString());
        DshModService.configureWindowsModuleFallback(builder, cli);
        Process process = builder.start();
        try {
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(40);
            StringBuilder output = new StringBuilder();
            while (System.nanoTime() < deadline && !output.toString().contains("dsh web:")) {
                while (process.getInputStream().available() > 0 && output.length() < 65536) {
                    output.append((char) process.getInputStream().read());
                }
                if (!process.isAlive()) break;
                Thread.sleep(100);
            }
            assertTrue(output.toString().contains("dsh web:"),
                    "The isolated web profile did not announce its local URL");
        } finally {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
            process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
        }
    }
}
