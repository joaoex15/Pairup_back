# PairUp

API REST (Spring Boot 3.2.3 / Java 21) de um app de paquera estilo Tinder para a
comunidade universitária. Só o backend — não há frontend neste repositório.

## Funcionalidades

- Cadastro/login com e-mail e senha ou com Google (OAuth2), autenticação via JWT
- Perfil (`Pessoa`) com filtros por gênero e interesse, e até 5 tags
- Likes, com match automático quando recíproco
- Upload de fotos (Cloudflare R2), visíveis só para o dono e para quem tem match
- Redes sociais liberadas apenas entre pessoas com match

## Stack

Spring Boot (Web, Security, OAuth2, Data MongoDB) · MongoDB · JWT · Cloudflare R2 (S3) ·
thumbnailator · springdoc-openapi

## Estrutura

Código organizado por funcionalidade em `src/main/java/com/example/pairup/`:

```
user/     conta e login
pessoa/   perfil
match/    likes e matches
imagem/   upload e acesso às fotos
tag/      tags
shared/   config, segurança e tratamento de erros
```

## Como rodar

Pré-requisitos: JDK 21, Maven e Docker.

1. Crie um `.env` na raiz:

   ```
   JWT_SECRET=            # mínimo 32 caracteres
   GOOGLE_CLIENT_ID=
   GOOGLE_CLIENT_SECRET=
   BASE_URL=              # ex.: http://localhost:8080
   FRONTEND_URL=          # uma única URL, ex.: http://localhost:5173
   CF_R2_ENDPOINT=
   CF_R2_ACCESS_KEY=
   CF_R2_SECRET_KEY=
   CF_R2_BUCKET=
   MONGODB_URI=           # opcional, default mongodb://localhost:27017/PAIRUP
   ```

2. Suba a aplicação:

   ```bash
   docker-compose up        # MongoDB + aplicação
   # ou, com um MongoDB já rodando:
   mvn spring-boot:run
   ```

3. Acesse o Swagger em `http://localhost:8080/swagger-ui/index.html`.

## Uso rápido da API

- `POST /users` cria a conta; `POST /users/login` devolve o token.
- Envie `Authorization: Bearer <token>` nas rotas protegidas (o token expira em 15 min).
- Crie o perfil com `POST /pessoas` antes de usar likes, matches e upload.

Lista completa de endpoints em [`docs/REFERENCIA_FUNCOES.md`](docs/REFERENCIA_FUNCOES.md).

## Testes

Os testes usam o perfil `test` (Mongo na porta 27018 e MinIO), sem tocar no banco real:

```bash
mvn test
```

Passo a passo para subir o ambiente de teste em [`docs/TESTES.md`](docs/TESTES.md).

## Documentação

- [`docs/ARQUITETURA.md`](docs/ARQUITETURA.md) — arquitetura, fluxos e riscos conhecidos
- [`docs/REFERENCIA_FUNCOES.md`](docs/REFERENCIA_FUNCOES.md) — classes, métodos e endpoints
- [`docs/TESTES.md`](docs/TESTES.md) — como rodar e resultados da suíte
- [`docs/ANALISE_SIMPLIFICACAO.md`](docs/ANALISE_SIMPLIFICACAO.md) — plano de simplificação
