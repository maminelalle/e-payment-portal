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

/** Tableau de bord administrateur (maquette A1). */
export interface AdminDashboard {
  activeSubscribers: number;
  newSubscribersThisMonth: number;
  contractsPendingSignature: number;
  /** Mois en cours comparé à la même durée écoulée du mois précédent. */
  transactionsThisMonth: MonthFigure;
  collectedThisMonth: MonthFigure;
  days: number;
  perDay: DayPoint[];
  perMethod: MethodShare[];
  contractsToProcess: ContractToProcess[];
  latestTransactions: Transaction[];
}

export interface MonthFigure {
  current: number;
  previous: number;
}

export interface DayPoint {
  day: string;
  count: number;
  collected: number;
}

export interface MethodShare {
  code: string;
  name: string;
  color: string;
  count: number;
  collected: number;
}

export interface ContractToProcess {
  id: string;
  reference: string;
  status: ContractStatus;
  subscriberName: string;
  createdAt: string;
  sentAt: string | null;
  signedAt: string | null;
}

/** Ligne du journal d'audit. `details` est un objet JSON sérialisé. */
export interface AuditEntry {
  id: number;
  occurredAt: string;
  actorSubject: string | null;
  actorLabel: string | null;
  action: string;
  resourceType: string | null;
  resourceId: string | null;
  outcome: 'SUCCESS' | 'FAILURE';
  ipAddress: string | null;
  details: string | null;
}

export interface AuditFilter {
  action?: string | null;
  acteur?: string | null;
  ressource?: string | null;
  resultat?: 'SUCCESS' | 'FAILURE' | null;
  du?: string | null;
  au?: string | null;
}

/** Libellés des actions journalisées ; une action inconnue s'affiche avec son code. */
export const AUDIT_ACTION_LABELS: Record<string, string> = {
  LOGIN: 'Connexion',
  ACCESS_DENIED: 'Accès refusé',
  ADMIN_REQUEST: 'Requête administrateur',
  SUBSCRIBER_CREATED: 'Abonné créé',
  SUBSCRIBER_UPDATED: 'Abonné modifié',
  SUBSCRIBER_ACTIVATED: 'Abonné activé',
  SUBSCRIBER_SUSPENDED: 'Abonné suspendu',
  SUBSCRIBER_DISABLED: 'Abonné désactivé',
  SUBSCRIBER_INVITATION_SENT: 'Invitation envoyée',
  PAYMENT_METHOD_STATUS_CHANGED: 'Moyen de paiement activé / désactivé',
  PAYMENT_METHOD_LOGO_CHANGED: 'Logo de moyen modifié',
  PAYMENT_METHOD_LOGO_REMOVED: 'Logo de moyen retiré',
  CONTRACT_DRAFT_CREATED: 'Brouillon de contrat créé',
  CONTRACT_DRAFT_UPDATED: 'Brouillon de contrat modifié',
  CONTRACT_DRAFT_DELETED: 'Brouillon de contrat supprimé',
  CONTRACT_SENT: 'Contrat envoyé pour signature',
  CONTRACT_SIGNED: 'Contrat signé',
  CONTRACT_ACTIVATED: 'Contrat activé',
  CONTRACT_SUSPENDED: 'Contrat suspendu',
  CONTRACT_RESUMED: 'Contrat repris',
  CONTRACT_TERMINATED: 'Contrat résilié',
  CONTRACT_EXPIRED: 'Contrat expiré',
  SIGNATURE_CODE_SENT: 'Code de signature envoyé',
  SIGNATURE_CODE_REJECTED: 'Code de signature erroné',
  SIGNATURE_CODE_EXPIRED: 'Code de signature expiré',
  SIGNATURE_COMPLETED: 'Signature terminée',
  ENTITLEMENTS_OPENED: 'Droits ouverts',
  ENTITLEMENTS_SUSPENDED: 'Droits suspendus',
  ENTITLEMENTS_RESUMED: 'Droits rouverts',
  ENTITLEMENTS_REVOKED: 'Droits révoqués',
  ENTITLEMENTS_EXPIRED: 'Droits expirés',
  PAYMENT_INITIATED: 'Paiement initié',
  PAYMENT_REFUSED: 'Paiement refusé par le portail',
  PAYMENT_UPDATED: 'Paiement mis à jour',
  PAYMENT_CANCELLED: 'Paiement abandonné',
  WEBHOOK_RECEIVED: 'Webhook reçu',
  WEBHOOK_REJECTED: 'Webhook rejeté (signature)',
  WEBHOOK_UNMATCHED: 'Webhook sans transaction',
  WEBHOOK_ANOMALY: 'Webhook incohérent',
  WEBHOOK_CONFLICT: 'Webhook contradictoire',
  WEBHOOK_EVENT_ID_REUSED: 'Identifiant de webhook réutilisé',
};

/** Tableau de bord de l'abonné : paiements du mois et plafonds mensuels utilisés. */
export interface SubscriberDashboard {
  payments: number;
  succeeded: number;
  failed: number;
  paid: number;
  limits: LimitUsage[];
}

export interface LimitUsage {
  code: string;
  name: string;
  shortLabel: string;
  color: string;
  logoUrl: string | null;
  currency: string;
  monthlyLimit: number;
  /** Paiements réussis et en cours du mois sur ce moyen. */
  engaged: number;
  remaining: number;
}
