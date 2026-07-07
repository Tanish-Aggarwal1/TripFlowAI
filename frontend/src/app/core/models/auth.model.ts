export interface AuthResponse {
  token: string;
  tokenType: string;
  userId: number;
  username: string;
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