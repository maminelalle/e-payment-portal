import { DatePipe } from '@angular/common';
import { Component, computed, ElementRef, HostListener, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { Preferences } from '../core/preferences';
import { Session } from '../core/session';

/** Élément du centre de notifications (cloche). */
export interface AppNotification {
  id: string;
  title: string;
  detail?: string;
  at: string;
  tone: 'info' | 'success' | 'warning' | 'danger';
  link?: string;
  queryParams?: Record<string, string>;
}

const TONE_ICONS: Record<AppNotification['tone'], string> = {
  info: 'pi pi-info-circle',
  success: 'pi pi-check-circle',
  warning: 'pi pi-exclamation-triangle',
  danger: 'pi pi-times-circle',
};

/**
 * Outils de l'en-tête, dans le même ordre pour l'admin et l'abonné :
 * langue · thème · notifications · utilisateur · déconnexion. Chaque bouton icône a un nom accessible et une infobulle.
 * Le badge de la cloche compte les notifications plus récentes que la dernière ouverture du panneau.
 */
@Component({
  selector: 'app-header-tools',
  imports: [DatePipe, RouterLink],
  template: `
    <div class="ep-lang" role="group" aria-label="Langue">
      <button type="button" [attr.aria-pressed]="preferences.language() === 'fr'" lang="fr"
              (click)="preferences.setLanguage('fr')">FR</button>
      <button type="button" [attr.aria-pressed]="preferences.language() === 'ar'" lang="ar"
              (click)="preferences.setLanguage('ar')">عربي</button>
    </div>
    <button type="button" class="icon-button" (click)="preferences.toggleTheme()"
            [attr.aria-label]="themeLabel()" [title]="themeLabel()">
      <i [class]="preferences.theme() === 'dark' ? 'pi pi-sun' : 'pi pi-moon'"></i>
    </button>
    <div class="bell">
      <button type="button" class="icon-button" aria-label="Notifications" title="Notifications"
              [attr.aria-expanded]="open()" aria-controls="notification-panel" (click)="toggle()">
        <i class="pi pi-bell"></i>
        @if (unread()) {
          <span class="badge" aria-hidden="true">{{ unread() > 9 ? '9+' : unread() }}</span>
        }
      </button>
      @if (open()) {
        <div class="panel card" id="notification-panel" role="dialog" aria-label="Notifications">
          <div class="panel-head">
            <strong>Notifications</strong>
            @if (notifications().length) {
              <span class="ep-subtle">{{ notifications().length }}</span>
            }
          </div>
          <ul>
            @for (n of notifications().slice(0, 8); track n.id) {
              <li>
                <a [routerLink]="n.link ?? null" [queryParams]="n.queryParams" (click)="open.set(false)"
                   [class.static]="!n.link">
                  <i [class]="icons[n.tone] + ' tone-' + n.tone" aria-hidden="true"></i>
                  <span class="text">
                    <span class="title">{{ n.title }}</span>
                    @if (n.detail) { <span class="detail">{{ n.detail }}</span> }
                    <span class="date"><bdi>{{ n.at | date: 'dd/MM/yyyy HH:mm' }}</bdi></span>
                  </span>
                </a>
              </li>
            } @empty {
              <li class="empty">
                <i class="pi pi-bell-slash" aria-hidden="true"></i>
                <span>Aucune notification.</span>
              </li>
            }
          </ul>
        </div>
      }
    </div>
    <a class="ep-user" [routerLink]="profileLink()" [title]="session.user()?.email ?? ''">
      <span class="avatar">{{ session.initials() }}</span>
      <span class="user-name">{{ session.user()?.fullName }}</span>
    </a>
    <button type="button" class="icon-button" aria-label="Se déconnecter" title="Se déconnecter"
            (click)="session.logout()">
      <i class="pi pi-sign-out ep-icon--flip"></i>
    </button>
  `,
  styles: `
    :host { display: flex; align-items: center; gap: var(--space-2); }
    .bell { position: relative; }
    .badge {
      position: absolute; top: -5px; inset-inline-end: -5px; min-width: 18px; height: 18px; padding: 0 5px;
      border-radius: var(--radius-pill); background: var(--danger); color: var(--surface);
      font-size: 11px; font-weight: 600; line-height: 18px; text-align: center;
    }
    .panel {
      position: absolute; z-index: 20; top: calc(100% + 8px); inset-inline-end: 0;
      width: min(360px, 90vw); max-height: 70vh; overflow: auto; box-shadow: var(--shadow-md);
    }
    .panel-head {
      display: flex; justify-content: space-between; align-items: center;
      padding: var(--space-3) var(--space-4); border-block-end: 1px solid var(--border);
    }
    ul { list-style: none; margin: 0; padding: 0; }
    li + li { border-block-start: 1px solid var(--border); }
    li a {
      display: flex; gap: var(--space-3); padding: var(--space-3) var(--space-4);
      color: var(--ink); text-decoration: none;
    }
    li a:hover { background: var(--surface-sunken); }
    li a.static { cursor: default; }
    li a i { margin-top: 3px; }
    .text { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
    .title { font-size: 14px; font-weight: 500; }
    .detail { font-size: 13px; color: var(--ink-muted); }
    .date { font-size: 12px; color: var(--ink-subtle); }
    .tone-info { color: var(--brand); }
    .tone-success { color: var(--success); }
    .tone-warning { color: var(--warning); }
    .tone-danger { color: var(--danger); }
    .empty {
      display: flex; flex-direction: column; align-items: center; gap: var(--space-2);
      padding: var(--space-8) var(--space-4); color: var(--ink-muted); font-size: 14px;
    }
    .empty i { font-size: 20px; color: var(--ink-subtle); }
    .ep-user { text-decoration: none; }
    [dir='rtl'] .ep-icon--flip, :host-context([dir='rtl']) .ep-icon--flip { transform: scaleX(-1); }
    @media (max-width: 640px) { .ep-lang { display: none; } }
  `,
})
export class HeaderTools {
  protected readonly preferences = inject(Preferences);
  protected readonly session = inject(Session);
  private readonly host = inject(ElementRef<HTMLElement>);

  readonly notifications = input<AppNotification[]>([]);
  readonly profileLink = input.required<string>();
  /** Espace (admin / abonné) : la date de dernière lecture est mémorisée séparément. */
  readonly space = input.required<string>();

  protected readonly icons = TONE_ICONS;
  protected readonly open = signal(false);
  private readonly seenAt = signal<number>(0);
  protected readonly unread = computed(
    () => this.notifications().filter((n) => new Date(n.at).getTime() > this.seenAt()).length,
  );
  protected readonly themeLabel = computed(() =>
    this.preferences.theme() === 'dark' ? 'Mode clair' : 'Mode sombre',
  );

  ngOnInit(): void {
    this.seenAt.set(Number(readStorage(this.storageKey())) || 0);
  }

  protected toggle(): void {
    this.open.update((v) => !v);
    if (this.open()) {
      const now = Date.now();
      this.seenAt.set(now);
      writeStorage(this.storageKey(), String(now));
    }
  }

  @HostListener('document:click', ['$event'])
  protected closeOutside(event: MouseEvent): void {
    if (this.open() && !this.host.nativeElement.contains(event.target as Node)) this.open.set(false);
  }

  @HostListener('document:keydown.escape')
  protected closeOnEscape(): void {
    this.open.set(false);
  }

  private storageKey(): string {
    return `ep-notifications-seen-${this.space()}`;
  }
}

function readStorage(key: string): string | null {
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
}

function writeStorage(key: string, value: string): void {
  try {
    localStorage.setItem(key, value);
  } catch {
    // stockage indisponible (navigation privée) : le badge se recalcule simplement à chaque visite
  }
}
