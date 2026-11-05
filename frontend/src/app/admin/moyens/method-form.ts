import { Component, computed, effect, inject, input, model, output, signal, untracked } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { DrawerModule } from 'primeng/drawer';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { TextareaModule } from 'primeng/textarea';

import { PaymentMethod, PaymentMethodDetails, PaymentProviderInfo } from '../../core/api.models';
import { applyServerErrors, fieldError } from '../../shared/form-errors';
import { MethodLogo } from '../../shared/method-logo';
import { AdminApi } from '../admin-api';

const MESSAGES: Record<string, Record<string, string>> = {
  code: {
    required: 'Le code est obligatoire.',
    pattern: '2 à 30 caractères : lettres, chiffres et « _ », en commençant par une lettre.',
  },
  providerCode: { required: 'Choisissez le fournisseur.' },
  currency: { required: 'La devise est obligatoire.', pattern: 'Code devise sur 3 lettres (MRU, EUR…).' },
  name: { required: 'Le nom est obligatoire.', maxlength: '100 caractères au maximum.' },
  shortLabel: { required: 'La pastille est obligatoire.', pattern: '1 à 4 lettres ou chiffres.' },
  color: { required: 'La couleur est obligatoire.', pattern: 'Couleur au format #RRGGBB.' },
  description: { maxlength: '200 caractères au maximum.' },
  instructions: { maxlength: '500 caractères au maximum.' },
  conditions: { maxlength: '1 000 caractères au maximum.' },
};

/**
 * Ajout ou modification d'un moyen de paiement (conception §3.3). À l'ajout : code, fournisseur raccordé et
 * devise, fixés ensuite ; le moyen est créé désactivé. Toujours : nom, pastille, couleur et textes affichés
 * à l'abonné (description, instructions, conditions).
 */
