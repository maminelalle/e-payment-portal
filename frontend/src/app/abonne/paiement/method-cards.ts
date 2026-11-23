import { Component, input, output } from '@angular/core';
import { ButtonModule } from 'primeng/button';

import { EntitledMethod } from '../../core/api.models';
import { formatAmount } from '../../shared/formatting';
import { MethodLogo } from '../../shared/method-logo';

/** Cartes « Bankily / Masrvi / Visa » de la maquette B1 : uniquement les moyens ouverts par le contrat. */
@Component({
  selector: 'app-method-cards',
  imports: [ButtonModule, MethodLogo],
  template: `
    <div class="cards">
      @for (m of methods(); track m.code; let first = $first) {
        <article class="card method" [class.unavailable]="!m.available">
          <header>
            <app-method-logo [method]="m" />
            @if (m.available) {
              <span class="status-pill success">Disponible</span>
            } @else {
              <span class="status-pill neutral">{{ m.unavailableReason }}</span>
            }
          </header>
          <h3>{{ m.name }}</h3>
          <p class="muted">{{ m.category === 'WALLET' ? 'Wallet mobile' : 'Carte bancaire' }}</p>
          <dl class="limits">
            <dt>Par opération</dt>
            <dd><bdi>{{ formatAmount(m.perTransactionLimit, m.currency) }}</bdi></dd>
            <dt>Par mois</dt>
            <dd><bdi>{{ formatAmount(m.monthlyLimit, m.currency) }}</bdi></dd>
          </dl>
          @if (showDetails() && m.instructions) {
            <p class="muted small">{{ m.instructions }}</p>
            @if (m.conditions) {
              <p class="muted small">
                <strong>{{ m.category === 'CARD' ? "Règles d'acceptation" : "Conditions d'utilisation" }} :</strong>
                {{ m.conditions }}
              </p>
            }
          }
          <p-button
            [label]="m.category === 'CARD' ? 'Payer par carte' : 'Payer avec ' + m.name"
            [outlined]="!first"
            styleClass="w-full"
            [disabled]="!m.available"
            (onClick)="pay.emit(m)"
          />
        </article>
      }
    </div>
  `,
  styles: `
    .cards {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
      gap: 16px;
    }
    .method {
      display: flex;
      flex-direction: column;
      padding: 20px;
    }
    .method.unavailable {
      opacity: 0.75;
    }
    header {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      margin-bottom: 16px;
    }
    h3 {
      margin: 0 0 2px;
      font-size: 1.05rem;
    }
    p {
      margin: 0 0 16px;
    }
    .limits {
      display: grid;
      grid-template-columns: auto 1fr;
      gap: 2px 12px;
      margin: 0 0 16px;
      font-size: 0.85rem;
    }
    .limits dt {
      color: var(--ink-muted);
    }
    .limits dd {
      margin: 0;
      text-align: end;
      font-variant-numeric: tabular-nums;
    }
    .small {
      font-size: 0.8rem;
      line-height: 1.5;
    }
    p-button {
      margin-top: auto;
    }
  `,
})
export class MethodCards {
  readonly methods = input.required<EntitledMethod[]>();
  readonly showDetails = input(false);
  readonly pay = output<EntitledMethod>();
  protected readonly formatAmount = formatAmount;
}
