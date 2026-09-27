/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.ui.main;

import com.jfoenix.controls.JFXButton;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.StringProperty;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.EntryPoint;
import org.jackhuang.hmcl.agent.DshCraftSignedUpdate;
import org.jackhuang.hmcl.agent.DshCraftUpdateHandoff;
import org.jackhuang.hmcl.setting.SettingsManager;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane.MessageType;
import org.jackhuang.hmcl.upgrade.*;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jackhuang.hmcl.util.i18n.SupportedLocale;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.io.IOUtils;
import org.jackhuang.hmcl.util.FileSaver;
import org.jackhuang.hmcl.util.io.JarUtils;
import org.jackhuang.hmcl.util.versioning.VersionNumber;
import org.tukaani.xz.XZInputStream;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.jackhuang.hmcl.setting.SettingsManager.settings;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// HMCL-style launcher settings, including DSHCraft's product-specific signed update action.
public final class SettingsPage extends ScrollPane {
    /// Keeps the update row synchronized with legacy HMCL update state where applicable.
    @SuppressWarnings("FieldCanBeLocal")
    private final InvalidationListener updateListener;

    /// Creates the existing HMCL settings rows without a second desktop interface.
    public SettingsPage() {
        this.setFitToWidth(true);

        VBox rootPane = new VBox(10);
        rootPane.setPadding(new Insets(10));
        this.setContent(rootPane);
        FXUtils.smoothScrolling(this);

        {
            ComponentList updatePaneList = new ComponentList();
            {
                ObjectProperty<UpdateChannel> updateChannel;
                {

                    JFXButton updateButton = FXUtils.newToggleButton4(SVG.UPDATE, 20);
                    updateButton.setOnAction(e -> onUpdate());
                    updateButton.setPadding(Insets.EMPTY);
                    FXUtils.installFastTooltip(updateButton, i18n("update.tooltip"));

                    var updatePane = new LineSelectButton<UpdateChannel>() {

                        {
                            getStyleClass().add("update-pane");
                            setNode(IDX_TRAILING, updateButton);
                        }

                        @Override
                        protected int getTrailingTextIndex() {
                            return LineComponent.IDX_TRAILING + 1;
                        }
                    };
                    updateChannel = updatePane.valueProperty();
                    updatePane.setTitle(i18n("update"));
                    boolean dshCraft = "DSHCraft".equals(Metadata.NAME);
                    updatePane.setValue(dshCraft ? UpdateChannel.STABLE : UpdateChannel.getChannel());

                    updatePane.setNullSafeConverter(channel -> i18n("update.channel." + channel.channelName));
                    updatePane.setItems(dshCraft ? List.of(UpdateChannel.STABLE)
                            : List.of(UpdateChannel.STABLE, UpdateChannel.DEVELOPMENT));
                    updatePane.setDescriptionConverter(channel -> i18n("update.note." + channel.channelName));

                    final StringProperty lblUpdateSubProperty = updatePane.subtitleProperty();

                    {
                        updateListener = any -> {
                            if (dshCraft) {
                                updateButton.setVisible(true);
                                updateButton.setManaged(true);
                                lblUpdateSubProperty.set(i18n("agent.update.signed.check"));
                                return;
                            }
                            boolean outdated = UpdateChecker.isOutdated();

                            updateButton.setVisible(outdated);
                            updateButton.setManaged(outdated);
                            updatePane.pseudoClassStateChanged(PseudoClass.getPseudoClass("active"), outdated);

                            if (outdated) {
                                lblUpdateSubProperty.set(i18n("update.newest_version", UpdateChecker.getLatestVersion().version()));
                            } else if (UpdateChecker.isCheckingUpdate()) {
                                lblUpdateSubProperty.set(i18n("update.checking"));
                            } else if (UpdateChecker.errorProperty().get() != null) {
                                Throwable t = UpdateChecker.errorProperty().get();
                                if (t instanceof SelfVerificationException) {
                                    lblUpdateSubProperty.set(i18n("update.unverified"));
                                } else {
                                    String msg = t.getClass().getSimpleName() + ": " + t.getLocalizedMessage();
                                    lblUpdateSubProperty.set(i18n("update.check_failed", msg));
                                }
                            } else {
                                lblUpdateSubProperty.set(i18n("update.latest"));
                            }
                        };
                        UpdateChecker.latestVersionProperty().addListener(new WeakInvalidationListener(updateListener));
                        UpdateChecker.outdatedProperty().addListener(new WeakInvalidationListener(updateListener));
                        UpdateChecker.checkingUpdateProperty().addListener(new WeakInvalidationListener(updateListener));
                        UpdateChecker.errorProperty().addListener(new WeakInvalidationListener(updateListener));
                        updateListener.invalidated(null);
                    }

                    updatePaneList.getContent().add(updatePane);
                }

                if (!"DSHCraft".equals(Metadata.NAME)) {
                    LineToggleButton previewPane = new LineToggleButton();
                    previewPane.setTitle(i18n("update.preview"));
                    previewPane.setSubtitle(i18n("update.preview.subtitle"));
                    previewPane.selectedProperty().bindBidirectional(settings().acceptPreviewUpdateProperty());

                    InvalidationListener checkUpdateListener = e -> {
                        UpdateChecker.requestCheckUpdate(updateChannel.get(), previewPane.isSelected());
                    };
                    updateChannel.addListener(checkUpdateListener);
                    previewPane.selectedProperty().addListener(checkUpdateListener);

                    updatePaneList.getContent().add(previewPane);
                }

                if (!"DSHCraft".equals(Metadata.NAME)) {
                    LineToggleButton disableAutoShowUpdateDialogPane = new LineToggleButton();
                    disableAutoShowUpdateDialogPane.setTitle(i18n("update.disable_auto_show_update_dialog"));
                    disableAutoShowUpdateDialogPane.setSubtitle(i18n("update.disable_auto_show_update_dialog.subtitle"));
                    disableAutoShowUpdateDialogPane.selectedProperty().bindBidirectional(settings().disableAutoShowUpdateDialogProperty());
                    updatePaneList.getContent().add(disableAutoShowUpdateDialogPane);
                }

                rootPane.getChildren().addAll(ComponentList.createComponentListTitle(i18n("update")), updatePaneList);
            }

            {
                ComponentList languagePaneList = new ComponentList();

                {
                    var chooseLanguagePane = new LineSelectButton<SupportedLocale>();
                    chooseLanguagePane.setTitle(i18n("settings.launcher.language"));
                    chooseLanguagePane.setSubtitle(i18n("settings.take_effect_after_restart"));

                    SupportedLocale currentLocale = I18n.getLocale();
                    chooseLanguagePane.setNullSafeConverter(locale -> {
                        if (locale.isDefault())
                            return locale.getDisplayName(currentLocale);
                        else if (locale.isSameLanguage(currentLocale))
                            return locale.getDisplayName(locale);
                        else
                            return locale.getDisplayName(currentLocale) + " - " + locale.getDisplayName(locale);
                    });
                    chooseLanguagePane.setItems(SupportedLocale.getSupportedLocales());
                    chooseLanguagePane.valueProperty().bindBidirectional(settings().languageProperty());

                    languagePaneList.getContent().add(chooseLanguagePane);

                }

                rootPane.getChildren().addAll(ComponentList.createComponentListTitle(i18n("settings.launcher.language")), languagePaneList);
            }

            {
                ComponentList miscPaneList = new ComponentList();

                {
                    LineToggleButton disableAprilFools = new LineToggleButton();
                    disableAprilFools.setTitle(i18n("settings.launcher.disable_april_fools"));
                    disableAprilFools.setSubtitle(i18n("settings.take_effect_after_restart"));
                    disableAprilFools.selectedProperty().bindBidirectional(settings().disableAprilFoolsProperty());
                    miscPaneList.getContent().add(disableAprilFools);
                }

                {
                    BorderPane debugPane = new BorderPane();

                    Label left = new Label(i18n("settings.launcher.debug"));
                    BorderPane.setAlignment(left, Pos.CENTER_LEFT);
                    debugPane.setLeft(left);

                    JFXButton openLogFolderButton = new JFXButton(i18n("settings.launcher.launcher_log.reveal"));
                    openLogFolderButton.setOnAction(e -> openLogFolder());
                    openLogFolderButton.getStyleClass().add("jfx-button-border");
                    if (LOG.getLogFile() == null)
                        openLogFolderButton.setDisable(true);

                    SpinnerPane exportLogPane = new SpinnerPane();

                    JFXButton logButton = FXUtils.newBorderButton(i18n("settings.launcher.launcher_log.export"));
                    exportLogPane.setContent(logButton);
                    logButton.setOnAction(e -> {
                        exportLogPane.showSpinner();
                        onExportLogs().whenCompleteAsync((result, exception) -> {
                            exportLogPane.hideSpinner();
                            if (exception == null) {
                                Controllers.dialog(i18n("settings.launcher.launcher_log.export.success", result));
                                FXUtils.showFileInExplorer(result);
                            } else {
                                LOG.warning("Failed to export logs", exception);
                                Controllers.dialog(
                                        i18n("settings.launcher.launcher_log.export.failed") + "\n" + StringUtils.getStackTrace(exception),
                                        null,
                                        MessageType.ERROR
                                );
                            }
                        }, Schedulers.javafx());
                    });

                    HBox buttonBox = new HBox();
                    buttonBox.setSpacing(10);
                    buttonBox.getChildren().addAll(openLogFolderButton, exportLogPane);
                    BorderPane.setAlignment(buttonBox, Pos.CENTER_RIGHT);
                    debugPane.setRight(buttonBox);

                    miscPaneList.getContent().add(debugPane);
                }

                rootPane.getChildren().addAll(ComponentList.createComponentListTitle(i18n("settings.launcher.misc")), miscPaneList);
            }
        }
    }

