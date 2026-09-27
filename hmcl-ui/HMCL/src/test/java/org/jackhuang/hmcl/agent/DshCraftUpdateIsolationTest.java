/*
 * DSHCraft direct HMCL fork
 * Copyright (C) 2026 DSHCraft contributors
 *
 * This file is licensed under the GNU General Public License, version 3 or later.
 */
package org.jackhuang.hmcl.agent;

import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.upgrade.UpdateChannel;
import org.jackhuang.hmcl.upgrade.UpdateChecker;
import org.jackhuang.hmcl.upgrade.UpdateHandler;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Ensures the GPL fork never treats an HMCL release as a DSHCraft update.
@NotNullByDefault
public class DshCraftUpdateIsolationTest {
    /// Disposable target that must not be overwritten by an HMCL update argument.
    @TempDir
    Path temporary;

    /// Startup and manual checks remain idle until a DSHCraft signed channel exists.
    @Test
    public void hmclUpdaterDoesNotRunForDshCraft() {
        assertEquals("DSHCraft", Metadata.NAME);
        UpdateChecker.init();
        UpdateChecker.requestCheckUpdate(UpdateChannel.STABLE, false);
        assertFalse(UpdateChecker.isCheckingUpdate());
        assertNull(UpdateChecker.getLatestVersion());
    }

    /// A direct legacy apply argument exits without altering a target file.
    @Test
    public void legacyApplyToCannotOverwriteDshCraftTarget() throws Exception {
        Path target = temporary.resolve("DSHCraft.exe");
        Files.writeString(target, "keep this file");
        assertTrue(UpdateHandler.processArguments(new String[]{"--apply-to", target.toString()}));
        assertEquals("keep this file", Files.readString(target));
        assertTrue(UpdateHandler.processArguments(new String[]{"--apply-to"}));
    }
}
