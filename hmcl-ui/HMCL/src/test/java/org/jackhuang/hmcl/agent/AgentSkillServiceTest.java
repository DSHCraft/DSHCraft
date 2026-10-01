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
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies that local skill imports cannot escape or overwrite an instance DSH_HOME.
@NotNullByDefault
public class AgentSkillServiceTest {
    /// Disposable source and isolated home roots.
    @TempDir
    Path temporary;

    /// A selected skill is copied into only the chosen instance.
    @Test
    public void installsOnlyInSelectedHome() throws Exception {
        Path source = Files.createDirectory(temporary.resolve("my-skill"));
        Files.writeString(source.resolve("SKILL.md"), "---\nname: my-skill\ndescription: Test\n---\nInstructions");
        Path first = temporary.resolve("first");
        Path second = temporary.resolve("second");
        Path installed = AgentSkillService.install(source, first);
        assertEquals(first.resolve("skills/my-skill"), installed);
        assertTrue(Files.isRegularFile(installed.resolve("SKILL.md")));
        assertFalse(Files.exists(second.resolve("skills/my-skill")));
        assertThrows(IOException.class, () -> AgentSkillService.install(source, first));
    }

    /// Invalid folders cannot create an installed skill entry.
    @Test
    public void rejectsMissingManifestAndInvalidName() throws Exception {
        Path missing = Files.createDirectory(temporary.resolve("missing-skill"));
        Path invalid = Files.createDirectory(temporary.resolve("Bad Name"));
        Files.writeString(invalid.resolve("SKILL.md"), "test");
        Path home = temporary.resolve("instance");
        assertThrows(IOException.class, () -> AgentSkillService.install(missing, home));
        assertThrows(IOException.class, () -> AgentSkillService.install(invalid, home));
        assertFalse(Files.exists(home.resolve("skills/Bad Name")));
    }

    /// A placeholder SKILL.md is not misreported as a usable DSH skill.
    @Test
    public void rejectsInvalidSkillHeader() throws Exception {
        Path source = Files.createDirectory(temporary.resolve("my-skill"));
        Files.writeString(source.resolve("SKILL.md"), "# Missing frontmatter");
        Path home = temporary.resolve("instance");
        assertThrows(IOException.class, () -> AgentSkillService.install(source, home));
        assertFalse(Files.exists(home.resolve("skills/my-skill")));
    }

    /// A downloaded public SKILL.md is validated and installed only into the captured home.
    @Test
    public void downloadsSingleFileSkillIntoSelectedHome() throws Exception {
        byte[] body = "---\nname: remote-skill\ndescription: Test\n---\nInstructions"
                .getBytes(StandardCharsets.UTF_8);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/remote-skill/SKILL.md", exchange -> {
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        try {
            URI source = URI.create("http://127.0.0.1:" + server.getAddress().getPort()
                    + "/remote-skill/SKILL.md");
            Path first = temporary.resolve("first");
            Path second = temporary.resolve("second");
            assertEquals("remote-skill", AgentSkillService.remoteSkillName(source));
            assertEquals(first.resolve("skills/remote-skill"), AgentSkillService.download(source, first));
            assertTrue(Files.readString(first.resolve("skills/remote-skill/SKILL.md")).contains("Instructions"));
            assertFalse(Files.exists(second.resolve("skills/remote-skill")));
            assertThrows(IOException.class, () -> AgentSkillService.download(source, first));
        } finally {
            server.stop(0);
        }
    }

    /// Public download URLs cannot carry credentials or select an unsafe target name.
    @Test
    public void rejectsUnsafeRemoteSkillUrls() {
        for (String url : new String[]{"https://user:secret@example.com/my-skill/SKILL.md",
                "https://example.com/my-skill/SKILL.md?token=secret",
                "https://example.com/BadName/SKILL.md", "http://example.com/my-skill/SKILL.md"}) {
            assertThrows(IOException.class, () -> AgentSkillService.remoteSkillName(URI.create(url)));
        }
    }
}