    private void openLogFolder() {
        FXUtils.openFolder(LOG.getLogFile().getParent());
    }

    /// Routes the existing settings-row button to this product's own signed updater.
    private void onUpdate() {
        if ("DSHCraft".equals(Metadata.NAME)) {
            checkDshCraftUpdate();
            return;
        }
        RemoteVersion target = UpdateChecker.getLatestVersion();
        if (target == null) {
            return;
        }
        UpdateHandler.updateFrom(target);
    }

    /// Checks the fork-specific signed feed away from the JavaFX thread and presents its version.
    private void checkDshCraftUpdate() {
        CompletableFuture.supplyAsync(() -> {
            try {
                return DshCraftSignedUpdate.fetchLatestSignedFeed();
            } catch (IOException | InterruptedException error) {
                if (error instanceof InterruptedException) Thread.currentThread().interrupt();
                throw new CompletionException(error);
            }
        }).whenComplete((feed, failure) -> Platform.runLater(() -> {
            if (failure != null) {
                showDshCraftUpdateError(failure);
                return;
            }
            if (feed == null) {
                showDshCraftUpdateError(new IOException("Signed DSHCraft feed is unavailable."));
                return;
            }
            if (VersionNumber.compare(Metadata.VERSION, feed.manifest().version()) >= 0) {
                Controllers.dialog(i18n("agent.update.signed.current"), i18n("update"), MessageType.INFO);
                return;
            }
            Controllers.confirm(i18n("agent.update.signed.confirm", feed.manifest().version()),
                    i18n("update"), () -> beginDshCraftUpdate(feed), () -> { });
        }));
    }

