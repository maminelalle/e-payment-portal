import { DatePipe } from '@angular/common';
import { Component, DestroyRef, effect, inject, input, signal } from '@angular/core';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { Router, RouterLink } from '@angular/router';
import { ConfirmationService, MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';

import { ApiProblem, Contract, EntitledMethod } from '../../core/api.models';
import { ContractStatusTag } from '../../shared/contract-status-tag';
import { formatAmount, formatIsoDate } from '../../shared/formatting';
import { AdminApi } from '../admin-api';

@Component({
  selector: 'app-contrat-fiche',
  imports: [DatePipe, RouterLink, ButtonModule, ContractStatusTag],
  templateUrl: './contrat-fiche.html',
  styleUrl: './contrat-fiche.scss',
})
export class ContratFiche {
  private readonly api = inject(AdminApi);
  private readonly router = inject(Router);
  private readonly messages = inject(MessageService);
  private readonly confirmation = inject(ConfirmationService);
  private readonly sanitizer = inject(DomSanitizer);

  /** Paramètre de route :id. */
  readonly id = input.required<string>();

  protected readonly contract = signal<Contract | null>(null);
  protected readonly notFound = signal(false);
  protected readonly busy = signal(false);
  protected readonly entitlements = signal<EntitledMethod[]>([]);
  protected readonly pdfUrl = signal<SafeResourceUrl | null>(null);
  private rawPdfUrl: string | null = null;

  protected readonly formatAmount = formatAmount;
  protected readonly formatDate = formatIsoDate;

  constructor() {
    inject(DestroyRef).onDestroy(() => this.revokePdf());
    effect(() => this.load(this.id()));
  }

  protected send(): void {
    const c = this.contract();
    if (!c) return;
    this.confirmation.confirm({
      header: 'Envoyer pour signature',
      message: `Le contrat ${c.reference} sera figé (PDF et empreinte) puis mis à disposition de ${c.subscriber.displayName}, qui sera prévenu par email.`,
      acceptLabel: 'Envoyer',
      rejectLabel: 'Annuler',
      rejectButtonProps: { severity: 'secondary', outlined: true },
      accept: () => {
        this.busy.set(true);
        this.api.sendContract(c.id).subscribe({
          next: (sent) => {
            this.busy.set(false);
            this.messages.add({ severity: 'success', summary: `Contrat ${sent.reference} envoyé pour signature` });
            this.show(sent);
          },
          error: (error) => this.fail(error),
        });
      },
    });
  }

  protected remove(): void {
    const c = this.contract();
    if (!c) return;
    this.confirmation.confirm({
      header: 'Supprimer le brouillon',
      message: `Le brouillon ${c.reference} sera supprimé définitivement.`,
      acceptLabel: 'Supprimer',
      rejectLabel: 'Annuler',
      acceptButtonProps: { severity: 'danger' },
      rejectButtonProps: { severity: 'secondary', outlined: true },
      accept: () =>
        this.api.deleteContractDraft(c.id).subscribe({
          next: () => {
            this.messages.add({ severity: 'success', summary: `Brouillon ${c.reference} supprimé` });
            this.router.navigate(['/admin/contrats']);
          },
          error: (error) => this.fail(error),
        }),
    });
  }

  /** Activer / suspendre / reprendre / résilier : le serveur ouvre ou ferme les droits avec le contrat. */
  protected lifecycle(action: 'activer' | 'suspendre' | 'reprendre' | 'resilier'): void {
    const c = this.contract();
    if (!c) return;
    const texts = {
      activer: ['Activer le contrat', `Les droits de ${c.subscriber.displayName} seront ouverts sur les moyens du contrat.`, 'Activer'],
      suspendre: ['Suspendre le contrat', `Les paiements de ${c.subscriber.displayName} seront bloqués jusqu'à la reprise.`, 'Suspendre'],
      reprendre: ['Reprendre le contrat', `Les droits de ${c.subscriber.displayName} seront rouverts.`, 'Reprendre'],
      resilier: ['Résilier le contrat', `Résiliation définitive : les droits sont révoqués, l'historique est conservé.`, 'Résilier'],
    } as const;
    const [header, message, label] = texts[action];
    this.confirmation.confirm({
      header,
      message,
      acceptLabel: label,
      rejectLabel: 'Annuler',
      acceptButtonProps: { severity: action === 'resilier' || action === 'suspendre' ? 'danger' : 'primary' },
      rejectButtonProps: { severity: 'secondary', outlined: true },
      accept: () => {
        this.busy.set(true);
        this.api.contractLifecycle(c.id, action).subscribe({
          next: (updated) => {
            this.busy.set(false);
            this.contract.set(updated);
            this.loadEntitlements(updated.id);
            this.messages.add({ severity: 'success', summary: `${header.split(' ')[0]} : ${updated.reference}` });
          },
          error: (error) => this.fail(error),
        });
      },
    });
  }

  protected download(): void {
    const c = this.contract();
    if (!c || !this.rawPdfUrl) return;
    const link = document.createElement('a');
    link.href = this.rawPdfUrl;
    link.download = `${c.reference}${c.status === 'DRAFT' ? '-brouillon' : ''}.pdf`;
    link.click();
  }

  private load(id: string): void {
    this.notFound.set(false);
    this.api.getContract(id).subscribe({
      next: (c) => this.show(c),
      error: () => this.notFound.set(true),
    });
  }

  private loadEntitlements(id: string): void {
    this.api.contractEntitlements(id).subscribe((list) => this.entitlements.set(list));
  }

  private show(contract: Contract): void {
    this.contract.set(contract);
    this.loadEntitlements(contract.id);
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
    const details = Object.values(problem?.errors ?? {});
    this.messages.add({
      severity: 'error',
      summary: 'Action impossible',
      detail: details.length ? details.join(' ') : (problem?.detail ?? 'Une erreur est survenue.'),
      life: 8000,
    });
  }
}
