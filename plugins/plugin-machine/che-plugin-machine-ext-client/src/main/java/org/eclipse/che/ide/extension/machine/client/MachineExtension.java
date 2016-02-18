/*******************************************************************************
 * Copyright (c) 2012-2016 Codenvy, S.A.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Codenvy, S.A. - initial API and implementation
 *******************************************************************************/
package org.eclipse.che.ide.extension.machine.client;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.web.bindery.event.shared.EventBus;

import org.eclipse.che.api.machine.gwt.client.events.WsAgentStateEvent;
import org.eclipse.che.api.machine.gwt.client.events.WsAgentStateHandler;
import org.eclipse.che.ide.actions.CreateSnapshotAction;
import org.eclipse.che.ide.actions.StopMachineAction;
import org.eclipse.che.ide.actions.StopWorkspaceAction;
import org.eclipse.che.ide.api.action.ActionManager;
import org.eclipse.che.ide.api.action.DefaultActionGroup;
import org.eclipse.che.ide.api.action.IdeActions;
import org.eclipse.che.ide.api.constraints.Constraints;
import org.eclipse.che.ide.api.extension.Extension;
import org.eclipse.che.ide.api.icon.Icon;
import org.eclipse.che.ide.api.icon.IconRegistry;
import org.eclipse.che.ide.api.keybinding.KeyBindingAgent;
import org.eclipse.che.ide.api.keybinding.KeyBuilder;
import org.eclipse.che.ide.api.parts.PartStackType;
import org.eclipse.che.ide.api.parts.PerspectiveManager;
import org.eclipse.che.ide.api.parts.WorkspaceAgent;
import org.eclipse.che.ide.extension.machine.client.actions.CreateMachineAction;
import org.eclipse.che.ide.extension.machine.client.actions.DestroyMachineAction;
import org.eclipse.che.ide.extension.machine.client.actions.EditCommandsAction;
import org.eclipse.che.ide.extension.machine.client.actions.ExecuteSelectedCommandAction;
import org.eclipse.che.ide.extension.machine.client.actions.NewTerminalAction;
import org.eclipse.che.ide.extension.machine.client.actions.RestartMachineAction;
import org.eclipse.che.ide.extension.machine.client.actions.RunCommandAction;
import org.eclipse.che.ide.extension.machine.client.actions.SelectCommandComboBoxReady;
import org.eclipse.che.ide.extension.machine.client.actions.SwitchPerspectiveAction;
import org.eclipse.che.ide.extension.machine.client.command.custom.CustomCommandType;
import org.eclipse.che.ide.extension.machine.client.command.valueproviders.ServerPortProvider;
import org.eclipse.che.ide.extension.machine.client.machine.console.ClearConsoleAction;
import org.eclipse.che.ide.extension.machine.client.machine.console.MachineConsoleToolbar;
import org.eclipse.che.ide.extension.machine.client.outputspanel.OutputsContainerPresenter;
import org.eclipse.che.ide.extension.machine.client.processes.ConsolesPanelPresenter;
import org.eclipse.che.ide.ui.toolbar.ToolbarPresenter;
import org.eclipse.che.ide.util.input.KeyCodeMap;

import static org.eclipse.che.ide.api.action.IdeActions.GROUP_CENTER_TOOLBAR;
import static org.eclipse.che.ide.api.action.IdeActions.GROUP_MAIN_MENU;
import static org.eclipse.che.ide.api.action.IdeActions.GROUP_RIGHT_TOOLBAR;
import static org.eclipse.che.ide.api.action.IdeActions.GROUP_RUN;
import static org.eclipse.che.ide.api.action.IdeActions.GROUP_WORKSPACE;
import static org.eclipse.che.ide.api.constraints.Anchor.AFTER;
import static org.eclipse.che.ide.api.constraints.Constraints.FIRST;
import static org.eclipse.che.ide.workspace.perspectives.project.ProjectPerspective.PROJECT_PERSPECTIVE_ID;

/**
 * Machine extension entry point.
 *
 * @author Artem Zatsarynnyi
 * @author Dmitry Shnurenko
 */
@Singleton
@Extension(title = "Machine", version = "1.0.0")
public class MachineExtension {

    public static final String GROUP_MACHINE_CONSOLE_TOOLBAR    = "MachineConsoleToolbar";
    public static final String GROUP_MACHINE_TOOLBAR            = "MachineGroupToolbar";
    public static final String GROUP_COMMANDS_LIST              = "CommandsListGroup";
    public static final String GROUP_COMMANDS_LIST_DISPLAY_NAME = "Run";

    @Inject
    public MachineExtension(MachineResources machineResources,
                            final EventBus eventBus,
                            final WorkspaceAgent workspaceAgent,
                            final ConsolesPanelPresenter consolesPanelPresenter,
                            final ServerPortProvider machinePortProvider,
                            final OutputsContainerPresenter outputsContainerPresenter,
                            final PerspectiveManager perspectiveManager,
                            IconRegistry iconRegistry,
                            CustomCommandType arbitraryCommandType) {
        machineResources.getCss().ensureInjected();

        eventBus.addHandler(WsAgentStateEvent.TYPE, new WsAgentStateHandler() {
            @Override
            public void onWsAgentStarted(WsAgentStateEvent event) {
                perspectiveManager.setPerspectiveId(PROJECT_PERSPECTIVE_ID);
                workspaceAgent.openPart(outputsContainerPresenter, PartStackType.INFORMATION);
                workspaceAgent.openPart(consolesPanelPresenter, PartStackType.INFORMATION);
            }

            @Override
            public void onWsAgentStopped(WsAgentStateEvent event) {
            }
        });

        iconRegistry.registerIcon(new Icon(arbitraryCommandType.getId() + ".commands.category.icon", machineResources.customCommandType()));
    }