    /// Downloads a verified release and starts the trusted old-launcher helper before exiting.
    private void beginDshCraftUpdate(DshCraftSignedUpdate.SignedFeed feed) {
        SettingsManager.savePendingChanges();
        CompletableFuture.runAsync(() -> {
            @Nullable Path downloadDirectory = null;
            try {
                @Nullable Path current = JarUtils.thisJarPath();
                if (current == null) throw new IOException("Current DSHCraft executable cannot be located.");
                String currentName = current.getFileName().toString().toLowerCase(Locale.ROOT);
                String suffix = currentName.endsWith(".exe") ? ".exe"
                        : currentName.endsWith(".jar") ? ".jar" : "";
                if (suffix.isEmpty() || !feed.manifest().artifactUrl().getPath()
                        .toLowerCase(Locale.ROOT).endsWith(suffix)) {
                    throw new IOException("Signed DSHCraft artifact format does not match the current launcher.");
                }
                downloadDirectory = Files.createTempDirectory("dshcraft-update-download-");
                Path candidate = downloadDirectory.resolve("DSHCraft-update" + suffix);
                DshCraftSignedUpdate.downloadVerifiedArtifact(feed.manifest(), candidate);
                FileSaver.waitForAllSaves();
                DshCraftUpdateHandoff.stageAndStartHelper(current, candidate, feed);
                Platform.runLater(() -> EntryPoint.exit(0));
            } catch (IOException | InterruptedException error) {
                if (error instanceof InterruptedException) Thread.currentThread().interrupt();
                if (downloadDirectory != null) {
                    try {
                        try (var entries = Files.list(downloadDirectory)) {
                            for (Path entry : entries.toList()) Files.deleteIfExists(entry);
                        }
                        Files.deleteIfExists(downloadDirectory);
                    } catch (IOException cleanupError) {
                        error.addSuppressed(cleanupError);
                    }
                }
                Platform.runLater(() -> showDshCraftUpdateError(error));
            }
        });
    }

