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
  deixa passar mas o `SecurityConfig` não libera (ex.: `/swagger-ui.html`) → **302 para
  `/oauth2/authorization/google`** (entry point do `oauth2Login`).
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
segurança HTTP, o codificador de senha e o CORS. Dependências injetadas por campo
(`@Autowired`): `OAuth2SuccessHandler`, `JwtFilter` (`:23-27`).

**`public SecurityFilterChain filterChain(HttpSecurity http) throws Exception`** (`:29-79`)
- O que faz: desliga CSRF (API stateless), aplica CORS, sessão `STATELESS`, regras de
  autorização, login OAuth2 com `OAuth2SuccessHandler` e insere o `JwtFilter` antes do
  `UsernamePasswordAuthenticationFilter`.
- Regras (`:38-70`): `permitAll` para `/users/login`, `/users`, `/oauth2/**`,
  `/login/oauth2/**`, `/swagger-ui/**`, `/api-docs/**`, `/v3/api-docs/**`; `GET /tags` e
  `/tags/ativas` públicos; `POST /tags` autenticado; `DELETE /tags/**` exige `ROLE_ADMIN`
  (ninguém tem — R15); `/api/imagens/**`, `/pessoas/**`, `/likes/**`, `/matches/**` e
  qualquer outra rota: autenticado.
- Retorno: o `SecurityFilterChain` construído.
- Observação: o `JwtFilter` tem **sua própria** lista de rotas públicas, que roda antes
  destas regras (ver JwtFilter).
- **Dispatch de `/error`**: quando o Spring responde com `sendError` (ex.: o
  `AccessDeniedHandler` de `hasRole("ADMIN")`), o container reencaminha a requisição para
  `/error`. Esse dispatch passa de novo pela cadeia de segurança, mas o `JwtFilter`
  (`OncePerRequestFilter`) não roda outra vez e o `SecurityContext` dele não foi salvo — a
  requisição de `/error` é anônima, `/error` não está em `permitAll`, e o entry point do
  `oauth2Login` responde **302 para `/oauth2/authorization/google`**. Por isso
  `DELETE /tags/{id}` responde 302, não 403 (R15). Vale para qualquer `sendError` em rota
  autenticada; 4xx devolvidos via `ResponseEntity` pelos controllers não são afetados.

**`public PasswordEncoder passwordEncoder()`** (`:81-84`) — bean `BCryptPasswordEncoder`, usado
por `UserService` para gerar e conferir hashes de senha.

**`public CorsConfigurationSource corsConfigurationSource()`** (`:86-108`) — CORS para `/**`:
6 origens fixas (localhost 5173/3000/8080 e domínios Vercel/Railway), métodos
GET/POST/PUT/DELETE/OPTIONS/PATCH, headers `Authorization`, `Content-Type`, `Accept`,
`Origin`, expõe `Authorization`, credenciais permitidas, cache de preflight 1h. Não lê
nenhuma propriedade (R17).

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
`FRONTEND_URL` (default `http://localhost:5173`, `:32-33`).

**`public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException`** (`:35-71`)
- O que faz: lê `email` e `name` do `OAuth2User`; busca `User` por e-mail ou cria um novo
  (`password=null`, `provider="google"`); se não existir `Pessoa` para esse `User`, cria uma
  mínima (nome, e-mail, `usuarioId`, `ativo=true`); gera JWT com `User.id`; redireciona para
  `{FRONTEND_URL sem barra final}/auth/callback?token=<jwt>`.
- Parâmetros: os padrões do Spring Security; `authentication.getPrincipal()` é o `OAuth2User`.
- Retorno: nenhum (resposta HTTP 302).
- Erros: exceções de repositório propagam; não há validação de idade/termos (R12). Se
  `FRONTEND_URL` tiver várias URLs separadas por vírgula, o redirect fica inválido (R9).
- Dependências: `UserRepository.findByEmail/save`, `PessoaRepository.findByUsuarioId/save`,
  `JwtService.generateToken`.

