# PairUp: análise do código, da documentação e do que dá para simplificar

> Data: 2026-10-03 · Estado analisado: working tree atual (incluindo as mudanças ainda não commitadas).
> Fonte: dois subagentes, rodados só em modo de análise (nenhum arquivo de código foi alterado):
> - **Documentador**: verificou se o código faz sentido e se a documentação bate com ele.
> - **Refatorador**: procurou o que dá para simplificar sem mudar o comportamento da API.
>
> Caminhos Java relativos a `src/main/java/com/example/pairup/`, já na estrutura por funcionalidade (`user/`, `pessoa/`, `match/`, `imagem/`, `tag/`, `shared/{config,security,exception}/`). Os números `R1–R45` são os riscos de `docs/ARQUITETURA.md`.

---

## Status (2026-10-22)

Aplicados: **C3, A4, A6, A8 (código morto), D13, D14**, e também a remoção do parâmetro
`request` sem uso de `SecurityUtils.getUserIdOrThrow` (parte de R25). Suíte 35/35 antes e
depois, com asserções reforçadas (ver `docs/TESTES.md`). Os demais itens continuam
pendentes, e os números de linha abaixo são de 2026-10-03.

## Status (2026-10-03)

| Item | Situação |
|---|---|
| **Reorganização de pacotes por funcionalidade** | **Aplicada em 2026-10-03.** Não estava no plano original deste documento. Só mudou `package`/`import` (nenhuma lógica); suíte 35/35 antes e depois (`docs/TESTES.md`). Todos os caminhos deste documento já usam a estrutura nova. |
| Simplificações seguras **A1–A13** (§3) | **Não aplicadas.** Os números de linha citados continuam válidos para o código atual. |
| Decisões de contrato **D1–D14** (§4) | **Não aplicadas** (aguardam decisão). A numeração tem lacunas: não existem D11, D12 nem D15. |
| Fase 0 (segurança) e Fase 1/2 (§6) | **Não iniciadas.** R1–R45 continuam abertos. |
| **Fase 3 (documentação)** | **Concluída em 2026-10-03:** README reescrito, R8/R15/R18 corrigidos, R29–R45 acrescentados ao `ARQUITETURA.md`, `REFERENCIA_FUNCOES.md` e `TESTES.md` atualizados. Os problemas listados na §5 abaixo foram resolvidos nos documentos; a §5 fica como registro do que estava errado. |

Correspondência dos achados deste documento com os riscos novos do `ARQUITETURA.md`: C3 → **R29**;
contradição feed/proxy → **R30**; perfil incompleto do Google → **R31**; vínculo Google por
e-mail → **R32**; `POST /tags` com `id` → **R33**; `catch → 401` → **R34**; upload sem limite →
**R35**; sem rate limit → **R36**; `Match` sem índice → **R37**; `destinoId` sem validação →
**R38**; `cienciaResponsabilidade=false` e próprio usuário no feed → **R39**; cache de 1 h no
proxy → **R40**; `tokens/StoredCredential` no histórico → **R43**.

## 1. Resumo em uma página

| Pergunta | Resposta curta |
|---|---|
| **O código faz sentido?** | **Na estrutura, sim.** As camadas controller → service → repository estão corretas, e nenhum controller acessa repositório direto. A mistura `User.id`/`Pessoa.id` em likes e matches **foi corrigida** no diff atual. **Na segurança, não:** ainda há 3 problemas graves que se encadeiam (ver §2). |
| **Dá para simplificar mantendo a funcionalidade?** | **Sim, cerca de 430–470 linhas** (de 3.091 linhas Java em `src/main`; 3.250 contando `application.yml` e `teste-upload.html`) sem mudar nenhuma resposta da API (§3). Outras cerca de 150 linhas saem se você aceitar mudar o formato dos erros e de alguns JSON (§4). |
| **A documentação está correta?** | **Em grande parte, sim.** `ARQUITETURA.md`, `REFERENCIA_FUNCOES.md` e `TESTES.md` citam linhas corretas, conferidas por amostragem em cerca de 40 referências. Mas há **3 riscos descritos errado (R8, R15, R18)**, vários achados novos não registrados, e o **README está desatualizado** (§5). |
| **Algum dos riscos R1–R28 foi resolvido?** | **Nenhum.** Todos continuam abertos. O R6 está **ativo** com o `.env` atual. |

---

## 2. O código faz sentido? Problemas reais encontrados

### 2.1 Críticos

