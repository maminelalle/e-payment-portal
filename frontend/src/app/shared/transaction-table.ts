import { DatePipe } from '@angular/common';
import { Component, input, output } from '@angular/core';
import { ButtonModule } from 'primeng/button';

import { Transaction, TRANSACTION_STATUS_LABELS, TransactionStatus } from '../core/api.models';
import { formatAmount } from './formatting';
import { MethodLogo } from './method-logo';

const TONES: Record<TransactionStatus, string> = {
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
            <th>Reçu</th>
          </tr>
        </thead>
        <tbody>
          @for (t of transactions(); track t.reference) {
            <tr>
              <td class="muted">{{ t.reference }}</td>
              <td>{{ t.createdAt | date: "dd/MM/yyyy 'à' HH:mm" }}</td>
              @if (showSubscriber()) {
                <td>
                  <div>{{ t.subscriberName }}</div>
                  <div class="muted small">{{ t.subscriberReference }}</div>
                </td>
              }
              <td>
                <span class="method">
                  <app-method-logo [method]="{ name: t.methodName, shortLabel: t.shortLabel, color: t.color, logoUrl: t.logoUrl }" [size]="28" />
                  {{ t.methodName }}
                </span>
              </td>
              <td class="amount">{{ formatAmount(t.amount, t.currency) }}</td>
              <td>
                <span [class]="'status-pill ' + tones[t.status]" [title]="t.failureMessage ?? ''">{{ labels[t.status] }}</span>
              </td>
              <td>
                @if (t.status === 'SUCCEEDED') {
                  <p-button icon="pi pi-download" [text]="true" [rounded]="true" size="small"
                    [ariaLabel]="'Télécharger le reçu ' + t.reference" (onClick)="receipt.emit(t)" />
                } @else {
                  <span class="muted">—</span>
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
    table { width: 100%; border-collapse: collapse; min-width: 640px; }
    th, td { padding: 12px 16px; text-align: left; border-bottom: 1px solid var(--ep-border); }
    th { font-size: 0.75rem; letter-spacing: 0.04em; text-transform: uppercase; color: var(--ep-text-muted); font-weight: 600; }
    tbody tr:last-child td { border-bottom: 0; }
    .amount { text-align: right; white-space: nowrap; font-weight: 600; }
    .method { display: inline-flex; align-items: center; gap: 10px; }
    .small { font-size: 0.8rem; }
    .empty { text-align: center; color: var(--ep-text-muted); padding: 24px; }
  `,
})
export class TransactionTable {
  readonly transactions = input.required<Transaction[]>();
  readonly showSubscriber = input(false);
  readonly emptyText = input("Aucun paiement pour l'instant.");
  /** Demande de téléchargement du reçu d'un paiement réussi. */
  readonly receipt = output<Transaction>();
  protected readonly labels = TRANSACTION_STATUS_LABELS;
  protected readonly tones = TONES;
  protected readonly formatAmount = formatAmount;
}