---

## Segurança

### JwtFilter
`shared/security/JwtFilter.java` — `OncePerRequestFilter` registrado no `SecurityConfig`. Autentica
cada requisição a partir do header `Authorization`. Dependências: `JwtService`,
`UserDetailsService` (implementado por `CustomUserDetailsService`).

- `PUBLIC_ROUTES` (`:30-44`): pares (método, caminho) que não exigem token: `POST /users`,
  `POST /users/login`, `GET /tags`, `GET /tags/ativas`, `GET /swagger-ui/**`,
  `GET /v3/api-docs/**`, `GET /api-docs/**`, `GET|POST /oauth2/**`, `GET|POST /login/oauth2/**`.
  Como a comparação é por prefixo (ver `isPublicRoute`), `GET /swagger-ui.html` **passa** pelo
  filtro (começa com `/swagger-ui`); quem o bloqueia é o `SecurityConfig`, com 302 para o
  OAuth (R8).

**`protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)`** (`:48-115`)
- Fluxo: rota pública → segue sem autenticar. Senão: sem `Bearer` → 401 "Token não
  fornecido"; `isTokenValid` falso → 401 "Token inválido"; extrai `userId`, carrega
  `UserDetails` (consulta ao banco), confere token x usuário → coloca
  `UsernamePasswordAuthenticationToken` no `SecurityContextHolder` (principal = `UserDetails`
  com `username = User.id`, sem authorities). Qualquer exceção (ex.: usuário apagado) → 401
  "Token inválido ou expirado".
- Erros: nunca lança; responde diretamente com JSON `{"erro": "..."}`.

| Método privado | Descrição |
|---|---|
| `isPublicRoute(String method, String path)` (`:117-133`) | Compara método (case-insensitive) e caminho. Padrões terminados em `/**` perdem os 3 últimos caracteres e viram `path.startsWith(base)` — **sem exigir a barra**: `/swagger-ui/**` libera `/swagger-ui.html`, `/oauth2/**` liberaria `/oauth2qualquercoisa`, `/api-docs/**` libera `/api-docs`. Os demais exigem igualdade exata (`matchesPath`). |
| `matchesPath(String routePath, String requestPath)` (`:135-137`) | Igualdade exata. |
| `sendUnauthorizedResponse(HttpServletResponse, String)` (`:139-144`) | Escreve 401 + JSON montado com `String.format` (sem escape de aspas). |

### JwtService
`shared/security/JwtService.java` — gera e valida JWT HS256 com a chave `jwt.secret`
(`JWT_SECRET`). Expiração fixa `EXPIRATION_MS = 15 min` (`:22`).

| Método | O que faz | Retorno / Erros |
|---|---|---|
| `private Key getKey()` (`:24-34`) | Converte `SECRET` em chave HMAC. | `IllegalStateException` se a chave tiver menos de 32 caracteres ou for nula. |
| `public String generateToken(String userId)` (`:39-52`) | Cria token com `sub=userId`, `iat`, `exp=agora+15min`. Chamado por `UserController.login` e `OAuth2SuccessHandler`. | Token compacto. |
| `public String extractUserId(String token)` (`:57-90`) | Faz o parse e devolve o `subject`. | `null` se token vazio, expirado, malformado, com assinatura inválida etc. (nunca lança). |
| `public boolean isTokenValid(String token)` (`:95-126`) | Valida estrutura, assinatura e expiração. | `true`/`false`. |
| `public boolean isTokenValid(String token, UserDetails userDetails)` (`:131-162`) | Confere se o `subject` é o `username` do `UserDetails` e se não expirou. | `true`/`false`. |
| `private boolean isTokenExpired(String token)` (`:167-182`) | Compara `exp` com agora. | `true` se expirado ou ilegível. |
| `public Date getExpirationDate(String token)` (`:187-203`) | Lê `exp`. Só usado internamente. | `Date` ou `null`. |

