# PairUp — Back-end

API REST do PairUp: contas, perfis, feed, likes, dislikes, matches, fotos e tags. O front
(`tinder/`) só fala com o banco e com o bucket por meio desta API.

## Stack

| Camada | Tecnologia | Versão / detalhe |
|---|---|---|
| Linguagem | Java | 21 |
| Framework | Spring Boot | 3.2.3 (Web, Validation, Security, OAuth2 Client, Mail) |
| Banco | MongoDB, via Spring Data MongoDB | 7.0 no Docker local |
| Autenticação | JWT (jjwt) + login Google (OAuth2/OIDC) | token de 15 min, sem refresh; BCrypt custo 12 |
| Fotos | Cloudflare R2, via AWS SDK v2 (S3) | MinIO nos testes |
| Imagem | Thumbnailator + ImageIO | valida pelo conteúdo e re-codifica em 800×800, qualidade 0.7 |
| E-mail | Spring Mail (SMTP) | opcional; sem `MAIL_HOST` fica desligado |
| Documentação da API | springdoc-openapi | 2.3.0, em `/swagger-ui.html`; desligado sem `SWAGGER_ENABLED=true` |
| Configuração | spring-dotenv | lê o `.env` da raiz |
| Utilitários | Lombok | — |
| Build | Maven | `pom.xml` |
| Container | Dockerfile multi-stage | Maven 3.9 + JRE 21 Alpine, usuário não-root |
| Hospedagem | Railway | — |

## Estrutura

O código é separado por assunto. Em cada pacote, o `Controller` recebe a requisição, o
`Service` aplica as regras e o `Repository` fala com o MongoDB.

```
Tinder_ufs/
├── pom.xml                      dependências e build
├── Dockerfile                   build Maven → imagem JRE 21
├── docker-compose.yml           Mongo + API para rodar local
├── .env.example                 variáveis necessárias
├── docs/                        ARQUITETURA, CONTRATO_FRONT_BACK, TESTES, este arquivo...
└── src/
    ├── main/java/com/example/pairup/
    │   ├── PairUpApplication.java      ponto de entrada (@EnableAsync)
    │   ├── user/                       conta de acesso
    │   │   ├── User, UserRepository, UserService, UserController
    │   │   └── UserCreateDTO, UserUpdateDTO, LoginRequestDTO
    │   ├── pessoa/                     perfil público e feed
    │   │   ├── Pessoa, PessoaRepository, PessoaService, PessoaController
    │   │   ├── PessoaRequestDTO (entrada), PessoaPerfilDTO, PessoaCompletaDTO (saída)
    │   │   └── Genero, Interesse (enums)
    │   ├── match/                      likes, dislikes, matches e e-mail de match
    │   │   ├── Like, LikeRepository, LikeService, LikeController
    │   │   ├── Dislike, DislikeRepository, DislikeService, DislikeController
    │   │   ├── Match, MatchRepository, MatchService, MatchController
    │   │   └── MatchEmailService, PessoaRedesSociaisDTO
    │   ├── imagem/                     upload, remoção, compressão e acesso às fotos
    │   │   ├── Imagem, ImagemRepository, ImagemService, ImagemController
    │   │   ├── ImagemProxyController, ImageCompressionUtil
    │   │   └── ImagemUploadDTO, ImagemUploadResponseDTO, AuditLogService
    │   ├── tag/                        interesses exibidos nos perfis (Tag, TagRequestDTO...)
    │   └── shared/
    │       ├── config/     SecurityConfig, OAuth2SuccessHandler, CloudflareR2Config, OpenAPIConfig
    │       ├── security/   JwtService, JwtFilter, RateLimitFilter, LoginAttemptService,
    │       │               SecurityUtils, CustomUserDetailsService
    │       └── exception/  GlobalExceptionHandler
    ├── main/resources/application.yml
    └── test/
        ├── java/...                    testes de controller e serviço (MockMvc + Mockito)
        └── resources/application-test.yml   perfil "test": Mongo e S3 locais e descartáveis
```

Tamanho aproximado: 3,5 mil linhas de código e 1,9 mil de testes.

## Modelo de dados (MongoDB)

