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

import org.eclipse.che.ide.api.action.Action;
import org.eclipse.che.ide.api.action.ActionEvent;
import org.eclipse.che.ide.debug.BreakpointManager;
import org.eclipse.che.ide.debug.Debugger;
import org.eclipse.che.ide.debug.DebuggerManager;
import org.eclipse.che.ide.debug.DebuggerState;
import org.eclipse.che.ide.ext.java.jdi.client.JavaRuntimeLocalizationConstant;
import org.eclipse.che.ide.ext.java.jdi.client.JavaRuntimeResources;

/**
 * Action which allows step with return in debugger session
 *
 * @author Mykola Morhun
 */
public class StepOutAction extends Action {

    private final DebuggerManager debuggerManager;
    private final BreakpointManager breakpointManager;

    @Inject
    public StepOutAction(DebuggerManager debuggerManager,
                         JavaRuntimeLocalizationConstant locale,
                         JavaRuntimeResources resources, BreakpointManager breakpointManager) {
        super(locale.stepOut(), locale.stepOutDescription(), null, resources.stepOutButton());

        this.debuggerManager = debuggerManager;
        this.breakpointManager = breakpointManager;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Debugger debugger = debuggerManager.getDebugger();
        if (debugger != null) {
            debugger.stepOut();
        }
    }

    @Override
    public void update(ActionEvent e) {
        Debugger debugger = debuggerManager.getDebugger();

        e.getPresentation().setEnabled(debugger != null &&
                debugger.getDebuggerState() == DebuggerState.CONNECTED &&
                breakpointManager.getCurrentBreakpoint() != null);
    }

}
