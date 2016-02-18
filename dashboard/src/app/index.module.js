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

import {Register} from '../components/utils/register';

import {ComponentsConfig} from '../components/components-config';

import {AdminsConfig} from './admin/admin-config';
import {CheColorsConfig} from './colors/che-color.constant';
import {CheCountriesConfig} from './countries/che-countries.constant';
import {DashboardConfig} from './dashboard/dashboard-config';
// switch to a config
import {DemoComponentsCtrl} from './demo-components/demo-components.controller';
import {IdeConfig} from './ide/ide-config';
import {NavbarConfig} from './navbar/navbar-config';
import {ProjectsConfig} from './projects/projects-config';
import {ProxySettingsConfig} from './proxy/proxy-settings.constant';
import {WorkspacesConfig} from './workspaces/workspaces-config';


// init module
let initModule = angular.module('userDashboard', ['ngAnimate', 'ngCookies', 'ngTouch', 'ngSanitize', 'ngResource', 'ngRoute',
  'angular-websocket', 'ui.bootstrap', 'ui.codemirror', 'ngMaterial', 'ngMessages', 'angularMoment', 'angular.filter',
  'ngDropdowns', 'ngLodash', 'angularCharts', 'ngClipboard', 'uuid4', 'angularFileUpload']);



// add a global resolve flag on all routes (user needs to be resolved first)
initModule.config(['$routeProvider', function ($routeProvider) {
  $routeProvider.accessWhen = function(path, route) {
    route.resolve || (route.resolve = {});
    route.resolve.app = ['cheBranding', '$q', 'cheProfile', 'cheUser', function (cheBranding, $q, cheProfile, cheUser) {
      var deferred = $q.defer();

      cheUser.fetchUser().then(() => {
        let profilePreferences = cheProfile.getPreferences();
        if (profilePreferences && profilePreferences.$resolved) {
          deferred.resolve();
        } else {
          profilePreferences.$promise.then(() => {
            deferred.resolve();
          }, (error) => {
            deferred.reject(error);
          });
        }
      }, (error) => {
        deferred.reject(error);
      });

      return deferred.promise;
    }];

    return $routeProvider.when(path, route);
  };

  $routeProvider.accessOtherWise = function(route) {
    route.resolve || (route.resolve = {});
    route.resolve.app = ['$q', 'cheProfile', 'cheUser', function ($q, cheProfile, cheUser) {
      var deferred = $q.defer();

      cheUser.fetchUser().then(() => {
        let profilePreferences = cheProfile.getPreferences();
        if (profilePreferences && profilePreferences.$resolved) {
          deferred.resolve();
        } else {
          profilePreferences.$promise.then(() => {
            deferred.resolve();
          }, (error) => {
            deferred.reject(error);
          });
        }
      }, (error) => {
        deferred.reject(error);
      });

      return deferred.promise;
    }];
    return $routeProvider.otherwise(route);
  };


}]);

var DEV = true;


// config routes
initModule.config(['$routeProvider', function ($routeProvider) {
  // add demo page
  if (DEV) {
    $routeProvider.accessWhen('/demo-components', {
      templateUrl: 'app/demo-components/demo-components.html',
      controller: 'DemoComponentsCtrl',
      controllerAs: 'demoComponentsCtrl'
    });
  }

}]);



/**
 * Setup route redirect module
 */
initModule.run(['$rootScope', '$location', 'routingRedirect', 'cheUser', '$timeout', 'ideIFrameSvc', 'cheIdeFetcher', 'routeHistory',
  function ($rootScope, $location, routingRedirect, cheUser, $timeout, ideIFrameSvc, cheIdeFetcher, routeHistory) {

    $rootScope.hideLoader = false;
    $rootScope.waitingLoaded = false;
    $rootScope.showIDE = false;

    // here only to create instances of these components
    cheIdeFetcher;
    routeHistory;

    $rootScope.$on('$viewContentLoaded', function() {
      ideIFrameSvc.addIFrame();

      $timeout(function() {
        if (!$rootScope.hideLoader) {
          if (!$rootScope.wantTokeepLoader) {
            $rootScope.hideLoader = true;
          } else {
            $rootScope.hideLoader = false;
          }
        }
        $rootScope.waitingLoaded = true;
      }, 1000);
    });

    $rootScope.$on('$routeChangeStart', (event, next)=> {
      if (DEV) {
        console.log('$routeChangeStart event with route', next);
      }
    });

    // When a route is about to change, notify the routing redirect node
    $rootScope.$on('$routeChangeSuccess', (event, next) => {
      if (next.resolve) {
        if (DEV) {
          console.log('$routeChangeSuccess event with route', next);
        }// check routes
        routingRedirect.check(event, next);
      }
    });

    $rootScope.$on('$routeChangeError', () => {
      $location.path('/');
    });
  }]);



// add interceptors
initModule.factory('ETagInterceptor', function ($window, $cookies, $q) {

  var etagMap = {};

  return {
    request: function(config) {
      // add IfNoneMatch request on the che api if there is an existing eTag
      if ('GET' === config.method) {
        if (config.url.indexOf('/api') === 0) {
          let eTagURI = etagMap[config.url];
          if (eTagURI) {
            config.headers = config.headers || {};
            angular.extend(config.headers, {'If-None-Match': eTagURI});
          }
        }
      }
      return config || $q.when(config);
    },
    response: function(response) {

      // if response is ok, keep ETag
      if ('GET' === response.config.method) {
        if (response.status === 200) {
          var responseEtag = response.headers().etag;
          if (responseEtag) {
            if (response.config.url.indexOf('/api') === 0) {

              etagMap[response.config.url] =  responseEtag;
            }
          }
        }

      }
      return response || $q.when(response);
    }
  };
});




