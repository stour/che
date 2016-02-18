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
package org.eclipse.che.ide.ext.git.client.action;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.web.bindery.event.shared.EventBus;

import org.eclipse.che.api.git.gwt.client.GitServiceClient;
import org.eclipse.che.api.git.shared.CheckoutRequest;
import org.eclipse.che.api.vfs.server.observation.UpdateContentEvent;
import org.eclipse.che.api.workspace.shared.dto.ProjectConfigDto;
import org.eclipse.che.ide.api.action.ActionEvent;
import org.eclipse.che.ide.api.app.AppContext;
import org.eclipse.che.ide.api.event.FileContentUpdateEvent;
import org.eclipse.che.ide.api.notification.NotificationManager;
import org.eclipse.che.ide.api.project.node.HasStorablePath;
import org.eclipse.che.ide.api.selection.Selection;
import org.eclipse.che.ide.dto.DtoFactory;
import org.eclipse.che.ide.ext.git.client.GitLocalizationConstant;
import org.eclipse.che.ide.ext.git.client.compare.ComparePresenter;
import org.eclipse.che.ide.ext.git.client.compare.changedList.ChangedListPresenter;
import org.eclipse.che.ide.part.explorer.project.ProjectExplorerPresenter;
import org.eclipse.che.ide.project.node.FileReferenceNode;
import org.eclipse.che.ide.project.node.FolderReferenceNode;
import org.eclipse.che.ide.project.node.ResourceBasedNode;
import org.eclipse.che.ide.rest.AsyncRequestCallback;
import org.eclipse.che.ide.rest.StringUnmarshaller;
import org.eclipse.che.ide.ui.dialogs.ConfirmCallback;
import org.eclipse.che.ide.ui.dialogs.DialogFactory;

import javax.validation.constraints.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.eclipse.che.api.git.shared.DiffRequest.DiffType.NAME_STATUS;;
import static org.eclipse.che.ide.api.notification.StatusNotification.Status.FAIL;

/**
 * Action for comparing with latest repository version
 *
 * @author Igor Vinokur
 */
@Singleton
public class RevertAction extends GitAction {
    private final ComparePresenter        comparePresenter;
    private final EventBus                eventBus;
    private final ChangedListPresenter    changedListPresenter;
    private final DialogFactory           dialogFactory;
    private final NotificationManager     notificationManager;
    private final GitServiceClient        gitService;
    private final GitLocalizationConstant locale;
    private final DtoFactory              dtoFactory;

    private ProjectConfigDto              project;

    private String                        projectPath;

    private final static String           REVISION = "HEAD";

    @Inject
    public RevertAction(ComparePresenter presenter,
                        EventBus eventBus,
                        ChangedListPresenter changedListPresenter,
                        AppContext appContext,
                        DialogFactory dialogFactory,
                        DtoFactory dtoFactory,
                        NotificationManager notificationManager,
                        GitServiceClient gitService,
                        GitLocalizationConstant constant,
                        ProjectExplorerPresenter projectExplorer) {
        super("Revert", "Revert", appContext, projectExplorer);
        this.comparePresenter = presenter;
        this.eventBus = eventBus;
        this.changedListPresenter = changedListPresenter;
        this.dialogFactory = dialogFactory;
        this.dtoFactory = dtoFactory;
        this.notificationManager = notificationManager;
        this.gitService = gitService;
        this.locale = constant;
    }

    /** {@inheritDoc} */
    @Override
    public void actionPerformed(ActionEvent e) {
        project = appContext.getCurrentProject().getRootProject();

        Selection<ResourceBasedNode< ? >> selection = getExplorerSelection();

        if (selection == null || selection.getHeadElement() == null) {
            return;
        } else {
            if (selection.isMultiSelection()) {
                List<String> paths = new ArrayList<>();
                for (ResourceBasedNode< ? > node : selection.getAllElements()) {
                    paths.add(normalizePath(((HasStorablePath)node).getStorablePath()));
                }
                checkout(paths);
            } else {
                if (selection.getHeadElement() instanceof FolderReferenceNode) {
                    gitService.diff(appContext.getWorkspaceId(), project,
                                    Collections.singletonList(normalizePath(((HasStorablePath)selection.getHeadElement()).getStorablePath())),
                                    NAME_STATUS, false, 0, REVISION, false, new AsyncRequestCallback<String>(new StringUnmarshaller()) {
                                        @Override
                                        protected void onSuccess(String result) {
                                            if (result.isEmpty()) {
                                                dialogFactory.createMessageDialog(locale.compareMessageIdenticalContentTitle(),
                                                                                  locale.compareMessageIdenticalContentText(),
                                                                                  new ConfirmCallback() {
                                                                                      @Override
                                                                                      public void accepted() {
                                                                                          //Do nothing
                                                                                      }
                                                                                  }).show();
                                            } else {
                                                List<String> changedFiles = Arrays.asList(result.split("\n"));
                                                checkout(changedFiles);
                                            }
                                        }

                                        @Override
                                        protected void onFailure(Throwable exception) {
                                            notificationManager.notify(locale.diffFailed(), FAIL, false);
                                        }
                                    });
                } else {
                    checkout(Collections.singletonList(normalizePath(((HasStorablePath)selection.getHeadElement()).getStorablePath())));
                }
            }
        }


    }

    private void checkout(final List<String> paths) {
        CheckoutRequest checkoutRequest = dtoFactory.createDto(CheckoutRequest.class).withFiles(paths).withRevision(REVISION);
        gitService.checkout(appContext.getWorkspaceId(), project, checkoutRequest, new AsyncRequestCallback<String>() {
            @Override
            protected void onSuccess(String result) {
                String projectPath = project.getPath();
                for (String path : paths) {
                    eventBus.fireEvent(new FileContentUpdateEvent(projectPath + "/" + path));
                }
            }

            @Override
            protected void onFailure(Throwable exception) {
                String s = exception.getMessage();
            }
        });
    }

    private Selection<ResourceBasedNode< ? >> getExplorerSelection() {
        final Selection<ResourceBasedNode< ? >> selection = (Selection<ResourceBasedNode< ? >>)projectExplorer.getSelection();
        if (selection == null || selection.isEmpty() || selection.getHeadElement() instanceof HasStorablePath) {
            return selection;
        } else {
            return null;
        }
    }

    private String normalizePath(final String path) {
        final String projectPath = project.getPath();

        String pattern = path;
        if (path.startsWith(projectPath)) {
            pattern = pattern.replaceFirst(projectPath, "");
        }
        if (pattern.startsWith("/")) {
            pattern = pattern.replaceFirst("/", "");
        }
        return pattern;
    }
}
