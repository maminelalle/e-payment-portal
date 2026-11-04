import { Component, ElementRef, HostListener, inject, signal, viewChild } from '@angular/core';
import { Router } from '@angular/router';
import { DialogModule } from 'primeng/dialog';
import { catchError, debounceTime, forkJoin, of, Subject, switchMap } from 'rxjs';

import { formatAmount } from '../shared/formatting';
import { AdminApi } from './admin-api';

interface SearchHit {
  group: string;
  icon: string;
  title: string;
  meta: string;
  link: string;
  queryParams?: Record<string, string>;
}

const PAGES: SearchHit[] = [
  { group: 'Pages', icon: 'pi pi-th-large', title: 'Tableau de bord', meta: '', link: '/admin' },
  { group: 'Pages', icon: 'pi pi-users', title: 'Abonnés', meta: '', link: '/admin/abonnes' },
  { group: 'Pages', icon: 'pi pi-credit-card', title: 'Moyens de paiement', meta: '', link: '/admin/moyens' },
  { group: 'Pages', icon: 'pi pi-file', title: 'Contrats', meta: '', link: '/admin/contrats' },
  { group: 'Pages', icon: 'pi pi-plus', title: 'Nouveau contrat', meta: '', link: '/admin/contrats/nouveau' },
  { group: 'Pages', icon: 'pi pi-arrow-right-arrow-left', title: 'Transactions', meta: '', link: '/admin/transactions' },
  { group: 'Pages', icon: 'pi pi-shield', title: 'Journal d’audit', meta: '', link: '/admin/audit' },
];

/**
 * Recherche globale de l'administrateur (Ctrl+K ou ⌘K) : abonnés, contrats et transactions en une saisie,
 * plus les pages de l'espace. Flèches pour choisir, Entrée pour ouvrir, Échap pour fermer.
 */
@Component({
  selector: 'app-recherche-globale',
  imports: [DialogModule],
  template: `
    <button type="button" class="trigger" (click)="show()" aria-label="Rechercher (Ctrl+K)">
      <i class="pi pi-search" aria-hidden="true"></i>
      <span class="label">Rechercher un abonné, un contrat, une transaction…</span>
      <kbd>Ctrl K</kbd>
    </button>

    <p-dialog [(visible)]="visible" [modal]="true" [dismissableMask]="true" [showHeader]="false"
              [style]="{ width: 'min(640px, 94vw)' }" position="top" (onShow)="focusInput()"
              [contentStyle]="{ padding: '0' }" ariaLabelledBy="global-search-input">
      <div class="search-box">
        <i class="pi pi-search" aria-hidden="true"></i>
        <input #input id="global-search-input" type="search" autocomplete="off" role="combobox"
               aria-label="Recherche globale" aria-controls="global-search-results"
               [attr.aria-activedescendant]="hits().length ? 'hit-' + active() : null" aria-expanded="true"
               placeholder="Nom, référence (ABN-, CTR-, TRX-), email…" [value]="query()"
               (input)="onInput($any($event.target).value)" (keydown)="onKeydown($event)" />
        @if (loading()) { <i class="pi pi-spin pi-spinner" aria-hidden="true"></i> }
      </div>
      <ul id="global-search-results" role="listbox" class="results">
        @for (hit of hits(); track $index; let i = $index) {
          @if (i === 0 || hits()[i - 1].group !== hit.group) {
            <li class="group" role="presentation">{{ hit.group }}</li>
          }
          <li [id]="'hit-' + i" role="option" [attr.aria-selected]="i === active()" [class.active]="i === active()"
              (mouseenter)="active.set(i)" (click)="open(hit)">
            <i [class]="hit.icon" aria-hidden="true"></i>
            <span class="title">{{ hit.title }}</span>
            @if (hit.meta) { <bdi class="meta">{{ hit.meta }}</bdi> }
          </li>
        } @empty {
          <li class="empty" role="presentation">
            {{ query().trim().length < 2 ? 'Saisissez au moins 2 caractères.' : 'Aucun résultat pour « ' + query().trim() + ' ».' }}
          </li>
        }
      </ul>
      <div class="hint">
        <span><kbd>↑</kbd><kbd>↓</kbd> choisir</span><span><kbd>Entrée</kbd> ouvrir</span><span><kbd>Échap</kbd> fermer</span>
      </div>
    </p-dialog>
  `,
  styles: `
    .trigger {
      display: inline-flex; align-items: center; gap: var(--space-2); width: min(420px, 100%);
      height: var(--control-h); padding: 0 var(--space-3); border: 1px solid var(--border-strong);
      border-radius: var(--radius-sm); background: var(--surface); color: var(--ink-subtle);
      font: inherit; font-size: 14px; cursor: pointer; text-align: start;
    }
    .trigger:hover { border-color: var(--brand); }
    .label { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    kbd {
      font-family: var(--font-mono); font-size: 11px; padding: 1px 6px; border: 1px solid var(--border);
      border-radius: 4px; background: var(--surface-sunken); color: var(--ink-muted); margin-inline-end: 2px;
    }
    .search-box {
      display: flex; align-items: center; gap: var(--space-3); padding: var(--space-4);
      border-block-end: 1px solid var(--border); color: var(--ink-subtle);
    }
    .search-box input {
      flex: 1; border: 0; outline: none; background: transparent; color: var(--ink); font: inherit; font-size: 16px;
    }
    .search-box input:focus-visible { box-shadow: none; }
    .results { list-style: none; margin: 0; padding: var(--space-2) 0; max-height: 55vh; overflow: auto; }
    .group { padding: var(--space-2) var(--space-4) var(--space-1); font-size: 12px; font-weight: 600; color: var(--ink-subtle); }
    li[role='option'] {
      display: flex; align-items: center; gap: var(--space-3); padding: var(--space-2) var(--space-4);
      cursor: pointer; color: var(--ink);
    }
    li[role='option'] i { color: var(--ink-subtle); }
    li.active { background: var(--brand-soft); }
    li.active i { color: var(--brand); }
    .title { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .meta { font-family: var(--font-mono); font-size: 12px; color: var(--ink-muted); white-space: nowrap; }
    .empty { padding: var(--space-6) var(--space-4); text-align: center; color: var(--ink-muted); font-size: 14px; }
    .hint {
      display: flex; gap: var(--space-4); padding: var(--space-2) var(--space-4); border-block-start: 1px solid var(--border);
      font-size: 12px; color: var(--ink-subtle);
    }
    @media (max-width: 900px) { .label, .trigger kbd { display: none; } .trigger { width: auto; } }
  `,
})
export class RechercheGlobale {
  private readonly api = inject(AdminApi);
  private readonly router = inject(Router);
  private readonly input = viewChild<ElementRef<HTMLInputElement>>('input');