| # | Problema | Onde | Por que importa |
|---|---|---|---|
| C1 (R1) | **`POST /users` aceita `id` no corpo da requisição** | `user/UserController.java:96` (`@RequestBody User`), `user/UserService.java:20-26` | Com o `id` de outra pessoa, o `save` vira upsert e **substitui a conta dela**. Isso é tomada de conta. |
| C2 (R2) | **`GET /pessoas` vaza dados privados** | `pessoa/PessoaPerfilDTO.java:27` (`List<Imagem>`), `imagem/Imagem.java:24` (`@DBRef Pessoa`) | Cada imagem é serializada junto com a `Pessoa` dona completa: e-mail, `usuarioId`, Instagram, WhatsApp e Telegram. Isso **anula a regra "redes sociais só depois do match"** e entrega os `usuarioId` que o C1 precisa. |
| C3 (R29) | ~~**`mvn test` grava no MongoDB de produção (Railway)**~~ | `src/test/resources/application.yml:6` + `spring-dotenv` lê o `.env` | Os testes não limpam dados. Sem o `.env` o contexto nem sobe (exige `GOOGLE_*`); com o `.env`, usa o banco de produção. — **Resolvido em 2026-10-22 (perfil `test`)** |

### 2.2 Altos

- **R6 ativo:** o `.env` define `CF_R2_PUBLIC_URL`, então `Imagem.url` guarda a URL pública do bucket. Todas as fotos ficam públicas e o proxy com checagem de match é contornado (`imagem/ImagemService.java:181-184`).
- **Contradição de produto entre feed e proxy:** sem a URL pública, o proxy responde 403 para quem não tem match (`imagem/ImagemProxyController.java:87-90`), e então **ninguém vê as fotos no feed antes do match**. É preciso decidir a política de fotos.
- **R3: os `@Transactional` não fazem nada,** porque não existe `TransactionManager`.
- **R4: não há índices únicos,** e o `Match` nem declara índice. Matches duplicados fazem `findByPessoaId1AndPessoaId2` responder 500.
- **R5: redes sociais e tags nunca são gravadas.** Nenhum endpoint chama `setInstagram`/`setWhatsapp`/`setTelegram`, então `GET /matches/{id}/redes-sociais` sempre devolve `null`.
- **Login com Google cria um perfil incompleto** (`shared/config/OAuth2SuccessHandler.java:53-62`). Depois disso, `POST /pessoas` sempre responde 409. A conta Google é vinculada à local **só pelo e-mail, sem verificar o e-mail**, então um atacante pode pré-registrar o e-mail da vítima.
- **R7: o healthcheck do Docker sempre falha,** porque não há actuator no `pom.xml`.
- **R10: excluir deixa órfãos.** Apagar o `User` mantém a `Pessoa`, que continua aparecendo no feed.

### 2.3 Médios e baixos (resumo)

- **Erros:** existem 4 formatos de corpo de erro (`{"error"}` vs `{"erro"}` etc.). O catch-all devolve 500 em erros que deveriam ser 4xx, por exemplo `?genero=INVALIDO`. Os blocos `catch (Exception) → 401` em `UserController`/`TagController` fazem "e-mail já cadastrado" virar **401**.
- **`POST /tags` aceita `id`,** então qualquer usuário pode sobrescrever qualquer tag.
- **Validação vazia:** os DTOs não têm anotações, então o `@Valid` não valida nada. (Correção de 2026-10-03: `password` ausente no login responde **401** "Credenciais inválidas.", não 500 — confirmado em execução.)
- **Upload:** não há limite de imagens, o MIME é o que o cliente informa, e se a compressão falhar o arquivo original é enviado assim mesmo (SVG com script, por exemplo).
- **N+1 no feed (R13):** uma consulta de imagens por pessoa, e o resultado é **descartado** logo em seguida.
- **`GET /pessoas`** sempre devolve `cienciaResponsabilidade: false` e lista também o próprio usuário.
- **Sem rate limit no login.** O cache de 1 h no redirect do proxy mantém o acesso à foto mesmo depois de desfazer o match.
- **`tokens/StoredCredential`** (credencial do Google Drive antigo) continua no **histórico do git** (commit `bc1b363`). Vale revogar.

---

## 3. Simplificações seguras (o comportamento da API não muda)

Cada item foi conferido no código, e os usos foram confirmados por grep.

