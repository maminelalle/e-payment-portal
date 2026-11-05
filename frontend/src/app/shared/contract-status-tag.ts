import { Component, computed, input } from '@angular/core';

import { CONTRACT_STATUS_LABELS, ContractStatus } from '../core/api.models';

const TONES: Record<ContractStatus, string> = {
  DRAFT: 'neutral',
  PENDING_SIGNATURE: 'warning',
  SIGNED: 'info',
  ACTIVE: 'success',
  SUSPENDED: 'danger',
  EXPIRED: 'neutral',
  TERMINATED: 'neutral',
  REPLACED: 'neutral',
};

/** Pastille de statut de contrat (« Brouillon », « En attente de signature », « À activer »… de la maquette A1). */
@Component({
  selector: 'app-contract-status-tag',
  template: `<span [class]="'status-pill ' + tone()">{{ label() }}</span>`,
})
export class ContractStatusTag {
  readonly status = input.required<ContractStatus>();
  protected readonly label = computed(() => CONTRACT_STATUS_LABELS[this.status()]);
  protected readonly tone = computed(() => TONES[this.status()]);
}
