import { expect, test } from '@playwright/test';

import { ABONNE, login, resetPreferences, waitForMail } from './helpers';

test.beforeEach(async ({ page }) => {
  await resetPreferences(page);
  await login(page, ABONNE, '/abonne');
});

test('accueil : situation du compte, contrat et moyens de paiement', async ({ page }) => {
  await expect(page.getByRole('heading', { level: 1 })).toContainText('Bonjour');
  await expect(page.getByRole('heading', { name: 'Mes moyens de paiement' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Dernières transactions' })).toBeVisible();
});

test('paiement Bankily réussi, puis visible dans l’historique avec son reçu', async ({ page }) => {
  const payButton = page.getByRole('button', { name: 'Payer avec Bankily' });
  await page.locator('app-method-cards article').first().waitFor();
  test.skip(!(await payButton.isVisible()), 'Bankily n’est pas ouvert pour ce compte');
  await payButton.click();
  await page.locator('#amount').fill('125');
  await page.locator('#phone').fill('22000001');
  await page.getByRole('button', { name: /^Payer 125,00/ }).click();
  // Le bouton apparaît grisé pendant la confirmation : on attend qu'il soit actif (paiement réussi).
  await expect(page.locator('.p-drawer').getByRole('button', { name: 'Télécharger le reçu' })).toBeEnabled({ timeout: 30_000 });

  await page.goto('/abonne/transactions');
  const firstRow = page.locator('app-transaction-table tbody tr').first();
  await expect(firstRow).toContainText('125,00');
  await expect(firstRow).toContainText('Réussie');
  const download = page.waitForEvent('download');
  await firstRow.getByRole('button', { name: /Télécharger le reçu/ }).click();
  expect((await download).suggestedFilename()).toMatch(/^recu-TRX-\d{6}\.pdf$/);
});

test('export CSV de l’historique', async ({ page }) => {
  await page.goto('/abonne/transactions');
  const exportButton = page.getByRole('button', { name: 'Exporter (Excel, CSV)' });
  await expect(page.getByRole('heading', { name: 'Transactions' })).toBeVisible();
  await page.waitForLoadState('networkidle');
  test.skip(await exportButton.isDisabled(), 'Aucune transaction à exporter');
  const download = page.waitForEvent('download');
  await exportButton.click();
  expect((await download).suggestedFilename()).toBe('mes-transactions.csv');
});

test('passage en arabe et en mode sombre depuis l’en-tête', async ({ page }) => {
  await page.getByRole('button', { name: 'عربي' }).click();
  await expect(page.locator('html')).toHaveAttribute('dir', 'rtl');
  await expect(page.getByRole('link', { name: 'المعاملات' }).first()).toBeVisible();
  await page.getByRole('button', { name: 'FR' }).click();
  await page.getByRole('button', { name: 'Mode sombre' }).click();
  await expect(page.locator('html')).toHaveClass(/dark/);
});

test('profil : nouveau téléphone et nouvelle adresse confirmés par le code reçu par email', async ({ page }) => {
  // Valeurs différentes à chaque passage : renvoyer les mêmes coordonnées est refusé.
  const digits = `4${String(Date.now()).slice(-7)}`;
  const address = `Lot ${digits.slice(-3)}, Tevragh Zeina, Nouakchott`;
  await page.goto('/abonne/profil');
  await page.getByRole('button', { name: 'Modifier mon téléphone et mon adresse' }).click();
  await page.locator('#contact-phone').fill(digits);
  await page.locator('#contact-address').fill(address);
  const since = new Date();
  await page.getByRole('button', { name: 'Recevoir mon code' }).click();

  const mail = await waitForMail(ABONNE.email, /confirmation/i, since);
  const code = mail.Text.match(/\b\d{6}\b/)?.[0];
  expect(code, 'code de confirmation').toBeTruthy();
  await page.locator('p-inputotp input').first().click();
  await page.keyboard.type(code!);
  await page.getByRole('button', { name: 'Confirmer' }).click();

  await expect(page.getByText('Coordonnées modifiées').first()).toBeVisible();
  const details = page.locator('dl.details');
  await expect(details).toContainText(`${digits.slice(0, 2)} ${digits.slice(2, 4)} ${digits.slice(4, 6)} ${digits.slice(6)}`);
  await expect(details).toContainText(address);
  // Avis de sécurité en tête de « Derniers emails reçus » (les passages précédents en ont laissé d'autres).
  await expect(page.getByText('Vos coordonnées E-Payment ont été modifiées').first()).toBeVisible();
});
