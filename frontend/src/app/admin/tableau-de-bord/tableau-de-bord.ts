import { DatePipe, NgTemplateOutlet } from '@angular/common';
import { Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ChartModule } from 'primeng/chart';
import { SelectModule } from 'primeng/select';
import { SelectButtonModule } from 'primeng/selectbutton';

import {
  AdminDashboard,
  ContractStatus,
  ContractToProcess,
  MonthFigure,
  SubscriberStatus,
  TRANSACTION_STATUS_LABELS,
  TransactionStatus,
} from '../../core/api.models';
import { ContractStatusTag } from '../../shared/contract-status-tag';
import { formatAmount } from '../../shared/formatting';
import { MethodLogo } from '../../shared/method-logo';
import { StatusTag } from '../../shared/status-tag';
import { TRANSACTION_STATUS_TONES } from '../../shared/transaction-table';
import { AdminApi } from '../admin-api';

const COUNT = new Intl.NumberFormat('fr-FR');
const SHORT_DAY = new Intl.DateTimeFormat('fr-FR', { day: 'numeric', month: 'short' });
const LONG_DAY = new Intl.DateTimeFormat('fr-FR', { weekday: 'long', day: 'numeric', month: 'long' });

/** Couleurs de lecture des graphiques (encre secondaire, grille discrète) : jamais la couleur d'une série. */
const INK_MUTED = '#64748b';
const GRID = '#f1f5f9';

/** Ordre de lecture des statuts : du cas courant aux cas de sortie. */
const SUBSCRIBER_ORDER: SubscriberStatus[] = ['ACTIVE', 'PENDING', 'SUSPENDED', 'DISABLED'];
const CONTRACT_ORDER: ContractStatus[] = [
  'ACTIVE', 'PENDING_SIGNATURE', 'SIGNED', 'DRAFT', 'SUSPENDED', 'EXPIRED', 'TERMINATED', 'REPLACED',
];
/** « Créée » est un état de quelques secondes : compté avec « En attente ». */
const TRANSACTION_ORDER: TransactionStatus[] = ['SUCCEEDED', 'FAILED', 'PENDING', 'CANCELLED', 'REFUNDED'];

export interface StatusRow<S> {
  status: S;
  count: number;
  /** Longueur de la barre, en % de la plus grande valeur du bloc. */
  bar: number;
  amount?: number;
}

function rows<S>(entries: { status: S; count: number; amount?: number }[]): StatusRow<S>[] {
  const max = Math.max(1, ...entries.map((e) => e.count));
  return entries.map((e) => ({ ...e, bar: (e.count / max) * 100 }));
}

/**
 * Tableau de bord administrateur (maquette A1, conception §3.1) : 4 indicateurs du mois, abonnés, contrats et
 * transactions par statut, volume quotidien, répartition par moyen, type et fournisseur, contrats à traiter,
 * dernières transactions.
 */
@Component({
  selector: 'app-tableau-de-bord',
  imports: [
    DatePipe, NgTemplateOutlet, FormsModule, RouterLink, ChartModule, SelectModule, SelectButtonModule,
    ContractStatusTag, MethodLogo, StatusTag,
  ],
  templateUrl: './tableau-de-bord.html',
  styleUrl: './tableau-de-bord.scss',
})
export class TableauDeBord {
  private readonly api = inject(AdminApi);

  protected readonly periods = [
    { label: '7 derniers jours', value: 7 },
    { label: '30 derniers jours', value: 30 },
    { label: '90 derniers jours', value: 90 },
  ];
  protected readonly days = signal(30);
  protected readonly data = signal<AdminDashboard | null>(null);
  protected readonly error = signal(false);

  protected readonly labels = TRANSACTION_STATUS_LABELS;
  protected readonly tones = TRANSACTION_STATUS_TONES;
  protected readonly amount = formatAmount;
  protected readonly count = (n: number) => COUNT.format(n);
  /** Indicateur : montant arrondi à l'unité (le détail au centime est dans l'infobulle). */
  protected readonly roundAmount = (n: number) => Math.round(n);

  protected readonly breakdowns = [
    { label: 'Moyen', value: 'method' },
    { label: 'Type', value: 'category' },
    { label: 'Fournisseur', value: 'provider' },
  ];
  protected readonly breakdown = signal<'method' | 'category' | 'provider'>('method');

  protected readonly subscriberRows = computed(() => {
    const counts = this.data()?.subscribersByStatus;
    return counts ? rows(SUBSCRIBER_ORDER.map((status) => ({ status, count: counts[status] ?? 0 }))) : [];
  });
  protected readonly subscriberTotal = computed(() => this.subscriberRows().reduce((sum, r) => sum + r.count, 0));

  protected readonly contractRows = computed(() => {
    const counts = this.data()?.contractsByStatus;
    return counts ? rows(CONTRACT_ORDER.map((status) => ({ status, count: counts[status] ?? 0 }))) : [];
  });
  protected readonly contractTotal = computed(() => this.contractRows().reduce((sum, r) => sum + r.count, 0));

  protected readonly transactionRows = computed(() => {
    const byStatus = new Map((this.data()?.transactionsByStatus ?? []).map((t) => [t.status, t]));
    return rows(
      TRANSACTION_ORDER.map((status) => {
        const merged = status === 'PENDING' ? [byStatus.get('CREATED'), byStatus.get('PENDING')] : [byStatus.get(status)];
        return {
          status,
          count: merged.reduce((sum, t) => sum + (t?.count ?? 0), 0),
          amount: merged.reduce((sum, t) => sum + (t?.amount ?? 0), 0),
        };
      }),
    );
  });

