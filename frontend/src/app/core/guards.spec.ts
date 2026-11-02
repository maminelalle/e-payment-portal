import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Route, Router, UrlSegment, UrlTree } from '@angular/router';

import { anonymousOnlyGuard, roleGuard } from './guards';
import { CurrentUser, Role, Session } from './session';

describe('guards', () => {
  const user = signal<CurrentUser | null>(null);

  function connectAs(...roles: Role[]) {
    user.set({ id: '1', email: 'x@exemple.mr', fullName: 'X', roles, lastLoginAt: null });
  }

  function runRoleGuard(role: Role) {
    return TestBed.runInInjectionContext(() => roleGuard(role)({} as Route, [] as UrlSegment[]));
  }

  function urlOf(result: unknown): string {
    return TestBed.inject(Router).serializeUrl(result as UrlTree);
  }

  beforeEach(() => {
    user.set(null);
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        {
          provide: Session,
          useValue: {
            user,
            hasRole: (role: Role) => user()?.roles.includes(role) ?? false,
            homeUrl: () => (user()?.roles.includes('ADMIN') ? '/admin' : user() ? '/abonne' : '/connexion'),
          },
        },
      ],
    });
  });

  it('envoie un visiteur non connecté vers la connexion', () => {
    expect(urlOf(runRoleGuard('ADMIN'))).toBe('/connexion');
  });

  it("laisse entrer l'administrateur dans son espace", () => {
    connectAs('ADMIN');
    expect(runRoleGuard('ADMIN')).toBe(true);
  });

  it("renvoie un abonné vers son espace s'il tente l'espace admin", () => {
    connectAs('ABONNE');
    expect(urlOf(runRoleGuard('ADMIN'))).toBe('/abonne');
  });

  it('ne réaffiche pas la page de connexion à un utilisateur connecté', () => {
    connectAs('ABONNE');
    const result = TestBed.runInInjectionContext(() => anonymousOnlyGuard({} as never, {} as never));
    expect(urlOf(result)).toBe('/abonne');
  });
});
