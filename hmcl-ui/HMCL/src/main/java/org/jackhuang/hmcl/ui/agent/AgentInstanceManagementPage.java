/*
 * DShCraft direct HMCL fork
 * Copyright (C) 2026 DShCraft contributors
 * This file is licensed under the GNU General Public License, version 3 or later.
 */
package org.jackhuang.hmcl.ui.agent;

import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.agent.AgentExtension;
import org.jackhuang.hmcl.agent.AgentInstance;
import org.jackhuang.hmcl.agent.AgentRepository;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.ui.construct.AdvancedListBox;
import org.jackhuang.hmcl.ui.construct.PageAware;
import org.jackhuang.hmcl.ui.construct.TabHeader;
import org.jackhuang.hmcl.ui.decorator.DecoratorAnimatedPage;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// Instance management follows HMCL's native sidebar/transition layout and stable instance identity.
@NotNullByDefault
public final class AgentInstanceManagementPage extends DecoratorAnimatedPage implements DecoratorPage, PageAware {
    /// Stable identity survives repository reloads that replace model objects.
    private String instanceId;
    /// Repository providing the current model snapshot.
    private final AgentRepository repository = AgentRepository.get();
    /// Decorator title follows the instance name.
    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>(State.fromTitle(i18n("agent.instances")));
    /// Current object backing the visible editor.
    private @Nullable AgentInstance current;
    /// Remembers the selected category across model replacement.
    private String sectionId = "settings";
    /// Current native category controller, recreated when the selected instance changes.
    private @Nullable TabHeader categories;
    /// Observes snapshot replacement only while the page is visible.
    private final ChangeListener<AgentInstance> contextListener = (observable, previous, selected) -> rebind();

    /// Creates an independently navigable page for an existing instance.
    public AgentInstanceManagementPage(String instanceId) {
        this.instanceId = instanceId;
        rebind();
    }

    /// Resolves current repository state before binding controls or installing callbacks.
    private void rebind() {
        @Nullable AgentInstance instance = repository.findInstance(instanceId);
        if (instance == current) return;
        current = instance;
        state.unbind();
        if (instance == null) {
            setLeft();
            setCenter(new AgentEditorPage(i18n("agent.instance.empty")));
            return;
        }
        state.bind(Bindings.createObjectBinding(
                () -> State.fromTitle(i18n("agent.workspace.manage", instance.getName())), instance.nameProperty()));
        AgentPages.populateInstanceManagement(instance, this);
    }

