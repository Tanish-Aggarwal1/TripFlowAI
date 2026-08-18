export interface AuthResponse {
  token: string;
  tokenType: string;
  userId: number;
  username: string;
  expiresAt: string; // ISO instant
}

// POST /api/auth/refresh — deliberately omits the user identity fields; the SPA already holds them.
export interface RefreshResponse {
  token: string;
  tokenType: string;
  expiresAt: string; // ISO instant
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  username: string;
  email: string;
  password: string;
}

export interface FieldError {
  field: string;
  message: string;
}