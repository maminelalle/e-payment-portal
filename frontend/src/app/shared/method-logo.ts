import { Component, computed, input, signal } from '@angular/core';

/** Ce qu'il faut pour représenter un moyen de paiement (catalogue, contrat ou droit). */
export interface MethodIdentity {
  name: string;
  shortLabel: string;
  color: string;
  logoUrl?: string | null;
}

/**
 * Logo d'un moyen de paiement (Bankily, Masrvi, Sedad, Visa…) envoyé par l'admin dans le catalogue,
 * ou à défaut la pastille colorée « BK », « MS »… Repli automatique si l'image ne se charge pas.
 */
@Component({
  selector: 'app-method-logo, ep-method-logo',
  template: `
    @if (source() && !failed()) {
      <img [src]="source()" alt="" [title]="name()" [style.width.px]="size()"
           [style.height.px]="size()" (error)="failed.set(true)" />
    } @else {
      <span class="badge" [style.background]="color()" [style.width.px]="size()" [style.height.px]="size()"
            [style.font-size.px]="fontSize()" [title]="name()">{{ initials() }}</span>
    }
  `,
  styles: `
    :host { display: inline-flex; flex-shrink: 0; }
    img {
      display: block;
      object-fit: contain;
      padding: 3px;
      border: 1px solid var(--ep-border);
      border-radius: 8px;
      background: #ffffff;
      box-sizing: border-box;
    }
    .badge {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      border-radius: 8px;
      color: #ffffff;
      font-weight: 700;
    }
  `,
})
export class MethodLogo {
  readonly method = input<MethodIdentity | null>(null);
  readonly src = input<string | null>(null);
  readonly code = input<string | null>(null);
  /** Côté en pixels (40 par défaut, comme les maquettes). */
  readonly size = input(40);
  protected readonly failed = signal(false);
  protected readonly fontSize = computed(() => Math.round(this.size() * 0.32));
  protected readonly name = computed(() => this.method()?.name || this.code() || '');
  protected readonly color = computed(() => this.method()?.color || '#64748b');
  protected readonly source = computed(() => this.src() || this.method()?.logoUrl || null);
  protected readonly initials = computed(() => {
    const value = this.code() || this.method()?.shortLabel || this.method()?.name || '';
    return value.replace(/[^A-Za-z0-9]/g, '').slice(0, 2).toUpperCase();
  });
}
