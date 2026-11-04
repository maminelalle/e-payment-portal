import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { ToastModule } from 'primeng/toast';
import { DrawerModule } from 'primeng/drawer';

import { Preferences } from '../core/preferences';
import { AbonneApi } from './abonne-api';
import { SentNotification } from '../core/api.models';
import { AppNotification, HeaderTools } from '../shared/header-tools';

@Component({
  selector: 'app-abonne-layout',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, ToastModule, DrawerModule, HeaderTools],
  templateUrl: './abonne-layout.html',
  styleUrl: './abonne-layout.scss',
})
export class AbonneLayout {
  protected readonly preferences = inject(Preferences);
  private readonly api = inject(AbonneApi);
  private readonly sent = signal<SentNotification[]>([]);
  protected readonly menuOpen = signal(false);

  /** Emails déjà envoyés à l'abonné, repris dans la cloche (contrat, paiements). */
  protected readonly notifications = computed<AppNotification[]>(() =>
    this.sent().map((n) => ({
      id: n.id,
      title: n.subject,
      at: n.createdAt,
      tone: n.template.includes('FAILED') ? 'danger' : n.template.includes('SUCCEEDED') || n.template.includes('ACTIVATED') ? 'success' : 'info',
      link: n.template.startsWith('PAYMENT') ? '/abonne/transactions' : n.template.startsWith('CONTRACT') ? '/abonne/contrat' : undefined,
    })),
  );

  constructor() {
    this.api.notifications().subscribe({ next: (items) => this.sent.set(items), error: () => this.sent.set([]) });
  }

  protected readonly menu = [
    { libelle: 'Accueil', lien: '/abonne', exact: true },
    { libelle: 'Mon contrat', lien: '/abonne/contrat', exact: false },
    { libelle: 'Moyens de paiement', lien: '/abonne/moyens', exact: false },
    { libelle: 'Transactions', lien: '/abonne/transactions', exact: false },
    { libelle: 'Profil', lien: '/abonne/profil', exact: false },
  ];
}
