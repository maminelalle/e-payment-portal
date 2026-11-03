import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';

import { Contract, EntitledMethod, LimitUsage, SubscriberDashboard, Transaction } from '../../core/api.models';
import { Session } from '../../core/session';
import { downloadBlob } from '../../shared/download';
import { formatAmount, formatIsoDate } from '../../shared/formatting';
import { MethodLogo } from '../../shared/method-logo';
import { AbonneApi } from '../abonne-api';
import { MethodCards } from '../paiement/method-cards';
import { PaymentDrawer } from '../paiement/payment-drawer';
import { TransactionTable } from '../../shared/transaction-table';

/** Accueil abonné (maquette B1) : état du contrat, chiffres du mois, plafonds, moyens autorisés, paiement. */
@Component({
  selector: 'app-accueil',
  imports: [DatePipe, RouterLink, ButtonModule, MethodCards, MethodLogo, PaymentDrawer, TransactionTable],
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
  protected readonly formatAmount = formatAmount;
  protected readonly dashboard = signal<SubscriberDashboard | null>(null);

  constructor() {
    this.api.currentContract().subscribe((c) => this.contract.set(c));
    this.api.myMethods().subscribe((m) => this.methods.set(m));
    this.loadLatest();
  }

  /** Part du plafond mensuel utilisée, bornée à 100 % pour la jauge. */
  protected usedPercent(limit: LimitUsage): number {
    return limit.monthlyLimit > 0 ? Math.min(100, Math.round((limit.engaged / limit.monthlyLimit) * 100)) : 0;
  }

  /** Largeur exacte de la jauge (un petit montant reste visible au lieu d'être arrondi à 0 %). */
  protected fillPercent(limit: LimitUsage): number {
    return limit.monthlyLimit > 0 ? Math.min(100, (limit.engaged / limit.monthlyLimit) * 100) : 0;
  }

  protected usageTone(limit: LimitUsage): string {
    const used = this.usedPercent(limit);
    return used >= 100 ? 'critical' : used >= 80 ? 'warning' : 'normal';
  }

  protected downloadReceipt(tx: Transaction): void {
    this.api.receipt(tx.reference).subscribe((blob) => downloadBlob(blob, `recu-${tx.reference}.pdf`));
  }

  protected loadLatest(): void {
    this.api.payments(0, 3).subscribe((page) => this.latest.set(page.content));
    this.api.dashboard().subscribe((d) => this.dashboard.set(d));
  }
}
