import { DOCUMENT } from '@angular/common';
import { computed, inject, Injectable, signal } from '@angular/core';

export type AppLanguage = 'fr' | 'ar';
export type ThemeMode = 'light' | 'dark';

@Injectable({ providedIn: 'root' })
export class Preferences {
  private readonly document = inject(DOCUMENT);
  readonly language = signal<AppLanguage>((localStorage.getItem('ep-language') as AppLanguage) || 'fr');
  readonly theme = signal<ThemeMode>((localStorage.getItem('ep-theme') as ThemeMode) || 'light');
  readonly isArabic = computed(() => this.language() === 'ar');

  constructor() {
    this.apply();
  }

  setLanguage(language: AppLanguage): void {
    this.language.set(language);
    localStorage.setItem('ep-language', language);
    this.apply();
  }

  setTheme(theme: ThemeMode): void {
    this.theme.set(theme);
    localStorage.setItem('ep-theme', theme);
    this.apply();
  }

  toggleTheme(): void {
    this.setTheme(this.theme() === 'light' ? 'dark' : 'light');
  }

  private apply(): void {
    this.document.documentElement.lang = this.language();
    this.document.documentElement.dir = this.isArabic() ? 'rtl' : 'ltr';
    this.document.documentElement.classList.toggle('dark', this.theme() === 'dark');
  }
}