@Component({
  selector: 'app-method-form',
  imports: [ReactiveFormsModule, ButtonModule, DrawerModule, InputTextModule, SelectModule, TextareaModule, MethodLogo],
  template: `
    <p-drawer [visible]="visible()" (visibleChange)="visible.set($event)" position="right" styleClass="method-drawer"
              [header]="method() ? 'Modifier ' + method()!.name : 'Nouveau moyen de paiement'">
      @if (!method()) {
        <p class="muted intro">
          Le moyen est créé <strong>désactivé</strong> : ajoutez son logo et vérifiez ses textes avant de l'activer.
          Il passe par l'un des fournisseurs raccordés au portail.
        </p>
      }

      <form [formGroup]="form" (ngSubmit)="save()" id="method-form" novalidate>
        <div class="preview">
          <app-method-logo [method]="preview()" [size]="48" />
          <span>
            <strong>{{ preview().name || 'Nom du moyen' }}</strong>
            <span class="muted">{{ kindLabel() }}</span>
          </span>
        </div>

        @if (!method()) {
          <div class="field">
            <label for="method-provider">Fournisseur</label>
            <p-select inputId="method-provider" formControlName="providerCode" [options]="providerOptions()"
                      optionLabel="label" optionValue="value" optionDisabled="disabled"
                      placeholder="Choisir le fournisseur" styleClass="w-full" />
            <small class="muted">Le type (wallet ou carte) est celui du fournisseur.</small>
            @if (error('providerCode'); as message) { <small class="error">{{ message }}</small> }
          </div>
          <div class="field-row">
            <div class="field">
              <label for="method-code">Code</label>
              <input pInputText id="method-code" formControlName="code" placeholder="CLICK" autocomplete="off"
                     class="mono" />
              <small class="muted">Fixé à la création (contrats, adresses).</small>
              @if (error('code'); as message) { <small class="error">{{ message }}</small> }
            </div>
            <div class="field">
              <label for="method-currency">Devise</label>
              <input pInputText id="method-currency" formControlName="currency" maxlength="3" class="mono" />
              @if (error('currency'); as message) { <small class="error">{{ message }}</small> }
            </div>
          </div>
        }

        <div class="field">
          <label for="method-name">Nom affiché</label>
          <input pInputText id="method-name" formControlName="name" placeholder="Click" />
          @if (error('name'); as message) { <small class="error">{{ message }}</small> }
        </div>
        <div class="field-row">
          <div class="field">
            <label for="method-short">Pastille</label>
            <input pInputText id="method-short" formControlName="shortLabel" maxlength="4" placeholder="CK" />
            <small class="muted">Affichée sans logo.</small>
            @if (error('shortLabel'); as message) { <small class="error">{{ message }}</small> }
          </div>
          <div class="field">
            <label for="method-color">Couleur</label>
            <span class="color">
              <input type="color" [value]="colorValue()" (input)="pickColor($any($event.target).value)"
                     aria-label="Choisir la couleur" />
              <input pInputText id="method-color" formControlName="color" maxlength="7" class="mono" />
            </span>
            @if (error('color'); as message) { <small class="error">{{ message }}</small> }
          </div>
        </div>
        <div class="field">
          <label for="method-description">Description</label>
          <input pInputText id="method-description" formControlName="description"
                 placeholder="Wallet mobile · paiement par téléphone" />
          @if (error('description'); as message) { <small class="error">{{ message }}</small> }
        </div>
        <div class="field">
          <label for="method-instructions">Instructions de paiement</label>
          <textarea pTextarea id="method-instructions" formControlName="instructions" rows="3"
                    placeholder="Saisissez le numéro associé à votre compte, puis confirmez dans l'application."></textarea>
          <small class="muted">Affichées à l'abonné au moment de payer.</small>
          @if (error('instructions'); as message) { <small class="error">{{ message }}</small> }
        </div>
        <div class="field">
          <label for="method-conditions">{{ kind() === 'CARD' ? "Règles d'acceptation" : "Conditions d'utilisation" }}</label>
          <textarea pTextarea id="method-conditions" formControlName="conditions" rows="3"
                    [placeholder]="kind() === 'CARD' ? 'Cartes Visa émises en Mauritanie, paiement en MRU.'
                      : 'Compte au nom de l’abonné, opérations du lundi au samedi.'"></textarea>
          <small class="muted">Visibles par l'abonné dans « Moyens de paiement ».</small>
          @if (error('conditions'); as message) { <small class="error">{{ message }}</small> }
        </div>

        @if (formError(); as message) {
          <p class="form-error" role="alert">{{ message }}</p>
        }
      </form>

      <ng-template #footer>
        <div class="drawer-footer">
          <p-button label="Annuler" severity="secondary" [outlined]="true" (onClick)="visible.set(false)" />
          <p-button [label]="method() ? 'Enregistrer' : 'Ajouter le moyen'" (onClick)="save()"
                    [loading]="saving()" />
        </div>
      </ng-template>
    </p-drawer>
  `,
  styles: `
    :host ::ng-deep .method-drawer { width: min(520px, 100vw); }
    .intro { margin: 0 0 20px; }
    .preview {
      display: flex; align-items: center; gap: 12px; margin-bottom: 20px; padding: 12px;
      border: 1px dashed var(--border-strong); border-radius: var(--radius-sm);
    }
    .preview > span { display: flex; flex-direction: column; }
    .color { display: flex; gap: 8px; align-items: center; }
    .color input[type='color'] {
      width: 40px; height: var(--control-h); padding: 2px; border: 1px solid var(--border-strong);
      border-radius: var(--radius-sm); background: var(--surface); cursor: pointer;
    }
    .color input[pInputText] { flex: 1 1 0; width: 0; min-width: 0; }
    .mono { font-family: var(--font-mono); }
    textarea { width: 100%; resize: vertical; }
    .form-error {
      padding: 10px 12px; border-radius: var(--radius-sm); background: var(--danger-soft); color: var(--danger);
    }
    .drawer-footer { display: flex; justify-content: flex-end; gap: 12px; }
  `,
})
export class MethodForm {
  private readonly api = inject(AdminApi);
  private readonly fb = inject(FormBuilder);

  readonly visible = model(false);
  /** Moyen à modifier, ou null pour un ajout. */
  readonly method = input<PaymentMethod | null>(null);
  readonly providers = input<PaymentProviderInfo[]>([]);
  readonly saved = output<{ method: PaymentMethod; created: boolean }>();

  protected readonly saving = signal(false);
  protected readonly formError = signal<string | null>(null);

