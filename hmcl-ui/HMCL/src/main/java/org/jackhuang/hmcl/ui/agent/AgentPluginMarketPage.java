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
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.SearchableListPage;
import org.jackhuang.hmcl.ui.TwoLineActionListCell;
import org.jackhuang.hmcl.ui.construct.IconedMenuItem;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jackhuang.hmcl.ui.construct.PopupMenu;
import org.jackhuang.hmcl.ui.construct.PromptDialogPane;
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

/// Searches npm for DSH plugin packages and installs a chosen version into one isolated instance.
@NotNullByDefault
public final class AgentPluginMarketPage extends SearchableListPage<AgentPluginMarketPage.MarketItem> {
    /// Current remote query.
    private String query = "dsh";
    /// Current npm search offset.
    private int offset;
    /// Total raw npm results for paging even when a page contains no eligible plugins.
    private int total;
    /// Prevents a stale request from replacing a newer page.
    private int requestId;

    /// Opens a live market list with the default DSH query.
    public AgentPluginMarketPage() {
        super(FXCollections.observableArrayList());
        setOnFailedAction(event -> refreshList());
        refreshList();
    }

    /// Applies HMCL's local filter to the current remote search page.
    @Override
    protected Predicate<MarketItem> createSearchPredicate(@Nullable String searchText) {
        if (searchText == null || searchText.isBlank()) return item -> true;
        String needle = searchText.toLowerCase(Locale.ROOT);
        return item -> item.plugin.name().toLowerCase(Locale.ROOT).contains(needle)
                || item.plugin.description().toLowerCase(Locale.ROOT).contains(needle);
    }

    /// Fetches a page of npm results asynchronously.
    @Override
    protected void refreshList() {
        int currentRequest = ++requestId;
        String currentQuery = query;
        int currentOffset = offset;
        String registry = AgentRepository.get().getCatalogRegistry();
        setLoading(true);
        failedReasonProperty().set(null);
        CompletableFuture.supplyAsync(() -> {
            try {
                return AgentNetworkService.searchNpmPlugins(currentQuery, registry, currentOffset);
            } catch (IOException | InterruptedException error) {
                if (error instanceof InterruptedException) Thread.currentThread().interrupt();
                throw new CompletionException(error);
            }
        }).whenComplete((results, error) -> Platform.runLater(() -> {
            if (currentRequest != requestId) return;
            setLoading(false);
            if (error != null) {
                Throwable reason = error.getCause() == null ? error : error.getCause();
                setFailedReason(i18n("agent.plugin.market.failed") + ": " + reason.getMessage());
                return;
            }
            List<MarketItem> items = new ArrayList<>();
            total = results.total();
            for (AgentNetworkService.NpmPlugin plugin : results.plugins()) items.add(new MarketItem(plugin));
            sourceItems().setAll(items);
        }));
    }

    /// Offers remote query, paging, refresh, and installed-plugin navigation.
    @Override
    protected List<ToolbarAction> toolbarActions() {
        return List.of(
                new ToolbarAction(i18n("agent.plugin.market.source"), SVG.PUBLIC, this::chooseSource),
                new ToolbarAction(i18n("agent.plugin.market.search"), SVG.SEARCH, this::requestQuery),
                new ToolbarAction(null, SVG.REFRESH, this::refreshList, i18n("button.refresh")),
                new ToolbarAction(null, SVG.ARROW_BACK, () -> {
                    if (offset >= 40) { offset -= 40; refreshList(); }
                }, i18n("agent.plugin.market.previous")),
                new ToolbarAction(null, SVG.ARROW_FORWARD, () -> {
                    if (offset + 40 < total && offset < 1000) { offset += 40; refreshList(); }
                }, i18n("agent.plugin.market.next")),
                new ToolbarAction(null, SVG.EXTENSION,
                        () -> Controllers.navigate(AgentPages.extensions()),
                        i18n("agent.plugin.market.installed")));
    }

    /// Uses HMCL's normal two-line version row.
    @Override
    protected ListCell<MarketItem> createListCell() {
        return new MarketCell();
    }

