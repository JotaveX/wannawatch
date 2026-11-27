# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

wannawatch? is a movie/series recommendation app (Home vitrine, Swipe, Reels, group Match). Monorepo:
`frontend/` (Angular 21) and `backend/` (Java 21 + Spring Boot 4), with Postgres 18 via `docker-compose.yml`.
`README.md` is the source of truth for the API table, routes, keyboard shortcuts and product rules — keep it
updated when those change.

**Language:** code identifiers are English, but comments, UI text, error messages, migration names, README and
commit messages are Brazilian Portuguese. Follow that.

## Commands

Setup: `cp .env.example .env` (needs `TMDB_API_TOKEN`; `ADMIN_TOKEN` / `YOUTUBE_API_KEY` / `R2_*` optional; `MAIL_*` default to Mailpit). The backend reads `../.env`
directly via `spring.config.import`, so it works from `backend/` without exporting variables.

```bash
docker compose up -d --wait        # Postgres on :5434 + Mailpit (SMTP :1025, inbox :8025)   (down -v to reset)

# backend (port 8081) — uses the Maven wrapper
cd backend
./mvnw spring-boot:run
./mvnw test                        # needs Docker: Testcontainers starts postgres:18-alpine
./mvnw test -Dtest=TmdbMapperTest  # single class (or -Dtest='Class#method')

# frontend (port 4200) — uses the local CLI, no global ng
cd frontend
npm start
npm test -- --watch=false                                   # Vitest via @angular/build:unit-test
npx ng test --watch=false --include src/app/core/format.spec.ts   # single spec
npm run build
npx prettier --write <files>                                # printWidth 100, single quotes
```

**Deploy** (README, "Deploy"): push on `main` → `.github/workflows/ci.yml` runs both test suites, then
`wrangler deploy` of `frontend/` (wannawatch.jotave.dev) and the GHCR image + SSH `deploy.sh` on the VPS
(`/opt/wannawatch`, copies of `deploy/`; API at apiwannawatch.jotave.dev via Cloudflare Tunnel). Changing
`deploy/compose.yml` needs a manual `scp` to the VPS.

Swagger: `http://localhost:8081/swagger-ui.html`. Component showcase (dev only): `/dev/componentes`.

## Backend architecture (`br.com.jotave.wannawatch`)

- **Packages by feature**: `catalog` (`api` / `application` / `domain`), `tmdb` (`client` + `importer`), `auth`,
  `signup` (register, onboarding titles, e-mail code), `social` (likes), `match`, `profile`, `shared` (web errors,
  cursors, CORS, clock).
- **Data access**: schema is owned by Flyway (`src/main/resources/db/migration`, `V<n>__descricao.sql`), with
  `ddl-auto: validate`. JPA entities exist in `catalog/domain`, but most reads and all social/match/auth logic use
  `JdbcClient` with hand-written SQL (services under `catalog/application`, `LikeService`, `MatchService`, etc.).
  Never edit an applied migration — add a new one.
- **Catalog source**: the frontend never talks to TMDB. `tmdb/importer` (`CatalogImporter` → `TmdbMapper` →
  `CatalogWriter`) pulls lists configured in `wannawatch.import.lists` into Postgres; it runs on startup if the DB
  is empty, on a daily cron, and via `POST /api/admin/import` (`X-Admin-Token`). Import is idempotent.
  `TmdbClient` throttles (`RequestThrottle`) and retries 429/5xx. Display-only data lives in a JSONB column on
  `title`; one `title` table covers both movies and series (`MediaType`).
- **Auth**: no Spring Security. Opaque bearer token (SHA-256 stored) resolved by `AuthUserResolver`, a
  `HandlerMethodArgumentResolver` — an endpoint is protected simply by declaring an `AuthUser` parameter (throws
  401 otherwise). Passwords are checked with `pgcrypto` in SQL. Seed users (`jotave`, `bia`, `caio`, password
  `wannawatch`) come from migration V4; everyone is friends with everyone.
- **Signup** (`signup`): 3 steps — account (logged in right away) → ≥5 titles + optional favorite (become likes,
  source `ONBOARDING`) → 6-digit e-mail code. Progress is `AccountStatus` (`app_user.titles_picked_at` /
  `email_verified_at`), returned as `account` by login/register and `GET /api/auth/account`. The code e-mail goes
  out after commit via `VerificationMailer` (SMTP; mocked with `@MockitoBean` in `SignupApiIntegrationTest`).
