import { Component, inject, signal } from '@angular/core';

import { EntitledMethod } from '../../core/api.models';
import { AbonneApi } from '../abonne-api';
import { MethodCards } from '../paiement/method-cards';
import { PaymentDrawer } from '../paiement/payment-drawer';

/** « Moyens de paiement » : seuls les moyens prévus par le contrat sont affichés (maquette B3). */
@Component({
  selector: 'app-abonne-moyens',
  imports: [MethodCards, PaymentDrawer],
  template: `
    <h1 class="page-title">Moyens de paiement</h1>
    <p class="page-subtitle">Seuls les moyens prévus par votre contrat sont affichés.</p>
    <div class="list">
      @if (methods() === null) {
        <p class="muted">Chargement…</p>
      } @else if (methods()!.length) {
        <app-method-cards [methods]="methods()!" [showDetails]="true" (pay)="paying.set($event)" />
      } @else {
        <p class="card empty muted">
          Aucun moyen de paiement ouvert : ils apparaissent dès que votre contrat est signé et actif.
        </p>
      }
    </div>
    <app-payment-drawer [method]="paying()" (closed)="paying.set(null)" />
  `,
  styles: `
    .list {
      margin-top: 24px;
    }
    .empty {
      padding: 24px;
    }
  `,
})
export class AbonneMoyens {
  private readonly api = inject(AbonneApi);
  protected readonly methods = signal<EntitledMethod[] | null>(null);
  protected readonly paying = signal<EntitledMethod | null>(null);

  constructor() {
    this.api.myMethods().subscribe((m) => this.methods.set(m));
  }
}
