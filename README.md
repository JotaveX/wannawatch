# wannawatch?

Recomendação de filmes e séries — sozinho ou com a galera. Quatro jeitos de descobrir o que assistir:

- **Início**: vitrine no estilo Netflix, com destaque, Top 10, "Seus amigos curtiram" e fileiras por tema.
- **Swipe**: cartas no estilo Tinder — passa, talvez, quero ver ou já assisti.
- **Reels**: feed vertical no estilo TikTok: vídeo curto quando o título tem um aprovado, senão a sinopse.
- **Match**: swipe em conjunto numa sala; acaba no primeiro título que todo mundo quiser ver.

Tudo isso é recomendado pelo seu gosto (e, no Match, pelo gosto do grupo), com o motivo escrito no
card: "Porque você curtiu Dark", "De Christopher Nolan", "Bom pra você e pra Bia".

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
- `YOUTUBE_API_KEY`: chave da YouTube Data API v3
  (<https://console.cloud.google.com/apis/credentials>), usada no teste dos vídeos do Reels.
  Vazia = busca desligada.
- `RESEND_API_KEY`: chave do [Resend](https://resend.com/api-keys). Preenchida, o código de
  confirmação do cadastro sai pela API do Resend, e `MAIL_FROM` precisa ser de um domínio
  verificado lá (com `onboarding@resend.dev` só chega no e-mail do dono da conta).
- `MAIL_*`: SMTP usado quando `RESEND_API_KEY` está vazia. O padrão é o Mailpit do docker-compose
  (nada sai de verdade; a caixa de entrada fica em <http://localhost:8025>).
- `R2_*`: bucket do [Cloudflare R2](https://developers.cloudflare.com/r2/) para as fotos de perfil
  (`R2_ACCOUNT_ID`, `R2_ACCESS_KEY_ID` e `R2_SECRET_ACCESS_KEY` de um token com "Object Read & Write",
  `R2_BUCKET` e `R2_PUBLIC_URL`, o endereço público do bucket, `https://pub-….r2.dev` ou domínio
  próprio). Vazias = as fotos ficam em `backend/data/photos` e o próprio back serve em
  <http://localhost:8081/fotos>.

Portas padrão (mude no `.env` se precisar):

| Serviço  | Porta |
|----------|-------|
| Postgres | 5434  |
| Mailpit (SMTP / caixa de entrada) | 1025 / 8025 |
| Back-end | 8081  |
| Front-end| 4200  |

## 2. Banco

```bash
docker compose up -d --wait
```

Sobe o Postgres e o Mailpit (o SMTP de mentira do cadastro). Para zerar o banco: `docker compose down -v`.

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
| `GET /api/home` | Destaque e fileiras da Home (com login: personalizadas, com "Pra você") |
| `GET /api/swipe/deck?seed=&cursor=&size=` | Deck do Swipe: com login, personalizado (varia com a `seed`); sem, aleatório fixo por `seed` |
| `GET /api/reels?cursor=&size=` | Feed do Reels: com login, personalizado; sem, por popularidade. Cada item traz `video` (`{ platform, key }`) quando o título tem vídeo aprovado, senão `null` |
| `GET /api/search?q=&page=&size=` | Busca fuzzy por título, pessoa e gênero |
| `POST /api/auth/login` · `POST /api/auth/logout` · `GET /api/auth/me` | Login por usuário ou e-mail (devolve o token e a etapa do cadastro em `account`) |
| `GET /api/auth/account` | Quem está logado e em que etapa do cadastro está (`{ user, account }`) |
| `POST /api/auth/register` | Cadastro, etapa 1: cria a conta já logada e manda o código por e-mail (201) |
| `PUT /api/me/onboarding/titles` | Cadastro, etapa 2: `{ titleIds, favoriteTitleId? }`, de 5 a 50 títulos (viram "quero ver") |
| `POST /api/auth/email/verify` · `POST /api/auth/email/resend` | Cadastro, etapa 3: confirma o código (`{ code }`) · manda outro (um por minuto) |
| `GET`/`PUT /api/me/profile` · `GET /api/me/profile/activity` | Meu perfil (nome, cor, Bu, favoritos) · curtidas por semana e últimos curtidos |
| `PUT`/`DELETE /api/me/profile/photo` | Envia (multipart, campo `file`, até 5 MB; vira JPEG 192×192) · remove a foto de perfil. Os usuários trazem `photoUrl` |
| `PUT`/`DELETE /api/likes/{titleId}?source=` | "Quero ver" |
| `PUT`/`DELETE /api/titles/{id}/signals/{kind}?source=` | Passa, talvez, já vi ou salvar (`kind`: `PASS`, `MAYBE`, `SEEN`, `SAVED`) |
| `GET /api/me/signals?ids=1,2,3` | Para cada título: quais sinais eu marquei |
| `GET /api/likes?titleIds=1,2,3` | Para cada título: se eu curti e quais amigos curtiram |
| `GET /api/likes/friends?size=` | Títulos que os amigos curtiram (fileira da Home) |
| `POST /api/match/rooms` · `GET /api/match/rooms` | Cria sala (`{ mediaType?, genre? }`) · salas abertas |
| `GET /api/match/rooms/{code}` · `POST …/join` | Estado da sala · entrar (só enquanto espera) |
| `POST …/start` · `DELETE /api/match/rooms/{code}` | Dono começa (2+ pessoas) · dono fecha (enquanto espera) |
| `GET …/deck?cursor=&size=` | Deck da sala na ordem do grupo, sem o que eu já votei, com o motivo de cada carta |
| `PUT …/votes/{titleId}` (`{ liked }`) · `DELETE …/votes/{titleId}` | Votar · desfazer voto |
| `DELETE …/participants/me` | Sair da sala |
| `POST /api/admin/videos/discover` (`{ limit?, titleIds?, skipApproved?, includeSearched?, source?, type?, genre?, yearFrom?, yearTo? }`) | Admin: busca Shorts no YouTube para `limit` títulos (1 a 20, padrão 10) e guarda só os verticais. Por padrão pega os mais populares ainda não buscados e pula quem já tem vídeo aprovado (`skipApproved: false` desliga); `includeSearched` busca de novo os já buscados; `source` = `POPULAR` / `TRENDING` (em alta) / `TOP_RATED` (≥100 votos) / `RECENT` (lançamentos); `type`, `genre` (slug) e `yearFrom`/`yearTo` filtram. `titleIds` ignora os filtros |
| `GET /api/admin/videos` · `PATCH /api/admin/videos/{id}` (`{ status }`) | Admin: títulos buscados com os vídeos achados (cada vídeo traz `reviewedAt`, quando foi aprovado/reprovado) · aprovar/reprovar (`PENDING`, `APPROVED`, `REJECTED`; voltar a `PENDING` limpa a data) |

- `type` é `MOVIE` ou `SERIES`; `genre` é o slug (ex.: `ficcao-cientifica`).
- `source` (opcional) diz de que tela veio a interação: `SWIPE`, `REELS`, `DETAILS`, `HOME` ou `MATCH`
  (e `ONBOARDING`, gravado pelo próprio back para os títulos escolhidos no cadastro).
- Listas com cursor devolvem `{ items, nextCursor }`; `nextCursor` nulo = acabou.
- Todo card de título (`TitleCard`) tem `reason`: `{ kind, text }` com o motivo da recomendação
  (ex.: `"Porque você curtiu Dark"`) nas listas personalizadas, e `null` nas outras.
- Erros seguem o formato ProblemDetail (`{ status, title, detail }`).
- Catálogo é aberto; curtidas, sinais, Match e `/auth/me` exigem `Authorization: Bearer <token>` (401 sem ele).
  Home, Swipe e Reels aceitam o token opcionalmente: sem ele respondem como antes; com um token
  inválido dão 401.
- Os endpoints `/api/admin/videos` pedem login de administrador (`app_user.is_admin`): 401 sem login, 403 para quem não é admin.
- CORS: origens liberadas em `CORS_ALLOWED_ORIGINS` (padrão `http://localhost:4200`).

## 4. Front-end

```bash
cd frontend
npm install
npm start          # http://localhost:4200
npm test -- --watch=false
```

- A URL da API fica em `src/environments/` (dev: `http://localhost:8081/api`, produção:
  `https://apiwannawatch.jotave.dev/api`).
- Vitrine dos componentes (só em dev): <http://localhost:4200/dev/componentes>.

Organização (`src/app`):

| Pasta | O que tem |
|---|---|
| `core/api` | Contratos da API (`models.ts`) e o cliente `CatalogApi`: o único lugar que fala com o back |
| `core/auth` | Sessão (`AuthStore`, token no localStorage), interceptor que põe o token e trata 401, guards de login |
| `core/social` | `LikesStore` (curtidas por título, minhas e dos amigos) e `SignalsStore` (passa/talvez/já vi/salvar), buscados em lote, com atualização otimista |
| `shared/ui` | Componentes base: logo, botões, botão redondo, seletor segmentado, pôster com placeholder, ícones |
| `layout` | Barra de abas (celular), barra lateral estilo TikTok (desktop) e menu do topo das telas escuras |
| `features` | Uma pasta por tela |
| `src/styles` | Design tokens (`tokens.css`) e estilos base |
| `src/testing` | Dados de exemplo para os testes |

## Deploy

| O quê | Onde |
|---|---|
| Front | <https://wannawatch.jotave.dev>: Worker só com arquivos estáticos na Cloudflare (`frontend/wrangler.jsonc`) |
| API | <https://apiwannawatch.jotave.dev>: VPS (Hostinger), Docker, exposta pelo Cloudflare Tunnel `wannawatch-vps` |
| Banco | Postgres 18 no Docker da mesma VPS (volume `wannawatch_pgdata`), sem porta aberta |
| Fotos | Bucket `wannawatch` no R2, público em <https://r2wannawatch.jotave.dev> |

**Automático:** `.github/workflows/ci.yml` roda os testes do back e do front em todo push e PR.
Num push na `main` com os testes verdes, publica o front (`wrangler deploy`), monta a imagem
`ghcr.io/jotavex/wannawatch-api` e atualiza a VPS. Segredos do repositório: `CLOUDFLARE_API_TOKEN`
(modelo "Edit Cloudflare Workers"), `CLOUDFLARE_ACCOUNT_ID`, `DEPLOY_SSH_KEY`, `DEPLOY_KNOWN_HOSTS` e
`DEPLOY_TARGET` (`root@<ip>`).

**Na VPS** (`/opt/wannawatch`): `compose.yml` e `deploy.sh` são cópias de `deploy/` (mudou um deles,
copie de novo com `scp`); os segredos ficam em `.env` (chmod 600, modelo em `deploy/.env.example`).
A chave SSH do CI só pode rodar o `deploy.sh` (`command="/opt/wannawatch/deploy.sh",restrict` no
`authorized_keys`); o token temporário do job chega pela entrada padrão para o `docker compose pull`.
As migrações rodam na subida da API e o catálogo é importado sozinho na primeira vez.

```bash
ssh root@<ip>
cd /opt/wannawatch
docker compose ps
docker compose logs -f api
./deploy.sh < /dev/null                                          # sobe de novo sem baixar imagem
docker compose exec db psql -U wannawatch                        # SQL no banco de produção
```

Os usuários de exemplo da V4 (`jotave`, `bia`, `caio`) existem em produção com senha aleatória, então
ninguém entra com eles. O administrador também não vem da migração: depois de criar a conta,
`UPDATE app_user SET is_admin = TRUE WHERE username = '<você>';`.

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
- Também vêm até 20 keywords por título e a lista de recomendações da TMDB (guardada pelo id da
  TMDB, mesmo quando o recomendado não está no catálogo). As duas alimentam a recomendação.
- A importação é idempotente: rodar de novo atualiza os títulos, não duplica.
- **Não trunque `title` para reimportar.** Curtidas (`title_like`) e sinais (`user_title_signal`)
  caem em cascata junto. Para atualizar o catálogo, basta rodar a importação de novo. O log de
  interações (`interaction_event`) sobrevive, mas as curtidas e os sinais não voltam sozinhos.
- Limite de 20 requisições/s à TMDB, com novas tentativas em 429/5xx.

## Vídeos do Reels (teste)

Em teste: achar vídeos curtos de cada título para o Reels virar vídeo de verdade.

- A busca usa a YouTube Data API v3 (`search.list` com `videoDuration=short`, `videoEmbeddable=true`,
  região BR, 15 resultados), com a consulta `"<título> [série] trailer #shorts"`, e lê os detalhes
  com `videos.list`.
- **Só ficam os verticais** (o Reels é 9:16). A busca não filtra por formato, então a proporção vem
  do player (`player.embedWidth/embedHeight`, sem tarjas) e os horizontais são descartados.
- Só os **metadados** vão para o banco (`title_video`): id do vídeo, canal, duração, proporção do
  player, se é embutível e se está bloqueado no Brasil. O vídeo nunca é baixado: os termos da API
  proíbem guardar o conteúdo, então ele toca pelo player oficial (embed).
- Cada título buscado fica em `title_video_search`, mesmo sem resultado, e não é buscado de novo
  (a não ser pedindo por `titleIds`). A cota padrão é de 100 buscas por dia.
- Tudo entra como `PENDING`; a revisão é em `/admin/videos`, só para administradores
  (`app_user.is_admin`; hoje só o `jvpiloni`, marcado na migration V12).
- **No Reels**: entra o vídeo aprovado, embutível e não bloqueado no Brasil (com mais de um, o mais
  bem colocado na busca). Só o reel na tela monta o player (`youtube-nocookie.com`, sem os controles
  do YouTube, em loop); ele começa sem som e toca sozinho, a não ser com `prefers-reduced-motion`,
  que espera o play. O vídeo ocupa o reel inteiro, sem o pôster atrás, e nada da interface do
  YouTube aparece: o iframe só fica visível depois que o vídeo começa a tocar; é maior que o reel,
  então o título e as marcas de cima e de baixo ficam cortados para fora; toda vez que volta a tocar
  (começo, retomar, loop) o YouTube desenha por ~4s um ícone de pausa no meio, e o nosso botão do
  meio (opaco, maior) fica por cima dele esse tempo; o loop é nosso (volta ao início um pouco antes
  do fim, porque o loop do YouTube mostra "Mais vídeos" e setas); e só mandamos `playVideo` quando a
  pessoa retoma (o autoplay toca sozinho). Pausado, o quadro parado (limpo) fica na tela com o nosso
  botão de play. Som, volume (controle deslizante de 0 a 100) e
  pausa são nossos, valem para o feed todo e vão para o player por `postMessage`. Por cima do vídeo
  fica só o título e a linha de informações, sem card; os amigos que curtiram flutuam como esferas
  sem texto, e o nome aparece ao passar o mouse, focar ou tocar. Os botões de ação ficam só no ícone
  (o "quero ver" mostra a contagem quando alguém quer ver).

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
| `/cadastro` | Criar conta em 3 etapas: conta, títulos de que gosta, confirmar o e-mail (sem navegação) | claro |
| `/perfil` | Seu perfil: números, curtidas por semana e últimos curtidos | claro |
| `/ajustes` | Nome, cor, Bu ou foto, favoritos, conta e sair | claro |
| `/admin/videos` | Só administradores: teste dos vídeos do Reels, duas abas: Verificação (fila dos pendentes por título com Aprovar/Reprovar só em ícone, busca no YouTube com "Pular títulos que já têm vídeo aprovado" e busca avançada — de onde, tipo, gênero, ano, buscar de novo —, "Reprovar todos") e Histórico (aprovados/reprovados, com filtro, busca e "Desfazer") (link "Vídeos" abaixo de Ajustes na barra lateral e em Ajustes › Conta) | claro |
| `/sobre` | Sobre e créditos | claro |
| `/series`, `/filmes` | Por enquanto abrem a busca | escuro |
| `/dev/componentes` | Vitrine de componentes (só em dev) | claro e escuro |

No celular a navegação fica numa barra de abas embaixo. No desktop (a partir de 1024px), Swipe,
Reels e as telas secundárias ganham uma barra lateral no estilo TikTok web; Início, Detalhes e
Busca usam o menu em pílula no topo.

Atalhos de teclado:

- **Swipe**: `→` quero ver, `←` passa, `↑` talvez, `↓` já assisti, `Z` volta o último.
- **Reels**: `↓`/`J` próximo, `↑`/`K` anterior, `L` quero ver, `S` salvar, `M` som e `P` pausa (nos reels com vídeo).
- **Match**: `→` quero ver, `←` passa, `Z` volta o último.

## Cadastro, login, amigos e Match

O cadastro (`/cadastro`) tem três etapas, nesta ordem:

1. **Conta**: nome, usuário (3 a 20 caracteres: minúsculas, números, `_` ou ponto), e-mail e senha
   (8+ caracteres). A conta já sai logada e um código de 6 dígitos vai para o e-mail.
2. **Do que você gosta**: pelo menos 5 títulos (até 50), que viram "quero ver" e já personalizam a
   recomendação; entre eles, opcionalmente, o **favorito de todos os tempos** (a estrela).
3. **Confirmar o e-mail**: o código vale 30 minutos e aceita 5 tentativas erradas; dá para pedir
   outro a cada minuto (o novo substitui o anterior). No banco fica só o SHA-256 do código.

A etapa em que a conta está vem do back (`account` no login e em `GET /api/auth/account`): quem
entra com o cadastro pela metade volta para a etapa que falta, em qualquer tela. O login aceita
usuário ou e-mail.

Sem conta dá para **ver o catálogo**: Início, detalhes, busca, Séries, Filmes e Sobre. No lugar do
avatar aparece "Entrar". Swipe, Reels, Match, perfil e ajustes pedem login (levam a
`/entrar` e voltam depois); "Já vi" na página de detalhes também. As regras de quem vê o quê ficam
no front: a API do Swipe e do Reels continua respondendo sem token (é só o catálogo, sem
personalização).

A migration `V4` cria três contas de teste, todas com a senha `wannawatch` (a `V10` marca as três
com o cadastro completo):

| Usuário | Nome |
|---|---|
| `jotave` | Jotave |
| `bia` | Bia |
| `caio` | Caio |

- Token opaco de 256 bits, válido por 30 dias; no banco fica só o SHA-256 dele. A senha é bcrypt
  (`pgcrypto`), conferida pelo próprio Postgres.
- Ainda não existe lista de amigos: **todo usuário é amigo de todo mundo**. "Fulano curtiu" (Home,
  Swipe, Reels, Detalhes) mostra o "quero ver" real dos outros usuários.
- Tudo fica salvo: "quero ver", talvez/passa/já vi do Swipe, o "salvar" do Reels e o "Já vi" da
  página de detalhes. "Voltar" no Swipe desfaz no back também.
- Cada curtida, sinal e voto do Match também entra num log que só cresce (`interaction_event`). Ele
  guarda o id da TMDB em vez de apontar para `title`, então sobrevive a uma reimportação do catálogo.

**Match**: a tela lista as salas abertas (esperando gente, de todo mundo, e as que você está votando).
Quem cria escolhe o deck (filmes, séries ou os dois, e opcionalmente um gênero); a sala aparece com o
nome do dono e um mascote sorteado (`public/mascot/rooms`, a lista fica em `room-mascots.ts` e o
tamanho dela em `MatchService.MASCOTS`). Quem cria e quem entra ficam na sala de espera, que avisa
quem entrou e saiu; o dono começa com 2 ou mais pessoas e, a partir daí, ninguém novo entra. Ao
começar, o deck é ordenado pelo gosto de quem está na sala (veja [Recomendação](#recomendação)) e
fica guardado (`match_room_deck`): todo mundo vota no mesmo deck, na mesma ordem, e ela não muda
mais, nem se alguém sair. A sala acaba no primeiro título com "quero ver" de **todos** os
participantes; se dois títulos virarem unanimidade ao mesmo tempo (quando alguém sai), vence o que
vem antes no deck. Quem sai tem os votos da sala apagados, e isso pode destravar o match para quem
ficou (o voto continua no log e contando no gosto da pessoa). Salas que começaram antes do deck
guardado seguem na ordem aleatória fixa pela seed da sala. O front consulta a sala a cada 2,5 s e a
lista de salas a cada 4 s.

## Recomendação

Baseada em conteúdo: com poucas centenas de títulos e um punhado de usuários, não há dados para
filtragem colaborativa. Tudo roda em Java, em memória, sem biblioteca de ML (pontuar o catálogo
inteiro para alguém leva microssegundos). Os pesos e limiares ficam em
`wannawatch.recommendation.*` no `application.yml`.

1. **Vetor de cada título** (`TitleFeatureIndex`, remontado ao subir e ao fim de cada importação):
   gêneros (o principal pesa mais), keywords, direção/criação, roteiro, elenco (protagonistas pesam
   mais), trilha e fotografia, idioma original, década e tipo. Keywords e elenco são ponderados por
   IDF, e pessoas ou keywords que aparecem num título só são descartadas. Cada família é
   normalizada, multiplicada pelo peso dela e somada. Parecido = cosseno, com um bônus quando a
   TMDB recomenda um a partir do outro (é o que alimenta os "parecidos" da página de detalhes).
2. **Perfil de gosto**: soma dos vetores do que a pessoa curtiu, salvou, votou "quero ver" no
   Match, marcou "talvez" e do título favorito (mais o gênero favorito), cada um com seu peso e
   perdendo metade do peso a cada 90 dias. Passa e "não" no Match montam um perfil negativo. "Já vi"
   não entra no gosto, só tira o título dos feeds.
3. **Score individual**: `0,70·afinidade + 0,15·popularidade + 0,15·qualidade`, em que afinidade =
   cosseno com o perfil positivo − metade do cosseno com o negativo, popularidade = percentil no
   catálogo e qualidade = média bayesiana da nota. Com menos de 5 sinais positivos, o score puxa
   para popularidade, qualidade e gênero favorito (cold start).
4. **Motivo**: "Porque você curtiu X" (se algo curtido ou salvo é parecido o bastante), senão a
   pessoa ou o gênero que mais pesou ("De Christopher Nolan", "Com Pedro Pascal", "Você curte
   comédia"), senão "Em alta" ou "Bem avaliado".
5. **Grupo (Match)**: para cada título, o percentil do score de cada participante (assim ninguém
   pesa mais por dar nota alta para tudo); `média − 0,5·desvio`, mais um bônus por quem quer ver e
   menos por quem já viu. Se o percentil de alguém fica abaixo de 0,25, o título vai para o fim; se
   todo mundo já viu, sai do deck.

Onde entra:

- **Início**: o destaque é o de maior score entre os 10 do trending; a fileira "Pra você" vem logo
  depois do Top 10 (sem o que você já curtiu, salvou, passou ou viu, e com no máximo 3 do mesmo
  gênero entre os 10 primeiros); "Porque você curtiu X" usa uma das suas 3 curtidas mais recentes
  (muda a cada dia; sem curtidas, não aparece); uma das fileiras de gênero é o seu gênero mais
  forte, e as fileiras de gênero e de séries vêm ordenadas pelo score. Top 10, "Seus amigos
  curtiram" e "Filmes aclamados" não mudam.
- **Swipe**: não mostra o que você já avaliou ("talvez" volta depois de 7 dias). A ordem é o score
  com uma variação por visita (a seed), e a cada 5 cartas uma é de exploração: bem avaliada, de um
  gênero que você quase não curte.
- **Reels**: tira o que você já viu ou passou, deixa os curtidos no fim e ordena por
  `0,6·score + 0,4·popularidade + 0,15` (o `+ 0,15` só para quem tem vídeo aprovado). Sem login,
  a ordem é `0,4·popularidade + 0,15 se tiver vídeo`.
- Swipe e Reels guardam a ordem calculada na primeira página por 30 minutos. A paginação segue
  essa ordem e não pula nem repete nada enquanto o score muda com os seus swipes.
- Sem login, a API devolve tudo como antes (Swipe aleatório fixo pela seed, Reels por
  popularidade com o reforço do vídeo), sem motivo.

## Acessibilidade

- Elementos reais de botão e link, com nome acessível em todo botão só de ícone.
- O motivo da recomendação faz parte do nome acessível do card (Início) ou o descreve
  (`aria-describedby` no Swipe e no Reels), sempre como texto.
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
- Recomendação por conteúdo, calculada em memória no back (sem ML, embeddings nem serviço extra);
  Reels, Swipe e o deck do Match paginam por cursor sobre uma ordem guardada.
- Match sem WebSocket: consulta periódica basta para salas pequenas. Toda escrita trava a linha da
  sala (`SELECT … FOR UPDATE`), então dois votos simultâneos não deixam o match passar batido.
- Front sem biblioteca de UI: componentes próprios sobre design tokens em CSS.
