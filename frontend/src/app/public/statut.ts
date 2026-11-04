import { DatePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { ProviderState, PublicStatus } from '../core/api.models';

const LABELS: Record<ProviderState, { text: string; tone: string }> = {
  UP: { text: 'Opérationnel', tone: 'success' },
  DOWN: { text: 'Indisponible', tone: 'danger' },
  SIMULATED: { text: 'Simulé', tone: 'neutral' },
};

/** Page publique de statut : disponibilité du portail et de chaque moyen de paiement, sans connexion. */
@Component({
  selector: 'app-statut',
  imports: [DatePipe, RouterLink],
  template: `
    <section class="card panel">
      <a class="brand" routerLink="/"><span class="brand-logo">EP</span><span class="brand-name">E-Payment</span></a>
      <h1>État du service</h1>
      @if (status(); as s) {
        <div [class]="'ep-callout ' + (allUp() ? 'ep-callout--success' : 'ep-callout--warning')" role="status">
          <i [class]="allUp() ? 'pi pi-check-circle' : 'pi pi-exclamation-triangle'" aria-hidden="true"></i>
          <span>{{ allUp() ? 'Tous les services fonctionnent normalement.' : 'Un ou plusieurs moyens de paiement sont perturbés.' }}</span>
        </div>
        <ul class="list">
          <li>
            <span>Portail E-Payment</span>
            <span class="status-pill success">Opérationnel</span>
          </li>
          @for (p of s.providers; track p.name) {
            <li>
              <span>{{ p.name }}</span>
              <span [class]="'status-pill ' + labels[p.state].tone">{{ labels[p.state].text }}</span>
            </li>
          }
        </ul>
        <p class="foot">Dernière vérification : <bdi>{{ s.checkedAt | date: 'dd/MM/yyyy HH:mm' }}</bdi></p>
      } @else if (error()) {
        <div class="ep-callout ep-callout--warning" role="alert">
          <i class="pi pi-exclamation-triangle" aria-hidden="true"></i>
          <span>Le portail ne répond pas pour le moment. Réessayez dans quelques minutes.</span>
        </div>
      } @else {
        <span class="skeleton" style="height: 160px"></span>
      }
    </section>
  `,
  styleUrl: './public-shell.scss',
  styles: `
    .list { list-style: none; margin: var(--space-5) 0 0; padding: 0; border: 1px solid var(--border); border-radius: var(--radius-md); }
    .list li { display: flex; justify-content: space-between; align-items: center; padding: var(--space-3) var(--space-4); }
    .list li + li { border-block-start: 1px solid var(--border); }
  `,
})
export class Statut {
  protected readonly labels = LABELS;
  protected readonly status = signal<PublicStatus | null>(null);
  protected readonly error = signal(false);
  protected readonly allUp = computed(() => (this.status()?.providers ?? []).every((p) => p.state !== 'DOWN'));

  constructor() {
    inject(HttpClient)
      .get<PublicStatus>('/api/public/statut')
      .subscribe({ next: (s) => this.status.set(s), error: () => this.error.set(true) });
  }
}
