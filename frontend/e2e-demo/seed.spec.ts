import { expect, request as playwrightRequest, test, type APIRequestContext, type Page } from '@playwright/test';
import { writeFileSync } from 'node:fs';

import { ADMIN, login, resetPreferences, waitForMail } from '../e2e/helpers';

/**
 * Données de démonstration sur la recette (docs/09-demo.md) : trois abonnés crédibles, créés par le vrai parcours
 * (invitation, mot de passe, signature par code email) et quelques paiements sur chaque moyen.
 * Relançable : un abonné déjà présent est laissé tel quel. Les comptes sont écrits dans e2e-demo/comptes-demo.txt.
 *
 *   E2E_BASE_URL=https://localhost:8443 E2E_ADMIN_EMAIL=… E2E_ADMIN_PASSWORD=… \
 *     npx playwright test --config playwright.demo.config.ts
 */
interface DemoSubscriber {
  key: string;
  type: 'MORALE' | 'PHYSIQUE';
  displayName: string;
  email: string;
  password: string;
  phone: string;
  address: string;
  methods: { code: string; perTransactionLimit: number; monthlyLimit: number }[];
  /** false : contrat envoyé mais laissé « à signer » pour la démonstration en direct. */
  sign: boolean;
  wallet: { method: string; amount: string }[];
  cards: { amount: string; card: string }[];
}

const DEMO: DemoSubscriber[] = [
  {
    key: 'atlas', type: 'MORALE', displayName: 'Société Atlas Mauritanie SARL', email: 'demo.atlas@exemple.mr',
    password: 'Atlas#Demo2026', phone: '22 45 67 89', address: 'Tevragh Zeina, Nouakchott',
    methods: [
      { code: 'BANKILY', perTransactionLimit: 100000, monthlyLimit: 1000000 },
      { code: 'MASRVI', perTransactionLimit: 100000, monthlyLimit: 1000000 },
      { code: 'VISA', perTransactionLimit: 300000, monthlyLimit: 3000000 },
    ],
    sign: true,
    wallet: [
      { method: 'BANKILY', amount: '12500.00' }, { method: 'MASRVI', amount: '8300.00' },
      { method: 'BANKILY', amount: '27000.00' }, { method: 'BANKILY', amount: '3750.51' },
      { method: 'MASRVI', amount: '1200.13' }, { method: 'MASRVI', amount: '15600.00' },
    ],
    cards: [{ amount: '45000.00', card: '4111 1111 1111 1111' }, { amount: '9900.00', card: '4000 0000 0000 0002' }],
  },
  {
    key: 'boulangerie', type: 'MORALE', displayName: 'Boulangerie El Amane', email: 'demo.boulangerie@exemple.mr',
    password: 'Amane#Demo2026', phone: '36 12 34 56', address: 'Ksar, Nouakchott',
    methods: [
      { code: 'BANKILY', perTransactionLimit: 50000, monthlyLimit: 400000 },
      { code: 'VISA', perTransactionLimit: 100000, monthlyLimit: 800000 },
    ],
    sign: true,
    wallet: [{ method: 'BANKILY', amount: '6400.00' }, { method: 'BANKILY', amount: '18250.00' }],
    cards: [{ amount: '32000.00', card: '4111 1111 1111 1111' }],
  },
  {
    key: 'lemine', type: 'PHYSIQUE', displayName: 'Mohamed Lemine Ahmed', email: 'demo.lemine@exemple.mr',
    password: 'Lemine#Demo2026', phone: '44 98 76 54', address: 'Arafat, Nouakchott',
    methods: [{ code: 'MASRVI', perTransactionLimit: 30000, monthlyLimit: 200000 }],
    sign: false, wallet: [], cards: [],
  },
];

test.describe.configure({ mode: 'serial' });
test.setTimeout(240_000);

