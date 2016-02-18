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
package org.eclipse.che.ide.ext.git.client.outputconsole;

import com.google.gwt.user.client.ui.AcceptsOneWidget;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.che.ide.api.app.AppContext;
import org.eclipse.che.ide.ext.git.client.GitLocalizationConstant;
import org.eclipse.che.ide.ext.git.client.GitResources;
import org.vectomatic.dom.svg.ui.SVGResource;

import java.util.ArrayList;
import java.util.List;

/**
 * Git output View Part.
 *
 * @author Vitaly Parfonov
 */

public class GitOutputConsolePresenter implements GitOutputPartView.ActionDelegate, GitOutputConsole {
    private final GitOutputPartView view;
    private final GitResources      resources;
    private final String            title;

    private final List<ConsoleOutputListener> outputListeners = new ArrayList<>();

    /** Construct empty Part */
    @Inject
    public GitOutputConsolePresenter(GitOutputPartView view,
                                     GitResources resources,
                                     AppContext appContext,
                                     GitLocalizationConstant locale,
                                     @Assisted String title) {
        this.view = view;
        this.view.setDelegate(this);

        this.title = title;
        this.resources = resources;

        String projectName = appContext.getCurrentProject().getRootProject().getName();

        view.print(locale.consoleProjectName(projectName) + "\n");
    }

    /** {@inheritDoc} */
    @Override
    public void go(AcceptsOneWidget container) {
        container.setWidget(view);
    }

    /**
     * Print text on console.
     *
     * @param text
     *         text that need to be shown
     */
    public void print(String text) {
        String[] lines = text.split("\n");
        for (String line : lines) {
            view.print(line.isEmpty() ? " " : line);
        }
        view.scrollBottom();

        for (ConsoleOutputListener outputListener : outputListeners) {
            outputListener.onConsoleOutput(this);
        }
    }

    /** {@inheritDoc} */
    public void clear() {
        view.clear();
    }

    /** {@inheritDoc} */
    @Override
    public void onClearClicked() {
        clear();
    }

    @Override
    public void onScrollClicked() {
        view.scrollBottom();
    }

    public void printInfo(String text) {
        view.printInfo(text);
        view.scrollBottom();

        for (ConsoleOutputListener outputListener : outputListeners) {
            outputListener.onConsoleOutput(this);
        }
    }

    public void printWarn(String text) {
        view.printWarn(text);
        view.scrollBottom();

        for (ConsoleOutputListener outputListener : outputListeners) {
            outputListener.onConsoleOutput(this);
        }
    }

    public void printError(String text) {
        view.printError(text);
        view.scrollBottom();

        for (ConsoleOutputListener outputListener : outputListeners) {
            outputListener.onConsoleOutput(this);
        }
    }

    @Override
    public String getTitle() {
        return title;
    }

    @Override
    public SVGResource getTitleIcon() {
        return resources.gitOutput();
    }

    @Override
    public boolean isFinished() {
        return true;
    }

    @Override
    public void stop() {
    }

    @Override
    public void close() {
        outputListeners.clear();
    }

    @Override
    public void addOutputListener(ConsoleOutputListener listener) {
        outputListeners.add(listener);
    }

}