    /// Distinguishes an empty remote market page from a loading or failed request.
    @Override
    protected String emptyListMessage() {
        return i18n("agent.plugin.market.empty");
    }

    /// Requests a new remote query through the native HMCL dialog.
    private void requestQuery() {
        PromptDialogPane.Builder.StringQuestion question = new PromptDialogPane.Builder.StringQuestion(
                i18n("agent.plugin.market.query"), query);
        Controllers.prompt(new PromptDialogPane.Builder(i18n("agent.plugin.market"), (questions, handler) -> handler.resolve())
                .addQuestion(question)).thenAccept(ignored -> {
            String value = question.getValue();
            if (value == null || value.isBlank()) return;
            query = value.trim();
            offset = 0;
            refreshList();
        });
    }

    /// Chooses one built-in or custom registry for both market metadata and the native installation.
    private void chooseSource() {
        String[] ids = {"official", "npmmirror", "huawei", "tencent", "custom"};
        String[] labels = {"npm (Official)", "npmmirror", "Huawei Cloud npm", "Tencent npm",
                i18n("agent.download.source.custom")};
        AgentRepository repository = AgentRepository.get();
        String current = repository.getCatalogRegistry();
        int selected = current.equals("https://registry.npmmirror.com") ? 1
                : current.equals("https://repo.huaweicloud.com/repository/npm") ? 2
                : current.equals("https://mirrors.cloud.tencent.com/npm") ? 3
                : current.equals(AgentNetworkService.OFFICIAL_REGISTRY) ? 0 : 4;
        PromptDialogPane.Builder.CandidatesQuestion choice = new PromptDialogPane.Builder.CandidatesQuestion(
                i18n("agent.download.source.choose"), labels).setSelectedIndex(selected);
        Controllers.prompt(new PromptDialogPane.Builder(i18n("agent.download.source.title"),
                (questions, handler) -> handler.resolve()).addQuestion(choice)).thenAccept(ignored -> {
            Integer index = choice.getValue();
            if (index == null || index < 0 || index >= ids.length) return;
            String source = ids[index];
            if ("custom".equals(source)) {
                Controllers.prompt(i18n("agent.download.source.custom"), (url, handler) -> {
                    try {
                        AgentNetworkService.validateRegistry(source, url);
                        handler.resolve();
                    } catch (IOException error) {
                        handler.reject(error.getMessage());
                    }
                }, selected == 4 ? current : "").thenAccept(url -> saveSource(source, url));
            } else {
                saveSource(source, "");
            }
        });
    }

    /// Saves a coherent registry choice and refreshes the market page.
    private void saveSource(String source, String custom) {
        AgentRepository repository = AgentRepository.get();
        try {
            repository.setDownloadRegistry(true, source, custom);
            repository.setDownloadRegistry(false, source, custom);
            offset = 0;
            refreshList();
        } catch (IOException error) {
            Controllers.dialog(error.getMessage(), i18n("message.error"), MessageDialogPane.MessageType.WARNING);
        }
    }

