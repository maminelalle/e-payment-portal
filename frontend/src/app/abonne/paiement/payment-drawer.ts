import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, effect, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { DrawerModule } from 'primeng/drawer';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';

import { ApiProblem, EntitledMethod, PaymentResult } from '../../core/api.models';
import { formatAmount } from '../../shared/formatting';
import { AbonneApi } from '../abonne-api';

/**
 * Paiement avec un moyen autorisé (maquette B3). Le serveur revérifie le droit et les plafonds :
 * l'interface n'est qu'une aide, un refus serveur est affiché tel quel.
 */
@Component({
  selector: 'app-payment-drawer',
  imports: [DatePipe, FormsModule, RouterLink, ButtonModule, DrawerModule, InputNumberModule, InputTextModule],
  templateUrl: './payment-drawer.html',
  styleUrl: './payment-drawer.scss',
})
export class PaymentDrawer {
  private readonly api = inject(AbonneApi);

  /** Moyen choisi ; null = tiroir fermé. */
  readonly method = input<EntitledMethod | null>(null);
  readonly closed = output<void>();

  protected readonly formatAmount = formatAmount;
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly result = signal<PaymentResult | null>(null);
  protected amount: number | null = null;
  protected phone = '';
  protected description = '';

  constructor() {
    effect(() => {
      if (this.method()) {
        this.amount = null;
        this.phone = '';
        this.description = '';
        this.error.set(null);
        this.result.set(null);
      }
    });
  }

  protected close(): void {
    this.closed.emit();
  }

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
      .pay({
        method: method.code,
        amount: this.amount,
        payerPhone: method.category === 'WALLET' ? this.phone : null,
        description: this.description.trim() || null,
      })
      .subscribe({
        next: (result) => {
          this.busy.set(false);
          this.result.set(result);
        },
        error: (error: unknown) => {
          this.busy.set(false);
          const problem = error instanceof HttpErrorResponse ? (error.error as ApiProblem | null) : null;
          const fields = Object.values(problem?.errors ?? {});
          this.error.set(fields.length ? fields.join(' ') : (problem?.detail ?? 'Paiement impossible pour le moment.'));
        },
      });
  }
}