  /** Répartition par type (wallet / carte) ou par fournisseur, en barres. */
  protected readonly groupRows = computed(() => {
    const d = this.data();
    const groups = (this.breakdown() === 'category' ? d?.perCategory : d?.perProvider) ?? [];
    const total = groups.reduce((sum, g) => sum + g.count, 0);
    const max = Math.max(1, ...groups.map((g) => g.count));
    return groups.map((g) => ({
      ...g,
      bar: (g.count / max) * 100,
      percent: total === 0 ? 0 : Math.round((g.count / total) * 100),
    }));
  });

  /** Premier jour de la période, pour ouvrir les transactions avec le même filtre. */
  protected readonly periodStart = computed(() => {
    const start = new Date();
    start.setUTCDate(start.getUTCDate() - (this.days() - 1));
    return start.toISOString().slice(0, 10);
  });

  protected readonly periodTotal = computed(() =>
    (this.data()?.perMethod ?? []).reduce((sum, m) => sum + m.count, 0),
  );

  protected readonly shares = computed(() => {
    const total = this.periodTotal();
    return (this.data()?.perMethod ?? []).map((m) => ({
      ...m,
      percent: total === 0 ? 0 : Math.round((m.count / total) * 100),
    }));
  });

  /** Volume par jour : une seule série (pas de légende, le titre la nomme), ligne 2 px, aire légère. */
  protected readonly volumeChart = computed(() => {
    const points = this.data()?.perDay ?? [];
    return {
      labels: points.map((p) => SHORT_DAY.format(new Date(p.day + 'T00:00:00'))),
      datasets: [
        {
          label: 'Transactions',
          data: points.map((p) => p.count),
          borderColor: '#2563eb',
          backgroundColor: 'rgba(37, 99, 235, 0.08)',
          borderWidth: 2,
          fill: true,
          tension: 0.25,
          pointRadius: 0,
          pointHoverRadius: 5,
          pointHoverBorderWidth: 2,
          pointHoverBorderColor: '#ffffff',
          pointHoverBackgroundColor: '#2563eb',
        },
      ],
    };
  });

  protected readonly volumeOptions = computed(() => {
    const points = this.data()?.perDay ?? [];
    return {
      maintainAspectRatio: false,
      interaction: { mode: 'index', intersect: false },
      plugins: {
        legend: { display: false },
        tooltip: {
          displayColors: false,
          callbacks: {
            title: (items: { dataIndex: number }[]) => {
              const point = points[items[0]?.dataIndex ?? 0];
              return point ? LONG_DAY.format(new Date(point.day + 'T00:00:00')) : '';
            },
            label: (item: { dataIndex: number }) => {
              const point = points[item.dataIndex];
              if (!point) return '';
              const n = point.count;
              return [
                `${COUNT.format(n)} transaction${n > 1 ? 's' : ''}`,
                `Encaissé : ${formatAmount(point.collected)}`,
              ];
            },
          },
        },
      },
      scales: {
        x: {
          grid: { display: false },
          border: { color: '#e2e8f0' },
          ticks: { color: INK_MUTED, maxTicksLimit: 6, maxRotation: 0 },
        },
        y: {
          beginAtZero: true,
          border: { display: false },
          grid: { color: GRID },
          ticks: { color: INK_MUTED, precision: 0, maxTicksLimit: 5 },
        },
      },
    };
  });

  /** Répartition : couleur du moyen au catalogue (l'identité suit le moyen), 2 px d'espace entre les parts. */
  protected readonly methodChart = computed(() => {
    const shares = this.shares();
    return {
      labels: shares.map((s) => s.name),
      datasets: [
        {
          data: shares.map((s) => s.count),
          backgroundColor: shares.map((s) => s.color),
          hoverBackgroundColor: shares.map((s) => s.color),
          borderColor: '#ffffff',
          borderWidth: 2,
          hoverOffset: 4,
        },
      ],
    };
  });

  protected readonly methodOptions = {
    cutout: '70%',
    maintainAspectRatio: false,
    plugins: {
      legend: { display: false },
      tooltip: {
        callbacks: {
          label: (item: { label: string; parsed: number }) =>
            `${item.label} : ${COUNT.format(item.parsed)} transaction${item.parsed > 1 ? 's' : ''}`,
        },
      },
    },
  };

  constructor() {
    effect(() => {
      const days = this.days();
      untracked(() => this.load(days));
    });
  }

  /** Écart en % avec la même période du mois précédent ; null si rien à comparer. */
  protected trend(figure: MonthFigure): number | null {
    if (!figure.previous) return null;
    return Math.round(((figure.current - figure.previous) / figure.previous) * 1000) / 10;
  }

  protected formatTrend(value: number): string {
    return `${value > 0 ? '+' : ''}${value.toLocaleString('fr-FR', { maximumFractionDigits: 1 })} %`;
  }

  /** « envoyé le 30/10/2026 », « signé le 01/11/2026 »… comme sur la maquette. */
  protected contractStep(c: ContractToProcess): { label: string; date: string } {
    if (c.status === 'SIGNED' && c.signedAt) return { label: 'signé le', date: c.signedAt };
    if (c.status === 'PENDING_SIGNATURE' && c.sentAt) return { label: 'envoyé le', date: c.sentAt };
    return { label: 'créé le', date: c.createdAt };
  }

  private load(days: number): void {
    this.error.set(false);
    this.api.dashboard(days).subscribe({
      next: (d) => this.data.set(d),
      error: () => this.error.set(true),
    });
  }
}
