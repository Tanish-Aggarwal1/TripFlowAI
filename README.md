# TripFlowAI
AI-powered multi-stop trip planning PWA. Built with Ionic + Angular, Spring Boot, PostgreSQL, Mapbox, and the Gemini API.

## Project Structure
TripFlowAI/

├── backend/          # Spring Boot (Java 17)

├── frontend/         # Ionic + Angular PWA (TypeScript)

├── docs/             # Project documentation and diagrams

├── .github/workflows # GitHub Actions CI/CD

└── README.md

## Tech Stack

- **Frontend:** Ionic + Angular (PWA)
- **Backend:** Spring Boot (Java 17)
- **Database:** PostgreSQL
- **Maps:** Mapbox GL JS
- **Route Optimization:** OpenRouteService
- **AI:** Google Gemini API
- **Auth:** Spring Security + JWT + BCrypt
- **Image Storage:** Cloudinary
- **CI/CD:** GitHub Actions

## Team

| Name | Role |
|------|------|
| Tanish Aggarwal | Lead Developer |
| Neel Solanki | Frontend / UI-UX |
| Pratham Doshi | Database & API Integration |
| Joann Monteiro | QA & Version Control |

## Branch Strategy

- `main` — protected, always deployable
- `feature/<Jira-Key><name>` — new features
- `fix/<Jira-Key><name>` — bug fixes
- All merges require a PR with at least 1 reviewer

## Deployment

**Health check:** `GET /actuator/health` — no authentication required, returns
`{"status":"UP"}` when the app and its dependencies are healthy. Configure this as
the health check path in Render (or any platform requiring a deploy-verification probe).

Only the `health` endpoint is exposed (`management.endpoints.web.exposure.include=health`
in `application.properties`) — all other actuator endpoints (`env`, `beans`, `heapdump`,
etc.) are intentionally never exposed.