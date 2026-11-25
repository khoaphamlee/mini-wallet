# ADR 0006: Issue and verify JWTs ourselves instead of using a managed identity provider

- Status: Accepted
- Date: 26/11/2026

## Context

Every API except register, login, refresh and the payment callback needs an authenticated user (PRD BR-13, `docs/architecture.md` section 6). We need short-lived access tokens (15 minutes), single-use refresh tokens (7 days) and two roles, `CUSTOMER` and `ADMIN`.

Managed identity providers such as Amazon Cognito, Keycloak or Auth0 already offer sign-up, sign-in, MFA and token issuing. This project is built by one person to learn and to show how a backend works, and it must run locally and in CI without cloud accounts.

## Decision

- The application issues its own access tokens: JWT signed with HS256, secret from `app.jwt.secret` (`JWT_SECRET` in production, at least 32 bytes, checked at startup).
- Claims: `iss` = `mini-wallet`, `sub` = user id, `role`, `iat`, `exp`. No personal data or secrets in the payload, since it is only Base64-encoded.
- Tokens are verified by Spring Security's OAuth2 Resource Server support (`NimbusJwtDecoder`), not by a hand-written filter. It checks the signature, the expiry (60 s clock skew) and the issuer.
- Sessions are stateless and CSRF protection is disabled, because the token travels in the `Authorization` header and never in a cookie.
- 401 and 403 from the security filters are passed to `GlobalExceptionHandler`, so they use the same ProblemDetail format as other errors.

## Options considered

| Option | Pros | Cons |
| --- | --- | --- |
| **Self-issued JWT + Spring Resource Server** (chosen) | Shows how authentication works end to end. Runs offline in tests. No extra service or cost | We own password storage, token rotation and their security. No MFA or social login out of the box |
| Amazon Cognito | Managed, MFA, social login, hosted sign-in page | Needs an AWS account, harder to run locally and in CI, hides the parts we want to learn |
| Keycloak (self-hosted) | Full-featured, open source, standard OIDC | One more heavy service to run and configure, too much for this project |
| Hand-written JWT filter (for example with jjwt) | Full control | Re-implements what Spring Security already does well; more code to get wrong |

## Consequences

- Moving to Cognito or Keycloak later mainly means configuring the Resource Server with their issuer and public keys (JWKS) and removing our token endpoints; the rest of the code reads the user from the same `Authentication` object.
- HS256 uses one shared secret: anyone who has it can create tokens. If another service ever needs to verify our tokens, switch to RS256 with a key pair.
- Access tokens cannot be revoked before they expire; this is why their lifetime is only 15 minutes. Refresh tokens are stored hashed and can be revoked (issue #6).
