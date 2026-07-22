import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  {
    path: '',
    redirectTo: 'login',
    pathMatch: 'full',
  },
  {
    path: 'login',
    loadComponent: () => import('./pages/auth/login/login.page').then(m => m.LoginPage)
  },
  {
    path: 'signup',
    loadComponent: () => import('./pages/auth/signup/signup.page').then(m => m.SignupPage)
  },
  {
    path: 'dashboard',
    loadComponent: () => import('./pages/trips/dashboard/dashboard.page').then(m => m.DashboardPage),
    canActivate: [authGuard]
  },
  {
    path: 'trips/new',
    loadComponent: () => import('./pages/trips/trip-edit/trip-edit.page').then(m => m.TripEditPage),
    canActivate: [authGuard]
  },

  {
  path: 'trips/:id',
  loadComponent: () => import('./pages/trips/trip-view/trip-view.page').then(m => m.TripViewPage),
  canActivate: [authGuard]
  },
  {
    path: 'trips/:id/edit',
    loadComponent: () => import('./pages/trips/trip-edit/trip-edit.page').then(m => m.TripEditPage),
    canActivate: [authGuard]
  },
  {
    path: 'trip-view',
    loadComponent: () => import('./pages/trips/trip-view/trip-view.page').then( m => m.TripViewPage)
  },

];