    /// Installs native categories and footer actions without a second page header or horizontal tabs.
    void showSections(AgentInstance instance, AgentEditorPage settings, AgentEditorPage versions, AgentEditorPage advanced) {
        TransitionPane content = new TransitionPane();
        TabHeader.Tab<AgentEditorPage> settingsTab = new TabHeader.Tab<>("settings");
        settingsTab.setNode(settings);
        TabHeader.Tab<AgentEditorPage> versionsTab = new TabHeader.Tab<>("versions");
        versionsTab.setNode(versions);
        TabHeader.Tab<AgentListPage<AgentExtension>> modsTab = new TabHeader.Tab<>("mods");
        modsTab.setNodeSupplier(() -> AgentPages.newInstanceExtensionPage(instance, "Plugin"));
        TabHeader.Tab<AgentListPage<AgentExtension>> mcpTab = new TabHeader.Tab<>("mcp");
        mcpTab.setNodeSupplier(() -> AgentPages.newInstanceExtensionPage(instance, "MCP"));
        TabHeader.Tab<AgentListPage<AgentExtension>> skillsTab = new TabHeader.Tab<>("skills");
        skillsTab.setNodeSupplier(() -> AgentPages.newInstanceExtensionPage(instance, "Skill"));
        TabHeader.Tab<AgentEditorPage> advancedTab = new TabHeader.Tab<>("advanced");
        advancedTab.setNode(advanced);
        TabHeader tabs = new TabHeader(content, settingsTab, versionsTab, modsTab, mcpTab, skillsTab, advancedTab);
        categories = tabs;
        AdvancedListBox navigation = new AdvancedListBox()
                .addNavigationDrawerTab(tabs, settingsTab, i18n("agent.workspace.settings"), SVG.SETTINGS)
                .addNavigationDrawerTab(tabs, versionsTab, i18n("agent.workspace.versions"), SVG.PACKAGE2)
                .addNavigationDrawerTab(tabs, modsTab, i18n("agent.resources.plugins"), SVG.EXTENSION)
                .addNavigationDrawerTab(tabs, mcpTab, i18n("agent.resources.mcp"), SVG.PUBLIC)
                .addNavigationDrawerTab(tabs, skillsTab, i18n("agent.resources.skills"), SVG.SCRIPT)
                .addNavigationDrawerTab(tabs, advancedTab, i18n("agent.workspace.advanced"), SVG.TUNE);
        VBox.setVgrow(navigation, Priority.ALWAYS);
        AdvancedListBox actions = new AdvancedListBox()
                .addNavigationDrawerItem(i18n("agent.launch"), SVG.ROCKET_LAUNCH, () -> AgentPages.launch(instance),
                        item -> item.disableProperty().bind(repository.runningProperty()))
                .addNavigationDrawerItem(i18n("agent.workspace.browse"), SVG.FOLDER_OPEN, () -> AgentPages.showInstanceHome(instance))
                .addNavigationDrawerItem(i18n("agent.console"), SVG.SCRIPT, AgentPages::showConsole)
                .addNavigationDrawerItem(i18n("agent.pack.export"), SVG.OUTPUT, () -> AgentPages.exportPack(instance));
        ComboBox<AgentInstance> selector = new ComboBox<>(repository.getInstances());
        selector.setMinWidth(0);
        selector.setMaxWidth(Double.MAX_VALUE);
        selector.setCellFactory(list -> instanceCell());
        selector.setButtonCell(instanceCell());
        selector.setValue(instance);
        selector.valueProperty().addListener((observable, previous, selected) -> {
            if (selected == null || selected.getId().equals(instanceId)) return;
            instanceId = selected.getId();
            repository.setSelectedInstance(selected);
            rebind();
        });
        VBox selection = new VBox(6, new Label(i18n("agent.instance.current")), selector);
        selection.setPadding(new Insets(12));
        setLeft(selection, navigation, actions);
        setCenter(content);
        tabs.select(tabs.getTabs().stream().filter(tab -> sectionId.equals(tab.getId())).findFirst().orElse(settingsTab), false);
        tabs.getSelectionModel().selectedItemProperty().addListener((observable, oldTab, newTab) -> {
            if (newTab != null) sectionId = newTab.getId();
        });
    }

    /// Keeps full instance names available when choosing and reflects renames in the selected value.
    private static ListCell<AgentInstance> instanceCell() {
        return new ListCell<>() {
            /// Rebinds a reused dropdown cell to its current instance.
            @Override
            protected void updateItem(@Nullable AgentInstance instance, boolean empty) {
                super.updateItem(instance, empty);
                textProperty().unbind();
                if (empty || instance == null) setText(null);
                else textProperty().bind(instance.nameProperty());
            }
        };
    }

    /// Opens a category directly from the corresponding download page.
    void showCategory(String category) {
        sectionId = category;
        if (categories != null) categories.getTabs().stream().filter(tab -> category.equals(tab.getId()))
                .findFirst().ifPresent(tab -> categories.select(tab, false));
    }

    /// Restores fresh models when returning from downloads, extension details or the browser.
    @Override
    public void onPageShown() {
        repository.selectedInstanceProperty().removeListener(contextListener);
        rebind();
        if (current != null) repository.setSelectedInstance(current);
        repository.selectedInstanceProperty().addListener(contextListener);
    }

    /// Detaches listeners while the page is outside the active navigation context.
    @Override
    public void onPageHidden() {
        repository.selectedInstanceProperty().removeListener(contextListener);
    }

    /// Provides the native decorator title.
    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state.getReadOnlyProperty();
    }
}
