# wannawatch?

Recomendação de filmes e séries — sozinho ou com a galera. Quatro jeitos de descobrir o que assistir:

- **Início**: vitrine no estilo Netflix, com destaque, Top 10, "Seus amigos curtiram" e fileiras por tema.
- **Swipe**: cartas no estilo Tinder — passa, talvez, quero ver ou já assisti.
- **Reels**: feed vertical de sinopses no estilo TikTok.
- **Match**: swipe em conjunto numa sala; acaba no primeiro título que todo mundo quiser ver.

Além disso: página de detalhes (elenco, equipe, onde assistir no Brasil, ficha técnica) e busca por
título, elenco e gênero.

Monorepo com:

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
| `POST /api/auth/login` · `POST /api/auth/logout` · `GET /api/auth/me` | Login (devolve o token) |
| `PUT`/`DELETE /api/likes/{titleId}` | "Quero ver" |
| `GET /api/likes?titleIds=1,2,3` | Para cada título: se eu curti e quais amigos curtiram |
| `GET /api/likes/friends?size=` | Títulos que os amigos curtiram (fileira da Home) |
| `POST /api/match/rooms` · `GET /api/match/rooms` | Cria sala (`{ mediaType?, genre? }`) · salas abertas |
| `GET /api/match/rooms/{code}` · `POST …/join` | Estado da sala · entrar (só enquanto espera) |
| `POST …/start` · `DELETE /api/match/rooms/{code}` | Dono começa (2+ pessoas) · dono fecha (enquanto espera) |
| `GET …/deck?cursor=&size=` | Deck da sala, sem o que eu já votei |
| `PUT …/votes/{titleId}` (`{ liked }`) · `DELETE …/votes/{titleId}` | Votar · desfazer voto |
| `DELETE …/participants/me` | Sair da sala |

- `type` é `MOVIE` ou `SERIES`; `genre` é o slug (ex.: `ficcao-cientifica`).
- Listas com cursor devolvem `{ items, nextCursor }`; `nextCursor` nulo = acabou.
- Erros seguem o formato ProblemDetail (`{ status, title, detail }`).
- Catálogo é aberto; curtidas, Match e `/auth/me` exigem `Authorization: Bearer <token>` (401 sem ele).
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
| `core/auth` | Sessão (`AuthStore`, token no localStorage), interceptor que põe o token e trata 401, guards de login |
| `core/social` | `LikesStore`: curtidas por título (minhas e dos amigos), buscadas em lote, com atualização otimista |
| `shared/ui` | Componentes base: logo, botões, botão redondo, seletor segmentado, pôster com placeholder, ícones |
| `layout` | Barra de abas (celular), barra lateral estilo TikTok (desktop) e menu do topo das telas escuras |
| `features` | Uma pasta por tela |
| `src/styles` | Design tokens (`tokens.css`) e estilos base |
| `src/testing` | Dados de exemplo para os testes |

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
certified by TMDB."* numa seção Sobre/Créditos do app: está em `/sobre`, com link na barra
lateral. Os dados de "onde assistir" vêm da JustWatch, creditada em `/sobre` e no próprio bloco
"Onde assistir" da página de detalhes.

## Telas

| Rota | Tela | Tema |
|---|---|---|
| `/` | Início | escuro |
| `/filme/:id`, `/serie/:id` | Detalhes | escuro |
| `/busca?q=` | Busca | escuro |
| `/swipe` | Swipe | claro |
| `/reels` | Reels | escuro |
| `/match`, `/match/:code` | Match: salas abertas e criar sala · sala de espera e votação | claro |
| `/entrar` | Login (sem navegação) | claro |
| `/perfil` | Seu perfil: números, curtidas por semana e últimos curtidos | claro |
| `/ajustes` | Nome, cor, Bu, favoritos, conta e sair | claro |
| `/sobre` | Sobre e créditos | claro |
| `/series`, `/filmes`, `/minha-lista` | Provisórias ("em breve") | claro |
| `/dev/componentes` | Vitrine de componentes (só em dev) | claro e escuro |

