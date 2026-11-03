import { DatePipe } from '@angular/common';
import { Component, effect, inject, input, signal } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ConfirmationService, MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { TextareaModule } from 'primeng/textarea';

import { Contract, Subscriber, SUBSCRIBER_TYPE_LABELS, Transaction } from '../../core/api.models';
import { downloadBlob } from '../../shared/download';
import { TransactionTable } from '../../shared/transaction-table';
import { ContractStatusTag } from '../../shared/contract-status-tag';
import { formatIsoDate } from '../../shared/formatting';
import { applyServerErrors, fieldError } from '../../shared/form-errors';
import { StatusTag } from '../../shared/status-tag';
import { AdminApi } from '../admin-api';

type StatusAction = 'activer' | 'suspendre' | 'desactiver';

@Component({
  selector: 'app-abonne-fiche',
  imports: [
    DatePipe,
    FormsModule,
    ReactiveFormsModule,
    RouterLink,
    ButtonModule,
    DialogModule,
    InputTextModule,
    TextareaModule,
    StatusTag,
    ContractStatusTag,
    TransactionTable,
  ],
  templateUrl: './abonne-fiche.html',
  styleUrl: './abonne-fiche.scss',
})
export class AbonneFiche {
  private readonly api = inject(AdminApi);
  private readonly messages = inject(MessageService);
  private readonly confirmation = inject(ConfirmationService);
  private readonly fb = inject(FormBuilder);

  /** Paramètre de route :id. */
  readonly id = input.required<string>();

  protected readonly typeLabels = SUBSCRIBER_TYPE_LABELS;
  protected readonly subscriber = signal<Subscriber | null>(null);
  protected readonly contracts = signal<Contract[]>([]);
  protected readonly transactions = signal<Transaction[]>([]);
  protected readonly formatDate = formatIsoDate;
  protected readonly notFound = signal(false);
  protected readonly busy = signal(false);

  protected readonly reasonDialog = signal<StatusAction | null>(null);
  protected reason = '';

  protected readonly editOpen = signal(false);
  protected readonly editError = signal<string | null>(null);
  protected readonly editForm = this.fb.nonNullable.group({
    displayName: ['', [Validators.required, Validators.maxLength(200)]],
    nni: [''],
    nif: [''],
    phone: ['', [Validators.required, Validators.pattern(/^[+\d\s.\-()]{8,20}$/)]],
    address: ['', [Validators.maxLength(300)]],
  });

  constructor() {
    effect(() => this.load(this.id()));
  }

  protected askStatusChange(action: StatusAction): void {
    const s = this.subscriber();
    if (!s) return;
    if (action === 'activer') {
      this.confirmation.confirm({
        header: s.status === 'SUSPENDED' ? 'Réactiver l’abonné' : 'Activer l’abonné',
        message: `${s.displayName} pourra se connecter au portail.`,
        acceptLabel: s.status === 'SUSPENDED' ? 'Réactiver' : 'Activer',
        rejectLabel: 'Annuler',
        rejectButtonProps: { severity: 'secondary', outlined: true },
        accept: () => this.changeStatus('activer'),
      });
    } else {
      this.reason = '';
      this.reasonDialog.set(action);
    }
  }

  protected confirmWithReason(): void {
    const action = this.reasonDialog();
    if (action) {
      this.reasonDialog.set(null);
      this.changeStatus(action, this.reason.trim() || undefined);
    }
  }

  protected resendInvitation(): void {
    const s = this.subscriber();
    if (!s) return;
    this.busy.set(true);
    this.api.resendInvitation(s.id).subscribe({
      next: ({ invitationSent }) => {
        this.busy.set(false);
        this.messages.add(
          invitationSent
            ? { severity: 'success', summary: 'Invitation renvoyée', detail: `Email envoyé à ${s.email}.` }
            : { severity: 'error', summary: 'Envoi impossible', detail: 'Le serveur mail ne répond pas.' },
        );
      },
      error: (error) => this.fail(error),
    });
  }

