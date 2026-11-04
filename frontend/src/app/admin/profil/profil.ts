import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { MessageService } from 'primeng/api';

import { Preferences } from '../../core/preferences';
import { Session } from '../../core/session';

@Component({
  selector: 'app-admin-profil',
  imports: [FormsModule, ButtonModule, InputTextModule],
  templateUrl: './profil.html',
  styleUrl: './profil.scss',
})
export class AdminProfil {
  protected readonly session = inject(Session);
  protected readonly preferences = inject(Preferences);
  private readonly messages = inject(MessageService);
  protected email = this.session.user()?.email ?? '';
  protected fullName = this.session.user()?.fullName ?? '';
  protected saving = false;

  protected save(): void {
    if (!this.email.trim() || !this.fullName.trim()) return;
    this.saving = true;
    this.session.updateProfile(this.email, this.fullName).subscribe({
      next: (user) => {
        this.session.user.set(user);
        this.saving = false;
        this.messages.add({ severity: 'success', summary: 'Profil mis à jour' });
      },
      error: () => {
        this.saving = false;
        this.messages.add({ severity: 'error', summary: 'Mise à jour impossible', detail: 'Vérifiez les informations saisies.' });
      },
    });
  }
}
