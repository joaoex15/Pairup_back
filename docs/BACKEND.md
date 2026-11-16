# PairUp — Back-end

API REST do PairUp: contas, perfis, feed, likes, dislikes, matches, fotos e tags. O front
(`tinder/`) só fala com o banco e com o bucket por meio desta API.

## Stack

| Camada | Tecnologia | Versão / detalhe |
|---|---|---|
| Linguagem | Java | 21 |
| Framework | Spring Boot | 3.2.3 (Web, Validation, Security, OAuth2 Client, Mail) |
| Banco | MongoDB, via Spring Data MongoDB | 7.0 no Docker local |
| Autenticação | JWT (jjwt) + login Google (OAuth2/OIDC) | token de 15 min, sem refresh |
| Fotos | Cloudflare R2, via AWS SDK v2 (S3) | MinIO nos testes |
| Imagem | Thumbnailator | redimensiona para 800×800, qualidade 0.7 |
| E-mail | Spring Mail (SMTP) | opcional; sem `MAIL_HOST` fica desligado |
| Documentação da API | springdoc-openapi | 2.3.0, em `/swagger-ui/index.html` |
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
    │   ├── tag/                        interesses exibidos nos perfis
    │   └── shared/
    │       ├── config/     SecurityConfig, OAuth2SuccessHandler, CloudflareR2Config, OpenAPIConfig
    │       ├── security/   JwtService, JwtFilter, SecurityUtils, CustomUserDetailsService
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
- **Cadastro** (`POST /users`): nome, e-mail e senha (6 a 72 caracteres). O DTO não aceita `id`
  nem `provider`, então não dá para sobrescrever uma conta existente.
- **Login** (`POST /users/login`): devolve um JWT válido por 15 minutos.
- **Login Google**: o Spring Security faz o fluxo OAuth2. Depois, o `OAuth2SuccessHandler` faz
  o seguinte:
  - exige `email_verified`;
  - **recusa entrar numa conta criada com senha** e volta para `/?erro=conta_com_senha`, porque
    o cadastro local não confirma o e-mail;
  - cria a conta Google e um perfil mínimo, se ainda não existirem;
  - redireciona para `/auth/callback?token=...` no front.
- **Alterar conta** (`PUT /users/me`): nome e e-mail.
  - Trocar o e-mail exige `senhaAtual` (403 se errada).
  - E-mail de outra conta → 409.
  - Conta Google não troca e-mail (400).
- **Apagar conta** (`DELETE /users/me`): apaga a conta e o perfil, em cascata.

### Perfil e feed
- **Criar perfil** (`POST /pessoas`):
  - exige 18 anos e o aceite dos termos;
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
  - curtir a si mesmo → 400; exige cadastro completo (403).
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
  - aceita só imagens de até 5 MB;
  - comprime para 800×800;
  - grava no R2 em `tinder_ufs/{pessoaId}/{uuid}.{ext}`;
  - com `perfil=true`, a foto vira a principal. Sem nenhuma principal, a enviada assume.
- **Listar e remover**: `GET /api/imagens/minhas-imagens` e `DELETE /api/imagens/{id}`.
  - A última foto não pode ser apagada.
  - Ao apagar a principal, a primeira foto ativa é promovida.
- **Acesso** (`GET /api/imagens/proxy/**`): só o dono ou quem tem match. Responde 302 para uma
  URL assinada do R2 (cache privado de 1 h).
  - Com `CF_R2_PUBLIC_URL` definida, as fotos do feed usam a URL pública direto.

### Tags
- `GET /tags` e `GET /tags/ativas` são públicos.
- `POST /tags` exige login.
- `DELETE /tags/**` exige `ROLE_ADMIN`, papel que ninguém tem hoje.

## Segurança

- **`JwtFilter`**: valida o `Authorization: Bearer` em toda rota não pública e monta o
  `SecurityContext`.
- **`SecurityConfig`**:
  - **Rotas públicas:** cadastro, login, OAuth2, Swagger e GET de tags.
  - **Origens CORS:** ficam fixas no código.
  - **Sessão:** stateless.
- **`SecurityUtils.getUserIdOrThrow()`**: é a única fonte do id de quem age.
- **Erros**: o `GlobalExceptionHandler` converte as exceções em respostas JSON.
  - `ResponseStatusException` → o status dela.
  - `@Valid` → 400 com a lista de campos.
  - Arquivo grande → 413.
  - Resto → 500 genérico.

## Configuração

| Variável | Para quê |
|---|---|
| `MONGODB_URI` | Conexão com o banco |
| `JWT_SECRET` | Assina os tokens (mínimo 32 caracteres) |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | Login Google |
| `BASE_URL` | Endereço público do back (redirect do OAuth) |
| `FRONTEND_URL` | Endereço do front, uma única URL |
| `CF_R2_ENDPOINT`, `CF_R2_ACCESS_KEY`, `CF_R2_SECRET_KEY`, `CF_R2_BUCKET` | Acesso ao R2 |
| `CF_R2_PUBLIC_URL` | URL pública das fotos (necessária para o feed mostrar fotos) |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM` | E-mail de match (opcional) |
| `PORT` | Porta HTTP (padrão 8080) |

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

Na última execução, em 2026-11-15, rodaram 65 testes e nenhum falhou. As classes de teste são:

| Assunto | Classes |
|---|---|
| Controllers | `User`, `Pessoa`, `Like`, `Match`, `Imagem` e `Tag` |
| Serviços | `LikeService` e `MatchEmailService` |
| Login Google | `OAuth2SuccessHandler` |
| Dislike e exclusão em cascata | `DislikeEExclusaoTest` |
| Documentação da API | `OpenApiDocs` |

## Limitações conhecidas

A lista completa, com referências ao código, está em `docs/ARQUITETURA.md` (seção 5). As
principais:
- **Índices e transações:** os índices únicos não são criados (falta `auto-index-creation`), e
  `@Transactional` não tem efeito porque não há transaction manager nem replica set.
- **Sessão:** o token dura 15 minutos e não é renovado.
- **Login:** sem rate limit e sem confirmação de e-mail no cadastro local.
- **Deploy:** o healthcheck do Docker chama `/actuator/health`, mas o actuator não está no
  projeto.
- **Moderação:** sem denúncia e sem bloqueio de usuários.