| # | Onde | O que simplificar | Linhas a menos | Risco |
|---|---|---|---|---|
| A1 | `shared/security/JwtService.java` (204 linhas) | O mesmo `parseClaimsJws` aparece em 3 métodos, cada um com 5 `catch` idênticos. A checagem de expiração é redundante, porque o parse já lança `ExpiredJwtException`. Tudo vira um `parse()` que devolve `Optional<Claims>`. | ~110 | baixo |
| A2 | `shared/security/JwtFilter.java:77-114` | O token é validado 2 vezes e há um ramo `else` inalcançável. Passa a **ser parseado 1 vez em vez de 4.** | ~20 | baixo |
| A3 | `match/LikeService.java:37-82` | A mesma consulta é feita 3 vezes. Basta uma `Optional<Like>` reaproveitada. | ~25 | baixo |
| A4 | ~~`match/LikeController.java:43-50` e `match/MatchController.java:41-48`~~ | `resolvePessoaId` está copiado nos dois. Vira um método em `PessoaService`. | ~20 | nenhum — **Aplicado em 2026-10-22: `PessoaService.getPessoaIdOrThrow`** |
| A5 | `MatchController` + `MatchService` + `MatchRepository` | A checagem "faz parte do match" aparece 2 vezes e o match é buscado 2 vezes. A query recebe o mesmo parâmetro 2 vezes. Proposta: `Match.envolve()` e `Match.outraPessoa()`. | ~15 | nenhum |
| A6 | ~~`pessoa/PessoaService.java:174-242`~~ | Dois conversores duplicados e o N+1 descartado. Vira um único `toPerfilDTO(p, imagens)`. **Mantém `cienciaResponsabilidade=false` na listagem**, para não mudar a saída. | ~35 + N consultas a menos | baixo — **Aplicado em 2026-10-22: `preencherPerfil` + `PessoaCompletaDTO extends PessoaPerfilDTO`** |
| A7 | `ImagemController` + `ImagemUploadResponseDTO` | O prefixo `/api/imagens/proxy/` está repetido em 3 lugares, o DTO é montado à mão e há um construtor sem uso. | ~15 | nenhum |
| A8 | ~~`imagem/ImagemService.java:147-179`~~ | `definirFotoPerfil` e `findById` são **código morto**. `salvarImagem` relê uma `Pessoa` que o controller já tem. | ~37 | nenhum — **Aplicado em 2026-10-22 (só a parte de código morto)** |
| A9 | `imagem/ImagemProxyController.java` | `isPathTraversal` roda 2 vezes. Há checagens de `null` impossíveis, substrings cobertas por outras e um regex `tinder_ufs/` redundante. O prefixo de storage **não** muda. | ~12 | baixo |
| A10 | `shared/config/SecurityConfig.java:55-66` | 5 matchers `.authenticated()` repetem o `anyRequest().authenticated()`. | ~12 | nenhum |
| A11 | `tag/TagController.java:89-102` | O corpo do `delete` é inalcançável, porque ninguém tem `ROLE_ADMIN`. Fica só o `return 403`. Também trocar o logger pelo `@Slf4j`. | ~10 | nenhum |
| A12 | vários | Remover os defaults do builder em `CustomUserDetailsService`, o `credentialsProvider` duplicado em `CloudflareR2Config`, o `.getId().toString()` sobre `String`, `PessoaRepository.findByEmail` (sem uso) e os **cerca de 62 comentários de changelog** ("✅ CORRIGIDO" etc.). Fazer em commit separado. | ~75 | nenhum |
| A13 | yml / compose / pastas | O bloco `management:` não tem efeito sem actuator. No compose: `args: JAR_FILE`, o volume `mongo-init.js` (é um diretório vazio), o `nginx` sem `nginx.conf`/`ssl` e o `version:`. Pastas vazias: `templates/`, `google-drive-credentials.json/`, `uploads/`, `tokens/`. | ~40 | nenhum |

**`pom.xml`: não há nada a remover.** Todas as dependências têm uso real.

### Exemplo: A1 (JwtService)

**Antes:** três métodos, cada um assim:

```java
try {
    Jwts.parserBuilder().setSigningKey(getKey()).build().parseClaimsJws(token)...
} catch (ExpiredJwtException e) { ... return null; }
  catch (MalformedJwtException e) { ... return null; }
  catch (SignatureException e) { ... return null; }
  catch (JwtException e) { ... return null; }
  catch (IllegalArgumentException e) { ... return null; }
```

**Depois:**

