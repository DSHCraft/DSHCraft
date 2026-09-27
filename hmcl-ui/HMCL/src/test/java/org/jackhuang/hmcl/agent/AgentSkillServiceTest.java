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
}
