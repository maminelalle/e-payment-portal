import { DatePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component, inject, input, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { ReceiptCheck } from '../core/api.models';
import { formatAmount } from '../shared/formatting';

/** Cible du QR code imprimé sur les reçus : confirme qu'un paiement existe et a réussi. */
@Component({
  selector: 'app-verification-recu',
  imports: [DatePipe, RouterLink],
  template: `
    <section class="card panel">
      <a class="brand" routerLink="/"><span class="brand-logo">EP</span><span class="brand-name">E-Payment</span></a>
      <h1>Vérification d’un reçu</h1>
      <p class="lead">Référence <bdi class="ep-ref">{{ reference() }}</bdi></p>
      @if (result(); as r) {
        @if (r.valid) {
          <div class="ep-callout ep-callout--success" role="status">
            <i class="pi pi-verified" aria-hidden="true"></i>
            <span><strong>Reçu authentique.</strong> Ce paiement a bien été confirmé par le fournisseur.</span>
          </div>
          <dl class="ep-dl details">
            <dt>Montant</dt><dd><bdi><strong>{{ formatAmount(r.amount, r.currency ?? 'MRU') }}</strong></bdi></dd>
            <dt>Date</dt><dd><bdi>{{ r.paidAt | date: 'dd/MM/yyyy HH:mm' }}</bdi></dd>
            <dt>Moyen</dt><dd>{{ r.methodName }}</dd>
            <dt>Payeur</dt><dd>{{ r.payee }}</dd>
          </dl>
        } @else {
          <div class="ep-callout ep-callout--warning" role="alert">
            <i class="pi pi-exclamation-triangle" aria-hidden="true"></i>
            <span><strong>Reçu non reconnu.</strong> Le code ne correspond à aucun paiement réussi. Vérifiez que
              vous avez scanné le QR code d’origine, sinon contactez l’émetteur du reçu.</span>
          </div>
        }
      } @else if (error()) {
        <div class="ep-callout ep-callout--warning" role="alert">
          <i class="pi pi-exclamation-triangle" aria-hidden="true"></i>
          <span>La vérification est momentanément indisponible. Réessayez dans quelques minutes.</span>
        </div>
      } @else {
        <span class="skeleton" style="height: 120px"></span>
      }
      <p class="foot">Cette page n’affiche jamais de numéro de carte ni de téléphone complet.</p>
    </section>
  `,
  styleUrl: './public-shell.scss',
  styles: `.details { margin-top: var(--space-5); }`,
})
export class VerificationRecu implements OnInit {
  private readonly http = inject(HttpClient);
  readonly reference = input.required<string>();
  /** Code imprimé dans le QR (?c=…). */
  readonly c = input<string>();
  protected readonly result = signal<ReceiptCheck | null>(null);
  protected readonly error = signal(false);
  protected readonly formatAmount = formatAmount;

  ngOnInit(): void {
    const code = this.c();
    this.http
      .get<ReceiptCheck>(`/api/public/recus/${encodeURIComponent(this.reference())}`, {
        params: code ? { c: code } : {},
      })
      .subscribe({ next: (r) => this.result.set(r), error: () => this.error.set(true) });
  }
}