```java
private Optional<Claims> parse(String token) {
    try {
        return Optional.of(Jwts.parserBuilder().setSigningKey(getKey()).build()
                               .parseClaimsJws(token).getBody());
    } catch (JwtException | IllegalArgumentException e) {
        log.debug("JWT inválido: {}", e.getMessage());
        return Optional.empty();
    }
}
public String extractUserId(String t) { return parse(t).map(Claims::getSubject).orElse(null); }
public boolean isTokenValid(String t) { return parse(t).isPresent(); }
```

O comportamento é o mesmo porque todas as exceções capturadas hoje são subclasses de `JwtException`.

### Exemplo: A3 (LikeService)

```java
Optional<Like> existente = likeRepository.findByPessoaOrigemIdAndPessoaDestinoId(origemId, destinoId);
if (existente.map(Like::isAtivo).orElse(false))
    throw new ResponseStatusException(BAD_REQUEST, "Você já curtiu essa pessoa");
Like like = existente.orElseGet(Like::new);
like.setPessoaOrigemId(origemId); like.setPessoaDestinoId(destinoId); like.setAtivo(true);
likeRepository.save(like);
boolean isMatch = likeRepository.findByPessoaOrigemIdAndPessoaDestinoId(destinoId, origemId)
        .map(Like::isAtivo).orElse(false);
if (isMatch) criarMatchSeNaoExistir(origemId, destinoId);
return Map.of("match", isMatch, "message", isMatch ? "É um match!" : "Like enviado com sucesso");
```

---

## 4. Simplificações que mudam o comportamento (precisam da sua decisão)

| # | O quê | Ganho | O que muda |
|---|---|---|---|
| D1–D4 | Remover os `try/catch` genéricos de `UserController`, `TagController`, `ImagemController` e `PessoaController` e usar o `GlobalExceptionHandler`, com **um único formato de erro** | ~70 linhas, e **corrige bugs** (401 para "e-mail duplicado", vazamento de `e.getMessage()`) | O corpo e o status das respostas de erro mudam, então o frontend precisa se adaptar |
| D5 | Uma lista única de rotas públicas, compartilhada por `JwtFilter` e `SecurityConfig`; liberar `/error` e `/swagger-ui.html` | ~30 linhas | Alguns 302 viram 401 ou 403, que são as respostas corretas |
| D6 | Adicionar actuator **ou** remover os healthchecks | — | Status do container |
| D7 | Remover os `@Transactional` sem efeito (ou configurar `MongoTransactionManager`) | ~12 anotações | Nada hoje. É uma decisão de design |
| D8 | Levar o merge do `PUT /pessoas/me` para o service | ~20 linhas | Contrato interno do service |
| **D9** | **Criar DTOs de entrada e saída dedicados** (sem `id`/`provider` no cadastro e sem `List<Imagem>` com `Pessoa`) | **Corrige C1 e C2** | O formato do JSON muda. **É a mais importante para segurança.** |
| D10 | Remover os getters calculados de `PessoaCompletaDTO` | ~20 linhas | Somem campos de `/pessoas/me` |
| D13 | ~~Apagar `static/teste-upload.html` (aponta para rotas que não existem)~~ | 1 arquivo | Deixa de ser servido — **Aplicado em 2026-10-22** |
| D14 | ~~Trocar o `application.yml` de teste (cópia de ~85 linhas) por um `application-test.yml` mínimo com `@ActiveProfiles("test")`~~ | ~75 linhas | **Junto com isso, isolar os testes do `.env` (C3)** — **Aplicado em 2026-10-22, já isolando do `.env`** |

**Não recomendado agora:** trocar as 4 queries de filtro por `Criteria`/`Example`. O ganho é pequeno (~8 linhas) e o `Example` erraria com os booleanos primitivos.

---

## 5. Documentação comparada ao código

> Registro histórico: todos os itens desta seção foram corrigidos na Fase 3 (2026-10-03).
> Os números de linha abaixo referem-se às versões antigas dos documentos.

### README.md (desatualizado)

