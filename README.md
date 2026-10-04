# PairUp

API REST (Spring Boot 3.2.3 / Java 21) para um aplicativo de paquera estilo Tinder
voltado a uma comunidade universitária: cadastro de usuário, perfil de pessoa, listagem
de perfis com filtros, likes, matches recíprocos, upload/proxy de fotos e liberação de
redes sociais só após match. Este repositório contém apenas o backend — não há frontend
aqui.

## Stack

- **Spring Boot 3.2.3** — Web, Validation, Security, OAuth2 Client, Data MongoDB, DevTools
- **MongoDB** (Spring Data MongoDB) — único banco de dados
- **JWT** (`io.jsonwebtoken`) — autenticação própria + login via Google OAuth2
- **Cloudflare R2** (AWS SDK v2 `s3`, compatível com S3) — armazenamento de imagens
- **thumbnailator** — compressão/redimensionamento de imagens no upload
- **springdoc-openapi** — Swagger UI em `/swagger-ui.html`, spec em `/api-docs`
- **spring-dotenv** — carrega `.env` como variáveis de ambiente

## Principais funcionalidades

- Cadastro/login local (e-mail + senha) e login via Google OAuth2
- Perfil de usuário (`Pessoa`) separado da conta (`User`), com filtros por gênero/interesse
- Sistema de likes com criação automática de match quando recíproco
- Upload de foto de perfil com compressão e acesso via proxy autenticado (nunca a URL direta do bucket)
- Liberação de redes sociais (Instagram/WhatsApp/Telegram) apenas entre pessoas com match ativo

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

## Rodando o projeto

### Pré-requisitos
- JDK 21 e Maven
- Docker + Docker Compose (para rodar tudo containerizado, incluindo o MongoDB)

### Variáveis de ambiente

Crie um arquivo `.env` na raiz (não versionado) com:

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
```

### Local, sem Docker

```bash
mvn spring-boot:run
```

### Com Docker Compose

```bash
docker-compose up
```

Sobe MongoDB (porta `27017`, database `TINDER`) e a aplicação (porta `8080`). O
serviço `nginx` no `docker-compose.yml` (perfil `production`, opcional) segue
provisionado mas não é usado pelo código hoje (ver seção abaixo). O serviço `redis`
que existia aqui foi removido: não havia nenhum cliente/dependência Redis no código
(rate limiting nunca foi implementado, apesar das chaves `app.security.rate-limit.*`
que existiam no `application.yml`).

### Documentação da API

Com a aplicação rodando: `http://localhost:8080/swagger-ui.html`

## Testes

```bash
mvn test
```

> ⚠️ No estado atual do repositório, a suíte de testes falha por completo (o contexto
> Spring não sobe em modo teste por falta da propriedade `cloudflare.r2.endpoint` em
> `src/test/resources/application.yml`). Ver detalhes e demais inconsistências
> conhecidas em [`docs/ARQUITETURA.md`](docs/ARQUITETURA.md).

## Documentação técnica

Um retrato detalhado da arquitetura, dos fluxos ponta a ponta e das inconsistências
conhecidas do código (geradas por análise do código-fonte real) está em
[`docs/ARQUITETURA.md`](docs/ARQUITETURA.md).
