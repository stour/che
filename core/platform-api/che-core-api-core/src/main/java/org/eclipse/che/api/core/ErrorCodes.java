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
package org.eclipse.che.api.core;

/**
 * Error codes that are used in exceptions.
 *
 * @author Igor Vinokur
 */
public class ErrorCodes {
    private ErrorCodes() {
    }

    public static final int NO_COMMITTER_NAME_OR_EMAIL_DEFINED = 15216;
    public static final int UNABLE_GET_PRIVATE_SSH_KEY         = 32068;
}
