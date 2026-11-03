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