| Coleção | O que guarda | Campos principais |
|---|---|---|
| `users` | Conta de acesso | `email`, `password` (BCrypt; nulo em conta Google), `provider` (`google` ou nulo) |
| `pessoa` | Perfil | `usuarioId`, `nome`, `curso`, `dataNasc`, `genero`, `interesse`, `tags`, redes sociais, `cienciaResponsabilidade`, `ativo` |
| `imagem` | Foto | `pessoa` (@DBRef), `url`, `publicId` (chave no R2), `perfil` (principal), `ativa` |
| `like` | Curtida | `pessoaOrigemId`, `pessoaDestinoId`, `ativo` |
| `dislike` | Perfil dispensado | `pessoaOrigemId`, `pessoaDestinoId`, `data` |
| `match` | Match | `pessoaId1`, `pessoaId2`, `ativo` |
| `tag` | Interesse disponível | `nome`, `ativa` |

**Conta × perfil.** `users` responde "quem pode entrar"; `pessoa` responde "quem aparece no
app". O JWT carrega o `User.id`, e o back encontra a `Pessoa` a partir dele. Por isso nenhum
endpoint recebe do front o id de quem está agindo.

## Funcionalidades

### Conta e login
- **Cadastro** (`POST /users`): nome, e-mail e senha (**8 a 72** caracteres). O e-mail é gravado
  em minúsculas. O DTO não aceita `id` nem `provider`, então não dá para sobrescrever uma conta
  existente.
- **Login** (`POST /users/login`): devolve um JWT válido por 15 minutos.
  - Senha errada, e-mail inexistente e conta Google dão a mesma resposta (401) no mesmo tempo.
  - 5 falhas seguidas bloqueiam o e-mail por 15 min (429).
  - Contas antigas com senha de 6 caracteres continuam entrando.
- **Login Google**: o Spring Security faz o fluxo OAuth2. Depois, o `OAuth2SuccessHandler` faz
  o seguinte:
  - exige `email_verified`;
  - **recusa entrar numa conta criada com senha** e volta para `/?erro=conta_com_senha`, porque
    o cadastro local não confirma o e-mail;
  - cria a conta Google e um perfil mínimo, se ainda não existirem;
  - redireciona para `/auth/callback#token=...` no front (token no fragmento, fora de logs e
    do `Referer`);
  - se o fluxo do Google falhar, redireciona para `/?erro=google_falhou`.
- **Alterar conta** (`PUT /users/me`): nome e e-mail.
  - Trocar o e-mail exige `senhaAtual` (403 se errada).
  - E-mail de outra conta → 409.
  - Conta Google não troca e-mail (400).
- **Apagar conta** (`DELETE /users/me`): apaga a conta e o perfil, em cascata.

### Perfil e feed
- **Criar perfil** (`POST /pessoas`):
  - exige 18 anos (e nascimento a no máximo 100 anos) e o aceite dos termos;
  - usa o e-mail da conta; o `email` do corpo é ignorado;
  - redes sociais só como usuário/número: Instagram `@?[A-Za-z0-9._]{1,30}`, Telegram
    `@?[A-Za-z0-9_]{1,32}`, WhatsApp com 8 a 20 dígitos/espaços/`()+-`;
  - aceita no máximo 5 tags, só ativas;
  - um perfil por conta (409 se já existe).
- **Editar perfil** (`PUT /pessoas/me`): campo ausente mantém o valor; string vazia apaga uma
  rede social.
- **Meu perfil** (`GET /pessoas/me`): perfil completo, com o campo calculado `perfilCompleto`.
  Sem ele completo, o front manda a pessoa para o onboarding.
- **Feed** (`GET /pessoas`):
  - paginado, com filtros opcionais de `interesse` e `genero`;
  - lista só cadastros completos e ativos;
  - busca as fotos da página numa única consulta;
  - não filtra compatibilidade nem quem já foi avaliado: isso fica no front.
- **Perfil de outra pessoa** (`GET /pessoas/{id}/perfil`): sem redes sociais.
- **Apagar perfil** (`DELETE /pessoas/me`): apaga o perfil e tudo que aponta para ele, ou seja,
  fotos (R2 e banco), likes, dislikes e matches.

### Likes, dislikes e matches
- **Curtir** (`POST /likes?destinoId=`):
  - grava a curtida;
  - se já existe a curtida inversa, cria o match e responde `match: true`;
  - a outra pessoa recebe um e-mail assíncrono (`@Async`), cuja falha só aparece no log;
  - curtir a si mesmo ou um perfil inexistente → 400; exige cadastro completo (403).
- **Passar** (`POST /dislikes?destinoId=`): grava o dislike para a pessoa não voltar ao deck.
  - É idempotente e responde 204.
  - Para si mesmo ou para um perfil inexistente → 400.
