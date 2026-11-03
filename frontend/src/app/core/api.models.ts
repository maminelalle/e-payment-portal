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
