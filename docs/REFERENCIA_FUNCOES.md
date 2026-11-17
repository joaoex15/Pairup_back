# PairUp — Referência de Classes e Funções

> Guia didático de **todos** os arquivos `.java` de `src/main/java`, no estado atual do
> working tree (2026-10-03, atualizado em 2026-10-22). Para cada classe: responsabilidade e cada método
> público/relevante com assinatura, o que faz, parâmetros, retorno, erros, dependências e,
> para endpoints, verbo + rota + autenticação. Caminhos relativos a
> `src/main/java/com/example/pairup/`. Os números de linha permitem conferir no código.
>
> O código é organizado em pacotes **por funcionalidade** (`user/`, `pessoa/`, `match/`,
> `imagem/`, `tag/`) mais `shared/` (`config/`, `security/`, `exception/`) para o que é
> transversal. Este guia agrupa as classes **por papel** (configuração, segurança,
> controllers, services...) para facilitar a leitura; o pacote real de cada classe aparece
> no caminho logo abaixo do título dela.
>
> **2026-10-22:** os endpoints não recebem mais `HttpServletRequest` (o parâmetro só era
> repassado a `SecurityUtils.getUserIdOrThrow`, que não o usava), e foram removidos
> `ImagemService.definirFotoPerfil`/`findById`. Os números de linha dos controllers podem
> estar deslocados em até 2 linhas. `PessoaService`, `ImagemService`, `SecurityUtils` e
> `PessoaCompletaDTO` foram conferidos de novo.
>
> **2026-11-16 (endurecimento de segurança):** reescritas as entradas de `SecurityConfig`,
> `OAuth2SuccessHandler`, `JwtFilter`, `JwtService`, `SecurityUtils`, `UserController`,
> `UserService`, `TagController`, `ImageCompressionUtil` e partes de `ImagemService`/
> `ImagemProxyController`; novas classes `RateLimitFilter`, `LoginAttemptService` e
> `TagRequestDTO`. Nessas entradas os números de linha foram retirados (mudaram demais);
> procure pelo nome do método.
>
> Visão de arquitetura, fluxos e riscos (códigos `R1`...`R45` citados aqui):
> [`docs/ARQUITETURA.md`](ARQUITETURA.md).

## Conceitos que você precisa antes de ler

- **`User` x `Pessoa`**: `User` é a *conta* (e-mail/senha ou Google). `Pessoa` é o *perfil
  de paquera* (nome, curso, gênero, fotos...). Ligação: `Pessoa.usuarioId = User.id`.
- **O JWT carrega `User.id`**. Likes, matches e imagens usam **`Pessoa.id`**. Por isso vários
  controllers fazem `pessoaService.findByUsuarioId(userId)` antes de qualquer coisa.
- **Autenticação**: header `Authorization: Bearer <token>`. Há **duas** listas de rotas
  públicas: `JwtFilter.PUBLIC_ROUTES` (comparação por prefixo) e os `permitAll` do
  `SecurityConfig` (padrões de caminho do Spring). Rota fora da lista do `JwtFilter` e sem
  token → 401 em JSON `{"erro": "..."}` gerado pelo próprio filtro. Rota que o `JwtFilter`
  deixa passar mas o `SecurityConfig` não libera → 401 JSON (entry point próprio); acesso
  negado → 403 JSON. Desde 2026-11-16 não há mais redirect 302 para o Google nesses casos.
- **Lombok**: `@Data` gera getters/setters/`equals`/`hashCode`/`toString`;
  `@AllArgsConstructor`/`@RequiredArgsConstructor` geram o construtor usado pelo Spring para
  injeção de dependências; `@Slf4j` cria o campo `log`.

## Índice

