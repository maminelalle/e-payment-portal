import { DatePipe } from '@angular/common';
import { Component, input, output } from '@angular/core';
import { ButtonModule } from 'primeng/button';

import { Transaction, TRANSACTION_STATUS_LABELS, TransactionStatus } from '../core/api.models';
import { formatAmount } from './formatting';
import { MethodLogo } from './method-logo';

export const TRANSACTION_STATUS_TONES: Record<TransactionStatus, string> = {
  CREATED: 'warning',
  PENDING: 'warning',
  SUCCEEDED: 'success',
  FAILED: 'danger',
  CANCELLED: 'neutral',
  REFUNDED: 'neutral',
};

/**
 * Tableau des transactions (maquette B1 : référence, date, moyen, montant, statut, reçu).
 * Côté admin, une colonne « Abonné » s'ajoute.
 */
@Component({
  selector: 'app-transaction-table',
  imports: [DatePipe, ButtonModule, MethodLogo],
  template: `
    <div class="card table">
      <table>
        <thead>
          <tr>
            <th>Référence</th>
            <th>Date</th>
            @if (showSubscriber()) {
              <th>Abonné</th>
            }
            <th>Moyen</th>
            <th class="amount">Montant</th>
            <th>Statut</th>
            <th class="action">Reçu</th>
          </tr>
        </thead>
        <tbody>
          @for (t of transactions(); track t.reference) {
            <tr>
              <td><bdi class="ep-ref">{{ t.reference }}</bdi></td>
              <td>
                <bdi class="ep-ltr">{{ t.createdAt | date: 'dd/MM/yyyy' }}</bdi>
                <span class="ep-cell-secondary"><bdi>{{ t.createdAt | date: 'HH:mm' }}</bdi></span>
              </td>
              @if (showSubscriber()) {
                <td>
                  <span class="ep-cell-primary">{{ t.subscriberName }}</span>
                  <bdi class="ep-ref ep-cell-secondary">{{ t.subscriberReference }}</bdi>
                </td>
              }
              <td>
                <span class="method">
                  <app-method-logo [method]="{ name: t.methodName, shortLabel: t.shortLabel, color: t.color, logoUrl: t.logoUrl }" [size]="28" />
                  {{ t.methodName }}
                </span>
              </td>
              <td class="amount"><bdi>{{ formatAmount(t.amount, t.currency) }}</bdi></td>
              <td>
                <span [class]="'status-pill ' + tones[t.status]" [title]="t.failureMessage ?? ''" [attr.aria-description]="t.failureMessage">{{ labels[t.status] }}</span>
                @if (t.status === 'FAILED' && t.failureMessage) { <span class="failure">{{ t.failureMessage }}</span> }
              </td>
              <td>
                @if (t.status === 'SUCCEEDED') {
                  <p-button icon="pi pi-download" label="Reçu" [text]="true" size="small"
                    [ariaLabel]="'Télécharger le reçu ' + t.reference" (onClick)="receipt.emit(t)" />
                } @else {
                  <span class="ep-subtle">—</span>
                }
              </td>
            </tr>
          } @empty {
            <tr>
              <td [attr.colspan]="showSubscriber() ? 7 : 6" class="empty">{{ emptyText() }}</td>
            </tr>
          }
        </tbody>
      </table>
    </div>
  `,
  styles: `
    .table { overflow-x: auto; }
    table { width: 100%; border-collapse: collapse; min-width: 640px; font-size: 14px; line-height: 20px; }
    th, td { padding: var(--space-3) var(--space-4); text-align: start; border-block-end: 1px solid var(--border); vertical-align: middle; }
    th { font-size: 13px; font-weight: 600; color: var(--ink-muted); background: var(--surface-sunken); white-space: nowrap; }
    tbody tr:hover td { background: var(--surface-sunken); }
    tbody tr:last-child td { border-block-end: 0; }
    .amount { text-align: end; white-space: nowrap; font-weight: 600; font-variant-numeric: tabular-nums; }
    .action { text-align: end; }
    td:last-child { text-align: end; }
    .failure { display: block; max-width: 28ch; margin-top: 4px; font-size: 12px; line-height: 16px; color: var(--ink-subtle); white-space: normal; }
    .method { display: inline-flex; align-items: center; gap: 10px; }
    .small { font-size: 0.8rem; }
    .empty { text-align: center !important; color: var(--ink-muted); padding: var(--space-12) var(--space-6); }
  `,
})
export class TransactionTable {
  readonly transactions = input.required<Transaction[]>();
  readonly showSubscriber = input(false);
  readonly emptyText = input("Aucun paiement pour l'instant.");
  /** Demande de téléchargement du reçu d'un paiement réussi. */
  readonly receipt = output<Transaction>();
  protected readonly labels = TRANSACTION_STATUS_LABELS;
  protected readonly tones = TRANSACTION_STATUS_TONES;
  protected readonly formatAmount = formatAmount;
}
