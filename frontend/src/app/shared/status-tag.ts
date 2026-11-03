import { Component, computed, input } from '@angular/core';

import { SUBSCRIBER_STATUS_LABELS, SubscriberStatus } from '../core/api.models';

const TONES: Record<SubscriberStatus, string> = {
  PENDING: 'warning',
  ACTIVE: 'success',
  SUSPENDED: 'danger',
  DISABLED: 'neutral',
};

/** Pastille de statut d'abonné (couleurs de la maquette). */
@Component({
  selector: 'app-status-tag',
  template: `<span class="status-pill" [class]="tone()">{{ label() }}</span>`,
})
export class StatusTag {
  readonly status = input.required<SubscriberStatus>();
  protected readonly label = computed(() => SUBSCRIBER_STATUS_LABELS[this.status()]);
  protected readonly tone = computed(() => 'status-pill ' + TONES[this.status()]);
}
