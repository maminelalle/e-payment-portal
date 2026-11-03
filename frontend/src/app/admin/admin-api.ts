import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import {
  CreatedSubscriber,
  NewSubscriber,
  PageResponse,
  PaymentMethod,
  PaymentMethodStatus,
  Subscriber,
  SubscriberDetails,
  SubscriberStatus,
  SubscriberType,
} from '../core/api.models';

export interface SubscriberQuery {
  q?: string;
  statut?: SubscriberStatus | null;
  type?: SubscriberType | null;
  page: number;
  size: number;
}

/** Appels de l'espace administrateur (/api/admin/**). */
@Injectable({ providedIn: 'root' })
export class AdminApi {
  private readonly http = inject(HttpClient);

  searchSubscribers(query: SubscriberQuery): Observable<PageResponse<Subscriber>> {
    let params = new HttpParams().set('page', query.page).set('size', query.size);
    if (query.q?.trim()) params = params.set('q', query.q.trim());
    if (query.statut) params = params.set('statut', query.statut);
    if (query.type) params = params.set('type', query.type);
    return this.http.get<PageResponse<Subscriber>>('/api/admin/abonnes', { params });
  }

  subscriberSummary(): Observable<Record<SubscriberStatus, number>> {
    return this.http.get<Record<SubscriberStatus, number>>('/api/admin/abonnes/resume');
  }

  getSubscriber(id: string): Observable<Subscriber> {
    return this.http.get<Subscriber>(`/api/admin/abonnes/${id}`);
  }

  createSubscriber(body: NewSubscriber): Observable<CreatedSubscriber> {
    return this.http.post<CreatedSubscriber>('/api/admin/abonnes', body);
  }

  updateSubscriber(id: string, details: SubscriberDetails): Observable<Subscriber> {
    return this.http.put<Subscriber>(`/api/admin/abonnes/${id}`, details);
  }

  changeSubscriberStatus(id: string, action: 'activer' | 'suspendre' | 'desactiver', motif?: string) {
    return this.http.post<Subscriber>(`/api/admin/abonnes/${id}/${action}`, motif ? { motif } : null);
  }

  resendInvitation(id: string): Observable<{ invitationSent: boolean }> {
    return this.http.post<{ invitationSent: boolean }>(`/api/admin/abonnes/${id}/invitation`, null);
  }

  paymentMethods(): Observable<PaymentMethod[]> {
    return this.http.get<PaymentMethod[]>('/api/admin/moyens');
  }

  changePaymentMethodStatus(code: string, status: PaymentMethodStatus): Observable<PaymentMethod> {
    return this.http.patch<PaymentMethod>(`/api/admin/moyens/${code}/statut`, { status });
  }
}
