import { test, type Page } from '@playwright/test';
import { mkdirSync } from 'node:fs';

import { ADMIN, login } from '../e2e/helpers';

/**
 * Captures d'écran du rapport de stage (docs/rapport/figures), prises sur la recette avec les données de démo.
 * Lancer : E2E_ADMIN_EMAIL=… E2E_ADMIN_PASSWORD=… E2E_ABONNE_EMAIL=… E2E_ABONNE_PASSWORD=…
 *          npx playwright test -c playwright.demo.config.ts captures
 */
const OUT = '../docs/rapport/figures';
mkdirSync(OUT, { recursive: true });

async function shot(page: Page, name: string, fullPage = false) {
  await page.waitForLoadState('networkidle');
  await page.waitForTimeout(1200);
  await page.screenshot({ path: `${OUT}/${name}.png`, fullPage });
}

test.use({ viewport: { width: 1440, height: 900 }, deviceScaleFactor: 1 });
test.describe.configure({ mode: 'serial' });

test('écrans publics', async ({ page }) => {
  await page.goto('/connexion');
  await shot(page, '01-connexion-portail');
  await page.getByRole('button', { name: 'Se connecter' }).click();
  await page.locator('#username').waitFor();
  await shot(page, '02-connexion-keycloak');
  await page.goto('/statut');
  await shot(page, '03-statut');
});

test('espace administrateur', async ({ page }) => {
  test.setTimeout(180_000);
  await login(page, ADMIN, '/admin');
  await shot(page, '10-admin-tableau-de-bord');
  await page.goto('/admin/abonnes');
  // Les abonnés créés par les tests automatiques encombrent la liste : seuls ceux de la démo sont montrés.
  await page.getByPlaceholder('Nom, référence, email ou téléphone').fill('demo.');
  await page.getByText('Société Atlas').first().waitFor();
  await shot(page, '11-admin-abonnes');
  await page.getByText('Société Atlas', { exact: false }).first().click();
  await page.waitForURL(/\/admin\/abonnes\/.+/);
  await shot(page, '12-admin-fiche-abonne', true);
  await page.goto('/admin/moyens');
  await shot(page, '13-admin-moyens');
  await page.goto('/admin/contrats');
  await page.getByPlaceholder('Référence (CTR-2026-0001)').fill('CTR-2026-001');
  await page.getByText('Atlas').first().waitFor();
  await shot(page, '14-admin-contrats');
  await page.goto('/admin/contrats/nouveau');
  await shot(page, '15-admin-assistant-contrat');
  await page.goto('/admin/contrats/modeles');
  await shot(page, '16-admin-modeles');
  await page.goto('/admin/contrats');
  await page.getByPlaceholder('Référence (CTR-2026-0001)').fill('CTR-2026-0016');
  await page.locator('tbody tr').filter({ hasText: 'Atlas' }).first().click();
  await page.waitForURL(/\/admin\/contrats\/.+/);
  await shot(page, '17-admin-fiche-contrat');
  await page.goto('/admin/transactions');
  await page.getByText('TRX-').first().waitFor();
  await shot(page, '18-admin-transactions');
  await page.goto('/admin/audit');
  await shot(page, '19-admin-audit');
});

test('espace abonné', async ({ page }) => {
  test.setTimeout(180_000);
  const abonne = { email: process.env['E2E_ABONNE_EMAIL'] ?? '', password: process.env['E2E_ABONNE_PASSWORD'] ?? '' };
  await login(page, abonne, '/abonne');
  await shot(page, '20-abonne-accueil');
  await page.goto('/abonne/contrat');
  await shot(page, '21-abonne-contrat');
  await page.goto('/abonne/moyens');
  await shot(page, '22-abonne-moyens');
  await page.getByRole('button', { name: 'Payer avec Bankily' }).click();
  await page.locator('#amount').fill('2500');
  await page.locator('#phone').fill('22000001');
  await shot(page, '23-abonne-paiement');
  await page.goto('/abonne/transactions');
  await page.getByText('TRX-').first().waitFor();
  await shot(page, '24-abonne-transactions');

  const statement = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Relevé PDF' }).click();
  await (await statement).saveAs(`${OUT}/releve.pdf`);
  const receipt = page.waitForEvent('download');
  await page.locator('app-transaction-table tbody tr').filter({ hasText: 'Réussie' }).first()
    .getByRole('button', { name: /Télécharger le reçu/ }).click();
  await (await receipt).saveAs(`${OUT}/recu.pdf`);
  const contract = page.waitForEvent('download');
  await page.goto('/abonne/contrat');
  await page.getByRole('button', { name: /Télécharger/ }).first().click();
  await (await contract).saveAs(`${OUT}/contrat.pdf`);

  await page.goto('/abonne/profil');
  await shot(page, '25-abonne-profil');
  await page.goto('/abonne');
  await page.getByRole('button', { name: 'عربي' }).click();
  await shot(page, '26-abonne-accueil-arabe');
  await page.getByRole('button', { name: 'FR' }).click();
});

test('abonné sur mobile', async ({ browser }) => {
  const context = await browser.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2, isMobile: true });
  const page = await context.newPage();
  const abonne = { email: process.env['E2E_ABONNE_EMAIL'] ?? '', password: process.env['E2E_ABONNE_PASSWORD'] ?? '' };
  await login(page, abonne, '/abonne');
  await shot(page, '27-abonne-mobile');
  await context.close();
});
