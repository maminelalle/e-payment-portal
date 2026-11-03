import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ButtonModule } from 'primeng/button';
import { debounceTime, Subject, switchMap } from 'rxjs';

import { Transaction, TransactionFilter } from '../../core/api.models';
import { downloadBlob } from '../../shared/download';
import { formatAmount } from '../../shared/formatting';
import { TransactionFilters } from '../../shared/transaction-filters';
import { TransactionTable } from '../../shared/transaction-table';
import { AdminApi } from '../admin-api';

const PAGE_SIZE = 20;

/** Transactions de tous les abonnés (lecture seule) : filtres, statut fournisseur, reçus. */
@Component({
  selector: 'app-admin-transactions',
  imports: [ButtonModule, TransactionFilters, TransactionTable],
  template: `
    <div class="page-header">
      <div>
        <h1 class="page-title">Transactions</h1>
        <p class="page-subtitle">
          {{ total() }} transaction{{ total() > 1 ? 's' : '' }}
          @if (pageAmount() > 0) { · {{ formatAmount(pageAmount()) }} réussis sur cette page }
        </p>
      </div>
    </div>
    <app-transaction-filters [methods]="methods()" (changed)="applyFilter($event)" />
    <app-transaction-table [transactions]="rows()" [showSubscriber]="true"
      emptyText="Aucune transaction ne correspond à ces critères." (receipt)="download($event)" />
    @if (rows().length < total()) {
      <p-button label="Afficher plus" severity="secondary" [outlined]="true" (onClick)="more()" [loading]="loading()" styleClass="more" />
    }
  `,
  styles: `:host ::ng-deep .more { margin-top: 16px; }`,
})
export class AdminTransactions {
  private readonly api = inject(AdminApi);
  protected readonly rows = signal<Transaction[]>([]);
  protected readonly total = signal(0);
  protected readonly loading = signal(false);
  protected readonly methods = signal<{ code: string; name: string }[]>([]);
  protected readonly formatAmount = formatAmount;
  private filter: TransactionFilter = {};
  private page = 0;
  private readonly reload$ = new Subject<void>();

  constructor() {
    this.api.paymentMethods().subscribe((m) => this.methods.set(m.map(({ code, name }) => ({ code, name }))));
    this.reload$
      .pipe(
        debounceTime(250),
        switchMap(() => {
          this.loading.set(true);
          return this.api.transactions(0, PAGE_SIZE, this.filter);
        }),
        takeUntilDestroyed(inject(DestroyRef)),
      )
      .subscribe((page) => {
        this.rows.set(page.content);
        this.total.set(page.totalElements);
        this.page = 1;
        this.loading.set(false);
      });
    this.reload$.next();
  }

  protected pageAmount(): number {
    return this.rows()
      .filter((t) => t.status === 'SUCCEEDED')
      .reduce((sum, t) => sum + t.amount, 0);
  }

  protected applyFilter(filter: TransactionFilter): void {
    this.filter = filter;
    this.reload$.next();
  }

  protected more(): void {
    this.loading.set(true);
    this.api.transactions(this.page, PAGE_SIZE, this.filter).subscribe((page) => {
      this.rows.update((rows) => [...rows, ...page.content]);
      this.page++;
      this.loading.set(false);
    });
  }

  protected download(tx: Transaction): void {
    this.api.transactionReceipt(tx.reference).subscribe((blob) => downloadBlob(blob, `recu-${tx.reference}.pdf`));
  }
}
