/*
 * Copyright (c) 2015-2016 Codenvy, S.A.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Codenvy, S.A. - initial API and implementation
 */
'use strict';

export class CheOutputColorsConfig {

  constructor(register) {
    // Register this factory
    register.app.constant('jsonOutputColors', JSON.stringify([
  {
    'type': 'docker',
    'color': 'blue'
  },
  {
    'type': 'info',
    'color': 'green'
  },
  {
    'type': 'error',
    'color': 'red'
  },
  {
    'type': 'warning',
    'color': 'yellow'
  },
  {
    'type': 'stdout',
    'color': 'black'
  },
  {
    'type': 'stderr',
    'color': 'brown'
  }
]
));
  }
}