- **Profile photos** (`profile/photo`): `PUT /api/me/profile/photo` (multipart) → `PhotoImages` re-encodes to a
  192px square JPEG → `PhotoStorage`: `R2PhotoStorage` (Cloudflare R2 via AWS SDK v2 S3, public bucket at
  `R2_PUBLIC_URL`) when `R2_ACCESS_KEY_ID` is set, else `LocalPhotoStorage` (`backend/data/photos`, served at `/fotos/**`;
  also used in tests). New key per upload (`avatars/{id}/{uuid}.jpg`), old object deleted after the DB swap.
  `app_user.photo_url` travels in every `UserRef` (`photoUrl`), so the front's `Avatar` just reads it.
- **Errors**: throw `NotFoundException` / `BadRequestException` / `ConflictException` / `ForbiddenException` /
  `UnauthorizedException`; `ApiExceptionHandler` turns them into ProblemDetail.
- **Pagination**: page-based lists use `PageResponse`; feeds (Swipe, Reels, Match deck) use `Cursor` +
  `CursorResponse` (`{ items, nextCursor }`). Swipe order is a stable hash of id + `seed`.
- **Search**: fuzzy in Postgres (`pg_trgm` + `unaccent`), threshold `wannawatch.search.similarity-threshold`.
- **Match**: polling, no WebSocket. Every write locks the room row (`SELECT … FOR UPDATE`) so concurrent votes
  can't miss a match. Room mascot count `MatchService.MASCOTS` must match `frontend/.../room-mascots.ts` and the
  files in `frontend/public/mascot/rooms`.
- **Reels videos (experiment)**: `video` package. `YoutubeClient` (YouTube Data API v3, `YOUTUBE_API_KEY`) finds
  short vertical trailers (`#shorts`, horizontals are discarded using the player's aspect ratio); only metadata is stored in `title_video` (status `PENDING`/`APPROVED`/`REJECTED`) and
  `title_video_search` (one row per searched title, so quota isn't wasted). Videos are never downloaded — they play
  through the YouTube embed (API terms). Admin endpoints take an `AuthUser` and call `auth/AdminGuard.require` (`app_user.is_admin`, 403
  otherwise); `account.admin` drives the `requireAdmin` guard and the "Vídeos" item below Ajustes. Review page:
  `/admin/videos`.
- **Tests**: integration tests use `TestcontainersConfiguration` and `src/test/resources/config/application.yml`
  (fake TMDB token, import disabled). TMDB responses are fixtures in `src/test/resources/tmdb` (`TmdbFixtures`).

## Frontend architecture (`frontend/src/app`)

- Standalone components, signals, zoneless, `OnPush`; no UI library — own components in `shared/ui` on top of
  CSS design tokens in `src/styles/tokens.css`. Mind the `anyComponentStyle` budget (6kB warn / 8kB error).
- `core/api`: `models.ts` mirrors backend DTOs; `CatalogApi` / `social-api.ts` (and the dev-only `VideoAdminApi`) are the only places that call HTTP.
  API base URL is in `src/environments/`.
- `core/auth`: `AuthStore` (token + `account` status in localStorage, `signupStep` / `ready`), interceptor adds
  the bearer token and handles 401, guards `requireLogin` / `requireSignupDone` / `guestOnly` / `signupPending`.
  Routes are split in `app.routes.ts`: `open` (catalog: Home, details, search, Séries, Filmes, Sobre — guests see
  an "Entrar" link) and `members` (Swipe, Reels, Match, profile… — need login). A logged-in user with an
  unfinished signup is sent to `/cadastro` from any route. Guest-facing UI must not call per-user endpoints.
- `core/social/LikesStore`: per-title likes (mine + friends'), batch-fetched, optimistic updates.
- **Layout is driven by route `data`** (`layout/route-meta.ts`): `shell` = `feed` (TikTok-style side nav on
  desktop ≥1024px), `cinema` (page renders its own top pill menu), or `bare` (no nav); `theme` = `light` / `dark`,
  applied by `App` to `<html data-theme>`. Mobile always gets the bottom tab bar (except `bare`). New routes go in
  `app.routes.ts` with lazy `loadComponent` and this metadata.
- `features/<tela>`: one folder per screen. Test data builders live in `src/testing`.

## Conventions worth keeping

- Accessibility is a requirement (verified with axe, WCAG 2.1 AA): real buttons/links, accessible names on
  icon-only buttons, visible focus, ≥4.5:1 contrast (flat 60% dark overlay over images, no gradients), ≥44px touch
  targets, keyboard alternatives to dragging, respect `prefers-reduced-motion`.
- TMDB/JustWatch attribution must stay on `/sobre` (and JustWatch in the details "Onde assistir" block).
