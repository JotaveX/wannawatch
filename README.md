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

O `.env` fica fora do git. Ele é lido pelo docker-compose e pelo back-end. Preencha:

- `TMDB_API_TOKEN`: o **API Read Access Token** da sua conta em
  <https://www.themoviedb.org/settings/api> (uso não comercial é gratuito).
- `ADMIN_TOKEN`: qualquer valor secreto; protege o endpoint que dispara a importação.
  Gere um com `openssl rand -hex 16`. Vazio = endpoint desligado.

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

### API

Documentação interativa (Swagger): <http://localhost:8081/swagger-ui.html>

| Endpoint | O que faz |
|---|---|
| `GET /api/genres?type=` | Gêneros, com quantos títulos cada um tem |
| `GET /api/titles?type=&genre=&sort=&page=&size=` | Lista paginada (`sort`: `POPULARITY`, `RATING`, `RELEASE`) |
| `GET /api/titles/{id}` | Detalhe completo: elenco, equipe, trailer, onde assistir, parecidos |
| `GET /api/home` | Destaque e fileiras da Home |
| `GET /api/swipe/deck?seed=&cursor=&size=` | Deck do Swipe: aleatório, mas fixo por `seed` |
| `GET /api/reels?cursor=&size=` | Feed do Reels por popularidade |
| `GET /api/search?q=&page=&size=` | Busca fuzzy por título, pessoa e gênero |

- `type` é `MOVIE` ou `SERIES`; `genre` é o slug (ex.: `ficcao-cientifica`).
- Listas com cursor devolvem `{ items, nextCursor }`; `nextCursor` nulo = acabou.
- Erros seguem o formato ProblemDetail (`{ status, title, detail }`).
- CORS: origens liberadas em `CORS_ALLOWED_ORIGINS` (padrão `http://localhost:4200`).

## 4. Front-end

```bash
cd frontend
npm install
npm start          # http://localhost:4200
npm test -- --watch=false
```

- A URL da API fica em `src/environments/` (dev: `http://localhost:8081/api`).
- Vitrine dos componentes (só em dev): <http://localhost:4200/dev/componentes>.

Organização (`src/app`):

| Pasta | O que tem |
|---|---|
| `core/api` | Contratos da API (`models.ts`) e o cliente `CatalogApi`: o único lugar que fala com o back |
| `mocks` | `SocialMock`: tudo que é de grupo/usuário (% match, grupo, continue assistindo). Trocar aqui quando existir back-end para isso |
| `shared/ui` | Componentes base: logo, botões, botão redondo, seletor segmentado, pôster com placeholder, ícones |
| `layout` | Barra de abas (celular) e barra lateral estilo TikTok (desktop) |
| `features` | Uma pasta por tela |
| `src/styles` | Design tokens (`tokens.css`) e estilos base |

## Importação da TMDB

O back-end busca filmes e séries na TMDB e grava no nosso banco; o front só fala com o back.
A importação roda de três jeitos:

1. **Automática na primeira subida**: se o banco não tiver nenhum título, ela dispara sozinha
   ao iniciar o back (leva menos de 1 minuto).
2. **Agendada**: todo dia às 4h (`wannawatch.import.cron` no `application.yml`; `"-"` desliga).
3. **Manual**, pelo endpoint de admin:

   ```bash
   source .env
   curl -X POST -H "X-Admin-Token: $ADMIN_TOKEN" localhost:8081/api/admin/import   # dispara (202)
   curl -H "X-Admin-Token: $ADMIN_TOKEN" localhost:8081/api/admin/import            # status e último resultado
   ```

O que é importado (configurável em `wannawatch.import.lists`):

| Lista            | Origem na TMDB       | Páginas (20 por página) |
|------------------|----------------------|-------------------------|
| `trending_day`   | `/trending/all/day`  | 2                       |
| `popular_movie`  | `/movie/popular`     | 5                       |
| `popular_series` | `/tv/popular`        | 5                       |
| `top_movie`      | `/movie/top_rated`   | 5                       |
| `top_series`     | `/tv/top_rated`      | 5                       |

Regras:

- Textos em pt-BR; título e sinopse vazios caem para en-US. Sinopse que continuar vazia fica nula.
- Classificação indicativa e "onde assistir" só do Brasil; se não houver, ficam vazios.
- Trailer: YouTube, oficial, preferindo pt e depois en.
- Conteúdo adulto é ignorado.
- A importação é idempotente: rodar de novo atualiza os títulos, não duplica.
- Limite de 20 requisições/s à TMDB, com novas tentativas em 429/5xx.

### Atribuição

A TMDB exige o logo dela e o aviso *"This product uses the TMDB API but is not endorsed or
certified by TMDB."* numa seção Sobre/Créditos do app. Os dados de "onde assistir" vêm da
JustWatch, que também precisa ser creditada. As duas coisas entram na tela Sobre (fase 4).
