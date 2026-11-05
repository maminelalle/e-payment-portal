import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';

import { ApiProblem, ContractTemplateSummary } from '../../core/api.models';
import { AdminApi } from '../admin-api';

/**
 * Modèles de contrat (conception §3.4) : versions, contrats établis avec chacune, aperçu PDF, versions proposées
 * dans l'assistant. Le contenu d'une version est figé ; une nouvelle version arrive avec une mise à jour du
 * portail (D14 : le modèle exécute du code, il n'est jamais saisi dans l'écran).
 */
@Component({
  selector: 'app-modeles',
  imports: [DatePipe, RouterLink, ButtonModule],
  template: `
    <a routerLink="/admin/contrats" class="back-link"><i class="pi pi-arrow-left"></i> Contrats</a>
    <div class="page-header">
      <div>
        <h1 class="page-title">Modèles de contrat</h1>
        <p class="page-subtitle">
          Les versions « Proposé » apparaissent dans l'assistant de création. Une version publiée n'est jamais
          modifiée : les contrats déjà envoyés gardent leur PDF.
        </p>
      </div>
    </div>

    <div class="card list">
      @for (t of templates(); track t.id) {
        <div class="row">
          <span class="main">
            <strong>{{ t.name }}</strong>
            <span class="muted">
              <bdi class="mono">{{ t.code }}</bdi> · version {{ t.version }}
              @if (t.createdAt) { · publiée le {{ t.createdAt | date: 'dd/MM/yyyy' }} }
              · {{ t.contractCount }} contrat{{ t.contractCount > 1 ? 's' : '' }}
            </span>
          </span>
          <span [class]="'status-pill ' + (t.status === 'ACTIVE' ? 'success' : 'neutral')">
            {{ t.status === 'ACTIVE' ? 'Proposé' : 'Archivé' }}
          </span>
          <span class="actions">
            <p-button label="Aperçu" icon="pi pi-eye" size="small" severity="secondary" [outlined]="true"
                      [loading]="previewing() === t.id" (onClick)="preview(t)" />
            <p-button [label]="t.status === 'ACTIVE' ? 'Archiver' : 'Proposer'" size="small"
                      [severity]="t.status === 'ACTIVE' ? 'secondary' : 'primary'" [text]="t.status === 'ACTIVE'"
                      [disabled]="busy() === t.id" (onClick)="toggle(t)" />
          </span>
        </div>
      } @empty {
        <p class="muted empty">Chargement…</p>
      }
    </div>
  `,
  styles: `
    .list { padding: 0; }
    .row {
      display: flex; flex-wrap: wrap; align-items: center; gap: 12px; padding: 16px 20px;
      border-block-end: 1px solid var(--border);
    }
    .row:last-child { border-block-end: 0; }
    .main { flex: 1; min-width: 220px; display: flex; flex-direction: column; gap: 2px; }
    .actions { display: flex; gap: 8px; }
    .mono { font-family: var(--font-mono); font-size: 13px; }
    .empty { padding: 24px; }
  `,
})
export class Modeles {
  private readonly api = inject(AdminApi);
  private readonly messages = inject(MessageService);

  protected readonly templates = signal<ContractTemplateSummary[]>([]);
  protected readonly busy = signal<string | null>(null);
  protected readonly previewing = signal<string | null>(null);

  constructor() {
    this.api.allContractTemplates().subscribe((list) => this.templates.set(list));
  }

  /** Aperçu avec un abonné et des moyens d'exemple, dans un nouvel onglet. */
  protected preview(t: ContractTemplateSummary): void {
    this.previewing.set(t.id);
    this.api.contractTemplatePreview(t.id).subscribe({
      next: (blob) => {
        this.previewing.set(null);
        const url = URL.createObjectURL(blob);
        window.open(url, '_blank', 'noopener');
        setTimeout(() => URL.revokeObjectURL(url), 60_000);
      },
      error: () => {
        this.previewing.set(null);
        this.messages.add({ severity: 'error', summary: 'Aperçu impossible' });
      },
    });
  }

  protected toggle(t: ContractTemplateSummary): void {
    const active = t.status !== 'ACTIVE';
    this.busy.set(t.id);
    this.api.changeContractTemplateStatus(t.id, active).subscribe({
      next: (updated) => {
        this.busy.set(null);
        this.templates.update((list) => list.map((x) => (x.id === updated.id ? updated : x)));
        this.messages.add({
          severity: 'success',
          summary: `${updated.name} v${updated.version} ${active ? 'proposé' : 'archivé'}`,
          detail: active ? undefined : 'Les brouillons qui l’utilisent devront choisir un autre modèle.',
        });
      },
      error: (error: HttpErrorResponse) => {
        this.busy.set(null);
        this.messages.add({ severity: 'error', summary: 'Modification impossible',
          detail: (error.error as ApiProblem | null)?.detail });
      },
    });
  }
}
