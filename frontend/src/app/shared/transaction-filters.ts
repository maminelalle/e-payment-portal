import { Component, input, OnInit, output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DatePickerModule } from 'primeng/datepicker';
import { IconFieldModule } from 'primeng/iconfield';
import { InputIconModule } from 'primeng/inputicon';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';

import { TRANSACTION_STATUS_LABELS, TransactionFilter, TransactionStatus } from '../core/api.models';
import { toIsoDate } from './formatting';

/** Filtres de l'historique : statut, moyen, période, référence. Émet le filtre à chaque changement. */
@Component({
  selector: 'app-transaction-filters',
  imports: [FormsModule, DatePickerModule, IconFieldModule, InputIconModule, InputTextModule, SelectModule],
  template: `
    <div class="toolbar">
      <p-iconfield class="search">
        <p-inputicon class="pi pi-search" />
        <input pInputText type="search" class="w-full" placeholder="Référence (TRX-000001)" [(ngModel)]="reference"
          (ngModelChange)="emit()" aria-label="Rechercher une transaction" />
      </p-iconfield>
      <p-select [options]="statuses" optionLabel="label" optionValue="value" [(ngModel)]="status" (onChange)="emit()"
        placeholder="Tous les statuts" [showClear]="true" ariaLabel="Filtrer par statut" />
      <p-select [options]="methods()" optionLabel="name" optionValue="code" [(ngModel)]="method" (onChange)="emit()"
        placeholder="Tous les moyens" [showClear]="true" ariaLabel="Filtrer par moyen" />
      <p-datepicker [(ngModel)]="period" selectionMode="range" [readonlyInput]="true" dateFormat="dd/mm/yy"
        placeholder="Période" [showIcon]="true" [showButtonBar]="true" (onSelect)="emitWhenRangeComplete()"
        (onClearClick)="emit()" ariaLabel="Filtrer par période" />
    </div>
  `,
})
export class TransactionFilters implements OnInit {
  /** Moyens proposés dans le filtre. */
  readonly methods = input<{ code: string; name: string }[]>([]);
  /** Référence déjà saisie à l'ouverture (lien « TRX-… » du tableau de bord). */
  readonly initialReference = input<string | undefined>();
  readonly changed = output<TransactionFilter>();

  protected readonly statuses = Object.entries(TRANSACTION_STATUS_LABELS)
    .filter(([value]) => value !== 'CREATED')
    .map(([value, label]) => ({ value: value as TransactionStatus, label }));
  protected reference = '';
  protected status: TransactionStatus | null = null;
  protected method: string | null = null;
  protected period: Date[] | null = null;

  ngOnInit(): void {
    this.reference = this.initialReference() ?? '';
  }

  protected emitWhenRangeComplete(): void {
    if (this.period?.[0] && this.period?.[1]) {
      this.emit();
    }
  }

  protected emit(): void {
    const [from, to] = this.period ?? [];
    this.changed.emit({
      q: this.reference.trim() || null,
      statut: this.status,
      moyen: this.method,
      du: toIsoDate(from),
      au: toIsoDate(to ?? from),
    });
  }
}
