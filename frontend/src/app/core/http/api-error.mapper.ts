import { HttpErrorResponse } from '@angular/common/http';
import { FieldError } from '../models/auth.model';

/** Shape the backend's canonical ApiError body takes on the wire (docs/api-contracts.md). */
interface ApiErrorBody {
  message?: string;
  fieldErrors?: FieldError[];
}

/** A normalised, typed error every service's catchError handler resolves to. */
export interface ApiClientError extends Error {
  status: number;
  fieldErrors: FieldError[] | null;
}

/**
 * Per-status message override. A plain string always wins for that status. A function
 * receives the parsed body and may return `undefined` to defer to the generic fallback
 * (network-error message / `fallbackToBackendMessage` / `defaultMessage`) instead of
 * forcing an override — e.g. a 400 that turns out to have no fieldErrors after all.
 */
type StatusMessage = string | ((body: ApiErrorBody | undefined) => string | undefined);

export interface ApiErrorMappingOptions {
  /** Overrides keyed by HTTP status, checked before any generic fallback. */
  messagesByStatus?: Partial<Record<number, StatusMessage>>;
  /** Message for a network-level failure (status 0). */
  networkErrorMessage?: string;
  /** When true, a status with no override falls back to the backend's own message if present. */
  fallbackToBackendMessage?: boolean;
  /** Used when nothing else matches. */
  defaultMessage?: string;
}

const DEFAULT_MESSAGE = 'Something went wrong, please try again.';
const DEFAULT_NETWORK_MESSAGE = 'Network error. Please check your connection.';

/**
 * Maps a failed HTTP response to a typed {@link ApiClientError}, parsing the shared
 * ApiError body shape exactly once. Extracted (SCRUM-220) out of TripService.handleError
 * and AuthService.handleAuthError, which had independently reimplemented the same
 * status/body parsing with different status branches and message wording — every future
 * service (photos, discovery, ...) would otherwise copy it again.
 *
 * Per-service wording lives in the caller's `options`, not here — e.g. "Trip not found."
 * for a 404 is more useful than a generic message, and AuthService's non-revealing 401
 * message is a deliberate security decision (UC-01/UC-02) that must not be diluted by a
 * one-size-fits-all default.
 */
export function mapApiError(err: HttpErrorResponse, options: ApiErrorMappingOptions = {}): ApiClientError {
  const {
    messagesByStatus = {},
    networkErrorMessage = DEFAULT_NETWORK_MESSAGE,
    fallbackToBackendMessage = false,
    defaultMessage = DEFAULT_MESSAGE,
  } = options;

  const body = err.error as ApiErrorBody | null | undefined;
  const override = messagesByStatus[err.status];
  const resolvedOverride = typeof override === 'function' ? override(body ?? undefined) : override;

  let message: string;
  if (err.status === 0) {
    message = networkErrorMessage;
  } else if (resolvedOverride !== undefined) {
    message = resolvedOverride;
  } else if (fallbackToBackendMessage && body?.message) {
    message = body.message;
  } else {
    message = defaultMessage;
  }

  const fieldErrors = body?.fieldErrors?.length ? body.fieldErrors : null;

  return Object.assign(new Error(message), { status: err.status, fieldErrors });
}