| Linha | Problema |
|---|---|
| 81-84 | Diz que "a suíte falha por falta de `cloudflare.r2.endpoint`". Isso já foi corrigido (35/35). **Falta o aviso de que os testes usam o banco de produção pelo `.env`.** |
| 64 | Diz que o banco do compose é `TINDER`. O real é `PAIRUP`; `TINDER` é o banco do Railway. |
| 24 | Diz "nunca a URL direta do bucket". É falso com o `.env` atual (R6). |
| 25 | Promete liberar redes sociais, mas não existe endpoint para cadastrá-las. |
| 16, 73 | O Swagger não funciona em `/swagger-ui.html`. O caminho que funciona é `/swagger-ui/index.html`. |
| 35-50 | Não diz quais variáveis são obrigatórias, que `FRONTEND_URL` precisa ser uma URL única, nem que `GOOGLE_*` é necessário até nos testes. |
| — | Não documenta: JWT de 15 min sem refresh, o fluxo OAuth (`/oauth2/authorization/google` → `/auth/callback?token=`), a distinção `User.id`/`Pessoa.id` e o contrato de upload. |

### docs/ARQUITETURA.md (preciso, com 3 erros)

- **R8 descrito errado:** o `JwtFilter` compara por prefixo e deixa `/swagger-ui.html` passar. O bloqueio vem do `SecurityConfig`, que responde com **302 para o OAuth**, não com 401.
- **R15 descrito errado:** `DELETE /tags` responde **302** em runtime, não 403, porque `/error` não está liberado.
- **R18 descrito errado e mais grave do que parece:** o padrão não é o Mongo local `PAIRUP`, é o **Mongo do Railway** pelo `.env`.
- A §4 diz que o `.env` "não foi lido", e isso esconde que o R6 está ativo.
- A contagem diz "~2.700 linhas", mas são 3.146.
- Faltam como riscos: C3, a contradição feed/proxy, o perfil incompleto do Google e a vinculação por e-mail, `POST /tags` com `id`, os 401 em `PUT /users/me`, o upload sem limite, a falta de rate limit, o `Match` sem índice, e os itens B, D, E, F e G que estão em `TESTES.md`.
- Os comentários no código citam "ARQUITETURA.md item crítico 1", mas o doc agora numera os riscos como R1–R28.

### docs/REFERENCIA_FUNCOES.md

- As assinaturas e as linhas batem com o código.
- Erros: as linhas 139 e 689 dizem que o Swagger responde 401 (é 302), e a linha 319 diz que `DELETE /tags` responde 403 (é 302).
- Não registra que `convertToPerfilDTO` omite `cienciaResponsabilidade`.

### docs/TESTES.md (o mais fiel ao comportamento real)

- A linha 119 interpreta o 302 do Swagger como "público". Provavelmente é o redirect do OAuth.
- As linhas 51-56 sobrescrevem `CF_R2_*`, mas isso não tem efeito, porque o yml de teste fixa os valores.
- Cita o container `tinder_ufs_mongo`, mas o compose atual usa `pairup_mongo`.

### HELP.md

Não existe e está no `.gitignore`. Nada a fazer.

---

## 6. Plano recomendado

**Fase 0: segurança (antes de qualquer refatoração)**
1. Isolar os testes do `.env`, com perfil de teste e URI fixa ou Testcontainers (C3 + D14).
2. Criar DTOs de entrada e saída (D9), que fecha C1 e C2.
3. Decidir a política de fotos (URL pública ou proxy) e alinhar o feed com ela.
4. Revogar o token do Google Drive que ficou no histórico do git.

**Fase 1: simplificação segura (§3), em commits pequenos e com a suíte rodando a cada passo**
1. A8 + A11 + A12 (código morto): ~55 linhas, risco nenhum.
2. A1 + A2 (JWT): ~130 linhas.
3. A6 (PessoaService + N+1): ~35 linhas.
4. A3 (LikeService): ~25 linhas.
5. A4 + A5 (helpers de Pessoa e Match): ~35 linhas.
6. A7 + A9 + A10: ~40 linhas.
7. A13 (yml, compose e pastas): ~40 linhas.
8. Comentários de changelog: ~70 linhas, em commit próprio.

**Fase 2: decisões de contrato.** D1–D5 (formato de erro único), D6 e D7, R4 (índices) e R5 (endpoint de redes sociais e tags).

**Fase 3: documentação.** Corrigir README, R8/R15/R18 e acrescentar os riscos novos ao `ARQUITETURA.md`.

### Onde o Testador deve focar depois da Fase 1

- Token expirado, inválido e ausente (A1/A2).
- Like duplicado e match recíproco (A3).
- `GET /pessoas` com filtros e com página vazia (A6).
- Upload e `minhas-imagens` com a URL do proxy (A7).
- Path traversal no proxy e chaves `tinder_ufs/...` (A9).
- `desfazer` e `redes-sociais` chamados por quem não participa do match (A5).
