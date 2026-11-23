import { Component, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { SelectModule } from 'primeng/select';
import { Observable } from 'rxjs';

import { downloadBlob } from './download';

const MONTH = new Intl.DateTimeFormat('fr-FR', { month: 'long', year: 'numeric' });

/**
 * Relevé mensuel PDF (paiements, totaux, commissions du mois) : choix d'un des 12 derniers mois puis
 * téléchargement. Sert à l'abonné (son relevé) et à l'admin (relevé d'un abonné).
 */
@Component({
  selector: 'app-statement-download',
  imports: [FormsModule, ButtonModule, SelectModule],
  template: `
    <span class="statement">
      <p-select [options]="months" optionLabel="label" optionValue="value" [(ngModel)]="month"
                ariaLabel="Mois du relevé" size="small" />
      <p-button label="Relevé PDF" icon="pi pi-file-pdf" severity="secondary" [outlined]="true" size="small"
                [loading]="busy()" (onClick)="download()" />
    </span>
  `,
  styles: `.statement { display: inline-flex; flex-wrap: wrap; align-items: center; gap: 8px; }`,
})
export class StatementDownload {
  private readonly messages = inject(MessageService);

  /** Télécharge le relevé du mois « AAAA-MM ». */
  readonly fetch = input.required<(month: string) => Observable<Blob>>();
  /** Début du nom de fichier (« releve », « releve-ABN-0016 »). */
  readonly fileName = input('releve');

  protected readonly months = lastMonths(12);
  protected month = this.months[0]!.value;
  protected readonly busy = signal(false);

  protected download(): void {
    this.busy.set(true);
    this.fetch()(this.month).subscribe({
      next: (blob) => {
        this.busy.set(false);
        downloadBlob(blob, `${this.fileName()}-${this.month}.pdf`);
      },
      error: () => {
        this.busy.set(false);
        this.messages.add({ severity: 'error', summary: 'Relevé indisponible', detail: 'Réessayez dans un instant.' });
      },
    });
  }
}

/** Mois en cours puis les précédents : { value: '2026-11', label: 'Novembre 2026' }. */
function lastMonths(count: number): { value: string; label: string }[] {
  const now = new Date();
  return Array.from({ length: count }, (_, i) => {
    const d = new Date(now.getFullYear(), now.getMonth() - i, 1);
    const value = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
    const label = MONTH.format(d);
    return { value, label: label.charAt(0).toUpperCase() + label.slice(1) };
  });
}
