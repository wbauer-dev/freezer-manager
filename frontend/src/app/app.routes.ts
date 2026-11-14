import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth-guard';
import { startPageGuard } from './household/start-page-guard';

/**
 * Feature folders follow the backend's bounded contexts (household, catalog, inventory, ...).
 */
export const routes: Routes = [
  {
    // Never shown: leads into the only household, or to the list of households.
    path: '',
    pathMatch: 'full',
    canActivate: [authGuard, startPageGuard],
    children: [],
  },
  {
    path: 'auth/callback',
    loadComponent: () => import('./core/auth/auth-callback').then((m) => m.AuthCallback),
  },
  {
    path: 'households',
    canActivate: [authGuard],
    loadComponent: () => import('./household/households-page').then((m) => m.HouseholdsPage),
  },
  {
    path: 'households/:householdId',
    canActivate: [authGuard],
    canActivateChild: [authGuard],
    loadComponent: () => import('./household/household-shell').then((m) => m.HouseholdShell),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'eat-first' },
      {
        path: 'eat-first',
        loadComponent: () => import('./inventory/eat-first-page').then((m) => m.EatFirstPage),
      },
      {
        path: 'shopping',
        loadComponent: () => import('./shopping/shopping-page').then((m) => m.ShoppingPage),
      },
      {
        path: 'freezers',
        loadComponent: () => import('./inventory/freezers-page').then((m) => m.FreezersPage),
      },
      {
        path: 'freeze',
        loadComponent: () => import('./inventory/freeze-page').then((m) => m.FreezePage),
      },
      {
        path: 'items/:itemId',
        loadComponent: () => import('./inventory/item-page').then((m) => m.ItemPage),
      },
      {
        path: 'catalog',
        loadComponent: () => import('./catalog/catalog-page').then((m) => m.CatalogPage),
      },
      {
        path: 'labels',
        loadComponent: () => import('./labeling/labels-page').then((m) => m.LabelsPage),
      },
      {
        path: 'members',
        loadComponent: () => import('./household/members-page').then((m) => m.MembersPage),
      },
    ],
  },
  {
    // Invitation links lead here; after logging in, the user joins the household.
    path: 'join/:code',
    canActivate: [authGuard],
    loadComponent: () => import('./household/join-page').then((m) => m.JoinPage),
  },
  {
    // The scanner inside the app: camera, label, food, take out.
    path: 'scan',
    canActivate: [authGuard],
    loadComponent: () => import('./labeling/scanner-page').then((m) => m.ScannerPage),
  },
  {
    // QR codes on labels link here.
    path: 's/:code',
    canActivate: [authGuard],
    loadComponent: () => import('./labeling/scan-page').then((m) => m.ScanPage),
  },
  { path: '**', redirectTo: 'households' },
];
