import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ConfirmationService, MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { ToggleSwitchModule } from 'primeng/toggleswitch';

import { AccountSession, SentNotification, Subscriber, SubscriberProfile } from '../../core/api.models';
import { formatPhone } from '../../shared/formatting';
import { Session } from '../../core/session';
import { StatusTag } from '../../shared/status-tag';
import { AbonneApi } from '../abonne-api';
import { ContactDialog } from './contact-dialog';

const TEMPLATE_LABELS: Record<string, string> = {
  CONTRACT_AVAILABLE: 'Contrat disponible',
  CONTRACT_ACTIVATED: 'Contrat activé',
  PAYMENT_SUCCEEDED: 'Paiement réussi',
  PAYMENT_FAILED: 'Paiement échoué',
  SIGNATURE_CODE: 'Code de signature',
  CONTACT_CHANGE_CODE: 'Code de confirmation',
  CONTACT_CHANGED: 'Coordonnées modifiées',
};

/**
 * Profil abonné (écran B5) : coordonnées (téléphone et adresse modifiables avec un code reçu par email),
 * mot de passe (chez Keycloak), sessions ouvertes, préférences et historique des notifications.
 */
@Component({
  selector: 'app-profil',
  imports: [DatePipe, FormsModule, ButtonModule, ConfirmDialogModule, ToggleSwitchModule, StatusTag, ContactDialog],
  providers: [ConfirmationService],
  templateUrl: './profil.html',
  styleUrl: './profil.scss',
})
export class Profil {
  private readonly api = inject(AbonneApi);
  private readonly confirmation = inject(ConfirmationService);
  private readonly messages = inject(MessageService);
  protected readonly session = inject(Session);
  protected readonly formatPhone = formatPhone;

  protected readonly profile = signal<SubscriberProfile | null>(null);
  protected readonly notifications = signal<SentNotification[]>([]);
  protected readonly paymentEmails = signal(true);
  protected readonly savingPreferences = signal(false);
  protected readonly closing = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly editingContact = signal(false);

  constructor() {
    this.loadProfile();
    this.api.notifications().subscribe((n) => this.notifications.set(n));
    this.api.notificationPreferences().subscribe((p) => this.paymentEmails.set(p.paymentEmails));
  }

  protected templateLabel(template: string): string {
    return TEMPLATE_LABELS[template] ?? template;
  }

  protected isCurrent(s: AccountSession): boolean {
    return s.id === this.profile()?.currentSessionId;
  }

  protected contactSaved(subscriber: Subscriber): void {
    this.profile.update((p) => (p ? { ...p, subscriber: { ...p.subscriber, ...subscriber } } : p));
    this.api.notifications().subscribe((n) => this.notifications.set(n));
    this.messages.add({ severity: 'success', summary: 'Coordonnées modifiées', detail: 'Un avis vous a été envoyé par email.' });
  }

  protected changePassword(): void {
    this.session.changePassword();
  }

  protected togglePaymentEmails(enabled: boolean): void {
    this.savingPreferences.set(true);
    this.api.updateNotificationPreferences({ paymentEmails: enabled }).subscribe({
      next: (p) => {
        this.paymentEmails.set(p.paymentEmails);
        this.savingPreferences.set(false);
      },
      error: () => {
        this.paymentEmails.set(!enabled);
        this.savingPreferences.set(false);
        this.error.set("La préférence n'a pas pu être enregistrée.");
      },
    });
  }

  protected endSession(s: AccountSession): void {
    this.closing.set(s.id);
    this.api.endSession(s.id).subscribe({
      next: () => {
        this.closing.set(null);
        this.loadProfile();
      },
      error: () => {
        this.closing.set(null);
        this.error.set("La session n'a pas pu être fermée.");
      },
    });
  }

  protected logoutEverywhere(): void {
    this.confirmation.confirm({
      header: 'Se déconnecter partout',
      message: 'Toutes vos sessions seront fermées, y compris celle-ci. Vous devrez vous reconnecter.',
      acceptLabel: 'Me déconnecter partout',
      rejectLabel: 'Annuler',
      rejectButtonProps: { severity: 'secondary', outlined: true },
      accept: () => this.api.logoutEverywhere().subscribe(() => this.session.expire()),
    });
  }

  private loadProfile(): void {
    this.api.profile().subscribe({
      next: (p) => this.profile.set(p),
      error: () => this.error.set("Le profil n'a pas pu être chargé."),
    });
  }
}
