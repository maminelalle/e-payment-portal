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
  selector: 'app-method-logo',
  template: `
    @if (method().logoUrl && !failed()) {
      <img [src]="method().logoUrl" [alt]="method().name" [title]="method().name" [style.width.px]="size()"
           [style.height.px]="size()" (error)="failed.set(true)" />
    } @else {
      <span class="badge" [style.background]="method().color" [style.width.px]="size()" [style.height.px]="size()"
            [style.font-size.px]="fontSize()" [title]="method().name">{{ method().shortLabel }}</span>
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
  readonly method = input.required<MethodIdentity>();
  /** Côté en pixels (40 par défaut, comme les maquettes). */
  readonly size = input(40);
  protected readonly failed = signal(false);
  protected readonly fontSize = computed(() => Math.round(this.size() * 0.32));
}
