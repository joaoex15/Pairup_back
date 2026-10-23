# PairUp

API REST (Spring Boot 3.2.3 / Java 21) para um aplicativo de paquera estilo Tinder
voltado a uma comunidade universitária: cadastro de usuário, perfil de pessoa, listagem
de perfis com filtros, likes, matches recíprocos, upload de fotos com acesso via proxy e
consulta de redes sociais só após match. Este repositório contém apenas o backend — não
há frontend aqui.

## Stack

- **Spring Boot 3.2.3** — Web, Validation, Security, OAuth2 Client, Data MongoDB, DevTools
- **MongoDB** (Spring Data MongoDB) — único banco de dados
- **JWT** (`io.jsonwebtoken` 0.11.5) — autenticação própria + login via Google OAuth2
- **Cloudflare R2** (AWS SDK v2 `s3`, compatível com S3) — armazenamento de imagens
- **thumbnailator** — compressão/redimensionamento de imagens no upload
- **springdoc-openapi** — Swagger UI em `/swagger-ui/index.html`, spec em `/api-docs`
- **spring-dotenv** — carrega o `.env` da pasta de execução como variáveis de ambiente
  (nos testes ele é neutralizado pelo perfil `test` — ver [Testes](#testes))

## Principais funcionalidades

- Cadastro/login local (e-mail + senha) e login via Google OAuth2
- Perfil de usuário (`Pessoa`) separado da conta (`User`), com filtros por gênero/interesse
- Sistema de likes com criação automática de match quando recíproco; desfazer match
- Upload de fotos (galeria; uma delas pode ser marcada como foto de perfil) com compressão.
  O acesso às fotos tem **dois modos**, conforme `CF_R2_PUBLIC_URL`:
  - **sem** `CF_R2_PUBLIC_URL`: as URLs gravadas apontam para o proxy autenticado
    `/api/imagens/proxy/...`, que só libera a foto para o dono ou para quem tem match ativo
    com o dono (redirect 302 para uma URL pré-assinada válida por 1h);
  - **com** `CF_R2_PUBLIC_URL` definida: as URLs gravadas no banco são a URL pública do
    bucket, e é isso que `GET /pessoas`, `GET /pessoas/{id}/perfil` e `GET /pessoas/me`
    devolvem — o proxy é contornado nessas respostas. Só a resposta do upload e
    `GET /api/imagens/minhas-imagens` reescrevem a URL para o proxy. (Risco R6 em
    [`docs/ARQUITETURA.md`](docs/ARQUITETURA.md).)
- Redes sociais (Instagram/WhatsApp/Telegram) consultáveis apenas entre pessoas com match
  ativo (`GET /matches/{matchId}/redes-sociais`). **Atenção:** hoje **não existe nenhum
  endpoint para cadastrar** essas redes — `POST`/`PUT /pessoas` não aceitam esses campos —,
  então a consulta devolve `null` nos três campos, a não ser que os dados tenham sido
  gravados direto no banco (risco R5).

## Estrutura do código

O código Java (`src/main/java/com/example/pairup/`) é organizado **por funcionalidade**:
cada pacote reúne a entidade, o repositório, o service, o controller e os DTOs de uma
mesma área do domínio. O que é transversal (usado por várias funcionalidades) fica em
`shared/`.

```
com.example.pairup
├── PairUpApplication.java      # @SpringBootApplication
├── user/      # conta: User, UserRepository, UserService, UserController, LoginRequestDTO
├── pessoa/    # perfil: Pessoa, Genero, Interesse, PessoaRepository, PessoaService,
│              #         PessoaController, PessoaPerfilDTO, PessoaCompletaDTO
├── match/     # likes e matches: Like, Match, LikeRepository, MatchRepository, LikeService,
│              #         MatchService, LikeController, MatchController, PessoaRedesSociaisDTO
├── imagem/    # fotos (R2): Imagem, ImagemRepository, ImagemService, AuditLogService,
│              #         ImagemController, ImagemProxyController, ImagemUploadDTO,
│              #         ImagemUploadResponseDTO, ImageCompressionUtil
├── tag/       # Tag, TagRepository, TagService, TagController
└── shared/
    ├── config/     # SecurityConfig, CloudflareR2Config, OpenAPIConfig, OAuth2SuccessHandler
    ├── security/   # JwtFilter, JwtService, CustomUserDetailsService, SecurityUtils
    └── exception/  # GlobalExceptionHandler
```

Os testes (`src/test/java/com/example/pairup/`) seguem o mesmo pacote da funcionalidade
que testam (ex.: `match/LikeControllerTest`, `match/MatchControllerTest`).

## Conceitos da API

### Autenticação (JWT)

- `POST /users` cria a conta (`{nome, email, password}`); `POST /users/login`
  (`{email, password}`) devolve `{token, userId, email}`.
- Envie o token em todas as rotas protegidas: `Authorization: Bearer <token>`.
- O token é HS256 assinado com `JWT_SECRET` e **expira em 15 minutos**
  (`EXPIRATION_MS`, `shared/security/JwtService.java:22`). **Não há refresh token nem
  endpoint de renovação/logout**: ao expirar, o cliente precisa refazer o login (local ou
  Google).
- Rotas públicas (sem token): `POST /users`, `POST /users/login`, `GET /tags`,
  `GET /tags/ativas`, `/oauth2/**`, `/login/oauth2/**`, Swagger (`/swagger-ui/**`,
  `/api-docs/**`). Sem token, as demais respondem `401 {"erro": "Token não fornecido"}`.

### Login com Google (OAuth2)

1. O frontend redireciona o navegador para `GET /oauth2/authorization/google`.
2. O Google volta para `${BASE_URL}/login/oauth2/code/google` (precisa estar cadastrado
   como redirect URI no console do Google).
3. O backend busca o `User` pelo e-mail do Google (ou cria um, sem senha), cria uma
   `Pessoa` mínima se ainda não houver (só nome/e-mail — sem gênero, interesse, data de
   nascimento nem aceite de termos) e redireciona para
   `${FRONTEND_URL}/auth/callback?token=<jwt>`.

Consequências práticas: depois do login Google o usuário **já tem** uma `Pessoa`, então
`POST /pessoas` responde `409`; o perfil deve ser completado com `PUT /pessoas/me` e
`PATCH /pessoas/me/ciencia-responsabilidade`. `FRONTEND_URL` precisa ser **uma única URL**
(sem vírgulas).

### `User.id` × `Pessoa.id`

- **`User.id`** identifica a **conta** (login). É o que vai dentro do JWT e o que
  `POST /users/login` devolve como `userId`.
- **`Pessoa.id`** identifica o **perfil**. É o `id` que aparece em `GET /pessoas`, e é o
  que likes (`POST /likes?destinoId=<Pessoa.id>`), matches (`pessoaId1`/`pessoaId2`) e
  imagens usam.
- Ligação: `Pessoa.usuarioId = User.id`. O backend traduz o `User.id` do token para o
  `Pessoa.id` do usuário logado; quem ainda não criou perfil recebe `400` ("Perfil não
  encontrado") nas rotas de likes, matches e upload.

### Upload de imagens

`POST /api/imagens/upload` (autenticado), `multipart/form-data`:

| Campo | Tipo | Observação |
|---|---|---|
| `file` | arquivo | obrigatório; `Content-Type` declarado precisa começar com `image/`; até **5 MB** |
| `perfil` | `true`/`false` | opcional (default `false`); `true` desmarca a foto de perfil anterior |

- Limites: 5 MB por arquivo e 30 MB por requisição (`application.yml`); não há limite de
  quantidade de fotos por pessoa.
- A imagem é redimensionada para caber em 800x800, qualidade 0,7; PNG e GIF mantêm o
  formato, o resto é convertido para JPG. Se a compressão falhar, o arquivo original é
  enviado mesmo assim.
- Resposta `200`:
  `{"id", "url": "/api/imagens/proxy/<chave>", "nomeOriginal", "perfil", "tamanhoBytes",
  "mensagem": "Upload realizado com sucesso", "sucesso": true}`.
  Erros: `400`/`500` com `{"sucesso": false, "mensagem": "..."}`; arquivo acima do limite
  do multipart → `413` (formato do `GlobalExceptionHandler`).
- Relacionados: `GET /api/imagens/minhas-imagens`, `DELETE /api/imagens/{imagemId}` (não
  apaga a última imagem ativa) e `GET /api/imagens/proxy/<chave>` (302 para URL
  pré-assinada; 403 sem match; 410 se a imagem foi apagada).

A lista completa de endpoints está em
[`docs/REFERENCIA_FUNCOES.md`](docs/REFERENCIA_FUNCOES.md#tabela-resumo-de-endpoints).

## Rodando o projeto

### Pré-requisitos
- JDK 21 e Maven (o Maven Wrapper `mvnw` foi removido; use o `mvn` instalado)
- Docker + Docker Compose (para rodar tudo containerizado, incluindo o MongoDB)

### Variáveis de ambiente

Crie um arquivo `.env` na raiz (não versionado; está no `.gitignore`):

| Variável | Obrigatória? | Uso |
|---|---|---|
| `JWT_SECRET` | **sim** (fora dos testes) | chave HS256; mínimo 32 caracteres (um valor curto só falha no primeiro login/validação) |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | **sim** (fora dos testes) | OAuth2 Google; sem elas o contexto Spring não sobe |
| `BASE_URL` | **sim** (fora dos testes) | monta o redirect URI do Google (`${BASE_URL}/login/oauth2/code/google`) |
| `CF_R2_ENDPOINT`, `CF_R2_ACCESS_KEY`, `CF_R2_SECRET_KEY`, `CF_R2_BUCKET` | **sim** (fora dos testes) | Cloudflare R2 / S3 |
| `CF_R2_PUBLIC_URL` | não | se definida, as URLs de foto passam a ser a URL pública do bucket (ver acima) |
| `MONGODB_URI` | não | default `mongodb://localhost:27017/PAIRUP` (sem credenciais) |
| `FRONTEND_URL` | não | **uma** URL; destino do redirect do login Google. Default `http://localhost:5173` |
| `PORT` | não | default `8080` |
| `MONGO_ROOT_USER`, `MONGO_ROOT_PASSWORD` | não | só para o `docker-compose.yml` (default `admin`/`admin123`) |

```
MONGODB_URI=
JWT_SECRET=
GOOGLE_CLIENT_ID=
GOOGLE_CLIENT_SECRET=
BASE_URL=
FRONTEND_URL=
PORT=
CF_R2_ENDPOINT=
CF_R2_ACCESS_KEY=
CF_R2_SECRET_KEY=
CF_R2_BUCKET=
CF_R2_PUBLIC_URL=
MONGO_ROOT_USER=
MONGO_ROOT_PASSWORD=
```

> As origens de CORS **não** vêm de variável de ambiente: são fixas em
> `shared/config/SecurityConfig.java` (localhost 5173/3000/8080 e os domínios de
> produção/preview).

### Local, sem Docker

```bash
mvn spring-boot:run
```

Atenção: o `spring-dotenv` usa o `.env` da raiz. Se o seu `.env` aponta `MONGODB_URI` para
um banco remoto (produção), é para lá que a aplicação local vai gravar.

### Com Docker Compose

```bash
docker-compose up
```

Sobe MongoDB (porta `27017`, database `PAIRUP`, usuário root `MONGO_ROOT_USER`/
`MONGO_ROOT_PASSWORD`) e a aplicação (porta `8080`). O `MONGODB_URI` do container da
aplicação é montado pelo próprio compose (aponta para o Mongo do compose), mas as variáveis
`CF_R2_*`, `GOOGLE_*` e `JWT_SECRET` vêm do `.env` — ou seja, os uploads vão para o bucket
configurado ali. O default de `FRONTEND_URL` no compose tem duas URLs separadas por vírgula,
o que quebra o redirect do login Google (risco R9): defina `FRONTEND_URL` no `.env`.
O healthcheck do container chama `/actuator/health`, que não existe (não há actuator no
`pom.xml`), então o container aparece como `unhealthy` (risco R7). O serviço `nginx`
(perfil `production`, opcional) depende de `nginx.conf`/`ssl/` que não estão no repositório.

### Documentação da API (Swagger)

Com a aplicação rodando: `http://localhost:8080/swagger-ui/index.html` (spec em
`http://localhost:8080/api-docs`).

O caminho `/swagger-ui.html` (configurado em `springdoc.swagger-ui.path`) **não
funciona**: ele não está liberado no `SecurityConfig` e responde `302` para
`/oauth2/authorization/google`. O Swagger UI não tem botão "Authorize" configurado para o
JWT.

## Testes

A suíte tem 35 testes de integração (`@SpringBootTest` + MockMvc) e passa 35/35 contra
Mongo e MinIO descartáveis.

Todas as classes de teste usam `@ActiveProfiles("test")`, que carrega
`src/test/resources/application-test.yml` por cima do `application.yml` principal. Esse
perfil fixa valores **literais** (sem `${...}`), então o `.env` (que o `spring-dotenv`
carrega também em `mvn test`) **não** leva os testes para produção:

| Propriedade | Valor no perfil `test` | Para mudar |
|---|---|---|
| `spring.data.mongodb.uri` | `mongodb://localhost:27018/PAIRUP_TEST` | `SPRING_DATA_MONGODB_URI` |
| `cloudflare.r2.*` | `http://localhost:9000`, bucket `test-bucket`, `test-access-key`/`test-secret-key` | `CLOUDFLARE_R2_ENDPOINT`, `CLOUDFLARE_R2_PUBLIC_URL` |
| `jwt.secret`, Google OAuth | valores de teste fixos | — (os testes não chamam o Google) |

`MONGODB_URI` e `CF_R2_*` **não** têm efeito nos testes.

Exemplo com Mongo e MinIO descartáveis (MinIO na porta 9100 para não colidir com outro
serviço na 9000; a imagem do MinIO fica no `quay.io`, não mais no Docker Hub):

```bash
docker run -d --rm --name pairup_test_mongo -p 27018:27017 mongo:7.0
docker run -d --rm --name pairup_test_minio -p 9100:9000 \
  -e MINIO_ROOT_USER=test-access-key -e MINIO_ROOT_PASSWORD=test-secret-key \
  quay.io/minio/minio server /data
docker run --rm --network host --entrypoint sh quay.io/minio/mc -c \
  "mc alias set t http://localhost:9100 test-access-key test-secret-key && mc mb -p t/test-bucket"

CLOUDFLARE_R2_ENDPOINT=http://localhost:9100 \
CLOUDFLARE_R2_PUBLIC_URL=http://localhost:9100/test-bucket \
mvn -B clean test

docker stop pairup_test_mongo pairup_test_minio   # descarta todos os dados de teste
```

Detalhes, histórico de execuções e limitações conhecidas da suíte em
[`docs/TESTES.md`](docs/TESTES.md).

## Documentação técnica

| Documento | Conteúdo |
|---|---|
| [`docs/ARQUITETURA.md`](docs/ARQUITETURA.md) | arquitetura, fluxos ponta a ponta, configuração e a lista numerada de riscos/inconsistências (R1, R2, ...) |
| [`docs/REFERENCIA_FUNCOES.md`](docs/REFERENCIA_FUNCOES.md) | referência classe a classe e método a método, com tabela-resumo de endpoints |
| [`docs/TESTES.md`](docs/TESTES.md) | como rodar a suíte com segurança, resultados das execuções e comportamento HTTP observado |
| [`docs/ANALISE_SIMPLIFICACAO.md`](docs/ANALISE_SIMPLIFICACAO.md) | análise do que dá para simplificar, decisões pendentes e plano por fases |
