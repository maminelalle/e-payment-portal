import { defineConfig, devices } from '@playwright/test';

/**
 * Tests de bout en bout sur un portail déjà démarré.
 *   Développement : E2E_BASE_URL=http://localhost (défaut), comptes de démonstration, MFA admin désactivée
 *                   (infra/keycloak/mfa-admin.sh off).
 *   Recette       : E2E_BASE_URL=https://recette… avec E2E_ADMIN_* et E2E_ABONNE_* (comptes de test).
 * Lancer : npm run e2e
 */
export default defineConfig({
  testDir: './e2e',
  timeout: 60_000,
  expect: { timeout: 10_000 },
  fullyParallel: false,
  workers: 1,
  retries: process.env['CI'] ? 1 : 0,
  reporter: [['list'], ['html', { open: 'never', outputFolder: 'e2e-report' }]],
  use: {
    baseURL: process.env['E2E_BASE_URL'] ?? 'http://localhost',
    ignoreHTTPSErrors: true,
    locale: 'fr-FR',
    timezoneId: 'Africa/Nouakchott',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    { name: 'desktop', use: { ...devices['Desktop Chrome'] }, testIgnore: /responsive\.spec\.ts/ },
    { name: 'mobile', use: { ...devices['Pixel 7'] }, testMatch: /responsive\.spec\.ts/ },
  ],
});
