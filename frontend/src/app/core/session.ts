import { HttpClient } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { OidcSecurityService } from 'angular-auth-oidc-client';
import { firstValueFrom } from 'rxjs';

export type Role = 'ADMIN' | 'ABONNE';

/** Réponse de GET /api/me et POST /api/me/connexion. */
export interface CurrentUser {
  id: string | null;
  email: string;
  fullName: string;
  roles: Role[];
  lastLoginAt: string | null;
}

/**
 * Session de l'utilisateur connecté. Les rôles viennent du backend (/api/me), qui les lit dans le jeton :
 * le frontend s'en sert pour l'affichage, le contrôle réel reste côté serveur.
 */
@Injectable({ providedIn: 'root' })
export class Session {
  private readonly oidc = inject(OidcSecurityService);
  private readonly http = inject(HttpClient);

  readonly user = signal<CurrentUser | null>(null);
  readonly isAdmin = computed(() => this.hasRole('ADMIN'));
  readonly initials = computed(() => initialsOf(this.user()?.fullName ?? ''));

  /** Exécuté au démarrage : traite le retour de Keycloak puis charge le profil. */
  async init(): Promise<void> {
    const isLoginCallback = new URLSearchParams(window.location.search).has('code');
    try {
      const result = await firstValueFrom(this.oidc.checkAuth());
      if (!result.isAuthenticated) {
        return;
      }
      // Le retour de connexion est journalisé une seule fois (LOGIN) ; un rechargement lit simplement le profil.
      const user$ = isLoginCallback
        ? this.http.post<CurrentUser>('/api/me/connexion', null)
        : this.http.get<CurrentUser>('/api/me');
      this.user.set(await firstValueFrom(user$));
    } catch (error) {
      // Keycloak ou l'API indisponible : l'application démarre quand même, sur la page de connexion.
      console.error('Initialisation de la session impossible', error);
      this.expire();
    }
  }

  hasRole(role: Role): boolean {
    return this.user()?.roles.includes(role) ?? false;
  }

  /** Page d'accueil de l'espace correspondant au rôle. */
  homeUrl(): string {
    if (this.hasRole('ADMIN')) return '/admin';
    if (this.hasRole('ABONNE')) return '/abonne';
    return '/connexion';
  }

  login(): void {
    this.oidc.authorize();
  }

  /** Déconnexion complète : fin de la session Keycloak puis retour à /connexion. */
  logout(): void {
    this.user.set(null);
    this.oidc.logoff().subscribe();
  }

  /** Oublie la session locale sans passer par Keycloak (jeton expiré ou refusé). */
  expire(): void {
    this.user.set(null);
    this.oidc.logoffLocal();
  }
}

function initialsOf(name: string): string {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]!.toUpperCase())
    .join('');
}
