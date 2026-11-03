import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';

import { Contract, EntitledMethod, Transaction } from '../../core/api.models';
import { Session } from '../../core/session';
import { formatIsoDate } from '../../shared/formatting';
import { AbonneApi } from '../abonne-api';
import { MethodCards } from '../paiement/method-cards';
import { PaymentDrawer } from '../paiement/payment-drawer';
import { TransactionTable } from '../paiement/transaction-table';

/** Accueil abonné (maquette B1) : état du contrat, moyens autorisés, paiement. */
@Component({
  selector: 'app-accueil',
  imports: [DatePipe, RouterLink, ButtonModule, MethodCards, PaymentDrawer, TransactionTable],
  templateUrl: './accueil.html',
  styleUrl: './accueil.scss',
})
export class Accueil {
  private readonly api = inject(AbonneApi);
  protected readonly session = inject(Session);

  protected readonly contract = signal<Contract | null | undefined>(undefined);
  protected readonly methods = signal<EntitledMethod[]>([]);
  protected readonly paying = signal<EntitledMethod | null>(null);
  protected readonly latest = signal<Transaction[]>([]);
  protected readonly today = new Date();
  protected readonly formatDate = formatIsoDate;

  constructor() {
    this.api.currentContract().subscribe((c) => this.contract.set(c));
    this.api.myMethods().subscribe((m) => this.methods.set(m));
    this.loadLatest();
  }

  protected loadLatest(): void {
    this.api.payments(0, 3).subscribe((page) => this.latest.set(page.content));
  }
}
