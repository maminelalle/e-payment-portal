import { DatePipe } from '@angular/common';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { IconFieldModule } from 'primeng/iconfield';
import { InputIconModule } from 'primeng/inputicon';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { TableLazyLoadEvent, TableModule } from 'primeng/table';
import { debounceTime, Subject, switchMap } from 'rxjs';

import { Contract, CONTRACT_STATUS_LABELS, ContractStatus } from '../../core/api.models';
import { ContractStatusTag } from '../../shared/contract-status-tag';
import { formatIsoDate } from '../../shared/formatting';
import { AdminApi } from '../admin-api';

const PAGE_SIZE = 10;

@Component({
  selector: 'app-contrats',
  imports: [
    DatePipe,
    FormsModule,
    RouterLink,
    ButtonModule,
    IconFieldModule,
    InputIconModule,
    InputTextModule,
    SelectModule,
    TableModule,
    ContractStatusTag,
  ],
  templateUrl: './contrats.html',
  styles: `
    .clickable { cursor: pointer; }
    .badges { display: flex; gap: 4px; }
    .badges .method-badge { width: 26px; height: 26px; font-size: 0.65rem; border-radius: 6px; }
    .empty { padding: 32px; text-align: center; color: var(--ep-text-muted); }
    .name { font-weight: 500; }
  `,
})
export class Contrats {
  private readonly api = inject(AdminApi);
  private readonly router = inject(Router);

  protected readonly pageSize = PAGE_SIZE;
  protected readonly formatDate = formatIsoDate;
  protected readonly statusOptions = Object.entries(CONTRACT_STATUS_LABELS).map(([value, label]) => ({
    value: value as ContractStatus,
    label,
  }));

  protected readonly rows = signal<Contract[]>([]);
  protected readonly total = signal(0);
  protected readonly loading = signal(true);

  protected search = '';
  protected statusFilter: ContractStatus | null = null;
  private page = 0;
  private readonly reload$ = new Subject<void>();

  constructor() {
    this.reload$
      .pipe(
        debounceTime(250),
        switchMap(() => {
          this.loading.set(true);
          return this.api.searchContracts({
            statut: this.statusFilter,
            q: this.search,
            page: this.page,
            size: PAGE_SIZE,
          });
        }),
        takeUntilDestroyed(inject(DestroyRef)),
      )
      .subscribe({
        next: (page) => {
          this.rows.set(page.content);
          this.total.set(page.totalElements);
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });
  }

  protected onLazyLoad(event: TableLazyLoadEvent): void {
    this.page = Math.floor((event.first ?? 0) / (event.rows ?? PAGE_SIZE));
    this.reload$.next();
  }

  protected onFilterChange(): void {
    this.page = 0;
    this.reload$.next();
  }

  protected open(contract: Contract): void {
    this.router.navigate(['/admin/contrats', contract.id]);
  }
}