### CustomUserDetailsService
`shared/security/CustomUserDetailsService.java` — implementa `UserDetailsService` para o Spring
Security. Dependência: `UserRepository`.

**`public UserDetails loadUserByUsername(String userId)`** (`:22-43`)
- Recebe na verdade o **`User.id`** (não e-mail). Busca o `User`; monta um `UserDetails` com
  `username = User.id`, senha (ou `""` para contas Google) e **lista de authorities vazia**
  (por isso nenhuma regra `hasRole` é satisfeita — R15).
- Erros: `UsernameNotFoundException` se o id não existe (o `JwtFilter` converte em 401).

### SecurityUtils
`shared/security/SecurityUtils.java` — utilitário estático para obter o usuário logado.

| Método | Descrição |
|---|---|
| `public static String getCurrentUserId()` (`:9-16`) | Lê o `SecurityContextHolder`; devolve `UserDetails.getUsername()` (= `User.id`) ou `null`. |
| `public static String getUserIdOrThrow()` (`:18-24`) | Igual, mas lança `RuntimeException("Usuário não autenticado")` se `null`. Na prática não lança em rotas protegidas, porque o `JwtFilter` já barrou quem não tem token. |

---

## Controllers

Convenção: "Auth" = exige `Authorization: Bearer`. Respostas de erro de autenticação (401) são
geradas pelo `JwtFilter` antes de o controller rodar.

### UserController
`user/UserController.java` — `@RequestMapping("/users")`. Conta do usuário. Dependências:
`UserService`, `JwtService`.

**`GET /users/me` — Auth — `getMe()`** (`:36-55`)
- Devolve o `User` logado com `password=null`. 404 se o usuário não existe; qualquer exceção → 401
  `{"erro": "Usuário não autenticado"}`.
- Chama `SecurityUtils.getUserIdOrThrow`, `UserService.findById`.

**`POST /users/login` — público — `login(@RequestBody @Valid LoginRequestDTO)`** (`:60-84`)
- Corpo: `{"email", "password"}`. Sucesso → 200 `{"token", "userId", "email"}`. Falha → 401
  `{"erro": "Credenciais inválidas."}`. Registra log `[AUDIT]`.
- Chama `UserService.login`, `JwtService.generateToken`.

**`POST /users` — público — `createUser(@RequestBody @Valid User)`** (`:89-105`)
- Corpo: entidade `User` (`nome`, `email`, `password`; aceita também `id`/`provider` — R1).
- 200 com o `User` criado (sem senha). `IllegalArgumentException` → 409 `{"erro": msg}` (inclui
  "Email já cadastrado" e o caso de senha nula — R14). `@Email`/`@NotBlank` no e-mail → 400 pelo
  `GlobalExceptionHandler`.
- Chama `UserService.createUser`.

**`PUT /users/me` — Auth — `updateMe(@RequestBody @Valid User)`** (`:110-130`)
- Atualiza só `nome` e `email` (o resto do corpo é ignorado). 200 com o usuário; 404 se não existe;
  **qualquer** exceção (inclusive "e-mail já cadastrado") → 401 `{"erro": "Usuário não autenticado"}`.
- Atenção: `@Valid User` exige `email` válido e não vazio mesmo para atualizar só o nome.
- Chama `UserService.updateUser`.

**`DELETE /users/me` — Auth — `deleteMe()`** (`:135-150`)
- Apaga o documento `User` → 204. Não apaga `Pessoa`/imagens/likes/matches (R10). Exceção → 401.
- Chama `UserService.deleteUser`.

### PessoaController
`pessoa/PessoaController.java` — `@RequestMapping("/pessoas")`. Perfil de paquera. Todas as
rotas Auth. Dependência: `PessoaService`. Erros próprios usam a chave `"error"` (em inglês).

**`GET /pessoas` — `getAll(Interesse interesse, Genero genero, Pageable pageable)`** (`:32-49`)
- Query params opcionais `interesse` (`HOMENS|MULHERES|TODOS|NAO_DEFINIDO`), `genero`
  (`MASCULINO|FEMININO|NAO_BINARIO|OUTRO`), `page`, `size` (default 20), `sort`.
