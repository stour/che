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
package org.eclipse.che.ide.extension.machine.client.processes;

import com.google.gwt.user.client.ui.AcceptsOneWidget;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.web.bindery.event.shared.EventBus;

import org.eclipse.che.api.machine.gwt.client.MachineServiceClient;
import org.eclipse.che.api.machine.gwt.client.events.DevMachineStateEvent;
import org.eclipse.che.api.machine.gwt.client.events.DevMachineStateHandler;
import org.eclipse.che.api.machine.shared.dto.CommandDto;
import org.eclipse.che.api.machine.shared.dto.MachineDto;
import org.eclipse.che.api.machine.shared.dto.MachineProcessDto;
import org.eclipse.che.api.promises.client.Operation;
import org.eclipse.che.api.promises.client.OperationException;
import org.eclipse.che.api.promises.client.Promise;
import org.eclipse.che.api.promises.client.PromiseError;
import org.eclipse.che.commons.annotation.Nullable;
import org.eclipse.che.ide.api.app.AppContext;
import org.eclipse.che.ide.api.mvp.View;
import org.eclipse.che.ide.api.notification.NotificationManager;
import org.eclipse.che.ide.api.parts.HasView;
import org.eclipse.che.ide.api.parts.WorkspaceAgent;
import org.eclipse.che.ide.api.parts.base.BasePresenter;
import org.eclipse.che.ide.dto.DtoFactory;
import org.eclipse.che.ide.extension.machine.client.MachineLocalizationConstant;
import org.eclipse.che.ide.extension.machine.client.MachineResources;
import org.eclipse.che.ide.extension.machine.client.command.CommandConfiguration;
import org.eclipse.che.ide.extension.machine.client.command.CommandType;
import org.eclipse.che.ide.extension.machine.client.command.CommandTypeRegistry;
import org.eclipse.che.ide.extension.machine.client.inject.factories.EntityFactory;
import org.eclipse.che.ide.extension.machine.client.inject.factories.TerminalFactory;
import org.eclipse.che.ide.extension.machine.client.machine.Machine;
import org.eclipse.che.ide.extension.machine.client.outputspanel.console.CommandConsoleFactory;
import org.eclipse.che.ide.extension.machine.client.outputspanel.console.CommandOutputConsole;
import org.eclipse.che.ide.extension.machine.client.outputspanel.console.DefaultOutputConsole;
import org.eclipse.che.ide.extension.machine.client.perspective.terminal.TerminalPresenter;
import org.eclipse.che.ide.extension.machine.client.processes.event.ProcessFinishedEvent;
import org.eclipse.che.ide.extension.machine.client.processes.event.ProcessFinishedHandler;
import org.eclipse.che.ide.ui.dialogs.ConfirmCallback;
import org.eclipse.che.ide.ui.dialogs.DialogFactory;
import org.eclipse.che.ide.util.loging.Log;
import org.vectomatic.dom.svg.ui.SVGResource;

import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.che.ide.api.outputconsole.OutputConsole;

import static org.eclipse.che.ide.api.notification.StatusNotification.Status.FAIL;
import static org.eclipse.che.ide.extension.machine.client.perspective.terminal.TerminalPresenter.TerminalStateListener;
import static org.eclipse.che.ide.extension.machine.client.processes.ProcessTreeNode.ProcessNodeType.COMMAND_NODE;
import static org.eclipse.che.ide.extension.machine.client.processes.ProcessTreeNode.ProcessNodeType.MACHINE_NODE;
import static org.eclipse.che.ide.extension.machine.client.processes.ProcessTreeNode.ProcessNodeType.ROOT_NODE;
import static org.eclipse.che.ide.extension.machine.client.processes.ProcessTreeNode.ProcessNodeType.TERMINAL_NODE;

/**
 * Presenter for managing machines process and terminals.
 *
 * @author Anna Shumilova
 * @author Roman Nikitenko
 * @author Vlad Zhukovskyi
 */