for (const demo of DEMO) {
  test(`démo : ${demo.displayName}`, async ({ browser }) => {
    const adminPage = await browser.newPage();
    await resetPreferences(adminPage);
    const api = await adminApi(adminPage);
    const existing = (await (await api.get('/api/admin/abonnes', { params: { q: demo.email } })).json()).content;
    if (existing.length) {
      test.info().annotations.push({ type: 'déjà présent', description: demo.email });
      await adminPage.close();
      return;
    }

    // 1. Abonné, contrat, envoi pour signature
    const digits = String(Date.now()).slice(-10);
    const created = await post(api, '/api/admin/abonnes', {
      type: demo.type, email: demo.email,
      details: { displayName: demo.displayName, phone: demo.phone, address: demo.address,
        ...(demo.type === 'MORALE' ? { nif: digits.slice(-8) } : { nni: digits }) },
    });
    const [template] = await (await api.get('/api/admin/contrats/modeles')).json();
    const draft = await post(api, '/api/admin/contrats', { subscriberId: created.subscriber.id, templateId: template.id });
    const today = new Date().toISOString().slice(0, 10);
    const end = new Date(Date.now() + 365 * 86_400_000).toISOString().slice(0, 10);
    const put = await api.put(`/api/admin/contrats/${draft.id}`, {
      data: { templateId: template.id, startDate: today, endDate: end, commissionRate: 1.5, methods: demo.methods },
    });
    expect(put.status(), await put.text()).toBe(200);
    await post(api, `/api/admin/contrats/${draft.id}/envoyer`, {});
    await adminPage.close();

    // 2. Mot de passe depuis l'invitation
    const page = await browser.newPage();
    const invitation = await waitForMail(demo.email, /mot de passe|password|compte/i);
    await page.goto(invitation.Text.match(/https?:\/\/\S+action-token\S+/)![0].replace(/&amp;/g, '&'));
    const proceed = page.getByRole('link', { name: /continuer|proceed/i });
    if (await proceed.isVisible({ timeout: 3_000 }).catch(() => false)) await proceed.click();
    await page.locator('#password-new').fill(demo.password);
    await page.locator('#password-confirm').fill(demo.password);
    await page.locator('input[type=submit], button[type=submit]').first().click();
    await expect(page.locator('#password-new')).toHaveCount(0, { timeout: 15_000 });
    if (!demo.sign) {
      await page.close();
      return;
    }

    // 3. Signature par le code reçu par email
    await resetPreferences(page);
    const subscriberApi = await sessionApi(page, () => login(page, { email: demo.email, password: demo.password }, '/abonne'));
    await page.goto('/abonne/contrat');
    await page.getByRole('button', { name: 'Signer électroniquement' }).click();
    await page.getByText(/J.ai lu le contrat/).first().click();
    const since = new Date();
    await page.getByRole('button', { name: 'Recevoir mon code' }).click();
    const code = (await waitForMail(demo.email, /code/i, since)).Text.match(/\b\d{6}\b/)![0];
    await page.locator('p-inputotp input').first().click();
    await page.keyboard.type(code);
    await page.getByRole('button', { name: 'Signer le contrat' }).click();
    await expect(page.getByText(/Actif|signé/i).first()).toBeVisible({ timeout: 15_000 });
    await expect(async () => {
      const methods = await (await subscriberApi.get('/api/abonne/moyens')).json();
      expect(methods.length).toBe(demo.methods.length);
    }).toPass({ timeout: 20_000 });

    // 4. Paiements wallet (le simulateur décide selon les centimes : ,13 refusé, ,51 solde insuffisant)
    for (const p of demo.wallet) {
      await subscriberApi.post('/api/abonne/paiements', {
        headers: { 'Idempotency-Key': crypto.randomUUID() },
        data: { method: p.method, amount: Number(p.amount), payerPhone: demo.phone, description: 'Facture fournisseur' },
      });
    }
    // 5. Paiements par carte : page sécurisée de la passerelle simulée
    for (const c of demo.cards) {
      const started = await (await subscriberApi.post('/api/abonne/paiements', {
        headers: { 'Idempotency-Key': crypto.randomUUID() },
        data: { method: 'VISA', amount: Number(c.amount), payerPhone: null, description: 'Commande matériel' },
      })).json();
      await page.goto(started.redirectUrl);
      await page.locator('input[name=cardNumber]').fill(c.card);
      await page.locator('input[name=expiry]').fill('12/28');
      await page.locator('input[name=cvc]').fill('123');
      await page.locator('input[name=holder]').fill(demo.displayName.toUpperCase());
      await page.locator('button[type=submit]').click();
      await page.waitForURL(/\/abonne/, { timeout: 20_000 });
    }
    await page.close();
  });
}

test.afterAll(() => {
  writeFileSync('e2e-demo/comptes-demo.txt', DEMO.map((d) =>
    `${d.displayName} : ${d.email} / ${d.password}${d.sign ? '' : ' (contrat à signer pendant la démo)'}`).join('\n') + '\n');
});

async function adminApi(page: Page): Promise<APIRequestContext> {
  return sessionApi(page, () => login(page, ADMIN, '/admin'));
}

/** Connexion dans le navigateur, puis appels API avec le jeton de la session (comme le portail). */
async function sessionApi(page: Page, doLogin: () => Promise<void>): Promise<APIRequestContext> {
  let token = '';
  page.on('request', (r) => {
    const header = r.headers()['authorization'];
    if (header?.startsWith('Bearer ')) token = header;
  });
  await doLogin();
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