No celular a navegação fica numa barra de abas embaixo. No desktop (a partir de 1024px), Swipe,
Reels e as telas secundárias ganham uma barra lateral no estilo TikTok web; Início, Detalhes e
Busca usam o menu em pílula no topo.

Atalhos de teclado:

- **Swipe**: `→` quero ver, `←` passa, `↑` talvez, `↓` já assisti, `Z` volta o último.
- **Reels**: `↓`/`J` próximo, `↑`/`K` anterior, `L` quero ver, `S` salvar.
- **Match**: `→` quero ver, `←` passa, `Z` volta o último.

## Login, amigos e Match

Não há cadastro: a migration `V4` cria três contas, todas com a senha `wannawatch`:

| Usuário | Nome |
|---|---|
| `jotave` | Jotave |
| `bia` | Bia |
| `caio` | Caio |

- O app inteiro exige login; sem sessão, qualquer tela leva a `/entrar` (e volta para onde ia).
- Token opaco de 256 bits, válido por 30 dias; no banco fica só o SHA-256 dele. A senha é bcrypt
  (`pgcrypto`), conferida pelo próprio Postgres.
- Ainda não existe lista de amigos: **todo usuário é amigo de todo mundo**. "Fulano curtiu" (Home,
  Swipe, Reels, Detalhes) mostra o "quero ver" real dos outros usuários.
- "Quero ver" do Swipe e do Reels fica salvo. Talvez/passa/já vi do Swipe e o "salvar" do Reels
  ainda ficam só na memória da tela.

**Match**: a tela lista as salas abertas (esperando gente, de todo mundo, e as que você está votando).
Quem cria escolhe o deck (filmes, séries ou os dois, e opcionalmente um gênero); a sala aparece com o
nome do dono e um mascote sorteado (`public/mascot/rooms`, a lista fica em `room-mascots.ts` e o
tamanho dela em `MatchService.MASCOTS`). Quem cria e quem entra ficam na sala de espera, que avisa
quem entrou e saiu; o dono começa com 2 ou mais pessoas e, a partir daí, ninguém novo entra. Todo
mundo vota no mesmo deck, na mesma ordem; a sala acaba no primeiro título com "quero ver" de
**todos** os participantes. Quem sai tem os votos apagados, e isso pode destravar o match para quem
ficou. O front consulta a sala a cada 2,5 s e a lista de salas a cada 4 s.

## Acessibilidade

- Elementos reais de botão e link, com nome acessível em todo botão só de ícone.
- Foco sempre visível e link "Pular para o conteúdo".
- Contraste de texto ≥ 4,5:1. Sobre imagens, uma camada chapada escura (60%) garante isso para
  qualquer imagem, sem degradê.
- Alvos de toque de pelo menos 44px.
- Swipe e Reels funcionam por botões e teclado, sem depender de arrastar.
- Animações respeitam "reduzir movimento" do sistema.
- Verificado com axe-core (WCAG 2.1 A/AA) em todas as telas, no desktop e no celular, sem violações.

## Decisões principais

- O front nunca fala com a TMDB: o back importa o catálogo para o Postgres e serve a API própria.
  O token fica só no `.env` do back.
- Uma tabela `title` para filme e série; dados só de exibição (produtoras, países, onde assistir)
  numa coluna JSONB.
- Busca fuzzy no próprio Postgres (`pg_trgm` + `unaccent`), sem serviço extra.
- Swipe em ordem aleatória mas estável (hash do id com uma seed); Reels e Swipe paginam por cursor.
- Match sem WebSocket: consulta periódica basta para salas pequenas. Toda escrita trava a linha da
  sala (`SELECT … FOR UPDATE`), então dois votos simultâneos não deixam o match passar batido.
- Front sem biblioteca de UI: componentes próprios sobre design tokens em CSS.
