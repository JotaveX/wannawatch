# wannawatch?

Recomendação de filmes e séries — sozinho ou em grupo. Monorepo com:

- `frontend/` — Angular 21 (standalone, signals, zoneless, Vitest)
- `backend/` — Java 21 + Spring Boot 4 (Maven, JPA, Flyway)
- `docker-compose.yml` — PostgreSQL 18 para desenvolvimento

## Pré-requisitos

- Java 21
- Node 20.19+ (testado com Node 24)
- Docker com Compose

Maven e Angular CLI não precisam estar instalados: o back usa o wrapper (`./mvnw`) e o front usa o CLI local (`npx ng`).

## 1. Configuração

```bash
cp .env.example .env
```

O `.env` fica fora do git. Ele é lido pelo docker-compose e pelo back-end.
A chave da TMDB (`TMDB_API_TOKEN`) só será necessária a partir da fase de importação.

Portas padrão (mude no `.env` se precisar):

| Serviço  | Porta |
|----------|-------|
| Postgres | 5434  |
| Back-end | 8081  |
| Front-end| 4200  |

## 2. Banco

```bash
docker compose up -d --wait
```

Para zerar o banco: `docker compose down -v`.

## 3. Back-end

```bash
cd backend
./mvnw spring-boot:run
```

- Health check: <http://localhost:8081/actuator/health>
- As migrations (Flyway) rodam sozinhas na inicialização.

Testes (sobem um Postgres temporário via Testcontainers, precisa do Docker rodando):

```bash
./mvnw test
```

## 4. Front-end

```bash
cd frontend
npm install
npm start          # http://localhost:4200
npm test -- --watch=false
```

## Importação da TMDB

_Em construção (fase 2)._
