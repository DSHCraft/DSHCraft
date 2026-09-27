/*
 * DSHCraft direct HMCL fork
 * Copyright (C) 2026 DSHCraft contributors
 *
 * This file is licensed under the GNU General Public License, version 3 or later.
 */
package org.jackhuang.hmcl.ui.agent;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXPopup;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.scene.control.ListCell;
import org.jackhuang.hmcl.agent.AgentInstance;
import org.jackhuang.hmcl.agent.AgentNetworkService;
import org.jackhuang.hmcl.agent.AgentRepository;
import org.jackhuang.hmcl.agent.DshModService;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.SearchableListPage;
import org.jackhuang.hmcl.ui.TwoLineActionListCell;
import org.jackhuang.hmcl.ui.construct.IconedMenuItem;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jackhuang.hmcl.ui.construct.PopupMenu;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Predicate;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// HMCL searchable version list backed by the real DSH npm Registry catalog.
@NotNullByDefault
public final class AgentCoreDownloadPage extends SearchableListPage<AgentCoreDownloadPage.VersionItem> {
    /// Creates the list and begins its first asynchronous npm catalog refresh.
    public AgentCoreDownloadPage() {
        super(FXCollections.observableArrayList());
        setRowRefreshSignal(AgentRepository.get().selectedInstanceProperty());
        setOnFailedAction(event -> refreshList());
        refreshList();
    }

    /// Filters the published versions with HMCL's existing search toolbar.
    @Override
    protected Predicate<VersionItem> createSearchPredicate(@Nullable String searchText) {
        if (searchText == null || searchText.isBlank()) return item -> true;
        String needle = searchText.trim().toLowerCase(Locale.ROOT);
        return item -> item.version.toLowerCase(Locale.ROOT).contains(needle)
                || item.tag.get().toLowerCase(Locale.ROOT).contains(needle);
    }

    /// Fetches real npm releases without blocking the JavaFX thread.
    @Override
    protected void refreshList() {
        if (isLoading()) return;
        setLoading(true);
        failedReasonProperty().set(null);
        CompletableFuture.supplyAsync(() -> {
            try {
                return AgentNetworkService.fetchDshCatalog(AgentRepository.get().getCatalogRegistry());
            } catch (Exception error) {
                if (error instanceof InterruptedException) Thread.currentThread().interrupt();
                throw new CompletionException(error);
            }
        }).whenComplete((catalog, error) -> Platform.runLater(() -> {
            setLoading(false);
            if (error != null) {
                Throwable reason = error.getCause() == null ? error : error.getCause();
                setFailedReason(i18n("agent.core.catalog.failed") + ": " + reason.getMessage()
                        + "\n" + i18n("agent.core.catalog.retry"));
                return;
            }
            List<VersionItem> versions = new ArrayList<>();
            for (AgentNetworkService.CoreRelease release : catalog.releases()) {
                String channel = release.version().equals(catalog.latest()) ? "latest"
                        : release.version().equals(catalog.next()) ? "next"
                        : release.version().equals(catalog.alpha()) ? "alpha" : "npm";
                String published = release.publishedAt() == null ? "" : release.publishedAt();
                String summary = published.length() >= 10 ? published.substring(0, 10) : published;
                if (release.deprecated() != null && !release.deprecated().isBlank()) {
                    summary += "  /  " + release.deprecated();
                }
                versions.add(new VersionItem(release.version(), summary,
                        isInstalled(release.version()) ? i18n("agent.core.installed") : channel));
            }
            sourceItems().setAll(versions);
        }));
    }

    /// Supplies HMCL's refresh toolbar before its shared search action.
    @Override
    protected List<ToolbarAction> toolbarActions() {
        return List.of(new ToolbarAction(i18n("button.refresh"), SVG.REFRESH, this::refreshList));
    }

    /// Uses the HMCL two-line action cell for published Core versions.
    @Override
    protected ListCell<VersionItem> createListCell() {
        return new VersionCell();
    }

    /// Checks the launcher-managed runtime cache without consulting a global DSH command.
    private static boolean isInstalled(String version) {
        try {
            DshModService.installedCoreCli(DshModService.runtimeRoot(), version);
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    /// Binds one npm version to the selected DSH instance and starts its isolated Core install.
    private static void install(VersionItem item) {
        AgentInstance selected = AgentRepository.get().getSelectedInstance();
        if (selected == null) {
            Controllers.dialog(i18n("agent.mod.instance.required"), i18n("message.error"),
                    MessageDialogPane.MessageType.WARNING);
            return;
        }
        AgentPages.installCore(selected, item.version);
    }

    /// Immutable release identity with JavaFX display properties for HMCL cells.
    static final class VersionItem {
        /// Published npm version.
        private final String version;
        /// Row title.
        private final StringProperty title;
        /// Row subtitle.
        private final StringProperty summary;
        /// Dist-tag or installed marker.
        private final StringProperty tag;

        /// Creates one display row from npm metadata.
        private VersionItem(String version, String summary, String tag) {
            this.version = version;
            this.title = new SimpleStringProperty(version);
            this.summary = new SimpleStringProperty(summary);
            this.tag = new SimpleStringProperty(tag);
        }
    }

    /// Connects DSH version actions to HMCL's physical two-line list row.
    private static final class VersionCell extends TwoLineActionListCell<VersionItem> {
        /// Install action displayed on the right side of each row.
        private final JFXButton installButton = createActionButton(SVG.DOWNLOAD,
                i18n("agent.core.install"), AgentCoreDownloadPage::install);

        /// Selects a published version without installing it.
        @Override
        protected void selectItem(VersionItem item) {
            AgentInstance selected = AgentRepository.get().getSelectedInstance();
            if (selected != null) selected.coreVersionProperty().set(item.version);
        }

        /// Installs a version when its primary row is opened.
        @Override
        protected void openItem(VersionItem item) {
            install(item);
        }

        /// Offers the same install action in HMCL's row context menu.
        @Override
        protected JFXPopup createPopup(VersionItem item) {
            PopupMenu menu = new PopupMenu();
            JFXPopup popup = new JFXPopup(menu);
            menu.getContent().add(new IconedMenuItem(SVG.DOWNLOAD, i18n("agent.core.install"),
                    () -> install(item), popup));
            return popup;
        }

        /// Provides the release version as the row title.
        @Override
        protected StringProperty titleProperty(VersionItem item) {
            return item.title;
        }

        /// Provides the npm publication date or deprecation note.
        @Override
        protected StringProperty subtitleProperty(VersionItem item) {
            return item.summary;
        }

        /// Provides the npm dist-tag or installed marker.
        @Override
        protected StringProperty tagProperty(VersionItem item) {
            return item.tag;
        }

        /// Marks the selected DSH instance's requested Core version.
        @Override
        protected void bindSelection(VersionItem item) {
            AgentInstance selected = AgentRepository.get().getSelectedInstance();
            selectionButton.setSelected(selected != null && item.version.equals(selected.coreVersionProperty().get()));
        }

        /// Reuses HMCL's 32-pixel image slot for the DSH version row.
        @Override
        protected void bindImage(VersionItem item) {
            imageView.setImage(FXUtils.newBuiltinImage("/assets/img/dshcraft-mark.png"));
        }

        /// Adds the install button next to HMCL's normal row controls.
        @Override
        protected void populateRightActions(VersionItem item) {
            rightActions.getChildren().add(installButton);
        }
    }
}
