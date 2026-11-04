import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, effect, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { DrawerModule } from 'primeng/drawer';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { Subscription } from 'rxjs';

import { ApiProblem, EntitledMethod, isFinal, Transaction } from '../../core/api.models';
import { downloadBlob } from '../../shared/download';
import { formatAmount } from '../../shared/formatting';
import { MethodLogo } from '../../shared/method-logo';
import { AbonneApi } from '../abonne-api';
import { PaymentOutcome } from './payment-outcome';
import { watchPayment } from './payment-watch';

/**
 * Paiement avec un moyen autorisé (maquette B3). Le serveur revérifie droit et plafonds à chaque appel.
 * Wallet : l'abonné confirme sur son téléphone, l'écran suit la confirmation.
 * Carte : redirection vers la page sécurisée de la passerelle (le portail ne voit jamais la carte).
 */
@Component({
  selector: 'app-payment-drawer',
  imports: [FormsModule, RouterLink, ButtonModule, DrawerModule, InputNumberModule, InputTextModule, MethodLogo,
    PaymentOutcome],
  templateUrl: './payment-drawer.html',
  styleUrl: './payment-drawer.scss',
})
export class PaymentDrawer {
  private readonly api = inject(AbonneApi);

  /** Moyen choisi ; null = tiroir fermé. */
  readonly method = input<EntitledMethod | null>(null);
  readonly closed = output<void>();
  /** Émis quand un paiement est terminé (pour rafraîchir l'historique). */
  readonly completed = output<Transaction>();

  protected readonly formatAmount = formatAmount;
  protected readonly busy = signal(false);
  protected readonly redirecting = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly tx = signal<Transaction | null>(null);
  protected amount: number | null = null;
  protected phone = '';
  protected description = '';
  /** Une clé par tentative : renvoyer le formulaire ne crée jamais un second paiement. */
  private idempotencyKey = '';
  private watching: Subscription | null = null;

  constructor() {
    inject(DestroyRef).onDestroy(() => this.watching?.unsubscribe());
    effect(() => {
      if (this.method()) {
        this.amount = null;
        this.phone = '';
        this.description = '';
        this.idempotencyKey = crypto.randomUUID();
        this.error.set(null);
        this.tx.set(null);
        this.redirecting.set(false);
      }
    });
  }

  protected close(): void {
    this.watching?.unsubscribe();
    this.closed.emit();
  }

  protected isFinal = isFinal;

  protected pay(): void {
    const method = this.method();
    if (!method) return;
    if (!this.amount || this.amount <= 0) {
      this.error.set('Indiquez un montant.');
      return;
    }
    if (this.amount > method.perTransactionLimit) {
      this.error.set(`Le plafond par transaction est de ${formatAmount(method.perTransactionLimit, method.currency)}.`);
      return;
    }
    if (method.category === 'WALLET' && !this.phone.trim()) {
      this.error.set(`Indiquez le numéro de votre wallet ${method.name}.`);
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    this.api
      .pay(
        {
          method: method.code,
          amount: this.amount,
          payerPhone: method.category === 'WALLET' ? this.phone : null,
          description: this.description.trim() || null,
        },
        this.idempotencyKey,
      )
      .subscribe({
        next: (tx) => {
          this.busy.set(false);
          if (tx.redirectUrl) {
            // Carte : la saisie se fait sur la page de la passerelle, qui ramène ensuite sur le portail.
            this.redirecting.set(true);
            window.location.assign(tx.redirectUrl);
            return;
          }
          this.show(tx);
        },
        error: (error: unknown) => {
          this.busy.set(false);
          const problem = error instanceof HttpErrorResponse ? (error.error as ApiProblem | null) : null;
          const fields = Object.values(problem?.errors ?? {});
          // Pas de réponse exploitable (réseau, passerelle) : le dire clairement plutôt qu'un message générique.
          const unreachable = error instanceof HttpErrorResponse && (error.status === 0 || error.status >= 502);
          this.error.set(
            fields.length
              ? fields.join(' ')
              : unreachable
                ? 'Le service de paiement ne répond pas. Vérifiez l’historique avant de réessayer : une même demande n’est jamais débitée deux fois.'
                : (problem?.detail ?? 'Paiement impossible pour le moment.'),
          );
        },
      });
  }

  protected downloadReceipt(): void {
    const tx = this.tx();
    if (tx?.status === 'SUCCEEDED') {
      this.api.receipt(tx.reference).subscribe((blob) => downloadBlob(blob, `recu-${tx.reference}.pdf`));
    }
  }

  private show(tx: Transaction): void {
    this.tx.set(tx);
    if (isFinal(tx.status)) {
      this.completed.emit(tx);
      return;
    }
    this.watching?.unsubscribe();
    this.watching = watchPayment(this.api, tx.reference).subscribe((latest) => {
      this.tx.set(latest);
      if (isFinal(latest.status)) {
        this.completed.emit(latest);
      }
    });
  }
}
