# Commit message examples

## Templates and examples

### Simple Bug Fix

```text
fix(auth): resolve session token expiration on logout

Previously, invalidating a session did not clear the token from
storage, allowing re-use after logout. Now clearing storage happens
atomically with session invalidation.
```

### Feature with Scope

```text
feat(api): add pagination support to user list endpoint

Users requested the ability to fetch large user lists in batches.
Endpoint now accepts ?page=<n>&size=<n> parameters. Default page
size is 20.
```

### Breaking Change Marker

```text
feat(auth)!: require OAuth2 for all API endpoints

BREAKING CHANGE: Basic auth is no longer supported. All clients
must migrate to OAuth2 bearer tokens. See migration guide in
docs/migration-oauth2.md.
```

### Multiple Concerns in Body

```text
refactor(parser): split lexer and parser modules

This change improves maintainability by separating concerns:

- Lexer now handles tokenization only
- Parser handles AST construction
- Error recovery is clearer in each module

No behavior change. Existing tests pass.
```

### Fix Without Scope (When Scope Adds Noise)

```text
fix: handle edge case in date parsing for year 9999

Year 9999 was incorrectly parsed as a 2-digit number in some
locales. Root cause was missing century boundary check.
```

### Revert

```text
revert: undo refactoring of email service (commit abc1234)

The async refactor introduced subtle timing bugs in test suite.
Reverting to stable version pending investigation.
```

## Pitfalls

Vague subjects without verbs:

```text
# Wrong
fix: stuff
chore: updates
docs: changes
```

```text
# Correct
fix: handle null pointer in user lookup
chore: upgrade lodash to 4.17.21
docs: add setup instructions for macOS
```

Body restates the diff instead of explaining why:

```text
# Wrong
fix: update login handler

Modified login.js to check token expiry. Also updated session.js
to clear storage.
```

```text
# Correct
fix: resolve session token expiration on logout

When a user logs out, the token must be cleared from storage
immediately to prevent reuse in a hijacking scenario.
```

Mixing unrelated changes:

```text
# Wrong
feat: add two-factor auth and update dependencies

Two independent concerns forced into one message. Should be split.
```

Breaking change missing the `!` marker:

```text
# Wrong
feat(api): remove deprecated endpoint

Git log hides the breaking-change signal.
Release tooling may miss it.
```

```text
# Correct
feat(api)!: remove deprecated endpoint

BREAKING CHANGE: /api/v1/users endpoint has been removed. Use
/api/v2/users instead.
```
