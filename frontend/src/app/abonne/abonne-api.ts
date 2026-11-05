import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';

import {
  Contract,
  EntitledMethod,
  NotificationPreferences,
  SentNotification,
  Subscriber,
  SubscriberDashboard,
  SubscriberProfile,
  Transaction,
  TransactionFilter,
  TransactionTotals,
  filterParams,
} from '../core/api.models';
import { mapPage, UiPage } from '../shared/pagination';

/** Réponse à une demande de code de signature (le code n'est jamais renvoyé par l'API). */
export interface OtpChallenge {
  signatureId: string;
  /** Email masqué : c••••••@exemple.mr */
  sentTo: string;
  expiresAt: string;
  resendAvailableAt: string;
  maxAttempts: number;
}

/** Réponse à une demande de changement de coordonnées (même principe que la signature). */
export interface ContactChallenge {
  changeId: string;
  sentTo: string;
  expiresAt: string;
  resendAvailableAt: string;
  maxAttempts: number;
}

/** Appels de l'espace abonné (/api/abonne/**) : l'abonné est toujours déduit du jeton côté serveur. */
@Injectable({ providedIn: 'root' })
export class AbonneApi {
  private readonly http = inject(HttpClient);

  /** Contrat courant, ou null si aucun ne lui a encore été envoyé (HTTP 204). */
  currentContract(): Observable<Contract | null> {
    return this.http
      .get<Contract>('/api/abonne/contrat', { observe: 'response' })
      .pipe(map((response) => (response.status === 204 ? null : response.body)));
  }

  contractPdf(): Observable<Blob> {
    return this.http.get('/api/abonne/contrat/pdf', { responseType: 'blob' });
  }

  requestSignatureCode(): Observable<OtpChallenge> {
    return this.http.post<OtpChallenge>('/api/abonne/contrat/signature', null);
  }

  confirmSignature(signatureId: string, code: string): Observable<Contract> {
    return this.http.post<Contract>(`/api/abonne/contrat/signature/${signatureId}/confirmation`, { code });
  }

  /** Moyens ouverts par le contrat en vigueur (et eux seuls). */
  profile(): Observable<SubscriberProfile> {
    return this.http.get<SubscriberProfile>('/api/abonne/profil');
  }

  /** Changement de téléphone et d'adresse, étape 1 : un code part à l'email du compte. */
  requestContactChange(phone: string, address: string | null): Observable<ContactChallenge> {
    return this.http.post<ContactChallenge>('/api/abonne/profil/coordonnees', { phone, address });
  }

  /** Étape 2 : le code reçu applique le changement. */
  confirmContactChange(changeId: string, code: string): Observable<Subscriber> {
    return this.http.post<Subscriber>(`/api/abonne/profil/coordonnees/${changeId}/confirmation`, { code });
  }

  endSession(sessionId: string): Observable<void> {
    return this.http.delete<void>(`/api/abonne/profil/sessions/${encodeURIComponent(sessionId)}`);
  }

  logoutEverywhere(): Observable<void> {
    return this.http.post<void>('/api/abonne/profil/sessions/deconnexion', {});
  }

  notifications(): Observable<SentNotification[]> {
    return this.http.get<SentNotification[]>('/api/abonne/notifications');
  }

  notificationPreferences(): Observable<NotificationPreferences> {
    return this.http.get<NotificationPreferences>('/api/abonne/notifications/preferences');
  }

  updateNotificationPreferences(preferences: NotificationPreferences): Observable<NotificationPreferences> {
    return this.http.put<NotificationPreferences>('/api/abonne/notifications/preferences', preferences);
  }

  dashboard(): Observable<SubscriberDashboard> {
    return this.http.get<SubscriberDashboard>('/api/abonne/tableau-de-bord');
  }

  myMethods(): Observable<EntitledMethod[]> {
    return this.http.get<EntitledMethod[]>('/api/abonne/moyens');
  }

  /**
   * Paiement. La clé d'idempotence est générée une fois par tentative : un double clic ou un nouvel envoi
   * après une coupure réseau renvoie la même transaction, jamais un second débit.
   */
  pay(
    body: { method: string; amount: number; payerPhone: string | null; description: string | null },
    idempotencyKey: string,
  ): Observable<Transaction> {
    return this.http.post<Transaction>('/api/abonne/paiements', body, {
      headers: { 'Idempotency-Key': idempotencyKey },
    });
  }

  /** Avec refresh, le serveur redemande le statut au fournisseur (retour de la page carte). */
  payment(reference: string, refresh = false): Observable<Transaction> {
    return this.http.get<Transaction>(`/api/abonne/paiements/${reference}`, {
      params: refresh ? { actualiser: true } : {},
    });
  }

  cancelPayment(reference: string): Observable<Transaction> {
    return this.http.post<Transaction>(`/api/abonne/paiements/${reference}/annuler`, null);
  }

  payments(page: number, size: number, filter: TransactionFilter = {}): Observable<UiPage<Transaction>> {
    return mapPage<Transaction>(this.http.get('/api/abonne/paiements', {
      params: filterParams(filter, page, size),
    }));
  }

  paymentTotals(filter: TransactionFilter = {}): Observable<TransactionTotals> {
    return this.http.get<TransactionTotals>('/api/abonne/paiements/totaux', { params: filterParams(filter, 0, 1) });
  }

  /** Historique en CSV (Excel), mêmes filtres que la liste. */
  paymentsCsv(filter: TransactionFilter = {}): Observable<Blob> {
    return this.http.get('/api/abonne/paiements/export.csv', { params: filterParams(filter, 0, 1), responseType: 'blob' });
  }

  /** Reçu PDF d'un paiement réussi. */
  receipt(reference: string): Observable<Blob> {
    return this.http.get(`/api/abonne/paiements/${reference}/recu`, { responseType: 'blob' });
  }
}
