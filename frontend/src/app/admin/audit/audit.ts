import { DatePipe } from '@angular/common';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { DatePickerModule } from 'primeng/datepicker';
import { IconFieldModule } from 'primeng/iconfield';
import { InputIconModule } from 'primeng/inputicon';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { TableLazyLoadEvent, TableModule } from 'primeng/table';
import { debounceTime, Subject, switchMap } from 'rxjs';

import { AUDIT_ACTION_LABELS, AuditEntry, AuditFilter } from '../../core/api.models';
import { toIsoDate } from '../../shared/formatting';
import { AdminApi } from '../admin-api';

const PAGE_SIZE = 25;

const RESOURCE_TYPES: Record<string, string> = {
  USER: 'Utilisateur',
  SUBSCRIBER: 'Abonné',
  CONTRACT: 'Contrat',
  TRANSACTION: 'Transaction',
  PAYMENT_METHOD: 'Moyen de paiement',
  SIGNATURE: 'Signature',
  WEBHOOK: 'Webhook',
  HTTP: 'Requête',
};

/** Journal d'audit (écran A6) : lecture seule, filtrable, détail de chaque événement. */
@Component({
  selector: 'app-audit',
  imports: [DatePipe, FormsModule, DatePickerModule, IconFieldModule, InputIconModule, InputTextModule, SelectModule,
    TableModule],
  templateUrl: './audit.html',
  styleUrl: './audit.scss',
})
export class Audit {
  private readonly api = inject(AdminApi);

  protected readonly pageSize = PAGE_SIZE;
  protected readonly rows = signal<AuditEntry[]>([]);
  protected readonly total = signal(0);
  protected readonly loading = signal(true);
  protected readonly actions = signal<{ value: string; label: string }[]>([]);
  protected readonly outcomes = [
    { value: 'SUCCESS', label: 'Réussite' },
    { value: 'FAILURE', label: 'Échec' },
  ];

  protected actor = '';
  protected resource = '';
  protected action: string | null = null;
  protected outcome: 'SUCCESS' | 'FAILURE' | null = null;
  protected period: Date[] | null = null;
  private page = 0;
  private readonly reload$ = new Subject<void>();

  constructor() {
    this.api.auditActions().subscribe((codes) =>
      this.actions.set(
        codes
          .map((value) => ({ value, label: this.actionLabel(value) }))
          .sort((a, b) => a.label.localeCompare(b.label, 'fr')),
      ),
    );
    this.reload$
      .pipe(
        debounceTime(250),
        switchMap(() => {
          this.loading.set(true);
          return this.api.audit(this.page, PAGE_SIZE, this.filter());
        }),
        takeUntilDestroyed(inject(DestroyRef)),
      )
      .subscribe({
        next: (page) => {
          this.rows.set(page.items);
          this.total.set(page.total);
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });
  }

  protected readonly copied = signal<string | null>(null);

  /** UUID raccourci (début) ; les références métier (TRX-…, CTR-…, e-mail) restent entières. */
  protected resourceLabel(id: string): string {
    return /^[0-9a-f]{8}-[0-9a-f]{4}-/i.test(id) ? `${id.slice(0, 8)}…` : id;
  }

  protected resourceTypeLabel(type: string): string {
    return RESOURCE_TYPES[type] ?? type;
  }

  protected ipLabel(ip: string | null): string {
    if (!ip) return '—';
    return ip === '::1' || ip === '127.0.0.1' || ip === '0:0:0:0:0:0:0:1' ? `localhost (${ip})` : ip;
  }

  protected copy(value: string): void {
    navigator.clipboard?.writeText(value).then(() => {
      this.copied.set(value);
      setTimeout(() => this.copied.set(null), 1500);
    });
  }

  protected actionLabel(code: string): string {
    return AUDIT_ACTION_LABELS[code] ?? code;
  }

  /** Détails JSON présentés en « clé : valeur » ; texte brut si ce n'est pas un objet. */
  protected detailLines(details: string | null): { key: string; value: string }[] {
    if (!details) return [];
    try {
      const parsed: unknown = JSON.parse(details);
      if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
        return Object.entries(parsed as Record<string, unknown>).map(([key, value]) => ({
          key,
          value: typeof value === 'object' ? JSON.stringify(value) : String(value),
        }));
      }
    } catch {
      // pas du JSON : affiché tel quel
    }
    return [{ key: '', value: details }];
  }

  protected onLazyLoad(event: TableLazyLoadEvent): void {
    this.page = Math.floor((event.first ?? 0) / (event.rows ?? PAGE_SIZE));
    this.reload$.next();
  }

  protected onFilterChange(): void {
    this.page = 0;
    this.reload$.next();
  }

  protected onPeriodSelect(): void {
    if (this.period?.[0] && this.period?.[1]) this.onFilterChange();
  }

  private filter(): AuditFilter {
    const [from, to] = this.period ?? [];
    return {
      action: this.action,
      acteur: this.actor.trim() || null,
      ressource: this.resource.trim() || null,
      resultat: this.outcome,
      du: toIsoDate(from),
      au: toIsoDate(to ?? from),
    };
  }
}