- **Listas**: `GET /likes/dados`, `GET /likes/recebidos`, `GET /dislikes/dados` e
  `GET /matches/meus` (só matches ativos). Todas são paginadas.
- **Contatos** (`GET /matches/{id}/redes-sociais`): devolve Instagram, WhatsApp e Telegram da
  outra pessoa, só para quem está no match e só com ele ativo.
- **Desfazer** (`PUT /matches/desfazer/{id}`): marca o match como inativo.

### Fotos
- **Enviar** (`POST /api/imagens/upload`, multipart):
  - até 5 MB e no máximo **6 fotos ativas** por perfil;
  - o tipo é descoberto pelos bytes do arquivo: só JPEG, PNG ou WebP. O Content-Type e o nome
    enviados pelo cliente são ignorados;
  - JPEG e PNG são decodificados e re-codificados em até 800×800, o que apaga metadados (EXIF
    com GPS) e qualquer conteúdo embutido. Resolução acima de 8000 px por lado é recusada;
  - WebP é guardado como veio, com Content-Type `image/webp`;
  - grava no R2 em `tinder_ufs/{pessoaId}/{uuid}.{jpg|png|webp}`;
  - com `perfil=true`, a foto vira a principal. Sem nenhuma principal, a enviada assume.
- **Listar e remover**: `GET /api/imagens/minhas-imagens` e `DELETE /api/imagens/{id}`.
  - A última foto não pode ser apagada.
  - Ao apagar a principal, a primeira foto ativa é promovida.
- **Acesso** (`GET /api/imagens/proxy/**`): só o dono ou quem tem match. Responde 302 para uma
  URL assinada do R2 válida por 15 min (cache privado de 10 min).
  - Com `CF_R2_PUBLIC_URL` definida, as fotos do feed usam a URL pública direto.

### Tags
- `GET /tags` e `GET /tags/ativas` são públicos.
- `POST /tags` e `DELETE /tags/**` exigem `ROLE_ADMIN`, papel que ninguém tem hoje: as tags
  são cadastradas direto no banco. O `POST` recebe `TagRequestDTO`, sem `id`.

## Segurança

Revisada em 2026-11-16; o histórico dos riscos está em `docs/ARQUITETURA.md` (§5.7).

### Autenticação
- **`JwtFilter`**: valida o `Authorization: Bearer` em toda rota não pública e monta o
  `SecurityContext`. Só a autenticação fica no `try`, então erros dos controllers não viram
  "401 Token inválido".
- **`JwtService`**: HS256, emissor `pairup-api`, `jti` aleatório, 15 min. Segredo com menos de
  32 bytes impede a aplicação de subir.
- **`SecurityUtils.getUserIdOrThrow()`**: é a única fonte do id de quem age (401 se ausente).
- **Senhas**: BCrypt custo 12; 8 a 72 caracteres no cadastro.

### Contra abuso
- **`RateLimitFilter`** (por IP, janela de 1 min, em memória): 10 requisições em
  `POST /users/login` e `POST /users`, 300 no resto. Excedeu → 429 com `Retry-After: 60`.
- **`LoginAttemptService`** (por e-mail): 5 senhas erradas seguidas → bloqueio de 15 min.
- **Login em tempo constante**: e-mail inexistente também roda um BCrypt.
- **Upload**: tipo pelos bytes, re-codificação, limite de resolução e de 6 fotos.

### Configuração HTTP
- **`SecurityConfig`**:
  - **Rotas públicas:** cadastro, login, OAuth2, `/error`, GET de tags e, só com
    `SWAGGER_ENABLED=true`, o Swagger.
  - **Origens CORS:** `CORS_ALLOWED_ORIGINS`, sem `*` e sem credenciais.
  - **Sessão:** stateless. A sessão HTTP só guarda o `state` do OAuth2 e é invalidada no fim.
  - **Cabeçalhos:** `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`, HSTS (1 ano,
    em HTTPS), `Referrer-Policy: no-referrer`, `Permissions-Policy`.
  - **Respostas de segurança:** 401 `{"erro": "Não autenticado"}` e 403
    `{"erro": "Acesso negado"}` em JSON (antes eram 302 para o Google).
- **Erros**: o `GlobalExceptionHandler` converte as exceções em respostas JSON, sem mensagem
  interna nem stack trace (`server.error.include-message: never`).
  - `ResponseStatusException` → o status dela.
  - `@Valid` → 400 com a lista de campos.
  - JSON malformado, parâmetro ausente ou de tipo errado → 400 "Requisição inválida".
  - Método não suportado → 405; Content-Type não suportado → 415; rota inexistente → 404.
  - Arquivo grande → 413.
  - Resto → 500 genérico (o nome da exceção só vai ao log).
