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
package org.eclipse.che.ide.ext.java.jdi.client.actions;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.che.ide.api.action.Action;
import org.eclipse.che.ide.api.action.ActionEvent;
import org.eclipse.che.ide.debug.BreakpointManager;
import org.eclipse.che.ide.debug.DebuggerState;
import org.eclipse.che.ide.ext.java.jdi.client.JavaRuntimeLocalizationConstant;
import org.eclipse.che.ide.ext.java.jdi.client.JavaRuntimeResources;
import org.eclipse.che.ide.ext.java.jdi.client.debug.DebuggerPresenter;

/**
 * Action which allows evaluate expression with debugger
 *
 * @author Mykola Morhun
 */
@Singleton
public class EvaluateExpressionAction extends Action {

    private final DebuggerPresenter presenter;
    private final BreakpointManager breakpointManager;

    @Inject
    public EvaluateExpressionAction(DebuggerPresenter presenter,
                                    JavaRuntimeLocalizationConstant locale,
                                    JavaRuntimeResources resources,
                                    BreakpointManager breakpointManager) {
        super(locale.evaluateExpression(), locale.evaluateExpressionDescription(), null, resources.evaluate());

        this.presenter = presenter;
        this.breakpointManager = breakpointManager;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        presenter.onEvaluateExpressionButtonClicked();
    }

    @Override
    public void update(ActionEvent e) {
        e.getPresentation().setEnabled(presenter.getDebuggerState() == DebuggerState.CONNECTED &&
                breakpointManager.getCurrentBreakpoint() != null);
    }

}
