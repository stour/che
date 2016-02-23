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

/**
 * Defines a directive for formatting output
 * @author Ann Shumilova
 */
export class CheFormatOutput {

  /**
   * Default constructor that is using resource
   * @ngInject for Dependency injection
   */
  constructor(jsonOutputColors) {
    this.restrict = 'A';
    this.jsonOutputColors = jsonOutputColors;
  }

  /**
   * Keep reference to the model controller
   */
  link($scope, element, attr) {
    $scope.$watch(attr.ngModel, (value) => {
      var lines = value.split('\n');
      lines.forEach((line) => {

      });
    });
  }
}
