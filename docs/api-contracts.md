# API Contracts

Living document. Add a new section per epic as endpoints are built. Update if a contract changes — announce the change in team chat when it does.

## Auth (Sprint 1)

### POST /api/auth/register
**Request:**
```json
{
  "email": "string",
  "password": "string",
  "displayName": "string"
}
```
**Success (201):**
```json
{
  "token": "string",
  "expiresAt": "ISO-8601 datetime"
}
```
**Errors:**
- 409 — email already registered
- 400 — validation failure (see standard error shape below)

### POST /api/auth/login
**Request:**
```json
{
  "email": "string",
  "password": "string"
}
```
**Success (200):** same shape as register
**Errors:**
- 401 — invalid credentials

## Standard Error Shape
All errors follow this structure:
```json
{
  "timestamp": "ISO-8601 datetime",
  "status": 400,
  "message": "string",
  "fieldErrors": { "field": "reason" }
}
```

## Auth Header
Protected endpoints require: `Authorization: Bearer <token>`