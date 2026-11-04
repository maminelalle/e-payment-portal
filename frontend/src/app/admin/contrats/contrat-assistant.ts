import { Component, computed, DestroyRef, effect, inject, input, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { Router, RouterLink } from '@angular/router';
import { ConfirmationService, MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { CheckboxModule } from 'primeng/checkbox';
import { DatePickerModule } from 'primeng/datepicker';
import { InputNumberModule } from 'primeng/inputnumber';
import { SelectModule } from 'primeng/select';
import { forkJoin, Observable } from 'rxjs';

import { ApiProblem, Contract, ContractTemplate, PaymentMethod, Subscriber } from '../../core/api.models';
import { formatAmount, formatIsoDate, fromIsoDate, toIsoDate } from '../../shared/formatting';
import { AdminApi } from '../admin-api';
import { MethodLogo } from '../../shared/method-logo';

interface MethodChoice {
  method: PaymentMethod;
  selected: boolean;
  perTransactionLimit: number | null;
  monthlyLimit: number | null;
}

/** Plafonds proposés par défaut (maquette A4). */
const DEFAULT_LIMITS: Record<'WALLET' | 'CARD', [number, number]> = {
  WALLET: [50_000, 500_000],
  CARD: [100_000, 1_000_000],
};

@Component({
  selector: 'app-contrat-assistant',
  imports: [FormsModule, RouterLink, ButtonModule, CheckboxModule, DatePickerModule, InputNumberModule, SelectModule, MethodLogo],
  templateUrl: './contrat-assistant.html',
  styleUrl: './contrat-assistant.scss',
})
export class ContratAssistant {
  private readonly api = inject(AdminApi);
  private readonly router = inject(Router);
  private readonly messages = inject(MessageService);
  private readonly confirmation = inject(ConfirmationService);
  private readonly sanitizer = inject(DomSanitizer);

  /** Paramètre de route :id (reprise d'un brouillon). */
  readonly id = input<string>();
  /** Paramètre ?abonne= (création depuis la fiche abonné). */
  readonly abonne = input<string>();

  protected readonly steps = ['Abonné et modèle', 'Moyens de paiement', 'Conditions', 'Vérification'];
  protected readonly step = signal(1);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly fieldErrors = signal<Record<string, string>>({});

  protected readonly contract = signal<Contract | null>(null);
  protected readonly subscribers = signal<Subscriber[]>([]);
  protected readonly templates = signal<ContractTemplate[]>([]);
  protected subscriberId: string | null = null;
  protected templateId: string | null = null;

  protected choices: MethodChoice[] = [];
  protected startDate: Date | null = null;
  protected endDate: Date | null = null;
  protected commissionRate: number | null = null;

  protected readonly pdfUrl = signal<SafeResourceUrl | null>(null);
  private rawPdfUrl: string | null = null;

  protected readonly formatAmount = formatAmount;
  protected readonly formatDate = formatIsoDate;

  protected readonly selectedSubscriber = computed(
    () => this.contract()?.subscriber.displayName ?? this.subscribers().find((s) => s.id === this.subscriberId)?.displayName,
  );

  constructor() {
    inject(DestroyRef).onDestroy(() => this.revokePdf());
    effect(() => {
      const id = this.id();
      const prefill = this.abonne();
      untracked(() => this.init(id, prefill));
    });
  }

  protected selectedChoices(): MethodChoice[] {
    return this.choices.filter((c) => c.selected);
  }

  protected templateLabel(): string {
    const t = this.contract()?.template ?? this.templates().find((x) => x.id === this.templateId);
    return t ? `${t.name} v${t.version}` : '—';
  }

  protected validity(): string {
    if (!this.startDate || !this.endDate) return '—';
    return `${formatIsoDate(toIsoDate(this.startDate))} → ${formatIsoDate(toIsoDate(this.endDate))}`;
  }

  protected onStartDateChange(date: Date | null): void {
    this.startDate = date;
    if (date && (!this.endDate || this.endDate < date)) {
      this.endDate = new Date(date.getFullYear() + 1, date.getMonth(), date.getDate() - 1);
    }
  }

  protected back(): void {
    this.error.set(null);
    this.step.update((s) => Math.max(1, s - 1));
  }

  protected next(): void {
    this.error.set(null);
    this.fieldErrors.set({});
    switch (this.step()) {
      case 1:
        return this.completeStep1();
      case 2:
        if (!this.checkMethods()) return;
        return this.save(() => this.step.set(3));
      case 3:
        if (!this.checkDates()) return;
        return this.save(() => {
          this.step.set(4);
          this.loadPreview();
        });
    }
  }

  protected saveDraftAndLeave(): void {
    if (!this.contract()) return;
    this.save((saved) => {
      this.messages.add({ severity: 'success', summary: `Brouillon ${saved.reference} enregistré` });
      this.router.navigate(['/admin/contrats', saved.id]);
    });
  }

  protected send(): void {
    const contract = this.contract();
    if (!contract) return;
    this.confirmation.confirm({
      header: 'Envoyer pour signature',
      message: `Le contrat ${contract.reference} sera figé (PDF et empreinte) puis mis à disposition de ${contract.subscriber.displayName}, qui sera prévenu par email.`,
      acceptLabel: 'Envoyer',
      rejectLabel: 'Annuler',
      rejectButtonProps: { severity: 'secondary', outlined: true },
      accept: () => {
        this.busy.set(true);
        this.api.sendContract(contract.id).subscribe({
          next: (sent) => {
            this.busy.set(false);
            this.messages.add({
              severity: 'success',
              summary: `Contrat ${sent.reference} envoyé`,
              detail: `${sent.subscriber.displayName} a été invité à le signer.`,
            });
            this.router.navigate(['/admin/contrats', sent.id]);
          },
          error: (error) => this.fail(error),
        });
      },
    });
  }

  private init(id: string | undefined, prefillSubscriber: string | undefined): void {
    this.busy.set(true);
    forkJoin({
      subscribers: this.api.searchSubscribers({ page: 0, size: 100 }),
      templates: this.api.contractTemplates(),
      methods: this.api.paymentMethods(),
    }).subscribe(({ subscribers, templates, methods }) => {
      // Seuls les abonnés en attente ou actifs peuvent recevoir un contrat.
      this.subscribers.set(subscribers.items.filter((s) => s.status === 'PENDING' || s.status === 'ACTIVE'));
      this.templates.set(templates);
      this.templateId = templates[0]?.id ?? null;
      this.subscriberId = prefillSubscriber ?? null;
      this.choices = methods
        .filter((m) => m.status === 'ACTIVE')
        .map((method) => ({
          method,
          selected: false,
          perTransactionLimit: DEFAULT_LIMITS[method.category][0],
          monthlyLimit: DEFAULT_LIMITS[method.category][1],
        }));
      const today = new Date();
      this.onStartDateChange(new Date(today.getFullYear(), today.getMonth(), today.getDate()));

      if (!id) {
        this.busy.set(false);
        return;
      }
      this.api.getContract(id).subscribe({
        next: (contract) => {
          this.busy.set(false);
          if (contract.status !== 'DRAFT') {
            this.router.navigate(['/admin/contrats', contract.id]);
            return;
          }
          this.applyContract(contract);
          this.step.set(2);
        },
        error: (error) => this.fail(error),
      });
    });
  }

  private applyContract(contract: Contract): void {
    this.contract.set(contract);
    this.subscriberId = contract.subscriber.id;
    this.templateId = contract.template.id;
    for (const choice of this.choices) {
      const included = contract.methods.find((m) => m.code === choice.method.code);
      choice.selected = !!included;
      if (included) {
        choice.perTransactionLimit = included.perTransactionLimit;
        choice.monthlyLimit = included.monthlyLimit;
      }
    }
    if (contract.startDate) this.startDate = fromIsoDate(contract.startDate);
    if (contract.endDate) this.endDate = fromIsoDate(contract.endDate);
    this.commissionRate = contract.commissionRate;
  }

  private completeStep1(): void {
    if (!this.subscriberId || !this.templateId) {
      this.error.set('Choisissez un abonné et un modèle de contrat.');
      return;
    }
    if (this.contract()) {
      this.step.set(2);
      return;
    }
    this.busy.set(true);
    this.api.createContractDraft(this.subscriberId, this.templateId).subscribe({
      next: (contract) => {
        this.busy.set(false);
        this.contract.set(contract);
        this.step.set(2);
      },
      error: (error) => this.fail(error),
    });
  }

  private checkMethods(): boolean {
    const selected = this.selectedChoices();
    if (selected.length === 0) {
      this.error.set('Cochez au moins un moyen de paiement.');
      return false;
    }
    for (const c of selected) {
      if (!c.perTransactionLimit || !c.monthlyLimit || c.perTransactionLimit <= 0) {
        this.error.set(`Renseignez les deux plafonds de ${c.method.name}.`);
        return false;
      }
      if (c.monthlyLimit < c.perTransactionLimit) {
        this.error.set(`Le plafond mensuel de ${c.method.name} doit être au moins égal au plafond par transaction.`);
        return false;
      }
    }
    return true;
  }

  private checkDates(): boolean {
    if (!this.startDate || !this.endDate) {
      this.fieldErrors.set({
        ...(this.startDate ? {} : { startDate: 'Date de début obligatoire.' }),
        ...(this.endDate ? {} : { endDate: 'Date de fin obligatoire.' }),
      });
      return false;
    }
    if (this.endDate < this.startDate) {
      this.fieldErrors.set({ endDate: 'La date de fin doit suivre la date de début.' });
      return false;
    }
    return true;
  }

  private save(then: (saved: Contract) => void): void {
    const contract = this.contract();
    if (!contract || !this.templateId) return;
    this.busy.set(true);
    const request: Observable<Contract> = this.api.updateContractDraft(contract.id, {
      templateId: this.templateId,
      startDate: toIsoDate(this.startDate),
      endDate: toIsoDate(this.endDate),
      commissionRate: this.commissionRate,
      methods: this.selectedChoices().map((c) => ({
        code: c.method.code,
        perTransactionLimit: c.perTransactionLimit!,
        monthlyLimit: c.monthlyLimit!,
      })),
    });
    request.subscribe({
      next: (saved) => {
        this.busy.set(false);
        this.contract.set(saved);
        then(saved);
      },
      error: (error) => this.fail(error),
    });
  }

  private loadPreview(): void {
    const contract = this.contract();
    if (!contract) return;
    this.revokePdf();
    this.api.contractPdf(contract.id).subscribe((blob) => {
      this.rawPdfUrl = URL.createObjectURL(blob);
      this.pdfUrl.set(this.sanitizer.bypassSecurityTrustResourceUrl(this.rawPdfUrl));
    });
  }

  private revokePdf(): void {
    if (this.rawPdfUrl) {
      URL.revokeObjectURL(this.rawPdfUrl);
      this.rawPdfUrl = null;
      this.pdfUrl.set(null);
    }
  }

  private fail(error: { error?: ApiProblem }): void {
    this.busy.set(false);
    const problem = error.error;
    this.fieldErrors.set(problem?.errors ?? {});
    const details = Object.values(problem?.errors ?? {});
    this.error.set(details.length ? details.join(' ') : (problem?.detail ?? 'Une erreur est survenue.'));
  }
}
