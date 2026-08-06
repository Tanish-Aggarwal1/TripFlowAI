import { HttpErrorResponse } from '@angular/common/http';
import { mapApiError } from './api-error.mapper';

function errorResponse(status: number, body?: unknown): HttpErrorResponse {
  return new HttpErrorResponse({ status, error: body });
}

describe('mapApiError', () => {
  it('maps a network error (status 0) to the default network message', () => {
    const result = mapApiError(errorResponse(0));

    expect(result.status).toBe(0);
    expect(result.message).toBe('Network error. Please check your connection.');
  });

  it('maps a network error (status 0) to a custom networkErrorMessage when given', () => {
    const result = mapApiError(errorResponse(0), { networkErrorMessage: 'Offline.' });

    expect(result.message).toBe('Offline.');
  });

  it('a string override for the status wins over the default message', () => {
    const result = mapApiError(errorResponse(404, { message: 'backend detail' }), {
      messagesByStatus: { 404: 'Trip not found.' },
    });

    expect(result.message).toBe('Trip not found.');
  });

  it('a function override that returns a string wins over the default message', () => {
    const result = mapApiError(errorResponse(409, { message: 'Email already registered' }), {
      messagesByStatus: { 409: (body) => body?.message ?? 'Conflict.' },
    });

    expect(result.message).toBe('Email already registered');
  });

  it('a function override that returns undefined falls through to fallbackToBackendMessage', () => {
    const result = mapApiError(errorResponse(400, { message: 'no field errors after all' }), {
      messagesByStatus: { 400: (body) => (body?.fieldErrors?.length ? 'Please fix the errors below.' : undefined) },
      fallbackToBackendMessage: true,
    });

    expect(result.message).toBe('no field errors after all');
  });

  it('a function override that returns undefined falls through to defaultMessage when fallback is off', () => {
    const result = mapApiError(errorResponse(400, { message: 'no field errors after all' }), {
      messagesByStatus: { 400: () => undefined },
      defaultMessage: 'Custom default.',
    });

    expect(result.message).toBe('Custom default.');
  });

  it('fallbackToBackendMessage true uses the backend message when there is no status override', () => {
    const result = mapApiError(errorResponse(500, { message: 'Something broke server-side' }), {
      fallbackToBackendMessage: true,
    });

    expect(result.message).toBe('Something broke server-side');
  });

  it('fallbackToBackendMessage false (default) never leaks the backend message', () => {
    const result = mapApiError(errorResponse(500, { message: 'Something broke server-side' }));

    expect(result.message).toBe('Something went wrong, please try again.');
  });

  it('a 401 with fallbackToBackendMessage false stays generic instead of leaking backend detail', () => {
    // Security-relevant: AuthService relies on this to keep 401 messages non-revealing (UC-01/UC-02).
    const result = mapApiError(errorResponse(401, { message: 'no user found with that email' }), {
      fallbackToBackendMessage: false,
      defaultMessage: 'Invalid credentials.',
    });

    expect(result.message).toBe('Invalid credentials.');
    expect(result.message).not.toContain('no user found');
  });

  it('normalizes an empty fieldErrors array to null', () => {
    const result = mapApiError(errorResponse(400, { message: 'bad', fieldErrors: [] }));

    expect(result.fieldErrors).toBeNull();
  });

  it('normalizes a missing fieldErrors property to null', () => {
    const result = mapApiError(errorResponse(400, { message: 'bad' }));

    expect(result.fieldErrors).toBeNull();
  });

  it('passes through a non-empty fieldErrors array', () => {
    const fieldErrors = [{ field: 'email', message: 'must not be blank' }];
    const result = mapApiError(errorResponse(400, { message: 'bad', fieldErrors }));

    expect(result.fieldErrors).toEqual(fieldErrors);
  });

  it('returns an Error instance carrying status and fieldErrors', () => {
    const result = mapApiError(errorResponse(403));

    expect(result).toBeInstanceOf(Error);
    expect(result.status).toBe(403);
    expect(result.fieldErrors).toBeNull();
  });
});
