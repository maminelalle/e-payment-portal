import { expect, test } from '@playwright/test';

import { resetPreferences } from './helpers';

test.beforeEach(({ page }) => resetPreferences(page));

test('la page de statut est publique et liste les moyens de paiement', async ({ page }) => {
  await page.goto('/statut');
  await expect(page.getByRole('heading', { name: 'État du service' })).toBeVisible();
  await expect(page.getByText('Portail E-Payment', { exact: true })).toBeVisible();
  await expect(page.getByText('Bankily', { exact: true })).toBeVisible();
});

test('un reçu au code invalide est signalé sans rien révéler', async ({ page }) => {
  await page.goto('/verification/recu/TRX-000001?c=0000000000000000');
  await expect(page.getByText('Reçu non reconnu.')).toBeVisible();
  await expect(page.getByText('Montant', { exact: true })).toHaveCount(0);
});

test('la page de connexion propose la langue arabe en RTL', async ({ page }) => {
  await page.goto('/connexion');
  await page.getByRole('button', { name: 'عربي' }).click();
  await expect(page.locator('html')).toHaveAttribute('dir', 'rtl');
  await expect(page.getByRole('button', { name: 'دخول' })).toBeVisible();
  await page.getByRole('button', { name: 'FR' }).click();
  await expect(page.locator('html')).toHaveAttribute('dir', 'ltr');
});

test('les routes protégées renvoient vers la connexion', async ({ page }) => {
  await page.goto('/admin/transactions');
  await expect(page).toHaveURL(/\/connexion/);
});
