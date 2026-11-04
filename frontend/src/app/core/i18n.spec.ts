import { TestBed } from '@angular/core/testing';

import { I18n } from './i18n';
import { Preferences } from './preferences';

describe('I18n', () => {
  let preferences: Preferences;
  let host: HTMLElement;

  // Laisse passer le MutationObserver et la micro-tâche de traduction.
  const settle = () => new Promise((resolve) => setTimeout(resolve));

  beforeEach(() => {
    localStorage.setItem('ep-language', 'fr');
    host = document.createElement('div');
    document.body.appendChild(host);
    preferences = TestBed.inject(Preferences);
    TestBed.inject(I18n);
  });

  afterEach(() => host.remove());

  it('garde un texte modifié par l’application au lieu de remettre le premier', async () => {
    host.textContent = 'Payer';
    await settle();
    host.firstChild!.nodeValue = 'Payer 125,00 MRU';
    await settle();
    expect(host.textContent).toBe('Payer 125,00 MRU');
  });

  it('traduit le texte courant en arabe puis revient au français', async () => {
    host.textContent = 'Transactions';
    host.setAttribute('title', 'Accueil');
    await settle();
    host.firstChild!.nodeValue = 'Afficher plus';
    host.setAttribute('title', 'Profil');
    await settle();

    preferences.setLanguage('ar');
    await settle();
    expect(host.textContent).toBe('عرض المزيد');

    preferences.setLanguage('fr');
    await settle();
    expect(host.textContent).toBe('Afficher plus');
    expect(host.getAttribute('title')).toBe('Profil');
  });
});
