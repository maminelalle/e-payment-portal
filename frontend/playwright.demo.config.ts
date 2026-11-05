import { defineConfig, devices } from '@playwright/test';

/** Données de démonstration (e2e-demo/seed.spec.ts), à lancer à part : jamais dans la suite de tests. */
export default defineConfig({
  testDir: './e2e-demo',
  timeout: 240_000,
  workers: 1,
  reporter: [['list']],
  use: {
    baseURL: process.env['E2E_BASE_URL'] ?? 'https://localhost:8443',
    ignoreHTTPSErrors: true,
    locale: 'fr-FR',
    timezoneId: 'Africa/Nouakchott',
    trace: 'retain-on-failure',
  },
  projects: [{ name: 'demo', use: { ...devices['Desktop Chrome'] } }],
});
