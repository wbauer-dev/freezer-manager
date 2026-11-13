import { Component, LOCALE_ID, computed, inject } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { OidcSecurityService } from 'angular-auth-oidc-client';

import { LANGUAGES, switchLanguage } from './core/i18n/languages';
import { InitialsAvatar } from './shared/initials-avatar';

@Component({
  selector: 'app-root',
  imports: [
    RouterOutlet,
    RouterLink,
    MatButtonModule,
    MatIconModule,
    MatMenuModule,
    InitialsAvatar,
  ],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  private readonly auth = inject(OidcSecurityService);

  protected readonly authenticated = computed(() => this.auth.authenticated().isAuthenticated);
  protected readonly userName = computed(() => {
    const user = this.auth.userData().userData;
    return user?.name ?? user?.preferred_username ?? '';
  });
  protected readonly email = computed(() => this.auth.userData().userData?.email ?? '');

  protected readonly languages = LANGUAGES;
  protected readonly currentLanguage = inject(LOCALE_ID);

  protected switchLanguage(code: string): void {
    if (code !== this.currentLanguage) {
      switchLanguage(code);
    }
  }

  protected logout(): void {
    this.auth.logoff().subscribe();
  }
}
