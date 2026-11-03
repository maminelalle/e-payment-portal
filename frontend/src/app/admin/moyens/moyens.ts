import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ConfirmationService, MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { ToggleSwitchModule } from 'primeng/toggleswitch';

import { ApiProblem, PaymentMethod } from '../../core/api.models';
import { MethodLogo } from '../../shared/method-logo';
import { AdminApi } from '../admin-api';

@Component({
  selector: 'app-moyens',
  imports: [FormsModule, ButtonModule, ToggleSwitchModule, MethodLogo],
  templateUrl: './moyens.html',
  styleUrl: './moyens.scss',
})
export class Moyens {
  private readonly api = inject(AdminApi);
  private readonly messages = inject(MessageService);
  private readonly confirmation = inject(ConfirmationService);

  protected readonly methods = signal<PaymentMethod[] | null>(null);
  protected readonly pending = signal<string | null>(null);
  /** Incrémenté pour redessiner les interrupteurs quand un changement est annulé ou échoue. */
  protected readonly revision = signal(0);

  protected readonly healthLabels = { UP: 'Disponible', DOWN: 'Indisponible', UNKNOWN: 'Non vérifiée' } as const;

  constructor() {
    this.api.paymentMethods().subscribe((methods) => this.methods.set(methods));
  }

  protected toggle(method: PaymentMethod, active: boolean): void {
    if (active) {
      this.apply(method, true);
      return;
    }
    this.confirmation.confirm({
      header: `Désactiver ${method.name}`,
      message: `${method.name} ne sera plus proposé dans les nouveaux contrats ni utilisable pour payer. Les contrats et transactions existants ne sont pas modifiés.`,
      acceptLabel: 'Désactiver',
      rejectLabel: 'Annuler',
      acceptButtonProps: { severity: 'danger' },
      rejectButtonProps: { severity: 'secondary', outlined: true },
      accept: () => this.apply(method, false),
      // Le ToggleSwitch a déjà basculé visuellement : on le redessine dans son état réel en cas d’annulation.
      reject: () => this.revision.update((n) => n + 1),
    });
  }

  /** Envoi du logo choisi ; le serveur vérifie le format réel (PNG, JPEG, WebP) et la taille (256 Ko). */
  protected uploadLogo(method: PaymentMethod, input: HTMLInputElement): void {
    const file = input.files?.[0];
    input.value = '';
    if (!file) return;
    if (file.size > 256 * 1024) {
      this.messages.add({ severity: 'error', summary: 'Logo trop lourd', detail: '256 Ko au maximum.' });
      return;
    }
    this.pending.set(method.code);
    this.api.uploadPaymentMethodLogo(method.code, file).subscribe({
      next: (updated) => this.replace(updated, `Logo de ${updated.name} mis à jour`),
      error: (error: HttpErrorResponse) => {
        this.pending.set(null);
        const problem = error.error as ApiProblem | null;
        this.messages.add({
          severity: 'error',
          summary: 'Logo refusé',
          detail: problem?.errors?.['logo'] ?? problem?.detail ?? 'Envoi impossible.',
        });
      },
    });
  }

  protected removeLogo(method: PaymentMethod): void {
    this.pending.set(method.code);
    this.api.removePaymentMethodLogo(method.code).subscribe({
      next: (updated) => this.replace(updated, `Logo de ${updated.name} retiré`),
      error: () => {
        this.pending.set(null);
        this.messages.add({ severity: 'error', summary: 'Suppression impossible' });
      },
    });
  }

  private replace(updated: PaymentMethod, summary: string): void {
    this.pending.set(null);
    this.methods.update((list) => list?.map((m) => (m.code === updated.code ? updated : m)) ?? null);
    this.messages.add({ severity: 'success', summary });
  }

  private apply(method: PaymentMethod, active: boolean): void {
    this.pending.set(method.code);
    this.api.changePaymentMethodStatus(method.code, active ? 'ACTIVE' : 'INACTIVE').subscribe({
      next: (updated) => {
        this.pending.set(null);
        this.methods.update((list) => list?.map((m) => (m.code === updated.code ? updated : m)) ?? null);
        this.messages.add({
          severity: 'success',
          summary: `${updated.name} ${active ? 'activé' : 'désactivé'}`,
        });
      },
      error: () => {
        this.pending.set(null);
        this.revision.update((n) => n + 1);
        this.messages.add({ severity: 'error', summary: 'Modification impossible' });
      },
    });
  }
}
