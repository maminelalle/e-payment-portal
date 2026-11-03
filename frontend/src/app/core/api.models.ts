/** Types renvoyés par l'API du portail (voir les contrôleurs du backend). */

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** Erreur au format ProblemDetail (RFC 9457) ; `errors` liste les champs invalides. */
export interface ApiProblem {
  status: number;
  detail?: string;
  errors?: Record<string, string>;
}

export type SubscriberType = 'PHYSIQUE' | 'MORALE';
export type SubscriberStatus = 'PENDING' | 'ACTIVE' | 'SUSPENDED' | 'DISABLED';

export interface Subscriber {
  id: string;
  reference: string;
  type: SubscriberType;
  displayName: string;
  nni: string | null;
  nif: string | null;
  phone: string;
  email: string;
  address: string | null;
  status: SubscriberStatus;
  createdAt: string;
  updatedAt: string;
}

export interface SubscriberDetails {
  displayName: string;
  nni?: string | null;
  nif?: string | null;
  phone: string;
  address?: string | null;
}

export interface NewSubscriber {
  type: SubscriberType;
  email: string;
  details: SubscriberDetails;
}

export interface CreatedSubscriber {
  subscriber: Subscriber;
  invitationSent: boolean;
}

export type PaymentMethodStatus = 'ACTIVE' | 'INACTIVE';

export interface PaymentMethod {
  id: string;
  code: string;
  name: string;
  category: 'WALLET' | 'CARD';
  shortLabel: string;
  color: string;
  /** Logo envoyé dans le catalogue (adresse publique), sinon null : pastille colorée. */
  logoUrl: string | null;
  description: string | null;
  instructions: string | null;
  currency: string;
  status: PaymentMethodStatus;
  provider: {
    code: string;
    name: string;
    status: string;
    healthStatus: 'UP' | 'DOWN' | 'UNKNOWN';
    lastHealthCheckAt: string | null;
  };
}

export type ContractStatus =
  | 'DRAFT'
  | 'PENDING_SIGNATURE'
  | 'SIGNED'
  | 'ACTIVE'
  | 'SUSPENDED'
  | 'EXPIRED'
  | 'TERMINATED';

export interface ContractTemplate {
  id: string;
  code: string;
  name: string;
  version: number;
}

export interface ContractMethod {
  code: string;
  name: string;
  shortLabel: string;
  color: string;
  logoUrl: string | null;
  category: 'WALLET' | 'CARD';
  perTransactionLimit: number;
  monthlyLimit: number;
}

export interface Contract {
  id: string;
  reference: string;
  status: ContractStatus;
  subscriber: { id: string; reference: string; displayName: string; email: string; status: SubscriberStatus };
  template: ContractTemplate;
  /** Dates au format ISO yyyy-MM-dd. */
  startDate: string | null;
  endDate: string | null;
  commissionRate: number | null;
  methods: ContractMethod[];
  pdfSha256: string | null;
  sentAt: string | null;
  /** Empreinte du PDF scellé après signature. */
  signedPdfSha256: string | null;
  signedAt: string | null;
  activatedAt: string | null;
  terminatedAt: string | null;
  terminationReason: string | null;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
}

export interface DraftChanges {
  templateId: string;
  startDate: string | null;
  endDate: string | null;
  commissionRate: number | null;
  methods: { code: string; perTransactionLimit: number; monthlyLimit: number }[];
}

/** Droit sur un moyen de paiement (« Mes moyens de paiement », droits d'un contrat côté admin). */
export interface EntitledMethod {
  entitlementId: string;
  contractId: string;
  code: string;
  name: string;
  category: 'WALLET' | 'CARD';
  shortLabel: string;
  color: string;
  logoUrl: string | null;
  description: string | null;
  instructions: string | null;
  currency: string;
  perTransactionLimit: number;
  monthlyLimit: number;
  validFrom: string;
  validTo: string;
  status: 'ACTIVE' | 'SUSPENDED' | 'REVOKED' | 'EXPIRED';
  available: boolean;
  unavailableReason: string | null;
}

export type TransactionStatus = 'CREATED' | 'PENDING' | 'SUCCEEDED' | 'FAILED' | 'CANCELLED' | 'REFUNDED';

/** Paiement (résultat, historique). Statut interne du portail ; le statut brut du fournisseur est à côté. */
export interface Transaction {
  reference: string;
  status: TransactionStatus;
  providerStatus: string | null;
  providerReference: string | null;
  methodCode: string;
  methodName: string;
  category: 'WALLET' | 'CARD';
  shortLabel: string;
  color: string;
  logoUrl: string | null;
  amount: number;
  currency: string;
  description: string | null;
  payerPhone: string | null;
  cardMaskedPan: string | null;
  failureCode: string | null;
  failureMessage: string | null;
  /** Carte en cours : page hébergée de la passerelle où saisir la carte. */
  redirectUrl: string | null;
  contractId: string;
  subscriberId: string;
  subscriberReference: string;
  subscriberName: string;
  createdAt: string;
  completedAt: string | null;
}

/** Filtres de l'historique des transactions (tous facultatifs ; dates au format yyyy-MM-dd). */
export interface TransactionFilter {
  statut?: TransactionStatus | null;
  moyen?: string | null;
  du?: string | null;
  au?: string | null;
  q?: string | null;
  abonne?: string | null;
}

export function filterParams(filter: TransactionFilter, page: number, size: number): Record<string, string | number> {
  const params: Record<string, string | number> = { page, size };
  for (const [key, value] of Object.entries(filter)) {
    if (value) params[key] = value;
  }
  return params;
}

export const TRANSACTION_STATUS_LABELS: Record<TransactionStatus, string> = {
  CREATED: 'Créée',
  PENDING: 'En attente',
  SUCCEEDED: 'Réussie',
  FAILED: 'Échouée',
  CANCELLED: 'Annulée',
  REFUNDED: 'Remboursée',
};

export function isFinal(status: TransactionStatus): boolean {
  return status === 'SUCCEEDED' || status === 'FAILED' || status === 'CANCELLED' || status === 'REFUNDED';
}

export const CONTRACT_STATUS_LABELS: Record<ContractStatus, string> = {
  DRAFT: 'Brouillon',
  PENDING_SIGNATURE: 'En attente de signature',
  SIGNED: 'Signé · à activer',
  ACTIVE: 'Actif',
  SUSPENDED: 'Suspendu',
  EXPIRED: 'Expiré',
  TERMINATED: 'Résilié',
};

export const SUBSCRIBER_STATUS_LABELS: Record<SubscriberStatus, string> = {
  PENDING: 'En attente',
  ACTIVE: 'Actif',
  SUSPENDED: 'Suspendu',
  DISABLED: 'Désactivé',
};

export const SUBSCRIBER_TYPE_LABELS: Record<SubscriberType, string> = {
  PHYSIQUE: 'Personne physique',
  MORALE: 'Personne morale',
};
