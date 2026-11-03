import { DatePipe } from '@angular/common';
import { Component, input } from '@angular/core';

import { Transaction, TRANSACTION_STATUS_LABELS } from '../../core/api.models';
import { formatAmount } from '../../shared/formatting';

/** Résultat d'un paiement (maquette B3) : réussi, échoué, en attente ou annulé, avec son détail. */
@Component({
  selector: 'app-payment-outcome',
  imports: [DatePipe],
  template: `
    @let t = tx();
    <div [class]="'outcome ' + t.status">
      <span class="icon">
        <i [class]="icon()"></i>
      </span>
      <h3>
        @switch (t.status) {
          @case ('SUCCEEDED') { Paiement réussi }
          @case ('FAILED') { Paiement échoué }
          @case ('CANCELLED') { Paiement annulé }
          @default { {{ waitingLabel() }} }
        }
      </h3>
      <p class="amount">{{ formatAmount(t.amount, '') }}<span>{{ t.currency }}</span></p>
      @if (t.status === 'FAILED' && t.failureMessage) {
        <p class="muted">{{ t.failureMessage }}</p>
      }
    </div>
    <dl class="card details">
      <dt>Référence</dt>
      <dd>{{ t.reference }}</dd>
      <dt>Date</dt>
      <dd>{{ (t.completedAt ?? t.createdAt) | date: "dd/MM/yyyy 'à' HH:mm" }}</dd>
      <dt>Moyen</dt>
      <dd>{{ t.methodName }}{{ t.cardMaskedPan ? ' · ' + t.cardMaskedPan : '' }}</dd>
      @if (t.description) {
        <dt>Motif</dt>
        <dd>{{ t.description }}</dd>
      }
      <dt>Statut</dt>
      <dd>{{ labels[t.status] }}{{ t.providerStatus ? ' (' + t.providerStatus + ')' : '' }}</dd>
    </dl>
  `,
  styles: `
    .outcome { text-align: center; }
    .icon {
      display: inline-flex; align-items: center; justify-content: center;
      width: 72px; height: 72px; border-radius: 50%;
      background: #fef3c7; color: #b45309;
    }
    .icon .pi { font-size: 1.8rem; }
    .SUCCEEDED .icon { background: #dcfce7; color: #15803d; }
    .FAILED .icon, .CANCELLED .icon { background: #fee2e2; color: #b91c1c; }
    h3 { margin: 16px 0 4px; }
    .amount { margin: 0 0 12px; font-size: 2rem; font-weight: 600; }
    .amount span { margin-left: 6px; font-size: 1rem; color: var(--ep-text-muted); }
    .details { display: grid; grid-template-columns: max-content 1fr; margin: 16px 0 0; padding: 4px 16px; }
    dt, dd { padding: 10px 0; border-bottom: 1px solid var(--ep-border); }
    dt { color: var(--ep-text-muted); }
    dd { margin: 0; text-align: right; overflow-wrap: anywhere; }
    dt:last-of-type, dd:last-of-type { border-bottom: 0; }
  `,
})
export class PaymentOutcome {
  readonly tx = input.required<Transaction>();
  protected readonly labels = TRANSACTION_STATUS_LABELS;
  protected readonly formatAmount = formatAmount;

  protected icon(): string {
    switch (this.tx().status) {
      case 'SUCCEEDED':
        return 'pi pi-check';
      case 'FAILED':
      case 'CANCELLED':
        return 'pi pi-times';
      default:
        return 'pi pi-spin pi-spinner';
    }
  }

  protected waitingLabel(): string {
    return this.tx().category === 'WALLET' ? 'Confirmez sur votre téléphone' : 'Paiement en cours de confirmation';
  }
}
