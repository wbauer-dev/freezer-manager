import { Component, OnInit, inject } from '@angular/core';
import { Router } from '@angular/router';

import { takeReturnUrl } from './auth-guard';

/**
 * Where the identity provider sends users back to. The login itself is completed before the app starts (see
 * app.config.ts); this page only continues to the address the user originally asked for.
 */
@Component({
  selector: 'app-auth-callback',
  template: '',
})
export class AuthCallback implements OnInit {
  private readonly router = inject(Router);

  ngOnInit(): void {
    this.router.navigateByUrl(takeReturnUrl() ?? '/households', { replaceUrl: true });
  }
}
