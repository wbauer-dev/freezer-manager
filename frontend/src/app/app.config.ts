import {
  ApplicationConfig,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import {
  provideRouter,
  withComponentInputBinding,
  withRouterConfig,
  withViewTransitions,
} from '@angular/router';
import { MatIconRegistry } from '@angular/material/icon';
import {
  authInterceptor,
  provideAuth,
  withAppInitializerAuthCheck,
} from 'angular-auth-oidc-client';

import { routes } from './app.routes';
import { authConfig } from './core/auth/auth.config';
import { SessionExpiry, sessionExpiryInterceptor } from './core/auth/session-expiry';
import { acceptLanguageInterceptor } from './core/i18n/accept-language.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    // Route parameters become component inputs; child pages also see the parent's :householdId.
    provideRouter(
      routes,
      withComponentInputBinding(),
      withRouterConfig({ paramsInheritanceStrategy: 'always' }),
      // Pages cross-fade; food avatars morph from the list into the item page.
      withViewTransitions({ skipInitialTransition: true }),
    ),
    provideHttpClient(
      withInterceptors([authInterceptor(), acceptLanguageInterceptor, sessionExpiryInterceptor]),
    ),
    provideAuth(authConfig, withAppInitializerAuthCheck()),
    // An ended session (e.g. after a phone slept) leads to the login instead of failing API calls.
    provideAppInitializer(() => {
      inject(SessionExpiry);
    }),
    provideAppInitializer(() => {
      inject(MatIconRegistry).setDefaultFontSetClass('material-symbols-rounded');
    }),
  ],
};
