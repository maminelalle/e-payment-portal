import { registerLocaleData } from '@angular/common';
import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import localeFr from '@angular/common/locales/fr';
import {
  ApplicationConfig,
  inject,
  LOCALE_ID,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { authInterceptor, provideAuth } from 'angular-auth-oidc-client';
import { providePrimeNG } from 'primeng/config';

import { routes } from './app.routes';
import { apiErrorInterceptor } from './core/api-error.interceptor';
import { authConfig } from './core/auth.config';
import { Session } from './core/session';
import { EPaymentPreset } from './theme';

registerLocaleData(localeFr);

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(withFetch(), withInterceptors([authInterceptor(), apiErrorInterceptor])),
    provideAuth({ config: authConfig }),
    provideAppInitializer(() => inject(Session).init()),
    { provide: LOCALE_ID, useValue: 'fr' },
    providePrimeNG({
      theme: { preset: EPaymentPreset, options: { darkModeSelector: '.dark' } },
    }),
  ],
};
