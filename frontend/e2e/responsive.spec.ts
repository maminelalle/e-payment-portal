import { expect, test } from '@playwright/test';

import { ABONNE, login, resetPreferences } from './helpers';

test('pas de défilement horizontal sur mobile (connexion, statut)', async ({ page }) => {
  await resetPreferences(page);
  for (const path of ['/connexion', '/statut']) {
    await page.goto(path);
    await page.waitForLoadState('networkidle');
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
    expect(overflow, path).toBeLessThanOrEqual(1);
  }
});

test('espace abonné sur mobile : pas de défilement horizontal, en français et en arabe', async ({ page }) => {
  await resetPreferences(page);
  await login(page, ABONNE, '/abonne');
  for (const language of ['fr', 'ar']) {
    await page.evaluate((l) => localStorage.setItem('ep-language', l), language);
    for (const path of ['/abonne', '/abonne/contrat', '/abonne/transactions', '/abonne/profil']) {
      await page.goto(path);
      await page.waitForLoadState('networkidle');
      const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
      expect(overflow, `${language} ${path}`).toBeLessThanOrEqual(1);
    }
  }
  // La langue se choisit depuis le menu sur téléphone.
  await page.getByRole('button', { name: 'Ouvrir le menu' }).or(page.getByRole('button', { name: 'فتح القائمة' })).click();
  await page.locator('.mobile-lang').getByRole('button', { name: 'FR' }).click();
  await expect(page.locator('html')).toHaveAttribute('lang', 'fr');
});
