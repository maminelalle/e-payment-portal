import { DatePipe, NgTemplateOutlet } from '@angular/common';
import { Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ChartModule } from 'primeng/chart';
import { SelectModule } from 'primeng/select';

import { AdminDashboard, ContractToProcess, MonthFigure, TRANSACTION_STATUS_LABELS } from '../../core/api.models';
import { ContractStatusTag } from '../../shared/contract-status-tag';
import { formatAmount } from '../../shared/formatting';
import { MethodLogo } from '../../shared/method-logo';
import { TRANSACTION_STATUS_TONES } from '../../shared/transaction-table';
import { AdminApi } from '../admin-api';

const COUNT = new Intl.NumberFormat('fr-FR');
const SHORT_DAY = new Intl.DateTimeFormat('fr-FR', { day: 'numeric', month: 'short' });
const LONG_DAY = new Intl.DateTimeFormat('fr-FR', { weekday: 'long', day: 'numeric', month: 'long' });

/** Couleurs de lecture des graphiques (encre secondaire, grille discrète) : jamais la couleur d'une série. */
const INK_MUTED = '#64748b';
const GRID = '#f1f5f9';

/**
 * Tableau de bord administrateur (maquette A1) : 4 indicateurs du mois, volume quotidien, répartition par
 * moyen, contrats à traiter, dernières transactions.
 */
@Component({
  selector: 'app-tableau-de-bord',
  imports: [DatePipe, NgTemplateOutlet, FormsModule, RouterLink, ChartModule, SelectModule, ContractStatusTag, MethodLogo],
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
