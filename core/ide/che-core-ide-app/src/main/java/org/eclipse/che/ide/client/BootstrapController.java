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
package org.eclipse.che.ide.client;

import elemental.client.Browser;
import elemental.events.Event;
import elemental.events.EventListener;

import com.google.gwt.core.client.Callback;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.ScheduledCommand;
import com.google.gwt.dom.client.Document;
import com.google.gwt.event.logical.shared.CloseEvent;
import com.google.gwt.event.logical.shared.CloseHandler;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.RootLayoutPanel;
import com.google.gwt.user.client.ui.SimpleLayoutPanel;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.web.bindery.event.shared.EventBus;

import org.eclipse.che.api.core.model.workspace.UsersWorkspace;
import org.eclipse.che.api.machine.gwt.client.events.WsAgentStateEvent;
import org.eclipse.che.api.workspace.gwt.client.event.WorkspaceStartedEvent;
import org.eclipse.che.api.workspace.gwt.client.event.WorkspaceStartedHandler;
import org.eclipse.che.api.workspace.shared.dto.UsersWorkspaceDto;
import org.eclipse.che.ide.analytics.AnalyticsEventLoggerExt;
import org.eclipse.che.ide.analytics.AnalyticsSessions;
import org.eclipse.che.ide.api.ProductInfoDataProvider;
import org.eclipse.che.ide.api.app.AppContext;
import org.eclipse.che.ide.api.component.Component;
import org.eclipse.che.ide.api.component.WsAgentComponent;
import org.eclipse.che.ide.api.event.WindowActionEvent;
import org.eclipse.che.ide.statepersistance.AppStateManager;
import org.eclipse.che.ide.util.loging.Log;
import org.eclipse.che.ide.workspace.WorkspacePresenter;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Performs initial application startup.
 *
 * @author Nikolay Zamosenchuk
 * @author Dmitry Shnurenko
 */
@Singleton
public class BootstrapController {

    private final Provider<WorkspacePresenter> workspaceProvider;
    private final ExtensionInitializer         extensionInitializer;
    private final EventBus                     eventBus;
    private final AnalyticsEventLoggerExt      analyticsEventLoggerExt;
    private final ProductInfoDataProvider      productInfoDataProvider;
    private final Provider<AppStateManager>    appStateManagerProvider;
    private final AppContext                   appContext;

    @Inject
    public BootstrapController(Provider<WorkspacePresenter> workspaceProvider,
                               ExtensionInitializer extensionInitializer,
                               EventBus eventBus,
                               AnalyticsEventLoggerExt analyticsEventLoggerExt,
                               ProductInfoDataProvider productInfoDataProvider,
                               Provider<AppStateManager> appStateManagerProvider,
                               AppContext appContext,
                               DtoRegistrar dtoRegistrar) {
        this.workspaceProvider = workspaceProvider;
        this.extensionInitializer = extensionInitializer;
        this.eventBus = eventBus;
        this.analyticsEventLoggerExt = analyticsEventLoggerExt;
        this.productInfoDataProvider = productInfoDataProvider;
        this.appStateManagerProvider = appStateManagerProvider;
        this.appContext = appContext;

        appContext.setStartUpActions(StartUpActionsParser.getStartUpActions());
        dtoRegistrar.registerDtoProviders();
    }

    @Inject
    private void startComponents(Map<String, Provider<Component>> components) {
        startComponents(components.values().iterator());
    }

    @Inject
    private void startWsAgentComponents(EventBus eventBus, final Map<String, Provider<WsAgentComponent>> components) {
        eventBus.addHandler(WorkspaceStartedEvent.TYPE, new WorkspaceStartedHandler() {
            @Override
            public void onWorkspaceStarted(UsersWorkspaceDto workspace) {
                startWsAgentComponents(components.values().iterator());
            }
        });
    }

    private void startComponents(final Iterator<Provider<Component>> componentProviderIterator) {
        if (componentProviderIterator.hasNext()) {
            Provider<Component> componentProvider = componentProviderIterator.next();

            final Component component = componentProvider.get();
            Log.info(component.getClass(), "starting...");
            component.start(new Callback<Component, Exception>() {
                @Override
                public void onSuccess(Component result) {
                    Log.info(result.getClass(), "started");
                    startComponents(componentProviderIterator);
                }

                @Override
                public void onFailure(Exception reason) {
                    Log.error(component.getClass(), reason);
                    initializationFailed(reason.getMessage());
                }
            });
        } else {
            startExtensionsAndDisplayUI();
        }
    }

