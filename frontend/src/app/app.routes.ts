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
    path: 'starting-up',
    loadComponent: () => import('./pages/starting-up/starting-up.page').then(m => m.StartingUpPage)
  },
  {
    path: 'dashboard',
    loadComponent: () => import('./pages/trips/dashboard/dashboard.page').then(m => m.DashboardPage),
    canActivate: [authGuard]
  },
  {
    path: 'feed',
    loadComponent: () => import('./pages/feed/feed.page').then(m => m.FeedPage),
    canActivate: [authGuard]
  },
  {
    path: 'profile',
    loadComponent: () => import('./pages/profile/profile.page').then(m => m.ProfilePage),
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

];