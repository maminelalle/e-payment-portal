import { DatePipe } from '@angular/common';
import { Component, input } from '@angular/core';

import { Transaction, TRANSACTION_STATUS_LABELS, TransactionStatus } from '../../core/api.models';
import { formatAmount } from '../../shared/formatting';
import { MethodLogo } from '../../shared/method-logo';

const TONES: Record<TransactionStatus, string> = {
  CREATED: 'warning',
  PENDING: 'warning',
  SUCCEEDED: 'success',
  FAILED: 'danger',
  CANCELLED: 'neutral',
  REFUNDED: 'neutral',
};

/** Tableau des transactions (maquette B1 : référence, date, moyen, montant, statut, reçu). */
@Component({
  selector: 'app-transaction-table',
  imports: [DatePipe, MethodLogo],
  template: `
    <div class="card table">
      <table>
        <thead>
          <tr>
            <th>Référence</th>
            <th>Date</th>
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
              <td class="muted">—</td>
            </tr>
          } @empty {
            <tr>
              <td colspan="6" class="empty">Aucun paiement pour l'instant.</td>
            </tr>
          }
        </tbody>
      </table>
    </div>
  `,
  styles: `
    .table { overflow-x: auto; }
    table { width: 100%; border-collapse: collapse; min-width: 640px; }
    th, td { padding: 14px 16px; text-align: left; border-bottom: 1px solid var(--ep-border); }
    th { font-size: 0.75rem; letter-spacing: 0.04em; text-transform: uppercase; color: var(--ep-text-muted); font-weight: 600; }
    tbody tr:last-child td { border-bottom: 0; }
    .amount { text-align: right; white-space: nowrap; font-weight: 600; }
    .method { display: inline-flex; align-items: center; gap: 10px; }
    .empty { text-align: center; color: var(--ep-text-muted); padding: 24px; }
  `,
})
export class TransactionTable {
  readonly transactions = input.required<Transaction[]>();
  protected readonly labels = TRANSACTION_STATUS_LABELS;
  protected readonly tones = TONES;
  protected readonly formatAmount = formatAmount;
}
