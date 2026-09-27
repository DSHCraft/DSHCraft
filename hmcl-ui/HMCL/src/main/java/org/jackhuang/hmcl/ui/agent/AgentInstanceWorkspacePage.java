/*
 * DShCraft direct HMCL fork
 * Copyright (C) 2026 DSHCraft contributors
 *
 * This file is licensed under the GNU General Public License, version 3 or later.
 */
package org.jackhuang.hmcl.ui.agent;

import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.Node;
import org.jackhuang.hmcl.agent.AgentInstance;
import org.jackhuang.hmcl.agent.AgentRepository;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.decorator.DecoratorAnimatedPage;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// HMCL-style DSH workspace: the instance list stays on the left and the selected instance editor stays on the right.
@NotNullByDefault
public final class AgentInstanceWorkspacePage extends DecoratorAnimatedPage implements DecoratorPage {
    /// Page title state displayed by HMCL's decorator.
    private final ReadOnlyObjectWrapper<State> state =
            new ReadOnlyObjectWrapper<>(State.fromTitle(i18n("agent.instances")));
    /// Persistent DSH state repository.
    private final AgentRepository repository = AgentRepository.get();
    /// Instance list control is retained so its shared HMCL list remains alive while embedded on the left.
    private final AgentListPage<AgentInstance> listPage;

    /// Creates the two-pane instance workspace and selects the current instance on first display.
    public AgentInstanceWorkspacePage() {
        listPage = new AgentListPage<>(
                i18n("agent.instances"),
                repository.getInstances(),
                repository::addInstance,
                repository::load,
                this::showInstance,
                repository::setSelectedInstance,
                AgentPages::removeInstanceFromWorkspace,
                AgentPages::launch,
                instance -> instance == repository.getSelectedInstance(),
                repository.selectedInstanceProperty(),
                i18n("agent.instance.add"),
                i18n("agent.launch"),
                false,
                false,
                AgentPages::exportPack);
        setLeft(listPage.getCenter());
        showInstance(repository.getSelectedInstance());
    }

    /// Shows one instance editor in the right pane without leaving the workspace.
    public void showInstance(@Nullable AgentInstance instance) {
        if (instance == null) {
            setCenter(new AgentEditorPage(i18n("agent.instance.empty")));
            return;
        }
        repository.setSelectedInstance(instance);
        AgentPages.openInstanceInWorkspace(instance, this::setCenter);
    }

    /// Returns the decorator title state.
    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state.getReadOnlyProperty();
    }
}