    /// Shows a bounded failure message without sending the user into HMCL's old installer.
    private void showDshCraftUpdateError(Throwable error) {
        Throwable cause = error instanceof CompletionException && error.getCause() != null
                ? error.getCause() : error;
        String message = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
        if ("DSHCraft update public key is not configured.".equals(message)) {
            message = i18n("agent.update.signed.unconfigured");
        }
        Controllers.dialog(i18n("agent.update.signed.failed", message), i18n("update"), MessageType.ERROR);
    }

    private static String getEntryName(Set<String> entryNames, String name) {
        if (entryNames.add(name)) {
            return name;
        }

        for (long i = 1; ; i++) {
            String newName = name + "." + i;
            if (entryNames.add(newName)) {
                return newName;
            }
        }
    }

    /// This method guarantees to close both `input` and the current zip entry.
    ///
    /// If no exception occurs, this method returns `true`;
    /// If an exception occurs while reading from `input`, this method returns `false`;
    /// If an exception occurs while writing to `output`, this method will throw it as is.
    private static boolean exportLogFile(ZipOutputStream output,
                                         Path file, // For logging
                                         String entryName,
                                         InputStream input,
                                         byte[] buffer) throws IOException {
        //noinspection TryFinallyCanBeTryWithResources
        try {
            output.putNextEntry(new ZipEntry(entryName));
            int read;
            while (true) {
                try {
                    read = input.read(buffer);
                    if (read <= 0)
                        return true;
                } catch (Throwable ex) {
                    LOG.warning("Failed to decompress log file " + file, ex);
                    return false;
                }

                output.write(buffer, 0, read);
            }
        } finally {
            try {
                input.close();
            } catch (Throwable ex) {
                LOG.warning("Failed to close log file " + file, ex);
            }
            output.closeEntry();
        }
    }

    private CompletableFuture<Path> onExportLogs() {
        return CompletableFuture.supplyAsync(Lang.wrap(() -> {
            String nameBase = "hmcl-exported-logs-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss"));
            List<Path> recentLogFiles = LOG.findRecentLogFiles(5);

            Path outputFile;
            if (recentLogFiles.isEmpty()) {
                outputFile = Metadata.CURRENT_DIRECTORY.resolve(nameBase + ".log");

                LOG.info("Exporting latest logs to " + outputFile);
                try (OutputStream output = Files.newOutputStream(outputFile)) {
                    LOG.exportLogs(output);
                }
            } else {
                outputFile = Metadata.CURRENT_DIRECTORY.resolve(nameBase + ".zip");

                LOG.info("Exporting latest logs to " + outputFile);

                byte[] buffer = new byte[IOUtils.DEFAULT_BUFFER_SIZE];
                try (var os = Files.newOutputStream(outputFile);
                     var zos = new ZipOutputStream(os)) {

                    Set<String> entryNames = new HashSet<>();

                    for (Path path : recentLogFiles) {
                        String fileName = FileUtils.getName(path);
                        String extension = StringUtils.substringAfterLast(fileName, '.');

                        if ("gz".equals(extension) || "xz".equals(extension)) {
                            // If an exception occurs while decompressing the input file, we should
                            // ensure the input file and the current zip entry are closed,
                            // then copy the compressed file content as-is into a new entry in the zip file.

                            InputStream input = null;
                            try {
                                input = Files.newInputStream(path);
                                input = "gz".equals(extension)
                                        ? new GZIPInputStream(input)
                                        : new XZInputStream(input);
                            } catch (Throwable ex) {
                                LOG.warning("Failed to open log file " + path, ex);
                                IOUtils.closeQuietly(input, ex);
                                input = null;
                            }

                            String entryName = getEntryName(entryNames, StringUtils.substringBeforeLast(fileName, "."));
                            if (input != null && exportLogFile(zos, path, entryName, input, buffer))
                                continue;
                        }

                        // Copy the log file content as-is into a new entry in the zip file.
                        // If an exception occurs while decompressing the input file, we should
                        // ensure the input file and the current zip entry are closed.

                        InputStream input;
                        try {
                            input = Files.newInputStream(path);
                        } catch (Throwable ex) {
                            LOG.warning("Failed to open log file " + path, ex);
                            continue;
                        }

                        exportLogFile(zos, path, getEntryName(entryNames, fileName), input, buffer);
                    }

                    zos.putNextEntry(new ZipEntry(getEntryName(entryNames, "hmcl-latest.log")));
                    LOG.exportLogs(zos);
                    zos.closeEntry();
                }
            }

            return outputFile;
        }), Schedulers.io());
    }
}