- Retorna `Page<PessoaPerfilDTO>` só de perfis `ativo=true`, **incluindo o próprio
  solicitante**; `cienciaResponsabilidade` vem sempre `false` (R39). Valor de enum inválido
  cai no handler genérico (500 — R16).
- Chama `PessoaService.getAllPerfisWithFilters`.

**`GET /pessoas/{id}/perfil` — `getPerfilById(String id)`** (`:51-67`)
- `id` = `Pessoa.id`. 200 com `PessoaPerfilDTO` ou 404. Não filtra perfis inativos.
- Chama `PessoaService.getPerfilById`.

**`GET /pessoas/{id}/redes-sociais` — `getRedesSociaisById(String id)`** (`:69-77`)
- Stub: sempre 403 sem corpo. O caminho correto é `GET /matches/{matchId}/redes-sociais`.

**`POST /pessoas` — `create(@RequestBody @Valid PessoaPerfilDTO)`** (`:79-128`)
- Corpo útil: `nome`, `curso`, `dataNasc` (`yyyy-MM-dd`), `email`, `genero`, `interesse`,
  `descricao`, `cienciaResponsabilidade`. Ignora `id`, `tags`, `imagens`, `fotoPerfilUrl`.
- Regras: `cienciaResponsabilidade` precisa ser `true` (400); se o usuário já tem perfil → 409;
  o service exige 18+ anos, e-mail de perfil único e um perfil por usuário (violações → 400).
- Retorna 200 com a **entidade** `Pessoa` criada (inclui `usuarioId`).
- Chama `PessoaService.findByUsuarioId`, `PessoaService.create`.

**`GET /pessoas/me` — `getMyProfile()`** (`:130-147`)
- 200 com `PessoaCompletaDTO` (inclui redes sociais e imagens) ou 404.
- Chama `PessoaService.getPessoaCompletaByUsuarioId`.

**`PUT /pessoas/me` — `updateMe(@RequestBody @Valid PessoaPerfilDTO)`** (`:149-187`)
- Merge parcial: só aplica campos não nulos entre `nome`, `curso`, `dataNasc`, `genero`,
  `interesse`, `descricao`. Não altera `email`, tags nem redes sociais.
- 200 com a entidade atualizada; 404 sem perfil; `RuntimeException` do service (ex.: menor de
  idade) → 400.
- Chama `PessoaService.findByUsuarioId`, `PessoaService.update`.

**`PATCH /pessoas/me/ciencia-responsabilidade` — `marcarCienciaResponsabilidade()`** (`:189-213`)
- Marca `cienciaResponsabilidade=true`. 200 com a entidade; 404 sem perfil; 400 em erro.
- Chama `PessoaService.marcarCienciaResponsabilidade`.

**`DELETE /pessoas/me` — `deleteMe()`** (`:215-230`)
- Apaga o perfil (se existir) → sempre 204. Não apaga imagens/likes/matches (R10).
- Chama `PessoaService.delete`.

### LikeController
`match/LikeController.java` — `@RequestMapping("likes")`. Todas as rotas Auth.
Dependências: `LikeService`, `PessoaService`.

Todas as rotas convertem o `User.id` do JWT em `Pessoa.id` com
`PessoaService.getPessoaIdOrThrow` (sem perfil → 400).

**`POST /likes?destinoId=<Pessoa.id>` — `darLike(String destinoId)`** (`:59-83`)
- Auto-like → 400. Já curtiu → 400 (vindo do service). Não verifica se `destinoId` existe.
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
| `POST /tags` | `create(@RequestBody @Valid Tag)` (`:52-78`) | Auth (qualquer usuário) | Corpo `{nome, descricao, categoria, ativa}`; `nome`/`categoria` obrigatórios (400). 200 com a tag. **Qualquer** exceção → 401 "Autenticação necessária..." (R20). Aceita `id` no corpo e sobrescreve a tag existente; sem `ativa`, a tag é criada com `ativa=false` (R33). |
| `DELETE /tags/{id}` | `delete(String id)` (`:84-102`) | `ROLE_ADMIN` | Nunca executa: o Spring Security nega antes, e por causa do dispatch de `/error` o cliente recebe **302 para `/oauth2/authorization/google`**, não 403 (R15). Se executasse, só responderia 403. |

