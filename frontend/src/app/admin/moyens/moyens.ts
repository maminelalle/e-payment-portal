import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ConfirmationService, MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { ToggleSwitchModule } from 'primeng/toggleswitch';

import { ApiProblem, PaymentMethod, ProviderHealth } from '../../core/api.models';
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

  protected readonly providers = signal<ProviderHealth[]>([]);
  protected readonly checking = signal(false);

  constructor() {
    this.api.paymentMethods().subscribe((methods) => this.methods.set(methods));
    this.api.providerStatus().subscribe({ next: (s) => this.providers.set(s.providers), error: () => {} });
  }

  /** Disponibilité en direct (sonde du fournisseur) ; à défaut, dernier état connu du catalogue. */
  protected health(method: PaymentMethod): { label: string; tone: string; message: string | null; latencyMs: number | null } {
    const live = this.providers().find((p) => p.code === method.provider.code);
    const state = live?.state ?? method.provider.healthStatus;
    const labels: Record<string, [string, string]> = {
      UP: ['En ligne', 'success'],
      DOWN: ['En panne', 'danger'],
      SIMULATED: ['Simulé', 'neutral'],
      UNKNOWN: ['Non vérifiée', 'warning'],
    };
    const [label, tone] = labels[state] ?? labels['UNKNOWN']!;
    return { label, tone, message: live?.message ?? null, latencyMs: live?.latencyMs ?? null };
  }

  protected checkProviders(): void {
    this.checking.set(true);
    this.api.providerStatus(true).subscribe({
      next: (s) => {
        this.providers.set(s.providers);
        this.checking.set(false);
        const down = s.providers.filter((p) => p.state === 'DOWN');
        this.messages.add(down.length
          ? { severity: 'warn', summary: 'Fournisseur indisponible', detail: down.map((p) => p.name).join(', ') + ' ne répond pas.' }
          : { severity: 'success', summary: 'Tous les fournisseurs répondent' });
      },
      error: () => {
        this.checking.set(false);
        this.messages.add({ severity: 'error', summary: 'Vérification impossible' });
      },
    });
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
    this.confirmation.confirm({
      header: `Retirer le logo de ${method.name}`,
      message: `Le logo de ${method.name} sera remplacé par ses initiales partout dans le portail.`,
      acceptLabel: 'Retirer',
      rejectLabel: 'Annuler',
      acceptButtonProps: { severity: 'danger' },
      rejectButtonProps: { severity: 'secondary', outlined: true },
      accept: () => this.doRemoveLogo(method),
    });
  }

  private doRemoveLogo(method: PaymentMethod): void {
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
