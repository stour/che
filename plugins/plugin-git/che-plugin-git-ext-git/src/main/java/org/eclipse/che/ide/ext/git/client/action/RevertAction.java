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
import org.eclipse.che.ide.project.node.ResourceBasedNode;
import org.eclipse.che.ide.rest.AsyncRequestCallback;
import org.eclipse.che.ide.rest.StringUnmarshaller;
import org.eclipse.che.ide.ui.dialogs.ConfirmCallback;
import org.eclipse.che.ide.ui.dialogs.DialogFactory;

import javax.validation.constraints.NotNull;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.eclipse.che.api.git.shared.DiffRequest.DiffType.NAME_STATUS;
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

    private final static String REVISION = "HEAD";

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
        ProjectConfigDto project = appContext.getCurrentProject().getRootProject();
        String pattern;
        final String path;

        Selection<ResourceBasedNode<?>> selection = getExplorerSelection();

        if (selection == null || selection.getHeadElement() == null) {
            path = project.getPath();
        } else {
            path = ((HasStorablePath)selection.getHeadElement()).getStorablePath();
        }

        pattern = path.replaceFirst(project.getPath(), "");
        pattern = (pattern.startsWith("/")) ? pattern.replaceFirst("/", "") : pattern;

        CheckoutRequest checkoutRequest = dtoFactory.createDto(CheckoutRequest.class)
                                                    .withFiles(Collections.singletonList(pattern))
                                                    .withRevision(REVISION);
        gitService.checkout(appContext.getWorkspaceId(), project, checkoutRequest, new AsyncRequestCallback<String>() {
            @Override
            protected void onSuccess(String result) {
                eventBus.fireEvent(new FileContentUpdateEvent(path));
            }

            @Override
            protected void onFailure(Throwable exception) {
            }
        });
    }

    private Selection<ResourceBasedNode<?>> getExplorerSelection() {
        final Selection<ResourceBasedNode<?>> selection = (Selection<ResourceBasedNode<?>>)projectExplorer.getSelection();
        if (selection == null || selection.isEmpty() || selection.getHeadElement() instanceof HasStorablePath) {
            return selection;
        } else {
            return null;
        }
    }

    @Override
    public void updateInPerspective(@NotNull ActionEvent event) {
        event.getPresentation().setVisible(getActiveProject() != null);
        event.getPresentation().setEnabled(isGitRepository() && compareSupported());
    }

    private boolean compareSupported() {
        Selection selection = projectExplorer.getSelection();
        return selection.isSingleSelection() && selection.getHeadElement() instanceof FileReferenceNode;
    }
}
