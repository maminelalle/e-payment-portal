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

/** Contrat initial, transmis du premier scénario au scénario de l'avenant. */
let contractId = '';

test('l’admin crée l’abonné et lui envoie son contrat', async ({ page }) => {
  await resetPreferences(page);
  const api = await adminApi(page);

  // Création de l'abonné par l'écran (formulaire « Nouvel abonné »), le reste par l'API.
  await page.goto('/admin/abonnes');
  await page.getByRole('button', { name: 'Nouvel abonné' }).click();
  await page.locator('#displayName').fill(`Société E2E ${stamp}`);
  await page.locator('#nif').fill(stamp);
  await page.locator('#phone').fill(`4${stamp.slice(-7)}`);
  await page.locator('#email').fill(abonne.email);
  await page.locator('#address').fill('Nouakchott');
  await page.getByRole('button', { name: "Créer l'abonné" }).click();
  await expect(page.getByText(`Invitation envoyée à ${abonne.email}.`)).toBeVisible({ timeout: 15_000 });
  const search = await (await api.get('/api/admin/abonnes', { params: { q: abonne.email } })).json();
  const created = { subscriber: { id: search.content[0].id as string } };

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
  contractId = draft.id;
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
  await signCurrentContract(page);

  await page.goto('/abonne');
  await expect(page.getByRole('button', { name: 'Payer avec Bankily' })).toBeEnabled();
});

test('avenant : l’admin le crée depuis la fiche, l’abonné le signe, il remplace le contrat', async ({ page }) => {
  test.setTimeout(180_000);
  await resetPreferences(page);
  const api = await adminApi(page);

  // Création depuis la fiche du contrat actif → assistant ouvert sur l'avenant prérempli
  await page.goto(`/admin/contrats/${contractId}`);
  const reference = (await page.locator('h1.page-title').innerText()).trim();
  await page.getByRole('button', { name: 'Créer un avenant' }).click();
  await page.getByRole('button', { name: "Créer l'avenant" }).click();
  await expect(page).toHaveURL(/\/admin\/contrats\/[0-9a-f-]{36}\/modifier/);
  await expect(page.locator('h1.page-title')).toContainText(`Avenant ${reference}-A1`);
  const amendmentId = page.url().split('/').at(-2)!;

  // Nouveaux moyens : Bankily et Masrvi (plus de carte), puis envoi
  const amendment = await (await api.get(`/api/admin/contrats/${amendmentId}`)).json();
  const updated = await api.put(`/api/admin/contrats/${amendmentId}`, {
    data: {
      templateId: amendment.template.id,
      startDate: amendment.startDate,
      endDate: amendment.endDate,
      commissionRate: 1.5,
      methods: [
        { code: 'BANKILY', perTransactionLimit: 50000, monthlyLimit: 500000 },
        { code: 'MASRVI', perTransactionLimit: 50000, monthlyLimit: 500000 },
      ],
    },
  });
  expect(updated.status(), await updated.text()).toBe(200);
  await post(api, `/api/admin/contrats/${amendmentId}/envoyer`, {});

  // Abonné : l'avenant est annoncé, l'ancien contrat reste utilisable jusqu'à la signature
  const subscriber = await page.context().browser()!.newPage();
  await resetPreferences(subscriber);
  await login(subscriber, abonne, '/abonne');
  await expect(subscriber.getByText('Un avenant à votre contrat est prêt à être signé')).toBeVisible();
  await expect(subscriber.getByRole('button', { name: 'Payer par carte' })).toBeVisible();
  await subscriber.goto('/abonne/contrat');
  await expect(subscriber.getByText('Avenant n°1')).toBeVisible();
  await signCurrentContract(subscriber);
  // L'activation suit la validation de la signature : on recharge jusqu'à voir les nouveaux moyens.
  await expect(async () => {
    await subscriber.goto('/abonne');
    await expect(subscriber.getByRole('button', { name: 'Payer avec Masrvi' })).toBeEnabled({ timeout: 2_000 });
  }).toPass({ timeout: 20_000 });
  await expect(subscriber.getByRole('button', { name: 'Payer par carte' })).toHaveCount(0);
  await subscriber.close();

  // Admin : l'original est remplacé, l'historique montre les deux versions
  await page.goto(`/admin/contrats/${contractId}`);
  await expect(page.getByText('Remplacé par avenant').first()).toBeVisible();
  await expect(page.locator('ol.versions li')).toHaveCount(2);
});

/** Signature du contrat (ou de l'avenant) en attente, avec le code reçu par email. */
async function signCurrentContract(page: Page) {
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
}

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