initModule.config(function($mdThemingProvider, jsonColors) {

  var cheColors = angular.fromJson(jsonColors);
  var getColor = function(key) {
    var color = cheColors[key];
    if (!color) {
      // return a flashy red color if color is undefined
      console.log('error, the color' + key + 'is undefined');
      return '#ff0000';
    }
    if (color.indexOf('$') === 0) {
      color = getColor(color);
    }
    return color;

  };


  var cheMap = $mdThemingProvider.extendPalette('indigo', {
    '500': getColor('$dark-menu-color'),
    '300' : 'D0D0D0'
  });
  $mdThemingProvider.definePalette('che', cheMap);

  var cheDangerMap = $mdThemingProvider.extendPalette('red', {
  });
  $mdThemingProvider.definePalette('cheDanger', cheDangerMap);

  var cheWarningMap = $mdThemingProvider.extendPalette('orange', {
    'contrastDefaultColor': 'light'
  });
  $mdThemingProvider.definePalette('cheWarning', cheWarningMap);

  var cheDefaultMap = $mdThemingProvider.extendPalette('blue', {
    'A400'  : getColor('$che-medium-blue-color')
  });
  $mdThemingProvider.definePalette('cheDefault', cheDefaultMap);

  var cheNoticeMap = $mdThemingProvider.extendPalette('blue', {
    'A400'  : getColor('$mouse-gray-color')
  });
  $mdThemingProvider.definePalette('cheNotice', cheNoticeMap);




  var cheAccentMap = $mdThemingProvider.extendPalette('blue', {
    '700' : getColor('$che-medium-blue-color'),
    'A400': getColor('$che-medium-blue-color'),
    'A200': getColor('$che-medium-blue-color'),
    'contrastDefaultColor': 'light'
  });
  $mdThemingProvider.definePalette('cheAccent', cheAccentMap);


  var cheNavyPalette = $mdThemingProvider.extendPalette('purple', {
    '500' : getColor('$che-navy-color'),
    'contrastDefaultColor': 'light'
  });
  $mdThemingProvider.definePalette('cheNavyPalette', cheNavyPalette);


  var toolbarPrimaryPalette = $mdThemingProvider.extendPalette('purple', {
    '500' : getColor('$che-white-color'),
    'contrastDefaultColor': 'dark'
  });
  $mdThemingProvider.definePalette('toolbarPrimaryPalette', toolbarPrimaryPalette);

  var toolbarAccentPalette = $mdThemingProvider.extendPalette('purple', {
    'A200' : 'EF6C00',
    '700' : 'E65100',
    'contrastDefaultColor': 'light'
  });
  $mdThemingProvider.definePalette('toolbarAccentPalette', toolbarAccentPalette);

  var cheGreyPalette = $mdThemingProvider.extendPalette('grey', {
    'A100' : 'efefef',
    'contrastDefaultColor': 'light'
  });
  $mdThemingProvider.definePalette('cheGrey', cheGreyPalette);

  $mdThemingProvider.theme('danger')
    .primaryPalette('che')
    .accentPalette('cheDanger')
    .backgroundPalette('grey');

  $mdThemingProvider.theme('warning')
    .primaryPalette('che')
    .accentPalette('cheWarning')
    .backgroundPalette('grey');


  $mdThemingProvider.theme('chedefault')
    .primaryPalette('che')
    .accentPalette('cheDefault')
    .backgroundPalette('grey');


  $mdThemingProvider.theme('chenotice')
    .primaryPalette('che')
    .accentPalette('cheNotice')
    .backgroundPalette('grey');

  $mdThemingProvider.theme('default')
    .primaryPalette('che')
    .accentPalette('cheAccent')
    .backgroundPalette('grey');

  $mdThemingProvider.theme('toolbar-theme')
    .primaryPalette('toolbarPrimaryPalette')
    .accentPalette('toolbarAccentPalette');

  $mdThemingProvider.theme('factory-theme')
    .primaryPalette('light-blue')
    .accentPalette('pink')
    .warnPalette('red')
    .backgroundPalette('purple');

  $mdThemingProvider.theme('onboarding-theme')
    .primaryPalette('cheNavyPalette')
    .accentPalette('pink')
    .warnPalette('red')
    .backgroundPalette('purple');

  $mdThemingProvider.theme('dashboard-theme')
    .primaryPalette('cheNavyPalette')
    .accentPalette('pink')
    .warnPalette('red')
    .backgroundPalette('purple');

  $mdThemingProvider.theme('maincontent-theme')
    .primaryPalette('che')
    .accentPalette('cheAccent')
    .backgroundPalette('cheGrey');


});

initModule.constant('userDashboardConfig', {
  developmentMode: DEV
});

initModule.config(['$routeProvider', '$locationProvider', '$httpProvider', function ($routeProvider, $locationProvider, $httpProvider) {
  // Add the ETag interceptor for Che API
  $httpProvider.interceptors.push('ETagInterceptor');
}]);


var instanceRegister = new Register(initModule);

new ProxySettingsConfig(instanceRegister);
new CheColorsConfig(instanceRegister);
new CheCountriesConfig(instanceRegister);
new ComponentsConfig(instanceRegister);
new AdminsConfig(instanceRegister);
new IdeConfig(instanceRegister);

new NavbarConfig(instanceRegister);
new ProjectsConfig(instanceRegister);
new WorkspacesConfig(instanceRegister);
new DashboardConfig(instanceRegister);

