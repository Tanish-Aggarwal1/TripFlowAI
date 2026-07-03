## Database Setup

### Prerequisites
- PostgreSQL 16 installed and running
- Database `tripflow` created locally

### Environment Configuration
Copy `.env.example` to `.env` in `backend/` and fill in:
DB_PASSWORD=your_local_password
JWT_SECRET=your_secret_here
JWT_EXPIRY_MS=3600000

Never commit `.env` — it's gitignored.

### Running Migrations
Migrations are managed by Flyway and run automatically on application startup.
Location: `src/main/resources/db/migration/`

Naming convention: `V{n}__description_in_snake_case.sql` (e.g. `V1__create_users.sql`)

Rule: schema changes are made only through new migration files. Never edit an already-applied migration — write a new one.

### Running the App Locally
```bash
cd backend
mvn spring-boot:run
```
App starts on `http://localhost:8080`. Flyway runs migrations on boot; check console output for confirmation.

### Package Structure
com.tripflow
├── controller/   # REST endpoints
├── service/      # business logic
├── repository/   # Spring Data JPA repositories
├── model/        # JPA entities
├── dto/          # request/response objects
├── config/       # Security, CORS, etc.
└── exception/    # custom exceptions + global handler