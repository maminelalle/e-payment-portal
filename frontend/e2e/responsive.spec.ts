import { expect, test } from '@playwright/test';

import { resetPreferences } from './helpers';

test('pas de défilement horizontal sur mobile (connexion, statut)', async ({ page }) => {
  await resetPreferences(page);
  for (const path of ['/connexion', '/statut']) {
    await page.goto(path);
    await page.waitForLoadState('networkidle');
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
    expect(overflow, path).toBeLessThanOrEqual(1);
  }
});