### ImagemController
`imagem/ImagemController.java` — `@RequestMapping("/api/imagens")`. Todas Auth.
Dependências: `ImagemService`, `PessoaService`. Sem anotações Swagger. Respostas usam
`ImagemUploadResponseDTO` (`sucesso`, `mensagem`, ...).

**`POST /api/imagens/upload` — `uploadImagem(@Valid @ModelAttribute ImagemUploadDTO)`** (`:27-69`)
- Requisição `multipart/form-data` com campos `file` (imagem) e `perfil` (`true`/`false`).
- 200 com `{id, url: "/api/imagens/proxy/<publicId>", nomeOriginal, perfil, tamanhoBytes, sucesso: true, mensagem}`.
- 400 sem perfil, arquivo vazio, MIME não-imagem ou > 5 MB (`IllegalArgumentException`);
  403 em `SecurityException`; 500 com a mensagem da exceção em outros erros (ex.: R2 fora do ar).
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
  sucesso → **302** com `Location` = URL pré-assinada (1h) e `Cache-Control: private,
  max-age=3600`. Qualquer outra exceção → 500.
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
`user/UserService.java` — regras da conta. Dependências: `UserRepository`, `PasswordEncoder`.

| Método | O que faz | Retorno / Erros |
|---|---|---|
| `public User findById(String id)` (`:16-18`) | Busca por id. | `User` ou `null`. |
| `public User createUser(User user)` (`:20-26`) | Rejeita e-mail já existente; aplica BCrypt na senha; salva. Salva o objeto recebido como está (se vier `id`, sobrescreve — R1). | `User` salvo. `IllegalArgumentException("Email já cadastrado")`; `IllegalArgumentException` do BCrypt se senha `null`. |
| `public User updateUser(String id, User userUpdates)` (`:28-46`) | Copia `nome`/`email` não nulos para o usuário existente e salva. | `User` atualizado ou `null` se não existe. Converte `DuplicateKeyException` em `IllegalArgumentException` (só ocorre se o índice único existir — R4). |
| `public void deleteUser(String id)` (`:48-50`) | `deleteById`. | — |
| `public User login(String email, String password)` (`:52-61`) | Busca por e-mail e confere a senha com BCrypt. | `User`. `BadCredentialsException` se e-mail inexistente ou senha errada (contas Google, sem senha, sempre falham). |

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
  perfil anterior; grava `Imagem` com `id` UUID, `url` (pública ou proxy), `publicId` = chave,
  tamanho e MIME.
- Erros: `IllegalArgumentException` (arquivo vazio, não-imagem, > 5 MB, pessoa inexistente);
  exceções do SDK S3 propagam (o controller devolve 500). Se o banco falhar depois do upload, o
  objeto fica órfão no bucket.

**`public void deletarImagem(String imagemId, String pessoaId)`** (`:100-130`)
- Confere existência e dono; impede apagar a última imagem ativa; tenta apagar do bucket
  (erro só é logado); marca `ativa=false`.
- Erros: `IllegalArgumentException` (não existe), `SecurityException` (não é dono),
  `IllegalStateException` (última imagem).