- **Logs**: valores vindos do cliente passam por `SecurityUtils.paraLog` (sem quebras de linha).
- **Limites**: corpo multipart até 6 MB; formulário até 1 MB.

## Configuração

| Variável | Para quê |
|---|---|
| `MONGODB_URI` | Conexão com o banco |
| `JWT_SECRET` | Assina os tokens (mínimo 32 bytes; gere com `openssl rand -base64 48`) |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | Login Google |
| `BASE_URL` | Endereço público do back (redirect do OAuth) |
| `FRONTEND_URL` | Endereço do front, uma única URL |
| `CF_R2_ENDPOINT`, `CF_R2_ACCESS_KEY`, `CF_R2_SECRET_KEY`, `CF_R2_BUCKET` | Acesso ao R2 |
| `CF_R2_PUBLIC_URL` | URL pública das fotos (necessária para o feed mostrar fotos) |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM` | E-mail de match (opcional) |
| `PORT` | Porta HTTP (padrão 8080) |
| `CORS_ALLOWED_ORIGINS` | Origens do front, separadas por vírgula (padrão `http://localhost:5173,https://pairup-flax.vercel.app`) |
| `SWAGGER_ENABLED` | `true` liga o Swagger (padrão `false`) |
| `RATE_LIMIT_ENABLED`, `RATE_LIMIT_AUTH`, `RATE_LIMIT_GERAL` | Limite por IP (padrão ligado, 10 e 300 por minuto) |
| `MONGO_ROOT_USER`, `MONGO_ROOT_PASSWORD` | Usuário e senha do Mongo no `docker-compose` (senha obrigatória) |

## Rodar e testar

```bash
# Rodar local
docker compose up -d mongodb
mvn spring-boot:run                     # http://localhost:8080

# Testes (Mongo e MinIO descartáveis; detalhes em docs/TESTES.md)
docker run -d --rm --name pairup_test_mongo -p 27018:27017 mongo:7.0
docker run -d --rm --name pairup_test_minio -p 9100:9000 \
  -e MINIO_ROOT_USER=test-access-key -e MINIO_ROOT_PASSWORD=test-secret-key \
  quay.io/minio/minio server /data
docker run --rm --network host --entrypoint sh quay.io/minio/mc -c \
  "mc alias set t http://localhost:9100 test-access-key test-secret-key && mc mb -p t/test-bucket"
CLOUDFLARE_R2_ENDPOINT=http://localhost:9100 \
CLOUDFLARE_R2_PUBLIC_URL=http://localhost:9100/test-bucket mvn -B test
docker stop pairup_test_mongo pairup_test_minio
```

Na última execução, em 2026-11-16, rodaram 80 testes e nenhum falhou. As classes de teste são:

| Assunto | Classes |
|---|---|
| Controllers | `User`, `Pessoa`, `Like`, `Match`, `Imagem` e `Tag` |
| Serviços | `LikeService` e `MatchEmailService` |
| Login Google | `OAuth2SuccessHandler` |
| Dislike e exclusão em cascata | `DislikeEExclusaoTest` |
| Documentação da API | `OpenApiDocs` |
| Segurança (erros, JWT, cabeçalhos, validação, senha) | `SegurancaTest` |

## Limitações conhecidas

A lista completa, com referências ao código, está em `docs/ARQUITETURA.md` (seção 5). As
principais:
- **Índices e transações:** os índices únicos não são criados (falta `auto-index-creation`), e
  `@Transactional` não tem efeito porque não há transaction manager nem replica set.
- **Sessão:** o token dura 15 minutos e não é renovado.
- **Login:** sem confirmação de e-mail no cadastro local. O rate limit é em memória: com várias
  réplicas, cada uma conta separado.
- **Dependências:** Spring Boot 3.2.3 tem CVEs conhecidas. A atualização para 3.2.12 quebra a
  consulta de fotos do feed por `@DBRef` e foi adiada (ver `docs/ARQUITETURA.md`, §5.7).
- **Fotos públicas:** com `CF_R2_PUBLIC_URL`, quem tiver a URL de uma foto a vê sem login.
- **Deploy:** o healthcheck do Docker chama `/actuator/health`, mas o actuator não está no
  projeto.
- **Moderação:** sem denúncia e sem bloqueio de usuários.
