import { HttpErrorResponse } from '@angular/common/http';
import { Component, effect, inject, input, model, output, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputOtpModule } from 'primeng/inputotp';
import { InputTextModule } from 'primeng/inputtext';
import { TextareaModule } from 'primeng/textarea';

import { ApiProblem, Subscriber } from '../../core/api.models';
import { formatPhone } from '../../shared/formatting';
import { AbonneApi, ContactChallenge } from '../abonne-api';

type Problem = ApiProblem & { remainingAttempts?: number };

/**
 * Changement de téléphone et d'adresse par l'abonné (conception §4.5) : nouvelles coordonnées, puis code reçu
 * par email. Le nom, le NNI/NIF et l'email restent modifiés par l'administrateur.
 */
@Component({
  selector: 'app-contact-dialog',
  imports: [FormsModule, ButtonModule, DialogModule, InputOtpModule, InputTextModule, TextareaModule],
  template: `
    <p-dialog [visible]="visible()" (visibleChange)="visible.set($event)" [modal]="true"
              [closable]="!busy()" [style]="{ width: 'min(480px, 94vw)' }" header="Modifier mes coordonnées">
      @if (step() === 'form') {
        <p class="muted">
          Un code de confirmation sera envoyé à votre email de connexion. Le changement ne s'applique qu'après
          sa saisie.
        </p>
        <div class="field">
          <label for="contact-phone">Téléphone</label>
          <input pInputText id="contact-phone" type="tel" inputmode="tel" autocomplete="tel" dir="ltr"
                 [(ngModel)]="phone" placeholder="22 00 00 01" [class.ng-invalid]="!!fieldErrors()['phone']"
                 [attr.aria-describedby]="fieldErrors()['phone'] ? 'contact-phone-error' : null" />
          @if (fieldErrors()['phone']; as message) {
            <small id="contact-phone-error" class="field-error">{{ message }}</small>
          } @else {
            <small class="muted">Numéro mauritanien : 8 chiffres commençant par 2, 3 ou 4.</small>
          }
        </div>
        <div class="field">
          <label for="contact-address">Adresse</label>
          <textarea pTextarea id="contact-address" rows="2" maxlength="300" autocomplete="street-address"
                    [(ngModel)]="address"></textarea>
        </div>
      } @else {
        <p class="muted">
          Un code à 6 chiffres a été envoyé à <strong>{{ challenge()?.sentTo }}</strong>. Il expire dans 5 minutes.
        </p>
        <div class="otp">
          <p-inputotp [(ngModel)]="code" [length]="6" [integerOnly]="true" (keydown.enter)="confirm()" />
        </div>
      }

      @if (error(); as message) {
        <p class="form-error" role="alert">{{ message }}</p>
      }

      <ng-template #footer>
        @if (step() === 'form') {
          <p-button label="Annuler" severity="secondary" [outlined]="true" (onClick)="visible.set(false)" />
          <p-button label="Recevoir mon code" icon="pi pi-envelope" [loading]="busy()" [disabled]="!phone.trim()"
                    (onClick)="requestCode()" />
        } @else {
          <p-button label="Retour" severity="secondary" [outlined]="true" [disabled]="busy()"
                    (onClick)="step.set('form')" />
          <p-button label="Confirmer" [loading]="busy()" [disabled]="code.length !== 6" (onClick)="confirm()" />
        }
      </ng-template>
    </p-dialog>
  `,
  styles: `
    .field { display: flex; flex-direction: column; gap: 6px; margin-top: 16px; }
    .field input, .field textarea { width: 100%; }
    .field-error { color: var(--danger); }
    .otp { display: flex; justify-content: center; margin: 20px 0 12px; }
    .form-error {
      margin: 16px 0 0; padding: 10px 12px; border-radius: var(--radius-sm);
      background: var(--danger-soft); color: var(--danger);
    }
  `,
})
export class ContactDialog {
  private readonly api = inject(AbonneApi);

  readonly visible = model(false);
  readonly subscriber = input.required<Subscriber>();
  readonly saved = output<Subscriber>();

  protected readonly step = signal<'form' | 'code'>('form');
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly fieldErrors = signal<Record<string, string>>({});
  protected readonly challenge = signal<ContactChallenge | null>(null);
  protected phone = '';
  protected address = '';
  protected code = '';

  constructor() {
    // Remise à zéro dès l'ouverture (et non à la fin de l'animation, qui effacerait une saisie rapide).
    effect(() => {
      if (this.visible()) untracked(() => this.reset());
    });
  }

  /** À chaque ouverture : coordonnées actuelles, première étape. */
  private reset(): void {
    this.phone = formatPhone(this.subscriber().phone);
    this.address = this.subscriber().address ?? '';
    this.code = '';
    this.step.set('form');
    this.error.set(null);
    this.fieldErrors.set({});
  }

  protected requestCode(): void {
    this.busy.set(true);
    this.error.set(null);
    this.fieldErrors.set({});
    this.api.requestContactChange(this.phone, this.address.trim() || null).subscribe({
      next: (challenge) => {
        this.busy.set(false);
        this.challenge.set(challenge);
        this.code = '';
        this.step.set('code');
      },
      error: (error) => {
        const problem = this.problem(error);
        if (problem?.errors) {
          this.busy.set(false);
          this.fieldErrors.set(problem.errors);
          return;
        }
        this.fail(problem);
      },
    });
  }

  protected confirm(): void {
    const challenge = this.challenge();
    if (!challenge || !/^\d{6}$/.test(this.code)) {
      this.error.set('Saisissez les 6 chiffres du code reçu par email.');
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    this.api.confirmContactChange(challenge.changeId, this.code).subscribe({
      next: (subscriber) => {
        this.busy.set(false);
        this.visible.set(false);
        this.saved.emit(subscriber);
      },
      error: (error) => {
        this.code = '';
        const problem = this.problem(error);
        this.fail(problem);
        // Code expiré, essais épuisés ou demande close : il faut recommencer depuis le formulaire.
        if (problem?.remainingAttempts === 0) this.step.set('form');
      },
    });
  }

  private problem(error: unknown): Problem | null {
    return error instanceof HttpErrorResponse ? (error.error as Problem | null) : null;
  }

  private fail(problem: Problem | null): void {
    this.busy.set(false);
    this.error.set(problem?.detail ?? 'Une erreur est survenue. Réessayez.');
  }
}
