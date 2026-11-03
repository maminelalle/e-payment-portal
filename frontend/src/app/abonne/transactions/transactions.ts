import { Component, inject, signal } from '@angular/core';
import { ButtonModule } from 'primeng/button';

import { Transaction } from '../../core/api.models';
import { AbonneApi } from '../abonne-api';
import { TransactionTable } from '../paiement/transaction-table';

const PAGE_SIZE = 20;

/** Historique des paiements de l'abonné (filtres et reçus PDF : semaine 10). */
@Component({
  selector: 'app-abonne-transactions',
  imports: [ButtonModule, TransactionTable],
  template: `
    <h1 class="page-title">Transactions</h1>
    <p class="page-subtitle">{{ total() }} paiement{{ total() > 1 ? 's' : '' }}</p>
    <div class="list">
      <app-transaction-table [transactions]="rows()" />
    </div>
    @if (rows().length < total()) {
      <p-button label="Afficher plus" severity="secondary" [outlined]="true" (onClick)="more()" [loading]="loading()" />
    }
  `,
  styles: `
    .list { margin: 24px 0 16px; }
  `,
})
export class AbonneTransactions {
  private readonly api = inject(AbonneApi);
  protected readonly rows = signal<Transaction[]>([]);
  protected readonly total = signal(0);
  protected readonly loading = signal(false);
  private page = 0;

  constructor() {
    this.more();
  }

  protected more(): void {
    this.loading.set(true);
    this.api.payments(this.page, PAGE_SIZE).subscribe((page) => {
      this.rows.update((rows) => [...rows, ...page.content]);
      this.total.set(page.totalElements);
      this.page++;
      this.loading.set(false);
    });
  }
}
