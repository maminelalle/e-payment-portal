import { Component, computed, DestroyRef, inject, input, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { debounceTime, Subject, switchMap } from 'rxjs';

import { Transaction, TRANSACTION_STATUS_LABELS, TransactionFilter, TransactionStatus } from '../../core/api.models';
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
          @if (collected() > 0) { · {{ formatAmount(collected()) }} encaissés sur la période }
          @if (commissions() > 0) { · {{ formatAmount(commissions()) }} de commissions }
        </p>
      </div>
      <p-button label="Exporter (Excel, CSV)" icon="pi pi-file-excel" severity="secondary" [outlined]="true" [loading]="exporting()"
        [disabled]="!total()" (onClick)="exportCsv()" />
    </div>
    <app-transaction-filters [methods]="methods()" [initialReference]="q()" [initialStatus]="status()" [initialFrom]="du()" (changed)="applyFilter($event)" />
    <app-transaction-table [transactions]="rows()" [showSubscriber]="true" [showCommission]="true"
      emptyText="Aucune transaction ne correspond à ces critères." (receipt)="download($event)" />
    @if (rows().length < total()) {
      <p-button label="Afficher plus" severity="secondary" [outlined]="true" (onClick)="more()" [loading]="loading()" styleClass="more" />
    }
  `,
  styles: `:host ::ng-deep .more { margin-top: 16px; }`,
})
export class AdminTransactions implements OnInit {
  private readonly api = inject(AdminApi);
  /** ?q=TRX-… : ouverture déjà filtrée sur une référence. */
  readonly q = input<string>();
  /** Statut et début de période depuis l'URL (?statut=FAILED&du=2026-10-07, liens du tableau de bord). */
  readonly statut = input<string>();
  readonly du = input<string>();
  protected readonly status = computed(() => {
    const statut = this.statut();
    return statut && statut in TRANSACTION_STATUS_LABELS ? (statut as TransactionStatus) : null;
  });
  protected readonly rows = signal<Transaction[]>([]);
  protected readonly total = signal(0);
  protected readonly collected = signal(0);
  protected readonly commissions = signal(0);
  protected readonly loading = signal(false);
  protected readonly exporting = signal(false);
  private readonly messages = inject(MessageService);
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
          this.api.transactionTotals(this.filter).subscribe({
            next: (t) => { this.collected.set(t.collected); this.commissions.set(t.commissions); },
            error: () => { this.collected.set(0); this.commissions.set(0); },
          });
          return this.api.transactions(0, PAGE_SIZE, this.filter);
        }),
        takeUntilDestroyed(inject(DestroyRef)),
      )
      .subscribe((page) => {
        this.rows.set(page.items);
        this.total.set(page.total);
        this.page = 1;
        this.loading.set(false);
      });
  }

  ngOnInit(): void {
    this.filter = { q: this.q() || null, statut: this.status(), du: this.du() || null };
    this.reload$.next();
  }

  protected applyFilter(filter: TransactionFilter): void {
    this.filter = filter;
    this.reload$.next();
  }

  protected more(): void {
    this.loading.set(true);
    this.api.transactions(this.page, PAGE_SIZE, this.filter).subscribe((page) => {
      this.rows.update((rows) => [...rows, ...page.items]);
      this.page++;
      this.loading.set(false);
    });
  }

  protected exportCsv(): void {
    this.exporting.set(true);
    this.api.transactionsCsv(this.filter).subscribe({
      next: (blob) => {
        this.exporting.set(false);
        downloadBlob(blob, 'transactions.csv');
      },
      error: () => {
        this.exporting.set(false);
        this.messages.add({ severity: 'error', summary: 'Export impossible', detail: 'Réessayez dans quelques instants.' });
      },
    });
  }

  protected download(tx: Transaction): void {
    this.api.transactionReceipt(tx.reference).subscribe((blob) => downloadBlob(blob, `recu-${tx.reference}.pdf`));
  }
}
