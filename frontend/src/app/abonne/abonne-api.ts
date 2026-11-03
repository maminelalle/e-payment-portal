import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';

import { Contract } from '../core/api.models';

/** Réponse à une demande de code de signature (le code n'est jamais renvoyé par l'API). */
export interface OtpChallenge {
  signatureId: string;
  /** Email masqué : c••••••@exemple.mr */
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
}
