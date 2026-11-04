import { expect, type Page } from '@playwright/test';
import { createHmac } from 'node:crypto';
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';

export const ADMIN = {
  email: process.env['E2E_ADMIN_EMAIL'] ?? 'admin@epayment.mr',
  password: process.env['E2E_ADMIN_PASSWORD'] ?? 'Admin#Demo2026',
};

/** Abonné de test : variables E2E_ABONNE_*, sinon celui créé par parcours.spec.ts, sinon le compte de démonstration. */
const ABONNE_FILE = 'e2e/.auth/abonne.json';
export const ABONNE: { email: string; password: string } = process.env['E2E_ABONNE_EMAIL']
  ? { email: process.env['E2E_ABONNE_EMAIL'], password: process.env['E2E_ABONNE_PASSWORD'] ?? '' }
  : existsSync(ABONNE_FILE)
    ? JSON.parse(readFileSync(ABONNE_FILE, 'utf8'))
    : { email: 'contact@exemple.mr', password: 'Abonne#Demo2026' };

export function saveAbonne(account: { email: string; password: string }) {
  mkdirSync('e2e/.auth', { recursive: true });
  writeFileSync(ABONNE_FILE, JSON.stringify(account));
  Object.assign(ABONNE, account);
}

/** Secret TOTP de l'admin de test : variable E2E_ADMIN_TOTP_SECRET, ou mémorisé lors de l'enrôlement. */
const TOTP_FILE = 'e2e/.auth/totp-secret';
/** Dernière fenêtre de 30 s utilisée : Keycloak refuse de revoir le même code, même d'un fichier de test à l'autre. */
const TOTP_LAST = 'e2e/.auth/totp-last';

/**
 * Connexion par la page Keycloak, puis retour sur l'espace du rôle.
 * Si Keycloak demande un code TOTP (MFA admin), le code est calculé ; à la première connexion,
 * l'enrôlement est fait en lisant la clé affichée en mode manuel.
 */
export async function login(page: Page, account: { email: string; password: string }, space: '/admin' | '/abonne') {
  await page.goto('/connexion');
  await page.getByRole('button', { name: 'Se connecter' }).click();
  await page.locator('#username').fill(account.email);
  await page.locator('#password').fill(account.password);
  await page.locator('#kc-login').click();

  const done = new RegExp(`${space}(/|$)`);
  const next = await Promise.race([
    page.waitForURL(done, { timeout: 20_000 }).then(() => 'done'),
    page.locator('#otp').waitFor({ timeout: 20_000 }).then(() => 'otp'),
    page.locator('#totp').waitFor({ timeout: 20_000 }).then(() => 'enroll'),
  ]);
  if (next === 'enroll') {
    await page.locator('a[href*="mode=manual"]').click();
    const secret = (await page.locator('#kc-totp-secret-key').innerText()).replace(/\s/g, '');
    mkdirSync('e2e/.auth', { recursive: true });
    writeFileSync(TOTP_FILE, secret);
    await page.locator('#totp').fill(await freshTotp(secret));
    await page.locator('#saveTOTPBtn, input[type=submit]').first().click();
  } else if (next === 'otp') {
    const secret = process.env['E2E_ADMIN_TOTP_SECRET'] ?? (existsSync(TOTP_FILE) ? readFileSync(TOTP_FILE, 'utf8') : '');
    if (!secret) throw new Error('Code TOTP demandé : définir E2E_ADMIN_TOTP_SECRET');
    await page.locator('#otp').fill(await freshTotp(secret));
    await page.locator('#kc-login').click();
  }
  await expect(page).toHaveURL(done, { timeout: 20_000 });
}

async function freshTotp(secret: string): Promise<string> {
  const last = existsSync(TOTP_LAST) ? Number(readFileSync(TOTP_LAST, 'utf8')) : 0;
  while (Math.floor(Date.now() / 30_000) <= last) await new Promise((resolve) => setTimeout(resolve, 1_000));
  writeFileSync(TOTP_LAST, String(Math.floor(Date.now() / 30_000)));
  return totp(secret);
}

/** Français et thème clair au premier chargement de chaque test. */
export async function resetPreferences(page: Page) {
  await page.addInitScript(() => {
    if (!sessionStorage.getItem('e2e-init')) {
      localStorage.setItem('ep-language', 'fr');
      localStorage.setItem('ep-theme', 'light');
      sessionStorage.setItem('e2e-init', '1');
    }
  });
}

/** Code TOTP (RFC 6238, SHA-1, 6 chiffres, 30 s) à partir d'une clé base32, comme une application d'authentification. */
export function totp(base32: string, time = Date.now()): string {
  const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';
  let bits = '';
  for (const char of base32.replace(/=+$/, '').toUpperCase()) {
    bits += alphabet.indexOf(char).toString(2).padStart(5, '0');
  }
  const key = Buffer.from(bits.match(/.{8}/g)!.map((b) => parseInt(b, 2)));
  const counter = Buffer.alloc(8);
  counter.writeBigUInt64BE(BigInt(Math.floor(time / 30_000)));
  const hmac = createHmac('sha1', key).update(counter).digest();
  const offset = hmac[hmac.length - 1]! & 0xf;
  const code = (hmac.readUInt32BE(offset) & 0x7fffffff) % 1_000_000;
  return String(code).padStart(6, '0');
}
