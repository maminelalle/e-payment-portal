import { Component, computed, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { ToastModule } from 'primeng/toast';
import { catchError, of, switchMap, timer } from 'rxjs';

import { ContractToProcess, ProviderHealth, ProviderState, TransactionAlert } from '../core/api.models';
import { AppNotification, HeaderTools } from '../shared/header-tools';
import { AdminApi } from './admin-api';
import { RechercheGlobale } from './recherche-globale';

const PROVIDER_LABELS: Record<ProviderState, string> = { UP: 'En ligne', DOWN: 'En panne', SIMULATED: 'Simulé' };

@Component({
  selector: 'app-admin-layout',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, ToastModule, ConfirmDialogModule, HeaderTools, RechercheGlobale],
  templateUrl: './admin-layout.html',
  styleUrl: './admin-layout.scss',
})
export class AdminLayout {
  private readonly api = inject(AdminApi);

  protected readonly menuOpen = signal(false);
  protected readonly providers = signal<ProviderHealth[]>([]);
  private readonly alerts = signal<TransactionAlert[]>([]);
  private readonly toProcess = signal<ContractToProcess[]>([]);
  protected readonly contractsToProcess = computed(() => this.toProcess().length);

  /** Cloche admin : fournisseurs en panne, transactions suspectes, contrats signés à activer ou en attente. */
  protected readonly notifications = computed<AppNotification[]>(() => [
    ...this.providers()
      .filter((p) => p.state === 'DOWN')
      .map((p) => ({
        id: `provider-${p.code}`,
        title: `${p.name} est indisponible`,
        detail: p.message ?? 'Les paiements par ce moyen échoueront tant qu’il ne répond pas.',
        at: new Date().toISOString(),
        tone: 'danger' as const,
        link: '/admin/moyens',
      })),
    ...this.alerts().map((a) => ({
      id: `alert-${a.type}-${a.reference}`,
      title: a.type === 'UNUSUAL_AMOUNT' ? `Montant inhabituel · ${a.subscriberName}` : `Échecs répétés · ${a.subscriberName}`,
      detail: `${a.reference} — ${a.detail}`,
      at: a.at,
      tone: 'warning' as const,
      link: '/admin/transactions',
      queryParams: { q: a.reference },
    })),
    ...this.toProcess().map((c) => ({
      id: `contract-${c.id}`,
      title: c.status === 'SIGNED' ? `Contrat signé à activer · ${c.subscriberName}` : `Contrat à traiter · ${c.subscriberName}`,
      detail: c.reference,
      at: c.signedAt ?? c.sentAt ?? c.createdAt,
      tone: 'info' as const,
      link: `/admin/contrats/${c.id}`,
    })),
  ].sort((a, b) => b.at.localeCompare(a.at)));

  protected readonly menu = [
    { libelle: 'Tableau de bord', icone: 'pi pi-th-large', lien: '/admin', exact: true },
    { libelle: 'Abonnés', icone: 'pi pi-users', lien: '/admin/abonnes', exact: false },
    { libelle: 'Moyens de paiement', icone: 'pi pi-credit-card', lien: '/admin/moyens', exact: false },
    { libelle: 'Contrats', icone: 'pi pi-file', lien: '/admin/contrats', exact: false },
    { libelle: 'Transactions', icone: 'pi pi-arrow-right-arrow-left', lien: '/admin/transactions', exact: false },
    { libelle: 'Paramètres et audit', icone: 'pi pi-shield', lien: '/admin/audit', exact: false },
  ];

  constructor() {
    // Rafraîchi toutes les 2 minutes tant que l'espace admin est ouvert.
    timer(0, 120_000)
      .pipe(
        switchMap(() => this.api.providerStatus().pipe(catchError(() => of(null)))),
        takeUntilDestroyed(inject(DestroyRef)),
      )
      .subscribe((s) => s && this.providers.set(s.providers));
    timer(0, 120_000)
      .pipe(
        switchMap(() => this.api.alerts().pipe(catchError(() => of([] as TransactionAlert[])))),
        takeUntilDestroyed(inject(DestroyRef)),
      )
      .subscribe((a) => this.alerts.set(a));
    this.api.dashboard(7).pipe(catchError(() => of(null))).subscribe((d) => {
      if (d) this.toProcess.set(d.contractsToProcess);
    });
  }

  protected providerLabel(state: ProviderState): string {
    return PROVIDER_LABELS[state];
  }
}