| Outros métodos | O que faz | Retorno / Erros |
|---|---|---|
| `public String gerarUrlPresignada(String publicId)` (`:132-139`) | URL GET pré-assinada válida por 1h. | `String`. |
| `public Imagem findByPublicId(String publicId)` (`:141-145`) | Busca pela chave. | `Imagem`; `IllegalArgumentException` se não existe. |
| `public List<Imagem> listarImagensAtivasPorUsuario(String pessoaId)` (`:147-150`) | Imagens ativas da pessoa (o nome diz "usuario" mas recebe `Pessoa.id`). | Lista. |
| `private resolverUrl(String key)` (`:152-157`) | `public-url/key` se configurado; senão `/api/imagens/proxy/key`. | — |
| `private validarArquivo(MultipartFile)` (`:159-170`) | Não vazio, `image/*`, ≤ 5 MB. | `IllegalArgumentException`. |
| `private getExtensao(String)` (`:172-175`) | Extensão em minúsculas ou `jpg`. | — |

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
| `Optional<Imagem> findByPessoaIdAndPerfilTrue(String)` (`:17`) | `ImagemService.salvarImagem` (não filtra `ativa`; se houver 2 com `perfil=true`, lança exceção de resultado não único) |
| `long countByPessoaIdAndAtivaTrue(String)` (`:19`) | `ImagemService.deletarImagem` |
| `List<Imagem> findByPessoaAndAtivaTrue(Pessoa)` (`:21`) | `PessoaService` (conversores) |
| `List<Imagem> findByPessoaIdInAndAtivaTrue(List<String>)` (`:23`) | `PessoaService.getAllPerfisWithFilters` |

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
(`@DBRef Pessoa` — serializado por inteiro no JSON, R2), `url`, `publicId` (chave no bucket,
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
`user/LoginRequestDTO.java` — `{email, password}` para `POST /users/login`. Sem anotações de
validação (o `@Valid` do controller não verifica nada).

### PessoaPerfilDTO
`pessoa/PessoaPerfilDTO.java` — usado **como entrada** (`POST`/`PUT /pessoas`) e **como saída**
(`GET /pessoas`, `GET /pessoas/{id}/perfil`). Campos: `id`, `nome`, `curso`, `dataNasc`,
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
`imagem/ImageCompressionUtil.java` — `@Component` que redimensiona e comprime imagens com
Thumbnailator. Configurável por `file.compress.width` (800), `height` (800), `quality` (0.7)
(`:14-24`).

| Método | Descrição |
|---|---|
| `public byte[] compressImage(MultipartFile file) throws IOException` (`:33-43`) | Encaixa a imagem em 800x800 mantendo proporção e aplica a qualidade; devolve os bytes. Lança `IOException`/exceções do Thumbnailator para arquivos que não são imagem (o `ImagemService` captura e usa os bytes originais). |
| `private String getFormat(String filename)` (`:49-56`) | `png`/`gif` mantêm o formato; todo o resto vira `jpg` (atenção: o `contentType` e a extensão salvos continuam os originais — um `.webp` vira bytes JPG com chave `.webp`). |

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
| POST | `/tags` | Bearer | `TagController.create` | 200 `Tag` |
| DELETE | `/tags/{id}` | `ROLE_ADMIN` (inexistente) | `TagController.delete` | nunca; responde 302 para `/oauth2/authorization/google` (R15) |
| POST | `/api/imagens/upload` | Bearer | `ImagemController.uploadImagem` | 200 `ImagemUploadResponseDTO` |
| DELETE | `/api/imagens/{imagemId}` | Bearer | `ImagemController.deletarImagem` | 200 `ImagemUploadResponseDTO` |
| GET | `/api/imagens/minhas-imagens` | Bearer | `ImagemController.listarMinhasImagens` | 200 lista |
| GET | `/api/imagens/proxy/{publicId}` | Bearer | `ImagemProxyController.proxyImagemHandler` | 302 para URL pré-assinada |
| GET | `/oauth2/authorization/google` | público | Spring Security | 302 para o Google |
| GET | `/login/oauth2/code/google` | público | Spring Security → `OAuth2SuccessHandler` | 302 para `${FRONTEND_URL}/auth/callback?token=` |
| GET | `/api-docs`, `/swagger-ui/index.html` | público | springdoc | spec / UI (`/swagger-ui.html` recebe 302 para `/oauth2/authorization/google` — R8) |