    private void startWsAgentComponents(final Iterator<Provider<WsAgentComponent>> componentProviderIterator) {
        if (componentProviderIterator.hasNext()) {
            Provider<WsAgentComponent> componentProvider = componentProviderIterator.next();

            final WsAgentComponent component = componentProvider.get();
            Log.info(component.getClass(), "starting...");
            component.start(new Callback<WsAgentComponent, Exception>() {
                @Override
                public void onSuccess(WsAgentComponent result) {
                    Log.info(result.getClass(), "started");
                    startWsAgentComponents(componentProviderIterator);
                }

                @Override
                public void onFailure(Exception reason) {
                    Log.error(component.getClass(), reason);
                    initializationFailed(reason.getMessage());
                }
            });
        } else {
            eventBus.fireEvent(WsAgentStateEvent.createWsAgentStartedEvent());
        }
    }

    private void startExtensionsAndDisplayUI() {
        appStateManagerProvider.get();

        Scheduler.get().scheduleDeferred(new ScheduledCommand() {
            @Override
            public void execute() {
                // Instantiate extensions
                extensionInitializer.startExtensions();

                Scheduler.get().scheduleDeferred(new ScheduledCommand() {
                    @Override
                    public void execute() {
                        displayIDE();
                    }
                });
            }
        });
    }

    private void displayIDE() {
        // Start UI
        SimpleLayoutPanel mainPanel = new SimpleLayoutPanel();

        RootLayoutPanel.get().add(mainPanel);

        // Make sure the root panel creates its own stacking context
        RootLayoutPanel.get().getElement().getStyle().setZIndex(0);

        WorkspacePresenter workspacePresenter = workspaceProvider.get();

        // Display IDE
        workspacePresenter.go(mainPanel);

        Document.get().setTitle(productInfoDataProvider.getDocumentTitle());

        final AnalyticsSessions analyticsSessions = new AnalyticsSessions();

        // Bind browser's window events
        Window.addWindowClosingHandler(new Window.ClosingHandler() {
            @Override
            public void onWindowClosing(Window.ClosingEvent event) {
                onWindowClose(analyticsSessions);
                eventBus.fireEvent(WindowActionEvent.createWindowClosingEvent(event));
            }
        });

        Window.addCloseHandler(new CloseHandler<Window>() {
            @Override
            public void onClose(CloseEvent<Window> event) {
                onWindowClose(analyticsSessions);
                eventBus.fireEvent(WindowActionEvent.createWindowClosedEvent());
            }
        });

        elemental.html.Window window = Browser.getWindow();

        window.addEventListener(Event.FOCUS, new EventListener() {
            @Override
            public void handleEvent(Event evt) {
                onSessionUsage(analyticsSessions, false);
            }
        }, true);

        window.addEventListener(Event.BLUR, new EventListener() {
            @Override
            public void handleEvent(Event evt) {
                onSessionUsage(analyticsSessions, false);
            }
        }, true);

        onSessionUsage(analyticsSessions, true); // This is necessary to forcibly print the very first event
    }

    private void onSessionUsage(AnalyticsSessions analyticsSessions, boolean force) {
        if (analyticsSessions.getIdleUsageTime() > 600000) { // 10 min
            analyticsSessions.makeNew();
            logSessionUsageEvent(analyticsSessions, true);
        } else {
            logSessionUsageEvent(analyticsSessions, force);
            analyticsSessions.updateUsageTime();
        }
    }

    private void onWindowClose(AnalyticsSessions analyticsSessions) {
        if (analyticsSessions.getIdleUsageTime() <= 60000) { // 1 min
            logSessionUsageEvent(analyticsSessions, true);
            analyticsSessions.updateUsageTime();
        }
    }

    private void logSessionUsageEvent(AnalyticsSessions analyticsSessions, boolean force) {
        if (force || analyticsSessions.getIdleLogTime() > 60000) { // 1 min, don't log frequently than once per minute
            Map<String, String> parameters = new HashMap<>();
            parameters.put("SESSION-ID", analyticsSessions.getId());

            analyticsEventLoggerExt.logEvent("session-usage", parameters);

            UsersWorkspace workspace = appContext.getWorkspace();

            if (workspace != null && workspace.isTemporary()) {
                analyticsEventLoggerExt.logEvent("session-usage", parameters);
            }

            analyticsSessions.updateLogTime();
        }
    }

    /**
     * Handles any of initialization errors.
     * Tries to call predefined IDE.eventHandlers.ideInitializationFailed function.
     *
     * @param reason
     *         failure encountered
     */
    private native void initializationFailed(String reason) /*-{
        try {
            $wnd.IDE.eventHandlers.initializationFailed(reason);
        } catch (e) {
            console.log(e.message);
        }
    }-*/;
}
