import { Component, DestroyRef, effect, inject, input, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { Subscription } from 'rxjs';

import { Transaction } from '../../core/api.models';
import { downloadBlob } from '../../shared/download';
import { AbonneApi } from '../abonne-api';
import { PaymentOutcome } from './payment-outcome';
import { watchPayment } from './payment-watch';

/**
 * Retour depuis la page de paiement carte de la passerelle. Le retour du navigateur ne prouve rien :
 * le statut est demandé au serveur, qui le vérifie auprès de la passerelle (ou via le webhook signé).
 */
@Component({
  selector: 'app-payment-return',
  imports: [RouterLink, ButtonModule, PaymentOutcome],
  template: `
    <div class="wrap">
      <h1 class="page-title">Paiement par carte</h1>
      @if (tx(); as t) {
        <div class="card box">
          <app-payment-outcome [tx]="t" />
          <div class="actions">
            @if (t.status === 'SUCCEEDED') {
              <p-button label="Télécharger le reçu" icon="pi pi-download" styleClass="w-full" (onClick)="downloadReceipt(t)" />
            }
            <p-button label="Retour à l'accueil" routerLink="/abonne" styleClass="w-full" [outlined]="t.status === 'SUCCEEDED'" />
            <p-button label="Mes transactions" severity="secondary" [outlined]="true" routerLink="/abonne/transactions" styleClass="w-full" />
          </div>
        </div>
      } @else if (error()) {
        <p class="card box muted">{{ error() }}</p>
      } @else {
        <p class="muted">Vérification du paiement auprès de la passerelle bancaire…</p>
      }
    </div>
  `,
  styles: `
    .wrap { max-width: 480px; margin: 0 auto; }
    .box { margin-top: 24px; padding: 24px; }
    .actions { display: flex; flex-direction: column; gap: 10px; margin-top: 24px; }
  `,
})
export class PaymentReturn {
  private readonly api = inject(AbonneApi);

  /** Paramètres ?ref=TRX-…&annule=1 posés par le portail dans les adresses de retour. */
  readonly ref = input<string>();
  readonly annule = input<string>();

  protected readonly tx = signal<Transaction | null>(null);
  protected readonly error = signal<string | null>(null);
  private watching: Subscription | null = null;

  constructor() {
    inject(DestroyRef).onDestroy(() => this.watching?.unsubscribe());
    effect(() => {
      const reference = this.ref();
      const cancelled = this.annule() === '1';
      untracked(() => this.load(reference, cancelled));
    });
  }

  protected downloadReceipt(t: Transaction): void {
    this.api.receipt(t.reference).subscribe((blob) => downloadBlob(blob, `recu-${t.reference}.pdf`));
  }

  private load(reference: string | undefined, cancelled: boolean): void {
    if (!reference) {
      this.error.set('Référence de paiement absente.');
      return;
    }
    this.watching?.unsubscribe();
    if (cancelled) {
      this.api.cancelPayment(reference).subscribe({
        next: (t) => this.tx.set(t),
        error: () => this.error.set('Paiement introuvable.'),
      });
      return;
    }
    this.watching = watchPayment(this.api, reference).subscribe({
      next: (t) => this.tx.set(t),
      error: () => this.error.set('Paiement introuvable.'),
    });
  }
}