  protected openEdit(): void {
    const s = this.subscriber();
    if (!s) return;
    this.editForm.reset({
      displayName: s.displayName,
      nni: s.nni ?? '',
      nif: s.nif ?? '',
      phone: s.phone,
      address: s.address ?? '',
    });
    const { nni, nif } = this.editForm.controls;
    nni.setValidators(s.type === 'PHYSIQUE' ? [Validators.required, Validators.pattern(/^\d{10}$/)] : []);
    nif.setValidators(s.type === 'MORALE' ? [Validators.required, Validators.pattern(/^\d{8}$/)] : []);
    nni.updateValueAndValidity();
    nif.updateValueAndValidity();
    this.editError.set(null);
    this.editOpen.set(true);
  }

  protected editFieldError(field: string): string | null {
    return fieldError(this.editForm, field, {
      required: 'Champ obligatoire.',
      pattern: field === 'nni' ? 'Le NNI comporte 10 chiffres.' : field === 'nif' ? 'Le NIF comporte 8 chiffres.' : 'Format invalide.',
      maxlength: 'Texte trop long.',
    });
  }

  protected saveEdit(): void {
    const s = this.subscriber();
    if (!s) return;
    if (this.editForm.invalid) {
      this.editForm.markAllAsTouched();
      return;
    }
    const value = this.editForm.getRawValue();
    this.busy.set(true);
    this.api
      .updateSubscriber(s.id, {
        displayName: value.displayName,
        nni: s.type === 'PHYSIQUE' ? value.nni : null,
        nif: s.type === 'MORALE' ? value.nif : null,
        phone: value.phone,
        address: value.address || null,
      })
      .subscribe({
        next: (updated) => {
          this.busy.set(false);
          this.subscriber.set(updated);
          this.editOpen.set(false);
          this.messages.add({ severity: 'success', summary: 'Abonné modifié' });
        },
        error: (error) => {
          this.busy.set(false);
          this.editError.set(applyServerErrors(this.editForm, error));
        },
      });
  }

  protected downloadReceipt(tx: Transaction): void {
    this.api.transactionReceipt(tx.reference).subscribe((blob) => downloadBlob(blob, `recu-${tx.reference}.pdf`));
  }

  protected reasonTitle(action: StatusAction): string {
    return action === 'suspendre' ? 'Suspendre l’abonné' : 'Désactiver l’abonné';
  }

  private changeStatus(action: StatusAction, motif?: string): void {
    const s = this.subscriber();
    if (!s) return;
    this.busy.set(true);
    this.api.changeSubscriberStatus(s.id, action, motif).subscribe({
      next: (updated) => {
        this.busy.set(false);
        this.subscriber.set(updated);
        const labels: Record<StatusAction, string> = {
          activer: 'Abonné actif : il peut se connecter.',
          suspendre: 'Abonné suspendu : ses sessions ont été fermées.',
          desactiver: 'Abonné désactivé définitivement.',
        };
        this.messages.add({ severity: 'success', summary: labels[action] });
      },
      error: (error) => this.fail(error),
    });
  }

  private load(id: string): void {
    this.notFound.set(false);
    this.api.getSubscriber(id).subscribe({
      next: (s) => this.subscriber.set(s),
      error: () => this.notFound.set(true),
    });
    this.api.searchContracts({ abonne: id, page: 0, size: 20 }).subscribe((page) => this.contracts.set(page.content));
    this.api.transactions(0, 5, { abonne: id }).subscribe((page) => this.transactions.set(page.content));
  }

  private fail(error: { error?: { detail?: string } }): void {
    this.busy.set(false);
    this.messages.add({
      severity: 'error',
      summary: 'Action impossible',
      detail: error.error?.detail ?? 'Une erreur est survenue.',
    });
  }
}
