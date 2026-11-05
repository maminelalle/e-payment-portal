import { expect, request as playwrightRequest, test, type APIRequestContext, type Page } from '@playwright/test';

import { ADMIN, login, resetPreferences, saveAbonne, waitForMail } from './helpers';

/**
 * Parcours complet d'un nouvel abonné, sur une base vide :
 * l'admin crée l'abonné et son contrat → l'abonné reçoit l'invitation, choisit son mot de passe →
 * signe le contrat avec le code reçu par email → le contrat est actif et le paiement est possible.
 * Les emails sont lus dans Mailpit (MAILPIT_URL, http://localhost:8025 par défaut).
 * Le compte créé est mémorisé (e2e/.auth/abonne.json) pour les scénarios abonné.
 */
const stamp = Date.now().toString().slice(-8);
const abonne = { email: `e2e-abonne-${stamp}@exemple.mr`, password: `Abonne#E2e${stamp}` };

test.describe.configure({ mode: 'serial' });

test('l’admin crée l’abonné et lui envoie son contrat', async ({ page }) => {
  await resetPreferences(page);
  const api = await adminApi(page);

  const created = await post(api, '/api/admin/abonnes', {
    type: 'MORALE',
    email: abonne.email,
    details: { displayName: `Société E2E ${stamp}`, nif: stamp, phone: `+2224${stamp.slice(-7)}`, address: 'Nouakchott' },
  });
  expect(created.invitationSent).toBe(true);

  const [template] = await (await api.get('/api/admin/contrats/modeles')).json();
  const draft = await post(api, '/api/admin/contrats', { subscriberId: created.subscriber.id, templateId: template.id });
  const updated = await api.put(`/api/admin/contrats/${draft.id}`, {
    data: {
      templateId: template.id,
      startDate: today(),
      endDate: today(365),
      commissionRate: 1.5,
      methods: [
        { code: 'BANKILY', perTransactionLimit: 50000, monthlyLimit: 500000 },
        { code: 'VISA', perTransactionLimit: 200000, monthlyLimit: 2000000 },
      ],
    },
  });
  expect(updated.status(), await updated.text()).toBe(200);
  const sent = await post(api, `/api/admin/contrats/${draft.id}/envoyer`, {});
  expect(sent.status).toBe('PENDING_SIGNATURE');
});

test('l’abonné choisit son mot de passe depuis l’invitation', async ({ page }) => {
  const mail = await waitForMail(abonne.email, /mot de passe|password|compte/i);
  const link = mail.Text.match(/https?:\/\/\S+action-token\S+/)?.[0];
  expect(link, 'lien d’invitation').toBeTruthy();

  await page.goto(link!.replace(/&amp;/g, '&'));
  // Keycloak peut d'abord demander de confirmer l'action (« Cliquez ici pour continuer »).
  const proceed = page.getByRole('link', { name: /continuer|proceed/i });
  if (await proceed.isVisible({ timeout: 3_000 }).catch(() => false)) await proceed.click();
  await page.locator('#password-new').fill(abonne.password);
  await page.locator('#password-confirm').fill(abonne.password);
  await page.locator('input[type=submit], button[type=submit]').first().click();
  await expect(page.locator('#password-new')).toHaveCount(0, { timeout: 15_000 });
  saveAbonne(abonne);
});

test('l’abonné signe son contrat avec le code reçu par email, puis peut payer', async ({ page }) => {
  await resetPreferences(page);
  await login(page, abonne, '/abonne');
  await page.goto('/abonne/contrat');
  await page.getByRole('button', { name: 'Signer électroniquement' }).click();
  await page.getByText('J’ai lu le contrat', { exact: false }).or(page.getByText("J'ai lu le contrat")).first().click();
  const since = new Date();
  await page.getByRole('button', { name: 'Recevoir mon code' }).click();

  const mail = await waitForMail(abonne.email, /code/i, since);
  const code = mail.Text.match(/\b\d{6}\b/)?.[0];
  expect(code, 'code de signature').toBeTruthy();
  await page.locator('p-inputotp input').first().click();
  await page.keyboard.type(code!);
  await page.getByRole('button', { name: 'Signer le contrat' }).click();
  await expect(page.getByText(/Actif|signé/i).first()).toBeVisible({ timeout: 15_000 });

  await page.goto('/abonne');
  await expect(page.getByRole('button', { name: 'Payer avec Bankily' })).toBeEnabled();
});

/** Connexion admin dans le navigateur, puis appels API avec le jeton de la session (comme le portail). */
async function adminApi(page: Page): Promise<APIRequestContext> {
  let token = '';
  page.on('request', (r) => {
    const header = r.headers()['authorization'];
    if (header?.startsWith('Bearer ')) token = header;
  });
  await login(page, ADMIN, '/admin');
  await expect.poll(() => token, { timeout: 15_000 }).not.toBe('');
  return playwrightRequest.newContext({
    baseURL: test.info().project.use.baseURL,
    ignoreHTTPSErrors: true,
    extraHTTPHeaders: { Authorization: token },
  });
}

async function post(api: APIRequestContext, url: string, data: unknown) {
  const response = await api.post(url, { data });
  expect(response.ok(), `${url} → ${response.status()} ${await response.text()}`).toBe(true);
  return response.json();
}

/** Date du jour à Nouakchott (UTC), décalée de quelques jours au besoin, au format AAAA-MM-JJ. */
function today(plusDays = 0): string {
  return new Date(Date.now() + plusDays * 86_400_000).toISOString().slice(0, 10);
}
