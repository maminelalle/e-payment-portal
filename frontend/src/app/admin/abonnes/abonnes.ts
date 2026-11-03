import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { DrawerModule } from 'primeng/drawer';
import { IconFieldModule } from 'primeng/iconfield';
import { InputIconModule } from 'primeng/inputicon';
import { InputTextModule } from 'primeng/inputtext';
import { RadioButtonModule } from 'primeng/radiobutton';
import { SelectModule } from 'primeng/select';
import { TableLazyLoadEvent, TableModule } from 'primeng/table';
import { debounceTime, Subject, switchMap } from 'rxjs';

import {
  Subscriber,
  SUBSCRIBER_STATUS_LABELS,
  SUBSCRIBER_TYPE_LABELS,
  SubscriberStatus,
  SubscriberType,
} from '../../core/api.models';
import { applyServerErrors, fieldError } from '../../shared/form-errors';
import { StatusTag } from '../../shared/status-tag';
import { AdminApi, SubscriberQuery } from '../admin-api';

const PAGE_SIZE = 10;

@Component({
  selector: 'app-abonnes',
  imports: [
    FormsModule,
    ReactiveFormsModule,
    ButtonModule,
    DrawerModule,
    IconFieldModule,
    InputIconModule,
    InputTextModule,
    RadioButtonModule,
    SelectModule,
    TableModule,
    StatusTag,
  ],
  templateUrl: './abonnes.html',
  styleUrl: './abonnes.scss',
})
export class Abonnes {
  private readonly api = inject(AdminApi);
  private readonly router = inject(Router);
  private readonly messages = inject(MessageService);
  private readonly fb = inject(FormBuilder);

  protected readonly pageSize = PAGE_SIZE;
  protected readonly typeLabels = SUBSCRIBER_TYPE_LABELS;
  protected readonly statusOptions = Object.entries(SUBSCRIBER_STATUS_LABELS).map(([value, label]) => ({
    value: value as SubscriberStatus,
    label,
  }));
  protected readonly typeOptions = Object.entries(SUBSCRIBER_TYPE_LABELS).map(([value, label]) => ({
    value: value as SubscriberType,
    label,
  }));

  protected readonly rows = signal<Subscriber[]>([]);
  protected readonly total = signal(0);
  protected readonly loading = signal(true);
  protected readonly summary = signal<{ total: number; active: number } | null>(null);

  protected search = '';
  protected statusFilter: SubscriberStatus | null = null;
  protected typeFilter: SubscriberType | null = null;
  private page = 0;

  protected readonly drawerOpen = signal(false);
  protected readonly saving = signal(false);
  protected readonly formError = signal<string | null>(null);
  protected readonly form = this.fb.nonNullable.group({
    type: this.fb.nonNullable.control<SubscriberType>('MORALE'),
    displayName: ['', [Validators.required, Validators.maxLength(200)]],
    nni: ['', [Validators.pattern(/^\d{10}$/)]],
    nif: ['', [Validators.pattern(/^\d{8}$/)]],
    phone: ['', [Validators.required, Validators.pattern(/^[+\d\s.\-()]{8,20}$/)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
    address: ['', [Validators.maxLength(300)]],
  });

  private readonly reload$ = new Subject<number>();

  constructor() {
    this.reload$
      .pipe(
        debounceTime(250),
        switchMap(() => {
          this.loading.set(true);
          return this.api.searchSubscribers(this.query());
        }),
        takeUntilDestroyed(inject(DestroyRef)),
      )
      .subscribe({
        next: (page) => {
          this.rows.set(page.content);
          this.total.set(page.totalElements);
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });
    this.loadSummary();
    this.applyTypeRules('MORALE');
    this.form.controls.type.valueChanges.pipe(takeUntilDestroyed()).subscribe((type) => this.applyTypeRules(type));
  }

  protected onLazyLoad(event: TableLazyLoadEvent): void {
    this.page = Math.floor((event.first ?? 0) / (event.rows ?? PAGE_SIZE));
    this.reload$.next(this.page);
  }

  protected onFilterChange(): void {
    this.page = 0;
    this.reload$.next(0);
  }

  protected typeLabel(subscriber: Subscriber): string {
    return this.typeLabels[subscriber.type];
  }

  protected open(subscriber: Subscriber): void {
    this.router.navigate(['/admin/abonnes', subscriber.id]);
  }

  protected initials(name: string): string {
    return name
      .split(/\s+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((part) => part[0]!.toUpperCase())
      .join('');
  }

  protected openDrawer(): void {
    this.form.reset({ type: 'MORALE' });
    this.formError.set(null);
    this.drawerOpen.set(true);
  }

  protected error(field: string): string | null {
    return fieldError(this.form, field, {
      required: 'Champ obligatoire.',
      email: 'Adresse email invalide.',
      pattern:
        field === 'nni'
          ? 'Le NNI comporte 10 chiffres.'
          : field === 'nif'
            ? 'Le NIF comporte 8 chiffres.'
            : 'Format invalide.',
      maxlength: 'Texte trop long.',
    });
  }

  protected create(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    this.saving.set(true);
    this.formError.set(null);
    this.api
      .createSubscriber({
        type: value.type,
        email: value.email,
        details: {
          displayName: value.displayName,
          nni: value.type === 'PHYSIQUE' ? value.nni : null,
          nif: value.type === 'MORALE' ? value.nif : null,
          phone: value.phone,
          address: value.address || null,
        },
      })
      .subscribe({
        next: ({ subscriber, invitationSent }) => {
          this.saving.set(false);
          this.drawerOpen.set(false);
          this.messages.add(
            invitationSent
              ? {
                  severity: 'success',
                  summary: `${subscriber.displayName} créé (${subscriber.reference})`,
                  detail: `Invitation envoyée à ${subscriber.email}.`,
                }
              : {
                  severity: 'warn',
                  summary: `${subscriber.displayName} créé (${subscriber.reference})`,
                  detail: "L'email d'invitation n'a pas pu partir : renvoyez-le depuis la fiche.",
                  life: 8000,
                },
          );
          this.onFilterChange();
          this.loadSummary();
        },
        error: (error) => {
          this.saving.set(false);
          this.formError.set(applyServerErrors(this.form, error));
        },
      });
  }

  private applyTypeRules(type: SubscriberType): void {
    const { nni, nif } = this.form.controls;
    nni.setValidators(type === 'PHYSIQUE' ? [Validators.required, Validators.pattern(/^\d{10}$/)] : []);
    nif.setValidators(type === 'MORALE' ? [Validators.required, Validators.pattern(/^\d{8}$/)] : []);
    nni.updateValueAndValidity({ emitEvent: false });
    nif.updateValueAndValidity({ emitEvent: false });
  }

  private query(): SubscriberQuery {
    return { q: this.search, statut: this.statusFilter, type: this.typeFilter, page: this.page, size: PAGE_SIZE };
  }

  private loadSummary(): void {
    this.api.subscriberSummary().subscribe((counts) => {
      const total = Object.values(counts).reduce((sum, n) => sum + n, 0);
      this.summary.set({ total, active: counts.ACTIVE });
    });
  }
}