  protected readonly form = this.fb.nonNullable.group({
    providerCode: ['', Validators.required],
    code: ['', [Validators.required, Validators.pattern(/^\s*[A-Za-z][A-Za-z0-9_]{1,29}\s*$/)]],
    currency: ['MRU', [Validators.required, Validators.pattern(/^[A-Za-z]{3}$/)]],
    name: ['', [Validators.required, Validators.maxLength(100)]],
    shortLabel: ['', [Validators.required, Validators.pattern(/^[A-Za-z0-9]{1,4}$/)]],
    color: ['#1D44B8', [Validators.required, Validators.pattern(/^#[0-9A-Fa-f]{6}$/)]],
    description: ['', Validators.maxLength(200)],
    instructions: ['', Validators.maxLength(500)],
    conditions: ['', Validators.maxLength(1000)],
  });
  private readonly values = toSignal(this.form.valueChanges, { initialValue: this.form.getRawValue() });

  protected readonly providerOptions = computed(() =>
    this.providers().map((p) => ({
      value: p.code,
      label: `${p.name} · ${p.kind === 'CARD' ? 'cartes' : 'wallet'}${p.status === 'ENABLED' ? '' : ' (non raccordé)'}`,
      disabled: p.status !== 'ENABLED',
    })),
  );

  /** Type du moyen : celui du fournisseur choisi (à l'ajout) ou du moyen modifié. */
  protected readonly kind = computed(() => {
    const editing = this.method();
    if (editing) return editing.category;
    return this.providers().find((p) => p.code === this.values().providerCode)?.kind ?? null;
  });
  protected readonly kindLabel = computed(() =>
    this.kind() === 'CARD' ? 'Carte bancaire' : this.kind() === 'WALLET' ? 'Wallet mobile' : 'Choisissez le fournisseur',
  );

  protected readonly preview = computed(() => {
    const v = this.values();
    const color = /^#[0-9A-Fa-f]{6}$/.test(v.color ?? '') ? v.color! : '#64748B';
    return { name: v.name?.trim() ?? '', shortLabel: v.shortLabel || '?', color, logoUrl: this.method()?.logoUrl ?? null };
  });
  protected readonly colorValue = computed(() => this.preview().color.toLowerCase());

  constructor() {
    // Remis à zéro à chaque ouverture : valeurs du moyen modifié, ou formulaire vide.
    effect(() => {
      if (this.visible()) untracked(() => this.reset());
    });
  }

  protected error(field: string): string | null {
    return fieldError(this.form, field, MESSAGES[field] ?? {});
  }

  protected pickColor(value: string): void {
    this.form.controls.color.setValue(value.toUpperCase());
    this.form.controls.color.markAsDirty();
  }

  protected save(): void {
    const editing = this.method();
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const details: PaymentMethodDetails = {
      name: v.name,
      shortLabel: v.shortLabel,
      color: v.color,
      description: v.description || null,
      instructions: v.instructions || null,
      conditions: v.conditions || null,
    };
    this.saving.set(true);
    this.formError.set(null);
    const request = editing
      ? this.api.updatePaymentMethod(editing.code, details)
      : this.api.createPaymentMethod(v.code, v.providerCode, v.currency, details);
    request.subscribe({
      next: (method) => {
        this.saving.set(false);
        this.visible.set(false);
        this.saved.emit({ method, created: !editing });
      },
      error: (error) => {
        this.saving.set(false);
        // Les erreurs des champs de « details » arrivent sous leur nom simple (name, color…).
        this.formError.set(applyServerErrors(this.form, error));
      },
    });
  }

  private reset(): void {
    const m = this.method();
    this.formError.set(null);
    this.form.reset({
      providerCode: '',
      code: '',
      currency: m?.currency ?? 'MRU',
      name: m?.name ?? '',
      shortLabel: m?.shortLabel ?? '',
      color: m?.color?.toUpperCase() ?? '#1D44B8',
      description: m?.description ?? '',
      instructions: m?.instructions ?? '',
      conditions: m?.conditions ?? '',
    });
    for (const name of ['providerCode', 'code', 'currency'] as const) {
      if (m) this.form.controls[name].disable(); else this.form.controls[name].enable();
    }
  }
}
