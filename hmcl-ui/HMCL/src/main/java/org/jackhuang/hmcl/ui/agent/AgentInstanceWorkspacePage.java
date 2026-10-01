/*
 * DShCraft direct HMCL fork
 * Copyright (C) 2026 DShCraft contributors
 * This file is licensed under the GNU General Public License, version 3 or later.
 */
package org.jackhuang.hmcl.ui.agent;

import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.agent.AgentInstance;
import org.jackhuang.hmcl.agent.AgentRepository;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.AdvancedListBox;
import org.jackhuang.hmcl.ui.decorator.DecoratorAnimatedPage;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// HMCL instance browser with navigation on the left and full-width rows in the center.
@NotNullByDefault
public final class AgentInstanceWorkspacePage extends DecoratorAnimatedPage implements DecoratorPage {
    /// Browser title is independent of instance selection.
    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>(State.fromTitle(i18n("agent.instances")));
    /// Observable repository supplying the browser.
    private final AgentRepository repository = AgentRepository.get();

    /// Creates the browser without placing an instance editor in its navigation rail.
    public AgentInstanceWorkspacePage() {
        AgentListPage<AgentInstance> list = new AgentListPage<>(i18n("agent.instances"), repository.getInstances(),
                repository::addInstance, repository::load, this::showInstance, repository::setSelectedInstance,
                AgentPages::removeInstanceFromWorkspace, AgentPages::launch,
                instance -> instance == repository.getSelectedInstance(), repository.selectedInstanceProperty(),
                i18n("agent.instance.add"), i18n("agent.launch"), false, false, AgentPages::exportPack);
        list.useNavigationToolbar();
        AdvancedListBox navigation = new AdvancedListBox()
                .addNavigationDrawerItem(i18n("agent.workspace.local"), SVG.FOLDER_OPEN, repository::load,
                        item -> item.setActive(true));
        VBox.setVgrow(navigation, Priority.ALWAYS);
        AdvancedListBox actions = new AdvancedListBox()
                .addNavigationDrawerItem(i18n("agent.instance.add"), SVG.ADD_CIRCLE,
                        () -> showInstance(repository.addInstance()))
                .addNavigationDrawerItem(i18n("agent.pack.import"), SVG.PACKAGE2, AgentPages::importPack)
                .addNavigationDrawerItem(i18n("settings"), SVG.SETTINGS, () -> Controllers.navigate(AgentPages.settings()));
        setLeft(navigation, actions);
        setCenter(list.getCenter());
    }

    /// Opens a selected instance in a separate management page.
    public void showInstance(@Nullable AgentInstance instance) {
        if (instance == null) return;
        repository.setSelectedInstance(instance);
        AgentPages.openInstance(instance);
    }

    /// Provides the decorator title.
    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state.getReadOnlyProperty();
    }
}