  protected visible = false;
  protected readonly query = signal('');
  protected readonly hits = signal<SearchHit[]>(PAGES);
  protected readonly active = signal(0);
  protected readonly loading = signal(false);
  private readonly search$ = new Subject<string>();

  constructor() {
    this.search$
      .pipe(
        debounceTime(200),
        switchMap((q) => {
          const pages = PAGES.filter((p) => normalize(p.title).includes(normalize(q)));
          if (q.length < 2) return of(q ? pages : PAGES);
          this.loading.set(true);
          const none = { items: [], total: 0 };
          return forkJoin({
            subscribers: this.api.searchSubscribers({ q, page: 0, size: 5 }).pipe(catchError(() => of(none))),
            contracts: this.api.searchContracts({ q, page: 0, size: 5 }).pipe(catchError(() => of(none))),
            transactions: this.api.transactions(0, 5, { q }).pipe(catchError(() => of(none))),
          }).pipe(
            switchMap(({ subscribers, contracts, transactions }) =>
              of<SearchHit[]>([
                ...subscribers.items.map((s) => ({
                  group: 'Abonnés', icon: 'pi pi-user', title: s.displayName, meta: s.reference, link: `/admin/abonnes/${s.id}`,
                })),
                ...contracts.items.map((c) => ({
                  group: 'Contrats', icon: 'pi pi-file', title: c.subscriber.displayName, meta: c.reference,
                  link: `/admin/contrats/${c.id}`,
                })),
                ...transactions.items.map((t) => ({
                  group: 'Transactions', icon: 'pi pi-arrow-right-arrow-left',
                  title: `${t.subscriberName ?? ''} · ${formatAmount(t.amount, t.currency)}`, meta: t.reference,
                  link: '/admin/transactions', queryParams: { q: t.reference },
                })),
                ...pages,
              ]),
            ),
          );
        }),
      )
      .subscribe((hits) => {
        this.hits.set(hits);
        this.active.set(0);
        this.loading.set(false);
      });
  }

  @HostListener('document:keydown', ['$event'])
  protected shortcut(event: KeyboardEvent): void {
    if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') {
      event.preventDefault();
      this.show();
    }
  }

  protected show(): void {
    this.query.set('');
    this.hits.set(PAGES);
    this.active.set(0);
    this.visible = true;
  }

  protected focusInput(): void {
    this.input()?.nativeElement.focus();
  }

  protected onInput(value: string): void {
    this.query.set(value);
    // Pages filtrées tout de suite : Entrée tapée avant la réponse du serveur ouvre déjà la bonne page.
    const q = normalize(value.trim());
    this.hits.set(q ? PAGES.filter((p) => normalize(p.title).includes(q)) : PAGES);
    this.active.set(0);
    this.search$.next(value.trim());
  }

  protected onKeydown(event: KeyboardEvent): void {
    const count = this.hits().length;
    if (event.key === 'ArrowDown' && count) {
      event.preventDefault();
      this.active.update((i) => (i + 1) % count);
    } else if (event.key === 'ArrowUp' && count) {
      event.preventDefault();
      this.active.update((i) => (i - 1 + count) % count);
    } else if (event.key === 'Enter' && count) {
      event.preventDefault();
      this.open(this.hits()[this.active()]!);
    }
  }

  protected open(hit: SearchHit): void {
    this.visible = false;
    this.router.navigate([hit.link], { queryParams: hit.queryParams });
  }
}

function normalize(value: string): string {
  return value.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();
}
