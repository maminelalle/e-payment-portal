import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, DestroyRef, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { CheckboxModule } from 'primeng/checkbox';
import { DialogModule } from 'primeng/dialog';
import { InputOtpModule } from 'primeng/inputotp';

import { ApiProblem, Contract } from '../../core/api.models';
import { ContractStatusTag } from '../../shared/contract-status-tag';
import { formatAmount, formatIsoDate } from '../../shared/formatting';
import { AbonneApi, OtpChallenge } from '../abonne-api';
import { MethodLogo } from '../../shared/method-logo';

type SigningStep = 'consent' | 'code' | 'done';

@Component({
  selector: 'app-mon-contrat',
  imports: [DatePipe, FormsModule, ButtonModule, CheckboxModule, DialogModule, InputOtpModule, ContractStatusTag, MethodLogo],
  templateUrl: './mon-contrat.html',
  styleUrl: './mon-contrat.scss',
})
export class MonContrat {
  private readonly api = inject(AbonneApi);
  private readonly messages = inject(MessageService);
  private readonly sanitizer = inject(DomSanitizer);

  protected readonly contract = signal<Contract | null | undefined>(undefined);
  protected readonly pdfUrl = signal<SafeResourceUrl | null>(null);
  private rawPdfUrl: string | null = null;

  protected readonly formatAmount = formatAmount;
  protected readonly formatDate = formatIsoDate;

  // Boîte de signature (maquette B2 : étape 1 accord, étape 2 code, étape 3 confirmation)
  protected readonly dialogOpen = signal(false);
  protected readonly step = signal<SigningStep>('consent');
  protected readonly stepIndex = computed(() => ({ consent: 1, code: 2, done: 3 })[this.step()]);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly challenge = signal<OtpChallenge | null>(null);
  protected readonly secondsBeforeResend = signal(0);
  protected accepted = false;
  protected code = '';
  private timer: ReturnType<typeof setInterval> | null = null;

  constructor() {
    inject(DestroyRef).onDestroy(() => {
      this.revokePdf();
      this.stopTimer();
    });
    this.load();
  }

  protected openSigning(): void {
    this.accepted = false;
    this.code = '';
    this.error.set(null);
    this.step.set('consent');
    this.dialogOpen.set(true);
  }

  protected closeSigning(): void {
    this.dialogOpen.set(false);
    this.stopTimer();
    if (this.step() === 'done') {
      this.load();
    }
  }

  protected requestCode(): void {
    this.busy.set(true);
    this.error.set(null);
    this.api.requestSignatureCode().subscribe({
      next: (challenge) => {
        this.busy.set(false);
        this.challenge.set(challenge);
        this.code = '';
        this.step.set('code');
        this.startResendTimer(challenge.resendAvailableAt);
      },
      error: (error) => this.fail(error),
    });
  }

  protected confirm(): void {
    const challenge = this.challenge();
    if (!challenge || !/^\d{6}$/.test(this.code)) {
      this.error.set('Saisissez les 6 chiffres du code reçu par email.');
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    this.api.confirmSignature(challenge.signatureId, this.code).subscribe({
      next: (signed) => {
        this.busy.set(false);
        this.stopTimer();
        this.contract.set(signed);
        this.step.set('done');
        this.load();
      },
      error: (error) => {
        this.code = '';
        this.fail(error);
      },
    });
  }

  protected download(): void {
    const c = this.contract();
    if (!c || !this.rawPdfUrl) return;
    const link = document.createElement('a');
    link.href = this.rawPdfUrl;
    link.download = `${c.reference}${c.signedAt ? '-signe' : ''}.pdf`;
    link.click();
  }

  protected resendLabel(): string {
    const s = this.secondsBeforeResend();
    return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
  }

  private load(): void {
    this.api.currentContract().subscribe({
      next: (contract) => {
        this.contract.set(contract);
        this.revokePdf();
        if (contract) {
          this.api.contractPdf().subscribe((blob) => {
            this.rawPdfUrl = URL.createObjectURL(blob);
            this.pdfUrl.set(this.sanitizer.bypassSecurityTrustResourceUrl(this.rawPdfUrl));
          });
        }
      },
      error: () => this.contract.set(null),
    });
  }

  private startResendTimer(availableAt: string): void {
    this.stopTimer();
    const tick = () => {
      const remaining = Math.max(0, Math.ceil((new Date(availableAt).getTime() - Date.now()) / 1000));
      this.secondsBeforeResend.set(remaining);
      if (remaining === 0) this.stopTimer();
    };
    tick();
    this.timer = setInterval(tick, 1000);
  }

  private stopTimer(): void {
    if (this.timer) {
      clearInterval(this.timer);
      this.timer = null;
    }
  }

  private revokePdf(): void {
    if (this.rawPdfUrl) {
      URL.revokeObjectURL(this.rawPdfUrl);
      this.rawPdfUrl = null;
      this.pdfUrl.set(null);
    }
  }

  private fail(error: unknown): void {
    this.busy.set(false);
    const problem = error instanceof HttpErrorResponse ? (error.error as (ApiProblem & { remainingAttempts?: number }) | null) : null;
    this.error.set(problem?.detail ?? 'Une erreur est survenue. Réessayez.');
    // Code expiré ou essais épuisés : il faut redemander un code.
    if (problem?.remainingAttempts === 0) {
      this.secondsBeforeResend.set(0);
      this.stopTimer();
    }
    if (error instanceof HttpErrorResponse && error.status >= 500) {
      this.messages.add({ severity: 'error', summary: 'Signature impossible', detail: 'Réessayez dans un instant.' });
    }
  }
}
