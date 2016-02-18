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
 * This class is handling the call to remote API
 * @author Florent Benoit
 */
export class CheRemoteWorkspace {

  /**
   * Default constructor that is using resource
   */
  constructor($resource, $q, cheWebsocket, authData) {
    this.$resource = $resource;
    this.$q = $q;
    this.cheWebsocket = cheWebsocket;
    this.authData = authData;

    // remote call
    this.remoteWorkspaceAPI = this.$resource('', {}, {
        create: {method: 'POST', url: authData.url + '/api/workspace/config?account=:accountId&token=' + authData.token},
        startWorkspace: {method: 'POST', url : authData.url + '/api/workspace/:workspaceId/runtime?environment=:envName&token=' + authData.token}
      }
    );
  }

  createWorkspaceFromConfig(accountId, workspaceConfig) {
    return this.remoteWorkspaceAPI.create({accountId : accountId}, workspaceConfig).$promise;
  }

  /**
   * Starts the given workspace by specifying the ID and the environment name
   * @param workspaceId the workspace ID
   * @param envName the name of the environment
   * @returns {*} promise
   */
  startWorkspace(remoteWsURL, workspaceId, envName) {

    let deferred = this.$q.defer();
    let deferredPromise = deferred.promise;

    let bus = this.cheWebsocket.getRemoteBus(remoteWsURL, workspaceId);
    // subscribe to workspace events
    bus.subscribe('workspace:' + workspaceId, (message) => {
      if (message.eventType === 'RUNNING' && message.workspaceId === workspaceId) {
        deferred.resolve();
      }
    });

    let promise = this.remoteWorkspaceAPI.startWorkspace({workspaceId: workspaceId, envName : envName}, {}).$promise;

    return deferredPromise;
  }



}
