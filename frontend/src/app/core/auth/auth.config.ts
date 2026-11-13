import { LogLevel, PassedInitialConfig } from 'angular-auth-oidc-client';

import { preferredLanguage } from '../i18n/languages';

/**
 * Login with the local Keycloak (see docker-compose.yml): authorization code flow with PKCE.
 * The access token is attached to all calls to the backend API.
 */
export const authConfig: PassedInitialConfig = {
  config: {
    authority: 'http://localhost:8081/realms/freezer',
    clientId: 'freezer-frontend',
    // A route of its own, so returning from the login never races with the app's start page.
    redirectUrl: `${window.location.origin}/auth/callback`,
    postLogoutRedirectUri: window.location.origin,
    scope: 'openid profile email',
    responseType: 'code',
    silentRenew: true,
    useRefreshToken: true,
    // Keycloak issues refresh tokens for normal sessions; offline tokens (offline_access) are not needed.
    disableRefreshTokenOfflineAccessScopeWarning: true,
    renewTimeBeforeTokenExpiresInSeconds: 30,
    secureRoutes: ['/api/'],
    // Keycloak shows the login page in the app's language.
    customParamsAuthRequest: { ui_locales: preferredLanguage() },
    logLevel: LogLevel.Warn,
  },
};
