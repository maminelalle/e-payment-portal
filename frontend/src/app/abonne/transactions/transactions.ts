import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ButtonModule } from 'primeng/button';
import { debounceTime, Subject, switchMap } from 'rxjs';

import { Transaction, TransactionFilter } from '../../core/api.models';
import { downloadBlob } from '../../shared/download';
import { TransactionFilters } from '../../shared/transaction-filters';
import { TransactionTable } from '../../shared/transaction-table';
import { AbonneApi } from '../abonne-api';

const PAGE_SIZE = 20;

/** Historique des paiements de l'abonné, filtrable, avec reçus PDF. */
@Component({
  selector: 'app-abonne-transactions',
  imports: [ButtonModule, TransactionFilters, TransactionTable],
  template: `
    <h1 class="page-title">Transactions</h1>
    <p class="page-subtitle">{{ total() }} paiement{{ total() > 1 ? 's' : '' }}</p>
    <div class="filters">
      <app-transaction-filters [methods]="methods()" (changed)="applyFilter($event)" />
    </div>
    <app-transaction-table [transactions]="rows()" emptyText="Aucun paiement ne correspond à ces critères."
      (receipt)="download($event)" />
    @if (rows().length < total()) {
      <p-button label="Afficher plus" severity="secondary" [outlined]="true" (onClick)="more()" [loading]="loading()" styleClass="more" />
    }
  `,
  styles: `
    .filters { margin-top: 24px; }
    :host ::ng-deep .more { margin-top: 16px; }
  `,
})
export class AbonneTransactions {
  private readonly api = inject(AbonneApi);
  protected readonly rows = signal<Transaction[]>([]);
  protected readonly total = signal(0);
  protected readonly loading = signal(false);
  protected readonly methods = signal<{ code: string; name: string }[]>([]);
  private filter: TransactionFilter = {};
  private page = 0;
  private readonly reload$ = new Subject<void>();

  constructor() {
    this.api.myMethods().subscribe((m) => this.methods.set(m.map(({ code, name }) => ({ code, name }))));
    this.reload$
      .pipe(
        debounceTime(250),
        switchMap(() => {
          this.loading.set(true);
          return this.api.payments(0, PAGE_SIZE, this.filter);
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

  protected applyFilter(filter: TransactionFilter): void {
    this.filter = filter;
    this.reload$.next();
  }

  protected more(): void {
    this.loading.set(true);
    this.api.payments(this.page, PAGE_SIZE, this.filter).subscribe((page) => {
      this.rows.update((rows) => [...rows, ...page.content]);
      this.page++;
      this.loading.set(false);
    });
  }

  protected download(tx: Transaction): void {
    this.api.receipt(tx.reference).subscribe((blob) => downloadBlob(blob, `recu-${tx.reference}.pdf`));
  }
}
