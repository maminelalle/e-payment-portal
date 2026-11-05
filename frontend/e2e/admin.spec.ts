import { expect, type Page, test } from '@playwright/test';

import { ADMIN, login, resetPreferences } from './helpers';

// Une seule connexion pour tous les tests : un code TOTP ne peut pas servir deux fois dans la même fenêtre de 30 s.
test.describe.configure({ mode: 'serial' });

let page: Page;

test.beforeAll(async ({ browser }) => {
  page = await browser.newPage();
  await resetPreferences(page);
  await login(page, ADMIN, '/admin');
});

test.afterAll(() => page.close());

test('tableau de bord et état des fournisseurs', async () => {
  await page.goto('/admin');
  await expect(page.getByRole('heading', { name: 'Tableau de bord' })).toBeVisible();
  await expect(page.getByLabel('État des fournisseurs')).toContainText('Bankily');
});

test('tableau de bord : statuts des abonnés, contrats et transactions, liens filtrés', async () => {
  await page.goto('/admin');
  for (const title of ['Abonnés', 'Contrats', 'Transactions']) {
    await expect(page.getByRole('heading', { level: 2, name: title, exact: true })).toBeVisible();
  }
  const overview = page.locator('section.overview');
  await expect(overview.locator('li')).toHaveCount(4 + 7 + 5);

  // Répartition par type puis par fournisseur.
  await page.getByRole('button', { name: 'Type' }).click();
  await page.getByRole('button', { name: 'Fournisseur' }).click();

  // Un statut ouvre la liste déjà filtrée.
  await overview.getByRole('link', { name: /Actif/ }).first().click();
  await expect(page).toHaveURL(/\/admin\/abonnes\?statut=ACTIVE/);
  await expect(page.locator('p-table tbody')).not.toContainText('Suspendu');

  await page.goto('/admin');
  await overview.getByRole('link', { name: /Brouillon/ }).click();
  await expect(page).toHaveURL(/\/admin\/contrats\?statut=DRAFT/);
  await expect(page.locator('p-table tbody')).not.toContainText('Actif');

  await page.goto('/admin');
  await overview.getByRole('link', { name: /Réussie/ }).click();
  await expect(page).toHaveURL(/\/admin\/transactions\?statut=SUCCEEDED&du=/);
  await expect(page.locator('app-transaction-table tbody')).not.toContainText('Échouée');
});

test('recherche globale Ctrl+K vers la liste des transactions', async () => {
  await page.goto('/admin');
  await page.getByRole('heading', { name: 'Tableau de bord' }).waitFor();
  await page.keyboard.press('Control+k');
  const search = page.getByRole('combobox', { name: 'Recherche globale' });
  await expect(search).toBeFocused();
  await search.fill('Transac');
  await expect(page.getByRole('option', { name: /Transactions/ })).toBeVisible();
  await page.keyboard.press('Enter');
  await expect(page).toHaveURL(/\/admin\/transactions/);
  await expect(page.getByRole('heading', { name: 'Transactions' })).toBeVisible();
});

test('le compteur de la liste des abonnés correspond aux lignes affichées', async () => {
  await page.goto('/admin/abonnes');
  await page.waitForLoadState('networkidle');
  const rows = await page.locator('p-table tbody tr td.ep-ref, p-table tbody tr bdi.ep-ltr').count();
  if (rows > 0) await expect(page.getByText(/sur 0$/)).toHaveCount(0);
  else await expect(page.getByText(/Aucun abonné/)).toBeVisible();
});

test('journal d’audit : libellés traduits', async () => {
  await page.goto('/admin/audit');
  await expect(page.locator('p-table tbody tr').first()).toBeVisible();
  await expect(page.getByText('Connexion').first()).toBeVisible();
  await expect(page.locator('td', { hasText: /^\s*LOGIN\s*$/ })).toHaveCount(0);
});