    @Inject
    private void prepareActions(MachineLocalizationConstant localizationConstant,
                                ActionManager actionManager,
                                KeyBindingAgent keyBinding,
                                NewTerminalAction newTerminalAction,
                                ExecuteSelectedCommandAction executeSelectedCommandAction,
                                SelectCommandComboBoxReady selectCommandAction,
                                EditCommandsAction editCommandsAction,
                                CreateMachineAction createMachine,
                                RestartMachineAction restartMachine,
                                DestroyMachineAction destroyMachineAction,
                                StopWorkspaceAction stopWorkspaceAction,
                                StopMachineAction stopMachineAction,
                                SwitchPerspectiveAction switchPerspectiveAction,
                                CreateSnapshotAction createSnapshotAction,
                                RunCommandAction runCommandAction) {
        final DefaultActionGroup mainMenu = (DefaultActionGroup)actionManager.getAction(GROUP_MAIN_MENU);

        final DefaultActionGroup workspaceMenu = (DefaultActionGroup)actionManager.getAction(GROUP_WORKSPACE);
        final DefaultActionGroup runMenu = (DefaultActionGroup)actionManager.getAction(GROUP_RUN);

        // register actions
        actionManager.registerAction("editCommands", editCommandsAction);
        actionManager.registerAction("selectCommandAction", selectCommandAction);
        actionManager.registerAction("executeSelectedCommand", executeSelectedCommandAction);

        // add actions in main menu
        runMenu.add(editCommandsAction);

        //add actions in machine menu
        final DefaultActionGroup machineMenu = new DefaultActionGroup(localizationConstant.mainMenuMachine(), true, actionManager);

        actionManager.registerAction("machine", machineMenu);
        actionManager.registerAction("createMachine", createMachine);
        actionManager.registerAction("destroyMachine", destroyMachineAction);
        actionManager.registerAction("restartMachine", restartMachine);
        actionManager.registerAction("stopWorkspace", stopWorkspaceAction);
        actionManager.registerAction("stopMachine", stopMachineAction);
        actionManager.registerAction("createSnapshot", createSnapshotAction);
        actionManager.registerAction("runCommand", runCommandAction);
        actionManager.registerAction("newTerminal", newTerminalAction);

        workspaceMenu.add(stopWorkspaceAction);

        mainMenu.add(machineMenu, new Constraints(AFTER, IdeActions.GROUP_PROJECT));
        machineMenu.add(createMachine);
        machineMenu.add(restartMachine);
        machineMenu.add(destroyMachineAction);
        machineMenu.add(stopMachineAction);
        machineMenu.add(createSnapshotAction);

        // add actions on center part of toolbar
        final DefaultActionGroup centerToolbarGroup = (DefaultActionGroup)actionManager.getAction(GROUP_CENTER_TOOLBAR);
        final DefaultActionGroup machineToolbarGroup = new DefaultActionGroup(GROUP_MACHINE_TOOLBAR, false, actionManager);
        actionManager.registerAction(GROUP_MACHINE_TOOLBAR, machineToolbarGroup);
        centerToolbarGroup.add(machineToolbarGroup);
        machineToolbarGroup.add(selectCommandAction);
        final DefaultActionGroup executeToolbarGroup = new DefaultActionGroup(actionManager);
        executeToolbarGroup.add(executeSelectedCommandAction);
        machineToolbarGroup.add(executeToolbarGroup);

        // add actions on right part of toolbar
        final DefaultActionGroup rightToolbarGroup = (DefaultActionGroup)actionManager.getAction(GROUP_RIGHT_TOOLBAR);
        rightToolbarGroup.add(switchPerspectiveAction);

        // add group for command list
        final DefaultActionGroup commandList = new DefaultActionGroup(GROUP_COMMANDS_LIST_DISPLAY_NAME, true, actionManager);

        actionManager.registerAction(GROUP_COMMANDS_LIST, commandList);
        commandList.add(editCommandsAction, FIRST);

        final DefaultActionGroup runContextGroup = (DefaultActionGroup)actionManager.getAction(IdeActions.GROUP_RUN_CONTEXT_MENU);
        runContextGroup.add(commandList);
        runContextGroup.addSeparator();

        // Define hot-keys
        keyBinding.getGlobal().addKey(new KeyBuilder().alt().charCode(KeyCodeMap.F12).build(), "newTerminal");
    }

    @Inject
    private void setUpMachineConsole(ActionManager actionManager,
                                     ClearConsoleAction clearConsoleAction,
                                     @MachineConsoleToolbar ToolbarPresenter machineConsoleToolbar) {

        // add toolbar to Machine console
        final DefaultActionGroup consoleToolbarActionGroup = new DefaultActionGroup(GROUP_MACHINE_CONSOLE_TOOLBAR, false, actionManager);
        consoleToolbarActionGroup.add(clearConsoleAction);
        consoleToolbarActionGroup.addSeparator();
        machineConsoleToolbar.bindMainGroup(consoleToolbarActionGroup);
    }
}
