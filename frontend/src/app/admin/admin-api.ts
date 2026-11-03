import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import {
  AdminDashboard,
  AuditEntry,
  AuditFilter,
  Contract,
  ContractStatus,
  ContractTemplate,
  CreatedSubscriber,
  DraftChanges,
  EntitledMethod,
  filterParams,
  NewSubscriber,
  PageResponse,
  PaymentMethod,
  PaymentMethodStatus,
  Subscriber,
  SubscriberDetails,
  SubscriberStatus,
  SubscriberType,
  Transaction,
  TransactionFilter,
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

  dashboard(days: number): Observable<AdminDashboard> {
    return this.http.get<AdminDashboard>('/api/admin/tableau-de-bord', { params: { jours: days } });
  }

  audit(page: number, size: number, filter: AuditFilter): Observable<PageResponse<AuditEntry>> {
    let params = new HttpParams().set('page', page).set('size', size);
    for (const [key, value] of Object.entries(filter)) {
      if (value) params = params.set(key, value);
    }
    return this.http.get<PageResponse<AuditEntry>>('/api/admin/audit', { params });
  }

  auditActions(): Observable<string[]> {
    return this.http.get<string[]>('/api/admin/audit/actions');
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

  searchContracts(query: {
    statut?: ContractStatus | null;
    abonne?: string;
    q?: string;
    page: number;
    size: number;
  }): Observable<PageResponse<Contract>> {
    let params = new HttpParams().set('page', query.page).set('size', query.size);
    if (query.statut) params = params.set('statut', query.statut);
    if (query.abonne) params = params.set('abonne', query.abonne);
    if (query.q?.trim()) params = params.set('q', query.q.trim());
    return this.http.get<PageResponse<Contract>>('/api/admin/contrats', { params });
  }

  contractTemplates(): Observable<ContractTemplate[]> {
    return this.http.get<ContractTemplate[]>('/api/admin/contrats/modeles');
  }

  getContract(id: string): Observable<Contract> {
    return this.http.get<Contract>(`/api/admin/contrats/${id}`);
  }

  createContractDraft(subscriberId: string, templateId: string): Observable<Contract> {
    return this.http.post<Contract>('/api/admin/contrats', { subscriberId, templateId });
  }

  updateContractDraft(id: string, changes: DraftChanges): Observable<Contract> {
    return this.http.put<Contract>(`/api/admin/contrats/${id}`, changes);
  }

  deleteContractDraft(id: string): Observable<void> {
    return this.http.delete<void>(`/api/admin/contrats/${id}`);
  }

  sendContract(id: string): Observable<Contract> {
    return this.http.post<Contract>(`/api/admin/contrats/${id}/envoyer`, null);
  }

  /** Cycle de vie après signature : chaque action ouvre ou ferme les droits de l'abonné. */
  contractLifecycle(id: string, action: 'activer' | 'suspendre' | 'reprendre' | 'resilier', motif?: string) {
    return this.http.post<Contract>(`/api/admin/contrats/${id}/${action}`, motif ? { motif } : null);
  }

  contractEntitlements(id: string): Observable<EntitledMethod[]> {
    return this.http.get<EntitledMethod[]>(`/api/admin/contrats/${id}/droits`);
  }

  /** PDF du contrat (aperçu « brouillon » ou document envoyé). */
  contractPdf(id: string): Observable<Blob> {
    return this.http.get(`/api/admin/contrats/${id}/pdf`, { responseType: 'blob' });
  }

  transactions(page: number, size: number, filter: TransactionFilter = {}): Observable<PageResponse<Transaction>> {
    return this.http.get<PageResponse<Transaction>>('/api/admin/transactions', {
      params: filterParams(filter, page, size),
    });
  }

  transactionReceipt(reference: string): Observable<Blob> {
    return this.http.get(`/api/admin/transactions/${reference}/recu`, { responseType: 'blob' });
  }

  paymentMethods(): Observable<PaymentMethod[]> {
    return this.http.get<PaymentMethod[]>('/api/admin/moyens');
  }

  /** Logo du moyen : PNG, JPEG ou WebP, 256 Ko au plus (contrôlé par le serveur). */
  uploadPaymentMethodLogo(code: string, file: File): Observable<PaymentMethod> {
    const form = new FormData();
    form.append('file', file);
    return this.http.put<PaymentMethod>(`/api/admin/moyens/${code}/logo`, form);
  }

  removePaymentMethodLogo(code: string): Observable<PaymentMethod> {
    return this.http.delete<PaymentMethod>(`/api/admin/moyens/${code}/logo`);
  }

  changePaymentMethodStatus(code: string, status: PaymentMethodStatus): Observable<PaymentMethod> {
    return this.http.patch<PaymentMethod>(`/api/admin/moyens/${code}/statut`, { status });
  }
}
