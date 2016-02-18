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
package org.eclipse.che.ide.ext.java.jdi.client;

import org.eclipse.che.api.promises.client.Operation;
import org.eclipse.che.api.promises.client.Promise;
import org.eclipse.che.api.promises.client.PromiseError;
import org.eclipse.che.ide.debug.Debugger;
import org.eclipse.che.ide.debug.DebuggerManager;
import org.eclipse.che.ide.ext.java.jdi.client.debug.expression.EvaluateExpressionPresenter;
import org.eclipse.che.ide.ext.java.jdi.client.debug.expression.EvaluateExpressionView;
import org.junit.Test;
import org.mockito.*;

import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

/**
 * Testing {@link org.eclipse.che.ide.ext.java.jdi.client.debug.expression.EvaluateExpressionPresenter} functionality.
 *
 * @author Artem Zatsarynnyi
 */
public class EvaluateExpressionTest extends BaseTest {
    private static final String EXPRESSION        = "expression";
    private static final String EMPTY_EXPRESSION  = "";
    private static final String EVALUATION_RESULT = "result";
    private static final String FAIL_REASON       = "reason";
    @Mock
    private EvaluateExpressionView      view;
    @Mock
    private DebuggerManager debuggerManager;
    @Mock
    private Debugger debugger;
    @Mock
    private Promise<String> promise;
    @Mock
    private PromiseError promiseError;

    @Captor
    private ArgumentCaptor<Operation<PromiseError>> errorCaptor;
    @InjectMocks
    private EvaluateExpressionPresenter presenter;

    @Test
    public void shouldShowDialog() throws Exception {
        presenter.showDialog();

        verify(view).setResult(eq(EMPTY_EXPRESSION));
        verify(view).setEnableEvaluateButton(eq(DISABLE_BUTTON));
        verify(view).showDialog();
        verify(view).focusInExpressionField();
    }

    @Test
    public void shouldCloseDialog() throws Exception {
        presenter.closeDialog();

        verify(view).close();
    }

    @Test
    public void shouldCloseDialogOnCancelClicked() throws Exception {
        presenter.onCancelClicked();

        verify(view).close();
    }

    @Test
    public void shouldDisableEvaluateButtonIfNoExpression() throws Exception {
        when(view.getExpression()).thenReturn(EMPTY_EXPRESSION);

        presenter.onExpressionValueChanged();

        verify(view).setEnableEvaluateButton(eq(DISABLE_BUTTON));
    }

    @Test
    public void shouldEnableEvaluateButtonIfExpressionNotEmpty() throws Exception {
        when(view.getExpression()).thenReturn(EXPRESSION);

        presenter.onExpressionValueChanged();

        verify(view).setEnableEvaluateButton(eq(!DISABLE_BUTTON));
    }

    @Test
    public void testEvaluateExpressionRequestIsSuccessful() throws Exception {
        when(debugger.evaluateExpression(anyString())).thenReturn(promise);
        when(view.getExpression()).thenReturn(EXPRESSION);
        when(promise.then(any(Operation.class))).thenReturn(promise);

        when(debuggerManager.getDebugger()).thenReturn(debugger);

        presenter.showDialog();
        presenter.onEvaluateClicked();

        verify(view, atLeastOnce()).setEnableEvaluateButton(eq(DISABLE_BUTTON));
        verify(debugger).evaluateExpression(eq(EXPRESSION));
    }

    @Test
    public void testEvaluateExpressionRequestIsFailed() throws Exception {
        when(view.getExpression()).thenReturn(EXPRESSION);
        when(debugger.evaluateExpression(view.getExpression())).thenReturn(promise);
        when(promise.then((Operation) anyObject())).thenReturn(promise);
        when(promise.catchError(Matchers.<Operation<PromiseError>> anyObject())).thenReturn(promise);
        when(debuggerManager.getDebugger()).thenReturn(debugger);
        when(promiseError.getMessage()).thenReturn(FAIL_REASON);

        presenter.showDialog();
        presenter.onEvaluateClicked();

        verify(view, atLeastOnce()).setEnableEvaluateButton(eq(DISABLE_BUTTON));
        verify(debugger).evaluateExpression(eq(EXPRESSION));
        verify(promise).catchError(errorCaptor.capture());

        errorCaptor.getValue().apply(promiseError);

        verify(view).setEnableEvaluateButton(eq(!DISABLE_BUTTON));
        verify(constants).evaluateExpressionFailed(FAIL_REASON);
    }

}