- [Raiz](#raiz)
  - [PairUpApplication](#pairupapplication)
- [Configuração (`shared/config/`)](#configuração)
  - [SecurityConfig](#securityconfig) · [CloudflareR2Config](#cloudflarer2config) · [OpenAPIConfig](#openapiconfig) · [OAuth2SuccessHandler](#oauth2successhandler)
- [Segurança (`shared/security/`)](#segurança)
  - [JwtFilter](#jwtfilter) · [JwtService](#jwtservice) · [CustomUserDetailsService](#customuserdetailsservice) · [SecurityUtils](#securityutils)
- [Controllers](#controllers)
  - [UserController](#usercontroller) · [PessoaController](#pessoacontroller) · [LikeController](#likecontroller) · [MatchController](#matchcontroller) · [TagController](#tagcontroller) · [ImagemController](#imagemcontroller) · [ImagemProxyController](#imagemproxycontroller)
- [Services](#services)
  - [UserService](#userservice) · [PessoaService](#pessoaservice) · [LikeService](#likeservice) · [MatchService](#matchservice) · [TagService](#tagservice) · [ImagemService](#imagemservice) · [AuditLogService](#auditlogservice)
- [Repositórios](#repositórios)
  - [UserRepository](#userrepository) · [PessoaRepository](#pessoarepository) · [LikeRepository](#likerepository) · [MatchRepository](#matchrepository) · [TagRepository](#tagrepository) · [ImagemRepository](#imagemrepository)
- [Entidades](#entidades)
  - [User](#user) · [Pessoa](#pessoa) · [Like](#like) · [Match](#match) · [Tag](#tag) · [Imagem](#imagem) · [Genero / Interesse](#genero--interesse)
- [DTOs](#dtos)
  - [LoginRequestDTO](#loginrequestdto) · [PessoaPerfilDTO](#pessoaperfildto) · [PessoaCompletaDTO](#pessoacompletadto) · [PessoaRedesSociaisDTO](#pessoaredessociaisdto) · [ImagemUploadDTO](#imagemuploaddto) · [ImagemUploadResponseDTO](#imagemuploadresponsedto)
- [Tratamento de erros (`shared/exception/`)](#tratamento-de-erros)
  - [GlobalExceptionHandler](#globalexceptionhandler)
- [Utilitários](#utilitários)
  - [ImageCompressionUtil](#imagecompressionutil)
- [Tabela-resumo de endpoints](#tabela-resumo-de-endpoints)

---

## Raiz

### PairUpApplication
`PairUpApplication.java` — ponto de entrada. `@SpringBootApplication` ativa autoconfiguração e
varredura de componentes em `com.example.pairup.*`.

| Método | Descrição |
|---|---|
| `public static void main(String[] args)` (`:9-11`) | Sobe o contexto Spring (`SpringApplication.run`). As propriedades vêm de `application.yml` + variáveis de ambiente/`.env` (via `spring-dotenv`). |

---

## Configuração

### SecurityConfig
`shared/config/SecurityConfig.java` — `@Configuration @EnableWebSecurity`. Define a cadeia de
segurança HTTP, o codificador de senha e o CORS. Dependências injetadas por campo:
`OAuth2SuccessHandler`, `JwtFilter`, `RateLimitFilter`; propriedades `FRONTEND_URL`,
`seguranca.cors.allowed-origins` e `springdoc.swagger-ui.enabled`.

**`public SecurityFilterChain filterChain(HttpSecurity http) throws Exception`**
- Desliga CSRF, form login, HTTP Basic e logout (API só com Bearer token); sessão `STATELESS`.
- Cabeçalhos: `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, HSTS de 1 ano com
  subdomínios (só em HTTPS), `Referrer-Policy: no-referrer` e `Permissions-Policy` sem
  câmera/microfone/localização.
- Regras: `OPTIONS /**`, `POST /users`, `POST /users/login`, `/oauth2/**`, `/login/oauth2/**`
  e `/error` públicos; Swagger (`/swagger-ui.html`, `/swagger-ui/**`, `/api-docs`,
  `/api-docs/**`, `/v3/api-docs/**`) público **só se** `SWAGGER_ENABLED=true`; `GET /tags` e
  `GET /tags/ativas` públicos; qualquer outra rota de `/tags` exige `ROLE_ADMIN` (ninguém tem —
  R15); o resto exige autenticação.
- `exceptionHandling`: sem autenticação → 401 `{"erro": "Não autenticado"}`; acesso negado →
  403 `{"erro": "Acesso negado"}`. Ambos escrevem a resposta direto (sem `sendError`), então
  não há mais o redirect para o Google descrito antes em R15.
- `oauth2Login`: sucesso → `OAuth2SuccessHandler`; falha → redirect para
  `{FRONTEND_URL}/?erro=google_falhou`.
- Ordem dos filtros: `RateLimitFilter` → `JwtFilter` → `UsernamePasswordAuthenticationFilter`.

**`jwtFilterRegistration` / `rateLimitFilterRegistration`** — `FilterRegistrationBean`
desligados: os dois filtros são `@Component` e, sem isso, o Spring Boot os registraria uma
segunda vez fora da cadeia de segurança.

**`public PasswordEncoder passwordEncoder()`** — `BCryptPasswordEncoder` com custo 12.

**`public CorsConfigurationSource corsConfigurationSource()`** — CORS para `/**` com as
origens de `CORS_ALLOWED_ORIGINS` (lista separada por vírgula; `*` é descartado; padrão
`http://localhost:5173,https://pairup-flax.vercel.app`). Métodos GET/POST/PUT/DELETE/
OPTIONS/PATCH, headers `Authorization`, `Content-Type`, `Accept`, **sem credenciais** (o front
usa `credentials: 'omit'`), cache de preflight 1 h.

### CloudflareR2Config
`shared/config/CloudflareR2Config.java` — cria os clientes AWS SDK v2 apontando para o endpoint
S3-compatível do Cloudflare R2. Lê `cloudflare.r2.endpoint`, `access-key`, `secret-key`
(`:18-25`); falha a inicialização se ausentes.

| Método | Descrição |
|---|---|
| `public S3Client s3Client()` (`:27-38`) | Cliente S3 com `endpointOverride`, credenciais estáticas, região `auto` e *path-style access*. Usado por `ImagemService` para `putObject`/`deleteObject`. |
| `public S3Presigner s3Presigner()` (`:40-48`) | Gerador de URLs pré-assinadas (mesmo endpoint/credenciais). Usado por `ImagemService.gerarUrlPresignada`. Não define path-style (diferente do `S3Client`). |

### OpenAPIConfig
`shared/config/OpenAPIConfig.java` — metadados do Swagger.

| Método | Descrição |
|---|---|
| `public OpenAPI customOpenAPI()` (`:13-26`) | Título "PairUp API", versão 1.0.0, contato e licença. Não declara esquema de segurança Bearer, então o Swagger UI não oferece botão "Authorize" para JWT. |

### OAuth2SuccessHandler
`shared/config/OAuth2SuccessHandler.java` — `@Component` que estende
`SimpleUrlAuthenticationSuccessHandler`; executado quando o login Google termina com sucesso.
Dependências (por campo): `UserRepository`, `PessoaRepository`, `JwtService`; propriedade
`FRONTEND_URL` (default `http://localhost:5173`).

**`public void onAuthenticationSuccess(HttpServletRequest, HttpServletResponse, Authentication)`**
- Invalida a sessão HTTP usada só para o `state` do OAuth2.
- Exige `email` presente e `email_verified=true`; senão → `/?erro=email_nao_verificado`.
- Normaliza o e-mail (trim + minúsculas).
- Conta existente com `provider` diferente de `google` → `/?erro=conta_com_senha`, sem vincular.
- Cria a conta Google e uma `Pessoa` mínima, se não existirem (R12/R31 continuam).
- Redireciona para `{FRONTEND_URL}/auth/callback#token=<jwt>` — token no **fragmento**, que não
  vai para o servidor do front, logs de proxy nem header `Referer`.

---

## Segurança

### JwtFilter
`shared/security/JwtFilter.java` — `OncePerRequestFilter` registrado no `SecurityConfig`.
Autentica cada requisição a partir do header `Authorization`. Dependências: `JwtService`,
`UserDetailsService` (implementado por `CustomUserDetailsService`).

- `PUBLIC_ROUTES`: `POST /users`, `POST /users/login`, `GET /tags`, `GET /tags/ativas`,
  `GET /swagger-ui.html`, `GET /swagger-ui/**`, `GET /v3/api-docs/**`, `GET /api-docs`,
  `GET /api-docs/**`, `GET /oauth2/**`, `GET|POST /login/oauth2/**`. Preflight `OPTIONS`
  também passa sem token.

**`protected void doFilterInternal(...)`**
- Rota pública → segue sem autenticar. Senão: sem `Bearer` → 401 "Token não fornecido";
  token inválido, expirado, de outro emissor ou de conta apagada → 401 "Token inválido ou
  expirado".
- Só a autenticação fica dentro do `try`; o `filterChain.doFilter` roda **fora** dele. Antes,
  qualquer exceção do controller virava "401 Token inválido".

| Método privado | Descrição |
|---|---|
| `isPublicRoute(String method, String path)` | Método case-insensitive. Padrões `/**` viram prefixo **com a barra** (`/swagger-ui/**` → `/swagger-ui/`), então `/oauth2qualquercoisa` não passa mais. Demais: igualdade exata. |
| `sendUnauthorizedResponse(HttpServletResponse, String)` | 401 + `{"erro": "..."}` (mensagens fixas). |

### JwtService
`shared/security/JwtService.java` — gera e valida JWT HS256 com a chave `jwt.secret`
(`JWT_SECRET`). Expiração fixa de 15 min, emissor `pairup-api`, tolerância de relógio de 30 s.

| Método | O que faz | Retorno / Erros |
|---|---|---|
| `void init()` (`@PostConstruct`) | Monta a chave HMAC uma vez, em UTF-8. | `IllegalStateException` na **subida** se o segredo for nulo ou tiver menos de 32 bytes (R41 resolvido). |
| `public String generateToken(String userId)` | Token com `jti` aleatório, `iss=pairup-api`, `sub=userId`, `iat`, `exp=agora+15min`. | Token compacto. |
| `private Claims parse(String token)` | Valida assinatura, expiração e emissor (`requireIssuer`). | `Claims` ou `null`. |
| `public String extractUserId(String token)` | `subject` do token válido. | `null` se inválido (nunca lança). |
| `public boolean isTokenValid(String token)` | Token válido? | `true`/`false`. |
| `public boolean isTokenValid(String token, UserDetails)` | Token válido e `subject` igual ao `username`. | `true`/`false`. |
| `public Date getExpirationDate(String token)` | Lê `exp`. | `Date` ou `null`. |

### RateLimitFilter
`shared/security/RateLimitFilter.java` — `OncePerRequestFilter`, roda antes do `JwtFilter`.
Janela fixa de 1 minuto por IP (`request.getRemoteAddr()`, já resolvido pelo Tomcat a partir
do `X-Forwarded-For` confiável), em memória.

- `POST /users/login` e `POST /users`: `seguranca.rate-limit.auth-por-minuto` (padrão 10,
  `RATE_LIMIT_AUTH`).
- Demais rotas: `seguranca.rate-limit.geral-por-minuto` (padrão 300, `RATE_LIMIT_GERAL`).
- Excedeu → 429, `Retry-After: 60`, `{"erro": "Muitas requisições..."}`.
- `seguranca.rate-limit.habilitado=false` desliga (usado nos testes). Limite de 100 mil
  chaves; acima disso as janelas expiradas são removidas. Com várias réplicas, cada uma conta
  separado.

### LoginAttemptService
`shared/security/LoginAttemptService.java` — bloqueio por **e-mail**, complementar ao limite
por IP.

| Método | Descrição |
|---|---|
| `boolean estaBloqueado(String email)` | `true` durante o bloqueio. |
| `void registrarFalha(String email)` | Conta falhas seguidas; na 5ª (`seguranca.login.max-falhas`) bloqueia por 15 min (`bloqueio-minutos`). Falhas mais antigas que a janela não acumulam. |
| `void registrarSucesso(String email)` | Zera o contador. |

### CustomUserDetailsService
`shared/security/CustomUserDetailsService.java` — implementa `UserDetailsService` para o Spring
Security. Dependência: `UserRepository`.

**`public UserDetails loadUserByUsername(String userId)`** (`:22-43`)
- Recebe na verdade o **`User.id`** (não e-mail). Busca o `User`; monta um `UserDetails` com
  `username = User.id`, senha (ou `""` para contas Google) e **lista de authorities vazia**
  (por isso nenhuma regra `hasRole` é satisfeita — R15).
- Erros: `UsernameNotFoundException` se o id não existe (o `JwtFilter` converte em 401).

### SecurityUtils
`shared/security/SecurityUtils.java` — utilitário estático (classe `final`, construtor privado).

| Método | Descrição |
|---|---|
| `public static String getCurrentUserId()` | `UserDetails.getUsername()` (= `User.id`) do `SecurityContextHolder`, ou `null`. |
| `public static String getUserIdOrThrow()` | Igual, mas lança `ResponseStatusException(401)` se `null` (antes era `RuntimeException`, que virava 500). |
| `public static String paraLog(String valor)` | Troca `\r`, `\n` e `\t` por `_` e corta em 100 caracteres, para valores do cliente irem ao log sem forjar linhas. |

---

## Controllers

Convenção: "Auth" = exige `Authorization: Bearer`. Respostas de erro de autenticação (401) são
geradas pelo `JwtFilter` antes de o controller rodar.

### UserController
`user/UserController.java` — `@RequestMapping("/users")`. Conta do usuário. Dependências:
`UserService`, `JwtService`, `LoginAttemptService`.

**`GET /users/me` — Auth — `getMe()`**
- Devolve o `User` logado com `password=null`; 404 se o usuário não existe.

**`POST /users/login` — público — `login(@RequestBody @Valid LoginRequestDTO)`**
- Corpo `{"email", "password"}` (obrigatórios; e-mail até 254, senha até 128 caracteres).
- E-mail bloqueado pelo `LoginAttemptService` → 429 `{"erro": "Muitas tentativas..."}`.
- Sucesso → 200 `{"token", "userId", "email"}` e zera as falhas. Falha → 401
  `{"erro": "Credenciais inválidas."}` e conta uma falha. O e-mail vai ao log pelo
  `SecurityUtils.paraLog`.

**`POST /users` — público — `createUser(@RequestBody @Valid UserCreateDTO)`**
- Corpo `{nome?, email, password}`: nome até 100, e-mail válido até 254, senha de **8 a 72**.
  O DTO não tem `id` nem `provider` (R1 resolvido).
- 200 com o `User` criado (sem senha); e-mail já usado → 409; validação → 400.

**`PUT /users/me` — Auth — `updateMe(@RequestBody @Valid UserUpdateDTO)`**
- Corpo `{nome?, email?, senhaAtual?}`. Trocar o e-mail exige `senhaAtual` (403 se errada);
  e-mail de outra conta → 409; conta Google → 400. Erros de negócio saem pelo
  `GlobalExceptionHandler` (não viram 401).

**`DELETE /users/me` — Auth — `deleteMe()`**
- Apaga o perfil (em cascata) e a conta → 204.

### PessoaController
`pessoa/PessoaController.java` — `@RequestMapping("/pessoas")`. Perfil de paquera. Todas as
rotas Auth. Dependências: `PessoaService`, `UserRepository`. Erros próprios usam a chave
`"error"` (em inglês). Erro de banco (`DataAccessException`) → 409 com mensagem genérica, sem
detalhes internos.

**`GET /pessoas` — `getAll(Interesse interesse, Genero genero, Pageable pageable)`** (`:32-49`)
- Query params opcionais `interesse` (`HOMENS|MULHERES|TODOS|NAO_DEFINIDO`), `genero`
  (`MASCULINO|FEMININO|NAO_BINARIO|OUTRO`), `page`, `size` (default 20), `sort`.
- Retorna `Page<PessoaPerfilDTO>` só de perfis `ativo=true`, **incluindo o próprio
  solicitante**; `cienciaResponsabilidade` vem sempre `false` (R39). Valor de enum inválido
  → 400 "Requisição inválida" (desde 2026-11-16).
- Chama `PessoaService.getAllPerfisWithFilters`.

**`GET /pessoas/{id}/perfil` — `getPerfilById(String id)`** (`:51-67`)
- `id` = `Pessoa.id`. 200 com `PessoaPerfilDTO` ou 404. Não filtra perfis inativos.
- Chama `PessoaService.getPerfilById`.

**`GET /pessoas/{id}/redes-sociais` — `getRedesSociaisById(String id)`** (`:69-77`)
- Stub: sempre 403 sem corpo. O caminho correto é `GET /matches/{matchId}/redes-sociais`.

**`POST /pessoas` — `create(@RequestBody @Valid PessoaRequestDTO)`**
- Corpo: `nome`, `curso`, `dataNasc` (`yyyy-MM-dd`), `genero`, `interesse`, `descricao`,
  `instagram`, `whatsapp`, `telegram`, `tags` (IDs), `cienciaResponsabilidade`. O campo `email`
  é **ignorado**: o perfil usa o e-mail da conta autenticada (antes dava para "reservar" o
  e-mail de outra pessoa).
- Regras: `cienciaResponsabilidade` precisa ser `true` (400); se o usuário já tem perfil → 409;
  o service exige 18+ anos e data de nascimento a no máximo 100 anos (violações → 400).
- Retorna 200 com a **entidade** `Pessoa` criada (inclui `usuarioId`).

**`GET /pessoas/me` — `getMyProfile()`** (`:130-147`)
- 200 com `PessoaCompletaDTO` (inclui redes sociais e imagens) ou 404.
- Chama `PessoaService.getPessoaCompletaByUsuarioId`.

**`PUT /pessoas/me` — `updateMe(@RequestBody @Valid PessoaRequestDTO)`**
- Merge parcial: só aplica campos não nulos. String vazia apaga uma rede social; lista vazia
  remove as tags. Não altera `email` nem `cienciaResponsabilidade`.
- 200 com a entidade atualizada; 404 sem perfil; `RuntimeException` do service (ex.: menor de
  idade) → 400.

**`PATCH /pessoas/me/ciencia-responsabilidade` — `marcarCienciaResponsabilidade()`** (`:189-213`)
- Marca `cienciaResponsabilidade=true`. 200 com a entidade; 404 sem perfil; 400 em erro.
- Chama `PessoaService.marcarCienciaResponsabilidade`.

**`DELETE /pessoas/me` — `deleteMe()`** (`:215-230`)
- Apaga o perfil (se existir) e, em cascata, fotos, likes, dislikes e matches → sempre 204.
- Chama `PessoaService.delete`.

### LikeController
`match/LikeController.java` — `@RequestMapping("likes")`. Todas as rotas Auth.
Dependências: `LikeService`, `PessoaService`.

Todas as rotas convertem o `User.id` do JWT em `Pessoa.id` com
`PessoaService.getPessoaIdOrThrow` (sem perfil → 400).

**`POST /likes?destinoId=<Pessoa.id>` — `darLike(String destinoId)`** (`:59-83`)
- `destinoId` vazio, com mais de 64 caracteres ou de perfil inexistente → 400 "Perfil
  inválido" (R38 resolvido). Auto-like → 400. Já curtiu → 400 (vindo do service). Exige
  cadastro completo (403).
- Retorna 200 `{"match": true|false, "message": "..."}`.
- Chama `PessoaService.getPessoaIdOrThrow`, `LikeService.darLike`.

**`GET /likes/dados` — `listarLikesDados(Pageable)`** (`:90-103`)
- `Page<Like>` dos likes ativos que eu dei (default 20 por página).
- Chama `LikeService.listarLikesDados`.

**`GET /likes/recebidos` — `listarLikesRecebidos(Pageable)`** (`:110-123`)
- `Page<Like>` dos likes ativos que recebi.
- Chama `LikeService.listarLikesRecebidos`.

### MatchController
`match/MatchController.java` — `@RequestMapping("matches")`. Todas as rotas Auth.
Dependências: `MatchService`, `PessoaService`.

Mesma conversão de `LikeController` (`PessoaService.getPessoaIdOrThrow`).

**`GET /matches/meus` — `listarMeusMatches(Pageable)`** (`:55-68`)
- `Page<Match>` com os matches ativos em que participo (`pessoaId1` ou `pessoaId2`).
- Chama `MatchService.listarMeusMatches`.

**`GET /matches/{matchId}/redes-sociais` — `getRedesSociaisDoMatch(String matchId)`** (`:75-113`)
- 404 se o match não existe; 403 (texto puro) se inativo ou se não participo; 404 se a outra
  pessoa não existe mais; 200 com `PessoaRedesSociaisDTO` da outra pessoa.
- Chama `MatchService.findById`, `PessoaService.getRedesSociaisById`.

**`PUT /matches/desfazer/{matchId}` — `desfazerMatch(String matchId)`** (`:119-148`)
- 404 se não existe; 403 se não participo; 200 texto "Match desfeito com sucesso." (soft-delete).
- Chama `MatchService.findById`, `MatchService.desfazerMatch`.

### TagController
`tag/TagController.java` — `@RequestMapping("/tags")`. Dependência: `TagService`.

| Endpoint | Método Java | Auth | Comportamento |
|---|---|---|---|
| `GET /tags` | `getAll()` (`:32-36`) | público | Lista todas as tags (`List<Tag>`). |
| `GET /tags/ativas` | `getAtivas()` (`:42-46`) | público | Lista tags com `ativa=true`. |
| `POST /tags` | `create(@RequestBody @Valid TagRequestDTO)` | `ROLE_ADMIN` | Corpo `{nome, descricao?, categoria, ativa}` (nome e categoria até 50, descrição até 200; `ativa` padrão `true`). O DTO não tem `id`, então não sobrescreve tag existente (R33 resolvido). Usuário comum → 403; sem token → 401. |
| `DELETE /tags/{id}` | `delete(String id)` | `ROLE_ADMIN` | Nunca executa: o Spring Security nega antes com 403 JSON. |

### ImagemController
`imagem/ImagemController.java` — `@RequestMapping("/api/imagens")`. Todas Auth.
Dependências: `ImagemService`, `PessoaService`. Sem anotações Swagger. Respostas usam
`ImagemUploadResponseDTO` (`sucesso`, `mensagem`, ...).

**`POST /api/imagens/upload` — `uploadImagem(@Valid @ModelAttribute ImagemUploadDTO)`** (`:27-69`)
- Requisição `multipart/form-data` com campos `file` (imagem) e `perfil` (`true`/`false`).
- 200 com `{id, url: "/api/imagens/proxy/<publicId>", nomeOriginal, perfil, tamanhoBytes, sucesso: true, mensagem}`.
- 400 sem perfil, arquivo vazio, > 5 MB, conteúdo que não é JPEG/PNG/WebP, resolução acima
  de 8000 px por lado ou limite de 6 fotos atingido (`IllegalArgumentException`); 403
  "Acesso negado" em `SecurityException`; 500 com mensagem genérica em outros erros (a
  mensagem da exceção não vai mais ao cliente).
- Chama `PessoaService.findByUsuarioId`, `ImagemService.salvarImagem`.

**`DELETE /api/imagens/{imagemId}` — `deletarImagem(String imagemId)`** (`:71-108`)
- 200 `{sucesso: true, mensagem: "Imagem deletada com sucesso"}`; 400 sem perfil ou se for a
  última imagem ativa; 404 imagem inexistente; 403 se não for o dono; 500 outros.
- Chama `ImagemService.deletarImagem`.

**`GET /api/imagens/minhas-imagens` — `listarMinhasImagens()`** (`:110-145`)
- 200 com lista de `{id, url (proxy), perfil, tamanhoBytes, sucesso}`; lista vazia se não há
  perfil; 500 sem corpo em erro.
- Chama `ImagemService.listarImagensAtivasPorUsuario`.

### ImagemProxyController
`imagem/ImagemProxyController.java` — `@RequestMapping("/api/imagens/proxy")`. Auth.
Dependências: `ImagemService`, `PessoaService`, `MatchService`, `AuditLogService`.

**`GET /api/imagens/proxy/**` — `proxyImagemHandler(HttpServletRequest)`** (`:32-49`)
- Extrai o `publicId` do caminho (tudo depois de `/api/imagens/proxy/`, ex.
  `tinder_ufs/<pessoaId>/<uuid>.jpg`). Se parecer path traversal → registra violação e 400.
- Delega para `proxyImagem`.

**`private ResponseEntity<Void> proxyImagem(String publicId)`** (`:51-105`)
- 400 se o formato do id for inválido; 404 se a imagem não existe ou não tem dono; 410 se
  inativa; 403 se o solicitante não tem perfil ou não é dono nem tem match ativo com o dono;
  sucesso → **302** com `Location` = URL pré-assinada (15 min) e `Cache-Control: private,
  max-age=600`. Qualquer outra exceção → 500. O dono é lido por `Imagem.idDoDono()`, sem
  carregar a `Pessoa`; o `publicId` só vai ao log já sanitizado.
- Chama `ImagemService.findByPublicId`, `PessoaService.findByUsuarioId`,
  `MatchService.existeMatchAtivo`, `AuditLogService.logImageAccess`,
  `ImagemService.gerarUrlPresignada`.

| Privado | Descrição |
|---|---|
| `isPathTraversal(String)` (`:107-125`) | `true` se contém `..`, `./`, `\`, `%2e`, nomes de arquivos sensíveis (`passwd`, `shadow`, `.env`, `web.xml`...). |
| `isValidPublicId(String)` (`:127-132`) | Não vazio, sem traversal, casa com `PUBLIC_ID_PATTERN` (`:28-30`) ou com o padrão `tinder_ufs/...` (redundante). |

---

## Services

### UserService
`user/UserService.java` — regras da conta. Dependências: `UserRepository`, `PasswordEncoder`,
`PessoaService`. No construtor gera um hash BCrypt falso usado no login.

| Método | O que faz | Retorno / Erros |
|---|---|---|
| `public static String normalizarEmail(String)` | `trim` + minúsculas. | `String` ou `null`. |
| `public User findById(String id)` | Busca por id (`null` → `null`). | `User` ou `null`. |
| `private Optional<User> buscarPorEmail(String)` | Busca pelo e-mail normalizado e, se não achar, pelo original (contas antigas com maiúsculas). | `Optional<User>`. |
| `public User createUser(UserCreateDTO)` | Recusa e-mail já usado; grava e-mail normalizado e senha em BCrypt. | `User`; `IllegalArgumentException("Email já cadastrado")` (também em `DuplicateKeyException`). |
| `public User updateUser(String id, UserUpdateDTO)` | Atualiza nome; troca de e-mail exige senha atual. | `User` ou `null`; `ResponseStatusException` 400/403/409. |
| `public void deleteUser(String id)` | Apaga o perfil (cascata) e a conta. | — |
| `public User login(String email, String password)` | Sempre roda **um** BCrypt (contra o hash falso quando o e-mail não existe ou a conta é Google), para o tempo de resposta não revelar quais e-mails têm conta. | `User`; `BadCredentialsException` em qualquer falha. |

### PessoaService
`pessoa/PessoaService.java` — regras do perfil e montagem de DTOs. Dependências:
`PessoaRepository`, `ImagemRepository`.

| Método | O que faz | Retorno / Erros |
|---|---|---|
| `public PessoaCompletaDTO getPessoaCompletaByUsuarioId(String usuarioId)` (`:31-36`) | Perfil completo do dono da conta. | DTO ou `null`. |
| `public Page<PessoaPerfilDTO> getAllPerfisWithFilters(Interesse, Genero, Pageable)` (`:40-75`) | Escolhe a query por combinação de filtros (só `ativo=true`), busca as imagens ativas da página **em uma única query** e monta os DTOs com `imagens` e `fotoPerfilUrl`. | `Page` de DTOs (página vazia se não houver resultados). O N+1 (R13) foi resolvido em 2026-10-22. |
| `public Pessoa create(Pessoa pessoa)` (`:79-96`) | Valida 18+ anos, e-mail de perfil único, um perfil por `usuarioId`; salva. | `Pessoa`. `RuntimeException` com mensagem amigável em cada violação. |
| `public Pessoa update(Pessoa pessoa)` (`:98-119`) | Recarrega do banco, impede desmarcar termos, revalida idade se `dataNasc` mudou, copia propriedades (exceto `id`, `usuarioId`, `cienciaResponsabilidade`) e salva. | `Pessoa` ou `null` se não existe. `RuntimeException`. |
| `public Pessoa marcarCienciaResponsabilidade(String id)` (`:121-128`) | Seta `cienciaResponsabilidade=true`. | `Pessoa`. `RuntimeException("Pessoa não encontrada")`. |
| `public PessoaPerfilDTO getPerfilById(String id)` (`:132-136`) | Perfil público por `Pessoa.id`. | DTO ou `null`. |
| `public PessoaRedesSociaisDTO getRedesSociaisById(String id)` (`:138-142`) | Só as redes sociais. Usado por `MatchController`. | DTO ou `null`. |
| `public Pessoa findById(String id)` (`:147-149`) | Busca por `Pessoa.id`. | `Pessoa` ou `null`. |
| `public Pessoa findByUsuarioId(String usuarioId)` (`:154-156`) | Busca o perfil da conta (`User.id`). Usado por quase todos os controllers. | `Pessoa` ou `null`. |
| `public String getPessoaIdOrThrow(String usuarioId)` (`:163-170`) | `Pessoa.id` do perfil da conta. Usado por `LikeController` e `MatchController` (antes era o `resolvePessoaId` copiado nos dois). | `String`; `ResponseStatusException(400, "Perfil não encontrado...")` se não há perfil. |
| `public void delete(String id)` (`:172-178`) | Apaga o perfil se existir. | — |
| `private convertToCompletaDTO(Pessoa)` (`:182-190`) | `preencherPerfil` + `cienciaResponsabilidade` + redes sociais. | — |
| `private convertToPerfilDTO(Pessoa, List<Imagem>)` (`:192-194`) | `preencherPerfil` com as imagens recebidas (sem redes sociais, mas com `email` e `imagens` como entidades — R2). **Não preenche `cienciaResponsabilidade`**: o campo fica no default `false` em `GET /pessoas` e `GET /pessoas/{id}/perfil`, mesmo para quem aceitou os termos (R39). | — |
| `private <T extends PessoaPerfilDTO> T preencherPerfil(T, Pessoa, List<Imagem>)` (`:197-215`) | Campos comuns aos dois DTOs: dados básicos, nomes das tags, `imagens` e `fotoPerfilUrl` (a imagem com `perfil=true`). | o próprio `dto`. |
| `private convertToRedesSociaisDTO(Pessoa)` (`:217-224`) | Monta `PessoaRedesSociaisDTO`. | — |

### LikeService
`match/LikeService.java` — likes e criação de match. Dependências: `LikeRepository`,
`MatchRepository`.

| Método | O que faz | Retorno / Erros |
|---|---|---|
| `public Page<Like> listarLikesDados(String pessoaId, Pageable)` (`:26-28`) | Likes ativos com `pessoaOrigemId = pessoaId`. | `Page<Like>`. |
| `public Page<Like> listarLikesRecebidos(String pessoaId, Pageable)` (`:33-35`) | Likes ativos com `pessoaDestinoId = pessoaId`. | `Page<Like>`. |
| `public Map<String,Object> darLike(String origemId, String destinoId)` (`:89-111`) | Se já existe like ativo → erro; cria ou reativa o like; se o outro lado já curtiu, cria o match (se ainda não existir). `@Transactional` sem efeito (R3). | `{"match": boolean, "message": String}`. `ResponseStatusException(400, "Você já curtiu essa pessoa")`. |
| `private jaDeuLike` (`:37-42`) / `existeLikeReverso` (`:44-49`) | Consultam like ativo em um sentido ou no outro. | `boolean`. |
| `private criarOuAtivarLike` (`:51-68`) | Insere like ou reativa um inativo. | — |
| `private criarMatchSeNaoExistir(String p1, String p2)` (`:70-82`) | Procura match em ambas as ordens (ativo ou não); se nenhum, cria ativo. | — |

### MatchService
`match/MatchService.java` — consultas e alteração de matches. Dependência: `MatchRepository`.

| Método | O que faz | Retorno |
|---|---|---|
| `public Page<Match> listarMeusMatches(String pessoaId, Pageable)` (`:21-23`) | Matches ativos onde a pessoa é `pessoaId1` ou `pessoaId2`. | `Page<Match>`. |
| `public Match findById(String id)` (`:25-27`) | Busca por id. | `Match` ou `null`. |
| `public void desfazerMatch(String id)` (`:29-35`) | `ativo=false` se existir (silencioso se não). | — |
| `public boolean existeMatchAtivo(String pessoaId1, String pessoaId2)` (`:37-40`) | Verifica nas duas ordens. Usado pelo proxy de imagens. | `boolean`. |

### TagService
`tag/TagService.java` — repasse ao `TagRepository`.

| Método | Retorno |
|---|---|
| `public List<Tag> getAll()` (`:14-16`) | Todas as tags. |
| `public Tag create(Tag tag)` (`:18-20`) | Tag salva (sem checar duplicidade de nome). |
| `public List<Tag> getAtivas()` (`:22-24`) | Tags `ativa=true`. |

### ImagemService
`imagem/ImagemService.java` — armazenamento de imagens no R2 e metadados no Mongo.
Dependências: `ImagemRepository`, `PessoaRepository`, `ImageCompressionUtil`, `S3Client`,
`S3Presigner`; propriedades `cloudflare.r2.bucket` e `cloudflare.r2.public-url` (`:35-39`).

**`public Imagem salvarImagem(MultipartFile file, String pessoaId, boolean isPerfil) throws IOException`** (`:41-98`)
- Valida o arquivo; carrega a `Pessoa`; comprime (se falhar, usa os bytes originais); envia ao
  bucket com chave `tinder_ufs/{pessoaId}/{uuid}.{ext}`; se `isPerfil`, desmarca a foto de
  perfil anterior; sem principal ativa, a nova assume o lugar; grava `Imagem` com `id` UUID, `url` (pública ou proxy), `publicId` = chave,
  tamanho e MIME.
- Erros: `IllegalArgumentException` (arquivo vazio, não-imagem, > 5 MB, pessoa inexistente);
  exceções do SDK S3 propagam (o controller devolve 500). Se o banco falhar depois do upload, o
  objeto fica órfão no bucket.

**`public void deletarImagem(String imagemId, String pessoaId)`** (`:100-130`)
- Confere existência e dono; impede apagar a última imagem ativa; tenta apagar do bucket
  (erro só é logado); marca `ativa=false` e `perfil=false`; se era a principal, promove a
  primeira foto ativa.
- Erros: `IllegalArgumentException` (não existe), `SecurityException` (não é dono),
  `IllegalStateException` (última imagem).

| Outros métodos | O que faz | Retorno / Erros |
|---|---|---|
| `public String gerarUrlPresignada(String publicId)` | URL GET pré-assinada válida por 15 min (`VALIDADE_URL`). | `String`. |
| `public Imagem findByPublicId(String publicId)` (`:141-145`) | Busca pela chave. | `Imagem`; `IllegalArgumentException` se não existe. |
| `public List<Imagem> listarImagensAtivasPorUsuario(String pessoaId)` (`:147-150`) | Imagens ativas da pessoa (o nome diz "usuario" mas recebe `Pessoa.id`). | Lista. |
| `private resolverUrl(String key)` (`:152-157`) | `public-url/key` se configurado; senão `/api/imagens/proxy/key`. | — |
| `private validarArquivo(MultipartFile)` | Não vazio e ≤ 5 MB. O tipo é checado pelo conteúdo em `ImageCompressionUtil.processar`. | `IllegalArgumentException`. |

`salvarImagem` (2026-11-16): recusa a 7ª foto ativa (`MAX_FOTOS = 6`); chama
`ImageCompressionUtil.processar` **antes** de enviar ao R2 e usa a extensão e o Content-Type
que ele devolve (nunca os do cliente); grava no R2 com `Content-Disposition: inline` e
`Cache-Control: private, max-age=86400`. Não existe mais fallback para os bytes originais.

### AuditLogService
`imagem/AuditLogService.java` — "auditoria" que apenas escreve no log (nada é persistido).

| Método | Descrição |
|---|---|
| `public void logSecurityViolation(String userId, String action)` (`:14-17`) | `log.error("SECURITY VIOLATION ...")`. Chamado pelo proxy ao detectar path traversal. |
| `public void logImageAccess(String userId, String imageId, String action)` (`:19-22`) | `log.info("AUDIT ...")`. Chamado a cada acesso autorizado ao proxy. |

---

## Repositórios

Todas estendem `MongoRepository<Entidade, String>` — já herdam `findById`, `findAll`, `save`,
`deleteById`, `count` etc. Os métodos abaixo são **queries derivadas do nome**: o Spring Data
gera a consulta a partir do nome do método.

### UserRepository
`user/UserRepository.java`
- `Optional<User> findByEmail(String email)` (`:9`) — usado em cadastro, login e OAuth.

### PessoaRepository
`pessoa/PessoaRepository.java`

| Método | Consulta gerada | Usado por |
|---|---|---|
| `Page<Pessoa> findByAtivoTrue(Pageable)` (`:13`) | `ativo = true` | `PessoaService.getAllPerfisWithFilters` |
| `Page<Pessoa> findByAtivoTrueAndInteresse(Interesse, Pageable)` (`:15`) | + `interesse` | idem |
| `Page<Pessoa> findByAtivoTrueAndGenero(Genero, Pageable)` (`:17`) | + `genero` | idem |
| `Page<Pessoa> findByAtivoTrueAndInteresseAndGenero(Interesse, Genero, Pageable)` (`:19`) | + ambos | idem |
| `Optional<Pessoa> findByUsuarioId(String)` (`:21`) | `usuarioId = ?` | `PessoaService.findByUsuarioId`, `OAuth2SuccessHandler` |
| `Optional<Pessoa> findByEmail(String)` (`:23`) | `email = ?` | **ninguém** |
| `boolean existsByEmail(String)` (`:25`) | existe por e-mail | `PessoaService.create` |
| `boolean existsByUsuarioId(String)` (`:27`) | existe por usuário | `PessoaService.create` |

### LikeRepository
`match/LikeRepository.java`
- `Page<Like> findByPessoaOrigemIdAndAtivoTrue(String, Pageable)` (`:15`) — likes dados.
- `Page<Like> findByPessoaDestinoIdAndAtivoTrue(String, Pageable)` (`:17`) — likes recebidos.
- `Optional<Like> findByPessoaOrigemIdAndPessoaDestinoId(String, String)` (`:19`) — like de A para B.

### MatchRepository
`match/MatchRepository.java`
- `Page<Match> findMeusMatchesAtivos(String pessoaId1, String pessoaId2, Pageable)` (`:17-18`) —
  `@Query` explícita: `ativo = true AND (pessoaId1 = ?0 OR pessoaId2 = ?1)`; o service passa o
  mesmo id nos dois parâmetros.
- `Optional<Match> findByPessoaId1AndPessoaId2(String, String)` (`:20`) — match em uma ordem.
- `boolean existsByPessoaId1AndPessoaId2AndAtivoTrue(String, String)` (`:22`) — match ativo em uma ordem.

### TagRepository
`tag/TagRepository.java`
- `List<Tag> findByAtivaTrue()` (`:9`).

### ImagemRepository
`imagem/ImagemRepository.java` — `pessoa` é um `@DBRef`; os métodos com `PessoaId`
consultam o id dentro da referência.

| Método | Usado por |
|---|---|
| `Optional<Imagem> findByPublicId(String)` (`:13`) | `ImagemService.findByPublicId` |
| `List<Imagem> findByPessoaIdAndAtivaTrue(String)` (`:15`) | `ImagemService.listarImagensAtivasPorUsuario` |
| `List<Imagem> findByPessoaIdAndPerfilTrue(String)` (`:17`) | `ImagemService.salvarImagem` (não filtra `ativa`: desmarca todas as marcadas) |
| `boolean existsByPessoaIdAndPerfilTrueAndAtivaTrue(String)` | `ImagemService.salvarImagem` |
| `long countByPessoaIdAndAtivaTrue(String)` (`:19`) | `ImagemService.deletarImagem` |
| `List<Imagem> findByPessoaAndAtivaTrue(Pessoa)` (`:21`) | `PessoaService` (conversores) |
| `List<Imagem> findByPessoaInAndAtivaTrue(List<Pessoa>)` (`:23`) | `PessoaService.getAllPerfisWithFilters` |

---

## Entidades

Documentos MongoDB (`@Document`). `@Indexed(unique = true)`/`@CompoundIndex` **só viram índices
reais se `auto-index-creation` estiver ativo**, o que não acontece hoje (R4).

### User
`user/User.java` — coleção `users`. Campos: `id`, `nome`, `email` (`@Email @NotBlank`,
único declarado), `password` (hash BCrypt; `null` para contas Google), `provider` (`"google"`
ou `null`). Construtores: vazio e `(nome, email, password)` (`:28-32`). Também é usada
diretamente como corpo de `POST /users` e `PUT /users/me` (R1).

### Pessoa
`pessoa/Pessoa.java` — coleção `pessoa`. Campos: `id`, `nome` (`@NotBlank`), `curso`,
`dataNasc` (`@Past`, `yyyy-MM-dd`), `email` (`@Email @NotBlank`, único declarado), `genero`
(`@NotNull`), `interesse` (`@NotNull`), `descricao`, `ativo` (default `true`),
`cienciaResponsabilidade` (default `false`), `instagram`, `whatsapp`, `telegram`, `tags`
(`@DBRef List<Tag>`, nunca preenchido), `usuarioId` (= `User.id`). As anotações de validação
**não são aplicadas** em nenhum fluxo (a entidade nunca é recebida com `@Valid`).

| Método | Descrição |
|---|---|
| `public Pessoa()` / `public Pessoa(String nome, String curso, LocalDate dataNasc, String email)` (`:62-69`) | Construtores. O segundo só é usado em testes. |
| `public boolean isMaiorDeIdade()` (`:72-75`) | `true` se `dataNasc` ≥ 18 anos atrás; `false` se `dataNasc` é `null`. Usado por `PessoaService.create/update`. Aparece como `"maiorDeIdade"` no JSON (R26). |

### Like
`match/Like.java` — coleção `like`. Campos `id`, `pessoaOrigemId`, `pessoaDestinoId` (ambos
`Pessoa.id`), `ativo`. Índice composto único declarado `unique_like` (`:11-13`).

### Match
`match/Match.java` — coleção `match`. Campos `id`, `pessoaId1`, `pessoaId2` (`Pessoa.id`),
`ativo`. Sem índices.

### Tag
`tag/Tag.java` — coleção `tag`. Campos `id`, `nome` (`@NotBlank`), `descricao`, `categoria`
(`@NotBlank`), `ativa`. Único construtor `(nome, descricao, categoria, ativa)` (`:20-25`) — o
Jackson/Spring Data o usam via nomes de parâmetros.

### Imagem
`imagem/Imagem.java` — coleção `imagem`. Campos: `id` (UUID atribuído pelo service), `pessoa`
(`@DBRef(lazy = true) Pessoa`, com `@JsonIgnore`; `idDoDono()` lê o id sem consultar), `url`, `publicId` (chave no bucket,
único declarado), `folderPath`, `perfil`, `dataUpload`, `tamanhoBytes`, `mimeType`, `ativa`.
Índice composto `pessoa_perfil_idx` (`:16`). Construtor completo (`:47-58`) preenche
`dataUpload = now()` e `ativa = true`.

### Genero / Interesse
`pessoa/Genero.java`: `MASCULINO`, `FEMININO`, `NAO_BINARIO`, `OUTRO`.
`pessoa/Interesse.java`: `HOMENS`, `MULHERES`, `TODOS`, `NAO_DEFINIDO`. Usados como filtro
em `GET /pessoas` e como campos de `Pessoa`/DTOs (o JSON usa o nome exato da constante).

---

## DTOs

### LoginRequestDTO
`user/LoginRequestDTO.java` — `{email, password}` para `POST /users/login`. Ambos
`@NotBlank`; e-mail até 254 e senha até 128 caracteres.

### UserCreateDTO / UserUpdateDTO
`user/UserCreateDTO.java` — `{nome (até 100), email (@Email, até 254), password (8 a 72)}`.
`user/UserUpdateDTO.java` — `{nome (até 100), email (@Email, até 254), senhaAtual (até 128)}`.

### PessoaRequestDTO
`pessoa/PessoaRequestDTO.java` — entrada de `POST /pessoas` e `PUT /pessoas/me`.

| Campo | Validação |
|---|---|
| `nome` | até 100 |
| `curso` | até 150 |
| `dataNasc` | `@Past` |
| `email` | até 254; ignorado pelo controller |
| `descricao` | até 500 |
| `instagram` | vazio ou `@?[A-Za-z0-9._]{1,30}` |
| `whatsapp` | vazio ou 8 a 20 de dígitos, espaço, `(`, `)`, `+`, `-` |
| `telegram` | vazio ou `@?[A-Za-z0-9_]{1,32}` |
| `tags` | até 5 IDs |

As redes sociais aceitam só o usuário/número: o front monta o link com prefixo fixo
(`instagram.com/`, `t.me/`, `wa.me/55`), então nada além de letras, números, ponto e
sublinhado chega ao `href`.

### TagRequestDTO
`tag/TagRequestDTO.java` — entrada de `POST /tags`: `nome` (obrigatório, até 50), `descricao`
(até 200), `categoria` (obrigatória, até 50), `ativa` (padrão `true`). Não tem `id`.

### PessoaPerfilDTO
`pessoa/PessoaPerfilDTO.java` — só **saída** (`GET /pessoas`, `GET /pessoas/{id}/perfil`); a
entrada é `PessoaRequestDTO`. Campos: `id`, `nome`, `curso`, `dataNasc`,
`email`, `genero`, `interesse`, `descricao`, `tags` (nomes), `cienciaResponsabilidade`,
`imagens` (`List<Imagem>` — entidades completas), `fotoPerfilUrl`. Sem validações (R19). Não
tem redes sociais.

### PessoaCompletaDTO
`pessoa/PessoaCompletaDTO.java` — saída de `GET /pessoas/me`. **Herda de `PessoaPerfilDTO`**
(todos os campos do perfil) e acrescenta `instagram`, `whatsapp`, `telegram`.

| Método derivado | Descrição (vira campo no JSON) |
|---|---|
| `public int getQuantidadeGaleria()` (`:28-35`) | Nº de imagens que não são a foto de perfil → `quantidadeGaleria`. |
| `public List<Imagem> getImagensGaleria()` (`:37-43`) | Imagens exceto a de perfil → `imagensGaleria`. |
| `public boolean isPerfilCompleto()` (`:45-52`) | `true` se nome, curso, dataNasc, genero, interesse preenchidos e termos aceitos → `perfilCompleto`. |

### PessoaRedesSociaisDTO
`match/PessoaRedesSociaisDTO.java` — `{id, instagram, whatsapp, telegram}`; saída de
`GET /matches/{id}/redes-sociais`.

### ImagemUploadDTO
`imagem/ImagemUploadDTO.java` — formulário multipart do upload: `file` (`MultipartFile`) e
`perfil` (`boolean`). Sem validações.

### ImagemUploadResponseDTO
`imagem/ImagemUploadResponseDTO.java` — envelope de resposta dos endpoints de imagem: `id`, `url`,
`nomeOriginal`, `perfil`, `tamanhoBytes`, `mensagem`, `sucesso`.

| Método | Descrição |
|---|---|
| `public ImagemUploadResponseDTO(String id, String url)` (`:19-23`) | Construtor de sucesso. Não usado. |
| `public static ImagemUploadResponseDTO fromImagem(Imagem, String nomeOriginal)` (`:25-35`) | Monta resposta de upload a partir da entidade (o controller depois troca a `url` pela de proxy). |
| `public static ImagemUploadResponseDTO error(String)` (`:37-42`) | `sucesso=false` + mensagem. |
| `public static ImagemUploadResponseDTO success(String)` (`:44-49`) | `sucesso=true` + mensagem. |

---

## Tratamento de erros

### GlobalExceptionHandler
`shared/exception/GlobalExceptionHandler.java` — `@RestControllerAdvice` que transforma exceções não
capturadas pelos controllers em JSON `{status, erro, timestamp}`.

| Handler | Exceção | Resposta |
|---|---|---|
| `handleResponseStatus` (`:35-38`) | `ResponseStatusException` | status e mensagem da exceção (ex.: 400 do like duplicado / perfil não encontrado). |
| `handleValidation` (`:41-55`) | `MethodArgumentNotValidException` (`@Valid` falhou) | 400 + `campos: ["campo: mensagem", ...]`. |
| `handleMaxUpload` (`:58-62`) | `MaxUploadSizeExceededException` | 413 "Arquivo muito grande...". |
| `handleGeneric` (`:65-71`) | qualquer outra `Exception` | 500 genérico; stack trace só no log. Captura também exceções que deveriam ser 4xx (R16). |
| `private buildResponse(int, String)` (`:73-79`) | — | Monta o corpo padrão. |

---

## Utilitários

### ImageCompressionUtil
`imagem/ImageCompressionUtil.java` — `@Component` que valida e normaliza fotos.
Configurável por `file.compress.width` (800), `height` (800), `quality` (0.7).

| Método | Descrição |
|---|---|
| `public ImagemProcessada processar(byte[] original)` | Detecta o formato pelos bytes. JPEG/PNG: confere a resolução pelo cabeçalho (máx. 8000 px por lado e 40 MP), decodifica e re-codifica em até 800×800 (remove EXIF/GPS e conteúdo embutido), com no máximo 2 decodificações simultâneas. WebP: bytes originais (o Java não decodifica WebP). Devolve bytes + `Formato`. `IllegalArgumentException` se não for imagem válida. |
| `static Formato detectarFormato(byte[])` | Magic number: `FF D8 FF` (JPEG), `89 PNG \r\n 1A \n` (PNG), `RIFF....WEBP` (WebP); senão `null`. |
| `private void validarDimensoes(byte[])` | Lê só largura/altura com `ImageReader`, sem decodificar os pixels (contra *decompression bomb*). |

Tipos: `enum Formato { JPEG, PNG, WEBP }` com `extensao` e `mimeType`; `record
ImagemProcessada(byte[] bytes, Formato formato)`.

---

## Tabela-resumo de endpoints

| Verbo | Rota | Auth | Controller.método | Sucesso |
|---|---|---|---|---|
| POST | `/users` | público | `UserController.createUser` | 200 `User` |
| POST | `/users/login` | público | `UserController.login` | 200 `{token,userId,email}` |
| GET | `/users/me` | Bearer | `UserController.getMe` | 200 `User` |
| PUT | `/users/me` | Bearer | `UserController.updateMe` | 200 `User` |
| DELETE | `/users/me` | Bearer | `UserController.deleteMe` | 204 |
| GET | `/pessoas` | Bearer | `PessoaController.getAll` | 200 `Page<PessoaPerfilDTO>` |
| GET | `/pessoas/{id}/perfil` | Bearer | `PessoaController.getPerfilById` | 200 `PessoaPerfilDTO` |
| GET | `/pessoas/{id}/redes-sociais` | Bearer | `PessoaController.getRedesSociaisById` | sempre 403 |
| POST | `/pessoas` | Bearer | `PessoaController.create` | 200 `Pessoa` |
| GET | `/pessoas/me` | Bearer | `PessoaController.getMyProfile` | 200 `PessoaCompletaDTO` |
| PUT | `/pessoas/me` | Bearer | `PessoaController.updateMe` | 200 `Pessoa` |
| PATCH | `/pessoas/me/ciencia-responsabilidade` | Bearer | `PessoaController.marcarCienciaResponsabilidade` | 200 `Pessoa` |
| DELETE | `/pessoas/me` | Bearer | `PessoaController.deleteMe` | 204 |
| POST | `/likes?destinoId=` | Bearer | `LikeController.darLike` | 200 `{match,message}` |
| GET | `/likes/dados` | Bearer | `LikeController.listarLikesDados` | 200 `Page<Like>` |
| GET | `/likes/recebidos` | Bearer | `LikeController.listarLikesRecebidos` | 200 `Page<Like>` |
| GET | `/matches/meus` | Bearer | `MatchController.listarMeusMatches` | 200 `Page<Match>` |
| GET | `/matches/{matchId}/redes-sociais` | Bearer | `MatchController.getRedesSociaisDoMatch` | 200 `PessoaRedesSociaisDTO` |
| PUT | `/matches/desfazer/{matchId}` | Bearer | `MatchController.desfazerMatch` | 200 texto |
| GET | `/tags` | público | `TagController.getAll` | 200 `List<Tag>` |
| GET | `/tags/ativas` | público | `TagController.getAtivas` | 200 `List<Tag>` |
| POST | `/tags` | `ROLE_ADMIN` (inexistente) | `TagController.create` | 403 para usuários comuns |
| DELETE | `/tags/{id}` | `ROLE_ADMIN` (inexistente) | `TagController.delete` | nunca; 403 JSON |
| POST | `/api/imagens/upload` | Bearer | `ImagemController.uploadImagem` | 200 `ImagemUploadResponseDTO` |
| DELETE | `/api/imagens/{imagemId}` | Bearer | `ImagemController.deletarImagem` | 200 `ImagemUploadResponseDTO` |
| GET | `/api/imagens/minhas-imagens` | Bearer | `ImagemController.listarMinhasImagens` | 200 lista |
| GET | `/api/imagens/proxy/{publicId}` | Bearer | `ImagemProxyController.proxyImagemHandler` | 302 para URL pré-assinada |
| GET | `/oauth2/authorization/google` | público | Spring Security | 302 para o Google |
| GET | `/login/oauth2/code/google` | público | Spring Security → `OAuth2SuccessHandler` | 302 para `${FRONTEND_URL}/auth/callback#token=` (falha → `/?erro=google_falhou`) |
| GET | `/api-docs`, `/swagger-ui.html` | público só com `SWAGGER_ENABLED=true` | springdoc | spec / UI; desligado → 401 |

Qualquer rota pode responder **429** quando o IP passa do limite do `RateLimitFilter`.