@Singleton
public class ConsolesPanelPresenter extends BasePresenter implements ConsolesPanelView.ActionDelegate,
        HasView, ProcessFinishedHandler, OutputConsole.ConsoleOutputListener {

    private static final String DEFAULT_TERMINAL_NAME = "Terminal";

    public static final String SSH_PORT = "22";

    private final DtoFactory                  dtoFactory;
    private final DialogFactory               dialogFactory;
    private final EntityFactory               entityFactory;
    private final TerminalFactory             terminalFactory;
    private final CommandConsoleFactory       commandConsoleFactory;
    private final NotificationManager         notificationManager;
    private final MachineLocalizationConstant localizationConstant;
    private final ConsolesPanelView           view;
    private final MachineResources            resources;
    private final AppContext                  appContext;
    private final MachineServiceClient        machineService;
    private final WorkspaceAgent              workspaceAgent;
    private final CommandTypeRegistry         commandTypeRegistry;

    ProcessTreeNode                rootNode;
    Map<String, TerminalPresenter> terminals = new HashMap<>();

    Map<String, OutputConsole>     consoles = new HashMap<>();
    Map<OutputConsole, String>     consoleCommands = new HashMap<>();

    @Inject
    public ConsolesPanelPresenter(ConsolesPanelView view,
                                  EventBus eventBus,
                                  DtoFactory dtoFactory,
                                  DialogFactory dialogFactory,
                                  EntityFactory entityFactory,
                                  TerminalFactory terminalFactory,
                                  CommandConsoleFactory commandConsoleFactory,
                                  CommandTypeRegistry commandTypeRegistry,
                                  WorkspaceAgent workspaceAgent,
                                  NotificationManager notificationManager,
                                  MachineLocalizationConstant localizationConstant,
                                  MachineServiceClient machineService,
                                  MachineResources resources,
                                  AppContext appContext) {
        this.view = view;
        this.terminalFactory = terminalFactory;
        this.workspaceAgent = workspaceAgent;
        this.commandConsoleFactory = commandConsoleFactory;
        this.commandTypeRegistry = commandTypeRegistry;
        this.dtoFactory = dtoFactory;
        this.dialogFactory = dialogFactory;
        this.notificationManager = notificationManager;
        this.localizationConstant = localizationConstant;
        this.resources = resources;
        this.entityFactory = entityFactory;
        this.appContext = appContext;
        this.machineService = machineService;

        this.fetchMachines();
        this.view.setDelegate(this);
        this.view.setTitle(localizationConstant.viewConsolesTitle());

        eventBus.addHandler(DevMachineStateEvent.TYPE, new DevMachineStateHandler() {
            @Override
            public void onMachineStarted(DevMachineStateEvent event) {
                fetchMachines();
            }

            @Override
            public void onMachineDestroyed(DevMachineStateEvent event) {

            }
        });

        eventBus.addHandler(ProcessFinishedEvent.TYPE, this);
    }

    @Override
    public void onProcessFinished(ProcessFinishedEvent event) {
        for (Map.Entry<String, OutputConsole> entry : consoles.entrySet()) {
            if (entry.getValue().isFinished()) {
                view.setStopButtonVisibility(entry.getKey(), false);
            }
        }
    }

    @Override
    public View getView() {
        return view;
    }

    @NotNull
    @Override
    public String getTitle() {
        return localizationConstant.viewConsolesTitle();
    }

    @Override
    public void setVisible(boolean visible) {
        view.setVisible(visible);
    }

    @Nullable
    @Override
    public SVGResource getTitleSVGImage() {
        return resources.terminal();
    }

    @Override
    public String getTitleToolTip() {
        return localizationConstant.viewProcessesTooltip();
    }

    @Override
    public void go(AcceptsOneWidget container) {
        container.setWidget(view);
    }

    /** Get the list of all available machines. */
    public void fetchMachines() {
        String workspaceId = appContext.getWorkspaceId();

        Promise<List<MachineDto>> machinesPromise = machineService.getWorkspaceMachines(workspaceId);

        machinesPromise.then(new Operation<List<MachineDto>>() {
            @Override
            public void apply(List<MachineDto> machines) throws OperationException {
                List<ProcessTreeNode> rootChildren = new ArrayList<>();

                rootNode = new ProcessTreeNode(ROOT_NODE, null, null, null, rootChildren);
                for (MachineDto descriptor : machines) {
                    if (descriptor.isDev()) {
                        List<ProcessTreeNode> processTreeNodes = new ArrayList<ProcessTreeNode>();
                        ProcessTreeNode machineNode = new ProcessTreeNode(MACHINE_NODE, rootNode, descriptor, null, processTreeNodes);
                        rootChildren.add(machineNode);
                        view.setProcessesData(rootNode);

                        restoreState(descriptor.getId());
                    }
                }

            }
        });
    }

    private void restoreState(final String machineId) {
        machineService.getProcesses(machineId).then(new Operation<List<MachineProcessDto>>() {
            @Override
            public void apply(List<MachineProcessDto> arg) throws OperationException {
                for (MachineProcessDto machineProcessDto : arg) {
                    final CommandDto commandDto = dtoFactory.createDto(CommandDto.class)
                                                            .withName(machineProcessDto.getName())
                                                            .withCommandLine(machineProcessDto.getCommandLine())
                                                            .withType(machineProcessDto.getType());

                    final CommandType type = commandTypeRegistry.getCommandTypeById(commandDto.getType());
                    if (type != null) {
                        final CommandConfiguration configuration = type.getConfigurationFactory().createFromDto(commandDto);

                        final CommandOutputConsole console = commandConsoleFactory.create(configuration, machineId);
                        console.listenToOutput(machineProcessDto.getOutputChannel());
                        console.attachToProcess(machineProcessDto);

                        addCommandOutput(machineId, console);
                    }

                }
            }
        }).catchError(new Operation<PromiseError>() {
            @Override
            public void apply(PromiseError arg) throws OperationException {
                notificationManager.notify(localizationConstant.failedToGetProcesses(machineId));
            }
        });
    }

    /**
     * Adds command node to process tree and displays command output
     *
     * @param machineId
     *         id of machine in which the command will be executed
     * @param outputConsole
     *         the console for command output
     */
    public void addCommandOutput(@NotNull String machineId, @NotNull OutputConsole outputConsole) {
        ProcessTreeNode machineTreeNode = findProcessTreeNodeById(machineId);
        if (machineTreeNode == null) {
            notificationManager.notify(localizationConstant.failedToExecuteCommand(), localizationConstant.machineNotFound(machineId),
                                       FAIL, true);
            Log.error(getClass(), localizationConstant.machineNotFound(machineId));
            return;
        }

        String commandId;
        String outputConsoleTitle = outputConsole.getTitle();
        ProcessTreeNode processTreeNode = getProcessTreeNodeByName(outputConsoleTitle, machineTreeNode);
        if (processTreeNode != null && isCommandStopped(processTreeNode.getId())) {
            commandId = processTreeNode.getId();
            view.hideProcessOutput(commandId);
        } else {
            ProcessTreeNode commandNode = new ProcessTreeNode(COMMAND_NODE, machineTreeNode, outputConsoleTitle, outputConsole.getTitleIcon(), null);
            commandId = commandNode.getId();
            view.addProcessNode(commandNode);
            addChildToMachineNode(commandNode, machineTreeNode);
        }
        
        updateCommandOutput(commandId, outputConsole);

        resfreshStopButtonState(commandId);
        workspaceAgent.setActivePart(this);
    }

    private void updateCommandOutput(@NotNull final String command, @NotNull OutputConsole outputConsole) {
        consoles.put(command, outputConsole);
        consoleCommands.put(outputConsole, command);

        outputConsole.go(new AcceptsOneWidget() {
            @Override
            public void setWidget(IsWidget widget) {
                view.addProcessWidget(command, widget);
                view.selectNode(view.getNodeById(command));
            }
        });

        outputConsole.addOutputListener(this);
    }

    /**
     * Adds new terminal to the processes panel
     *
     * @param machineId
     *         id of machine in which the terminal will be added
     */
    @Override
    public void onAddTerminal(@NotNull final String machineId) {
        machineService.getMachine(machineId).then(new Operation<MachineDto>() {
            @Override
            public void apply(MachineDto arg) throws OperationException {
                Machine machine = entityFactory.createMachine(arg);
                final ProcessTreeNode machineTreeNode = findProcessTreeNodeById(machineId);

                if (machineTreeNode == null) {
                    notificationManager.notify(localizationConstant.failedToConnectTheTerminal(),
                                               localizationConstant.machineNotFound(machineId), FAIL, true);
                    Log.error(getClass(), localizationConstant.machineNotFound(machineId));
                    return;
                }

                final TerminalPresenter newTerminal = terminalFactory.create(machine);
                final IsWidget terminalWidget = newTerminal.getView();
                final String terminalName = getUniqueTerminalName(machineTreeNode);
                final ProcessTreeNode terminalNode =
                        new ProcessTreeNode(TERMINAL_NODE, machineTreeNode, terminalName, resources.terminalTreeIcon(), null);
                addChildToMachineNode(terminalNode, machineTreeNode);

                final String terminalId = terminalNode.getId();
                terminals.put(terminalId, newTerminal);
                view.addProcessNode(terminalNode);
                view.addProcessWidget(terminalId, terminalWidget);
                resfreshStopButtonState(terminalId);

                newTerminal.setVisible(true);
                newTerminal.connect();
                newTerminal.setListener(new TerminalStateListener() {
                    @Override
                    public void onExit() {
                        onStopProcess(terminalNode);
                        terminals.remove(terminalId);
                    }
                });
            }
        }).catchError(new Operation<PromiseError>() {
            @Override
            public void apply(PromiseError arg) throws OperationException {
                notificationManager.notify(localizationConstant.failedToFindMachine(machineId));
            }
        });

    }

    @Override
    public void onPreviewSsh(@NotNull final String machineId) {
        ProcessTreeNode machineTreeNode = findProcessTreeNodeById(machineId);
        if (machineTreeNode == null) {
            return;
        }

        MachineDto machine = (MachineDto)machineTreeNode.getData();

        OutputConsole defaultConsole = commandConsoleFactory.create("SSH");
        addCommandOutput(machineId, defaultConsole);

        String machineName = machine.getName();
        String sshServiceAddress = getSshServerAddress(machine);
        String machineHost = "";
        String sshPort = SSH_PORT;
        if (sshServiceAddress != null) {
            String[] parts = sshServiceAddress.split(":");
            machineHost = parts[0];
            sshPort = (parts.length == 2) ? parts[1] : sshPort;
        }

        if (defaultConsole instanceof DefaultOutputConsole) {
            ((DefaultOutputConsole)defaultConsole).printText(localizationConstant.sshConnectInfo(machineName, machineHost, sshPort));
        }
    }

    /**
     * Returns the ssh service address in format - host:port (example - localhost:32899)
     *
     * @param machine
     *         machine to retrieve address
     * @return ssh service address in format host:port
     */
    private String getSshServerAddress(MachineDto machine) {
        if (machine.getMetadata().getServers().containsKey(SSH_PORT)) {
            return machine.getMetadata().getServers().get(SSH_PORT).getAddress();
        } else {
            return null;
        }
    }

    @Override
    public void onCloseTerminal(@NotNull ProcessTreeNode node) {
        String terminalId = node.getId();
        if (terminals.containsKey(terminalId)) {
            onStopProcess(node);
            terminals.get(terminalId).stopTerminal();
            terminals.remove(terminalId);
        }
    }

    @Override
    public void onTerminalSelected(@NotNull String terminalId) {
        view.showProcessOutput(terminalId);
        resfreshStopButtonState(terminalId);
    }

    @Override
    public void onCommandSelected(@NotNull String commandId) {
        view.showProcessOutput(commandId);
        resfreshStopButtonState(commandId);
    }

    @Override
    public void onStopCommandProcess(@NotNull ProcessTreeNode node) {
        String commandId = node.getId();
        if (consoles.containsKey(commandId) && !consoles.get(commandId).isFinished()) {
            consoles.get(commandId).stop();
        }
    }

    @Override
    public void onCloseCommandOutputClick(@NotNull ProcessTreeNode node) {
        String commandId = node.getId();
        OutputConsole console = consoles.get(commandId);

        if (console == null) {
            return;
        }

        if (console.isFinished()) {
            console.close();
            onStopProcess(node);
            consoles.remove(commandId);
            consoleCommands.remove(console);
            return;
        }

        dialogFactory.createConfirmDialog("", localizationConstant.outputsConsoleViewStopProcessConfirmation(console.getTitle()),
                                          getConfirmCloseConsoleCallback(console, node), null)
                     .show();
    }

    private ConfirmCallback getConfirmCloseConsoleCallback(final OutputConsole console, final ProcessTreeNode node) {
        return new ConfirmCallback() {
            @Override
            public void accepted() {
                console.stop();
                onStopProcess(node);

                console.close();
                consoles.remove(node.getId());
                consoleCommands.remove(console);
            }
        };
    }

    private void onStopProcess(@NotNull ProcessTreeNode node) {
        String processId = node.getId();
        ProcessTreeNode parentNode = node.getParent();

        int processIndex = view.getNodeIndex(processId);
        if (processIndex < 0) {
            return;
        }

        int countWidgets = terminals.size() + consoles.size();
        if (countWidgets == 1) {
            view.hideProcessOutput(processId);
            removeChildFromMachineNode(node, parentNode);
            return;
        }

        int neighborIndex = processIndex > 0 ? processIndex - 1 : processIndex + 1;
        ProcessTreeNode neighborNode = view.getNodeByIndex(neighborIndex);
        String neighborNodeId = neighborNode.getId();
        
        removeChildFromMachineNode(node, parentNode);
        view.selectNode(neighborNode);
        resfreshStopButtonState(neighborNodeId);
        view.showProcessOutput(neighborNodeId);
        view.hideProcessOutput(processId);
    }
    
    private void resfreshStopButtonState(String selectedNodeId) {
        for (Map.Entry<String, OutputConsole> entry : consoles.entrySet()) {
            String nodeId = entry.getKey();
            if (selectedNodeId.equals(nodeId) && !entry.getValue().isFinished()) {
                view.setStopButtonVisibility(selectedNodeId, true);
            } else {
                view.setStopButtonVisibility(nodeId, false);
            }
        }
    }

    private void addChildToMachineNode(ProcessTreeNode childNode, ProcessTreeNode machineTreeNode) {
        machineTreeNode.getChildren().add(childNode);
        view.setProcessesData(rootNode);
        view.selectNode(childNode);
    }

    private void removeChildFromMachineNode(ProcessTreeNode childNode, ProcessTreeNode machineTreeNode) {
        view.removeProcessNode(childNode);
        machineTreeNode.getChildren().remove(childNode);
        view.setProcessesData(rootNode);
    }

    private ProcessTreeNode findProcessTreeNodeById(@NotNull String id) {
        for (ProcessTreeNode processTreeNode : rootNode.getChildren()) {
            if (id.equals(processTreeNode.getId())) {
                return processTreeNode;
            }
        }
        return null;
    }

    private String getUniqueTerminalName(ProcessTreeNode machineNode) {
        String terminalName = DEFAULT_TERMINAL_NAME;
        if (!isTerminalNameExist(machineNode, terminalName)) {
            return DEFAULT_TERMINAL_NAME;
        }

        int counter = 2;
        do {
            terminalName = localizationConstant.viewProcessesTerminalNodeTitle(String.valueOf(counter));
            counter++;
        } while (isTerminalNameExist(machineNode, terminalName));
        return terminalName;
    }

    private boolean isTerminalNameExist(ProcessTreeNode machineNode, String terminalName) {
        for (ProcessTreeNode node : machineNode.getChildren()) {
            if (TERMINAL_NODE == node.getType() && node.getName().equals(terminalName)) {
                return true;
            }
        }
        return false;
    }

    private ProcessTreeNode getProcessTreeNodeByName(String processName, ProcessTreeNode machineTreeNode) {
        for (ProcessTreeNode processTreeNode : machineTreeNode.getChildren()) {
            if (processTreeNode.getName().equals(processName)) {
                return processTreeNode;
            }
        }
        return null;
    }

    private boolean isCommandStopped(String commandId) {
        return consoles.containsKey(commandId) && consoles.get(commandId).isFinished();
    }

    @Override
    public void onConsoleOutput(OutputConsole console) {
        String command = consoleCommands.get(console);
        if (command != null) {
            view.markProcessHasOutput(command);
        }
    }

}