    /// Loads package versions before asking which version to install into the captured instance.
    private static void chooseVersion(MarketItem item) {
        AgentInstance instance = AgentRepository.get().getSelectedInstance();
        if (instance == null) {
            Controllers.dialog(i18n("agent.mod.instance.required"), i18n("message.error"),
                    MessageDialogPane.MessageType.WARNING);
            return;
        }
        String registry = AgentRepository.get().getCatalogRegistry();
        Controllers.showToast(i18n("agent.plugin.market.versions.loading"));
        CompletableFuture.supplyAsync(() -> {
            try {
                return AgentNetworkService.fetchPluginVersions(item.plugin.name(), registry);
            } catch (IOException | InterruptedException error) {
                if (error instanceof InterruptedException) Thread.currentThread().interrupt();
                throw new CompletionException(error);
            }
        }).whenComplete((versions, error) -> Platform.runLater(() -> {
            if (error != null) {
                Throwable reason = error.getCause() == null ? error : error.getCause();
                Controllers.dialog(i18n("agent.plugin.market.versions.failed") + ": " + reason.getMessage(),
                        i18n("message.error"), MessageDialogPane.MessageType.ERROR);
                return;
            }
            List<String> choices = new ArrayList<>();
            String coreVersion = instance.coreVersionProperty().get();
            if (item.plugin.name().startsWith("@deepseek-ai/dsh-") && versions.contains(coreVersion))
                choices.add(coreVersion);
            for (String version : versions) {
                if (!choices.contains(version) && choices.size() < 30) choices.add(version);
            }
            if (choices.isEmpty()) {
                Controllers.dialog(i18n("agent.plugin.market.versions.empty"), i18n("message.error"),
                        MessageDialogPane.MessageType.WARNING);
                return;
            }
            PromptDialogPane.Builder.CandidatesQuestion choice = new PromptDialogPane.Builder.CandidatesQuestion(
                    i18n("agent.plugin.market.version"), choices.toArray(String[]::new));
            Controllers.prompt(new PromptDialogPane.Builder(item.plugin.name(), (questions, handler) -> handler.resolve())
                    .addQuestion(choice)).thenAccept(ignored -> {
                Integer index = choice.getValue();
                if (index != null && index >= 0 && index < choices.size())
                    AgentPages.installMarketPlugin(instance, item.plugin, choices.get(index));
            });
        }));
    }

    /// One package row and its display properties.
    static final class MarketItem {
        /// npm package metadata.
        private final AgentNetworkService.NpmPlugin plugin;
        /// Row title.
        private final StringProperty title;
        /// Row summary.
        private final StringProperty summary;
        /// Latest version marker.
        private final StringProperty tag;

        /// Creates a display row from one search result.
        private MarketItem(AgentNetworkService.NpmPlugin plugin) {
            this.plugin = plugin;
            title = new SimpleStringProperty(plugin.name());
            summary = new SimpleStringProperty(plugin.description());
            tag = new SimpleStringProperty(plugin.version());
        }
    }

    /// HMCL list cell that opens package versions and install action.
    private static final class MarketCell extends TwoLineActionListCell<MarketItem> {
        /// Direct install action.
        private final JFXButton installButton = createActionButton(SVG.DOWNLOAD,
                i18n("agent.mod.install"), AgentPluginMarketPage::chooseVersion);

        /// Market rows open details but do not select a persistent local item.
        private MarketCell() {
            selectionButton.setVisible(false);
            selectionButton.setManaged(false);
        }

        /// The market has no persistent row selection.
        @Override protected void selectItem(MarketItem item) { chooseVersion(item); }
        /// Opens the package version picker.
        @Override protected void openItem(MarketItem item) { chooseVersion(item); }
        /// Supplies a native HMCL action menu.
        @Override protected JFXPopup createPopup(MarketItem item) {
            PopupMenu menu = new PopupMenu();
            JFXPopup popup = new JFXPopup(menu);
            menu.getContent().add(new IconedMenuItem(SVG.DOWNLOAD, i18n("agent.mod.install"),
                    () -> chooseVersion(item), popup));
            return popup;
        }
        /// Exposes the npm package name.
        @Override protected StringProperty titleProperty(MarketItem item) { return item.title; }
        /// Exposes the npm package description.
        @Override protected StringProperty subtitleProperty(MarketItem item) { return item.summary; }
        /// Exposes the npm search result's latest version.
        @Override protected StringProperty tagProperty(MarketItem item) { return item.tag; }
        /// The market is browsable, not a selected instance list.
        @Override protected void bindSelection(MarketItem item) { selectionButton.setSelected(false); }
        /// Uses the launcher mark for npm plugin packages.
        @Override protected void bindImage(MarketItem item) {
            imageView.setImage(FXUtils.newBuiltinImage("/assets/img/dshcraft-mark.png"));
        }
        /// Adds the install control to the row.
        @Override protected void populateRightActions(MarketItem item) { rightActions.getChildren().add(installButton); }
    }
}
