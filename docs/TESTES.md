# PairUp — Relatório de Testes (Testador)

> Terceiro estágio do pipeline Documentador → Refatorador → Testador.
> Rodada de 2026-10-03, sobre o working tree **não commitado** (HEAD `a9744cb` + diff
> do Refatorador: remoção de código morto em services/repositories/DTOs/`JwtService`,
> `resolvePessoaId` em `LikeController`/`MatchController`, like duplicado agora `400`,
> remoção de `mvnw`/`.mvn`/`Dockerfile.test`, limpeza de `application.yml`/`docker-compose.yml`).
> Este arquivo substitui a versão anterior. Revisado em 2026-10-03 (após a reorganização de
> pacotes): nova seção "Execução de 2026-10-03 — reorganização por funcionalidade",
> correção da interpretação do `302` de `/swagger-ui.html`, do comando de sobrescrita do S3
> e da explicação dos containers Mongo.

## Execução de 2026-10-22 — remoção de redundâncias e perfil `test`

- **Resultado: 35/35 antes e 35/35 depois** (`mvn -B clean test`), sem nenhuma conexão
  a produção: 72 conexões a `localhost:27018`, 0 a `rlwy`, 0 a `r2.cloudflarestorage`.
- **O isolamento do `.env` agora é garantido pelo próprio projeto.** O
  `src/test/resources/application.yml` (cópia de 87 linhas do principal, com
  `${MONGODB_URI:...}`) foi substituído por `src/test/resources/application-test.yml`,
  só com o que difere, em valores literais. Todas as 7 classes têm
  `@ActiveProfiles("test")`. A 2ª execução desta rodada foi feita **sem** passar
  `MONGODB_URI`/`JWT_SECRET`, com o `.env` de produção presente, e mesmo assim foi para o
  Mongo descartável. Isso resolve o C3/R29.
- **Asserções reforçadas** (antes só conferiam o status HTTP):
  - `PessoaControllerTest`: o JSON de `GET /pessoas` (página com `nome`, `tags` e
    `imagens`), de `GET /pessoas/{id}/perfil` (campos básicos, sem `instagram`/`whatsapp`),
    de `GET /pessoas/me` (`cienciaResponsabilidade`, `perfilCompleto`,
    `quantidadeGaleria`) e o nome depois de `PUT /pessoas/me`.
    `testRedesSociaisDeveLancarExcecao` passou a se chamar
    `testRedesSociaisSemMatchDeveRetornar403`, que é o que ele verifica.
  - `LikeControllerTest.darLike`: o 1º like responde `match:false` e o recíproco
    `match:true`.
  - `MatchControllerTest`: o match aparece para os dois lados, some da lista depois de
    desfeito, e `/matches/{id}/redes-sociais` devolve as redes da **outra** pessoa.
- Imagem do MinIO: `minio/minio` não está mais no Docker Hub. Use `quay.io/minio/minio` e
  `quay.io/minio/mc`.

## Execução de 2026-10-03 — reorganização por funcionalidade

- **Resultado: 35/35 antes e 35/35 depois** da mudança de pacotes "por camada" → "por
  funcionalidade" (só `package`/`import` mudaram). Os relatórios atuais em
  `target/surefire-reports/` (2026-10-03 19:51) já têm os nomes de pacote novos e registram
  `Tests run: 35, Failures: 0, Errors: 0, Skipped: 0` somando as 7 classes.
- Infra usada: **Mongo descartável em `localhost:27018`** (os logs do surefire mostram 72
  conexões a `localhost:27018` e nenhuma a `rlwy`) e **MinIO descartável** com bucket
  `test-bucket`. A porta exata do MinIO não fica registrada nos relatórios do surefire;
  nesta máquina a `9000` está ocupada por um MinIO de outro projeto, então o endpoint deve
  ser sobrescrito com `CLOUDFLARE_R2_ENDPOINT` (ver "Como rodar a suíte com segurança").
- Classes de teste por pacote (`src/test/java/com/example/pairup/`):

| Pacote | Classe | Testes |
|---|---|---|
| raiz | `PairUpApplicationTests` | 1 |
| `user/` | `UserControllerTest` | 6 |
| `pessoa/` | `PessoaControllerTest` | 8 |
| `match/` | `LikeControllerTest` | 5 |
| `match/` | `MatchControllerTest` | 5 |
| `imagem/` | `ImagemControllerTest` | 7 |
| `tag/` | `TagControllerTest` | 3 |
| **Total** | 7 classes | **35** |

## Como rodar a suíte com segurança

Desde 2026-10-22 as classes de teste ativam o perfil `test`
(`src/test/resources/application-test.yml`). Ele sobrepõe o `application.yml` principal
com valores **literais**. Por isso o `.env` (produção), que o `spring-dotenv` carrega
também em `mvn test`, não tem efeito no Mongo, no S3, no JWT nem no OAuth dos testes:

| Propriedade | Valor fixo | Sobrescrever com |
|---|---|---|
| `spring.data.mongodb.uri` | `mongodb://localhost:27018/PAIRUP_TEST` | `SPRING_DATA_MONGODB_URI` |
| `cloudflare.r2.endpoint` / `public-url` | `http://localhost:9000` / `.../test-bucket` | `CLOUDFLARE_R2_ENDPOINT` / `CLOUDFLARE_R2_PUBLIC_URL` |
| `cloudflare.r2.access-key` / `secret-key` / `bucket` | `test-access-key` / `test-secret-key` / `test-bucket` | `CLOUDFLARE_R2_*` |
| `jwt.secret`, `spring.security.oauth2...google.*` | valores de teste | — |

`MONGODB_URI` e `CF_R2_*` **não** afetam os testes (são placeholders só do yml principal).
**Uma classe de teste nova sem `@ActiveProfiles("test")` volta a ler o `.env`.**

```bash
docker run -d --rm --name pairup_test_mongo -p 27018:27017 mongo:7.0
# MinIO em porta alternativa (a 9000 pode estar ocupada); imagem no quay.io
docker run -d --rm --name pairup_test_minio -p 9100:9000 \
  -e MINIO_ROOT_USER=test-access-key -e MINIO_ROOT_PASSWORD=test-secret-key \
  quay.io/minio/minio server /data
docker run --rm --network host --entrypoint sh quay.io/minio/mc -c \
  "mc alias set t http://localhost:9100 test-access-key test-secret-key && mc mb -p t/test-bucket"

CLOUDFLARE_R2_ENDPOINT=http://localhost:9100 \
CLOUDFLARE_R2_PUBLIC_URL=http://localhost:9100/test-bucket \
mvn -B clean test

docker stop pairup_test_mongo pairup_test_minio
```

### Containers Mongo: `tinder_ufs_mongo` × `pairup_mongo`

- `tinder_ufs_mongo` é o container criado pelo **compose antigo** (antes do rename para
  PairUp): projeto compose `tinder_ufs`, `MONGO_INITDB_DATABASE=TINDER`, porta `27017`, volume
  `tinder_ufs_mongo_data`. Ele continua existindo (e rodando) nesta máquina com os dados de
  desenvolvimento antigos.
- `pairup_mongo` é o nome definido no **`docker-compose.yml` atual** (`container_name:
  pairup_mongo`, `MONGO_INITDB_DATABASE: PAIRUP`). Como o nome do projeto compose vem da
  pasta (`Tinder_ufs` → `tinder_ufs`), um `docker-compose up` hoje reutilizaria o mesmo
  volume `tinder_ufs_mongo_data` — e, num volume já inicializado, `MONGO_INITDB_*` é
  ignorado (o banco `PAIRUP` só é criado quando a aplicação gravar nele). Além disso a porta
  `27017` conflita com o `tinder_ufs_mongo` se ele estiver de pé.
- Nenhum dos dois deve ser usado pelos testes: use um Mongo descartável em outra porta
  (`27018`), como acima.

## Resumo executivo

- **Suíte automatizada: 35 testes, 35 passaram, 0 falhas, 0 erros, 0 skipped** — `BUILD
  SUCCESS` em 2 execuções consecutivas (a 2ª sobre o banco já sujo pela 1ª), sem
  flutuação. Nenhuma regressão em relação à baseline anterior (35/35).
- A remoção do `mvnw` não impede a build: o `mvn` do sistema (`/usr/bin/mvn`, JDK 21.0.12)
  funciona. Mas o README/CI que dependam de `./mvnw` vão quebrar (ver "Riscos de setup").
- **Fluxos ponta a ponta via HTTP real funcionam**: cadastro, login/JWT, perfil, listagem
  com filtros, like, match recíproco, redes sociais pós-match, upload, proxy de imagem
  com controle de acesso por match, desfazer match, exclusões. O item crítico 1 (mismatch
  `User.id`/`Pessoa.id`) está **resolvido em runtime**.
- Problemas confirmados empiricamente nesta rodada (nenhum é regressão do diff atual):
  likes duplicados por **condição de corrida** (sem índice único) que depois tornam o
  par permanentemente inutilizável (`500`); healthcheck `/actuator/health` inexistente;
  `DELETE /tags/{id}` responde `302` para o Google em vez de `403`; CORS ignora
  `FRONTEND_URL`; enum inválido em filtro → `500`; listagem expõe `email` de terceiros e
  inclui o próprio solicitante; upload aceita arquivo que não é imagem; perfil órfão após
  `DELETE /users/me`.
- Nenhum arquivo de teste nem de produção foi alterado nesta rodada.

## Segurança do ambiente de teste (antes de rodar)

> Histórico (2026-10-03), anterior ao perfil `test`. Hoje o isolamento vem do
> `application-test.yml` (ver "Como rodar a suíte com segurança").

- `.env` na raiz contém `MONGODB_URI` apontando para o MongoDB de produção (Railway,
  `*.proxy.rlwy.net`) e credenciais reais do R2; `spring-dotenv` (`pom.xml:116`) carrega
  esse arquivo automaticamente. `src/test/resources/application.yml` usa
  `${MONGODB_URI:...}`, ou seja, **sem sobrescrita, `mvn test` iria para produção**.
- Mitigação usada: Mongo **descartável** (`pairup_tester_mongo`, `mongo:7.0`, porta 27018,
  sem auth) e MinIO descartável (`pairup_test_minio`, porta 9000, bucket `test-bucket`),
  com `MONGODB_URI`, `JWT_SECRET` e `CF_R2_*` sobrescritos por variável de ambiente.
  (Correção de 2026-10-03: as `CF_R2_*` só tiveram efeito na execução da aplicação real
  com o `application.yml` principal; em `mvn test` elas são ignoradas e o MinIO só foi
  usado porque estava exatamente em `localhost:9000`, o valor fixo do YAML de teste.)
  Conferido no log: 72 conexões a `localhost:27018`, **0 ocorrências de `rlwy`** ou de
  `r2.cloudflarestorage`. O container `tinder_ufs_mongo` (dados de dev do compose antigo —
  ver "Containers Mongo") **não foi tocado**.
- A aplicação foi executada a partir de uma cópia do jar fora da raiz do projeto com
  `env -i` (sem `.env` acessível), para garantir zero acesso a Railway/R2.
- Ao final: app parada, `pairup_tester_mongo` e `pairup_test_minio` removidos (todo o
  dado de teste foi junto). Nada foi deixado rodando por esta rodada.

## Resultado da suíte automatizada

> Histórico (2026-10-03). Comando da época, com o yml de teste antigo.

Comando (as linhas `CF_R2_*` não afetam `mvn test` — ver "Como rodar a suíte com
segurança"; funcionaram porque o MinIO estava na porta fixa `9000`):

```
MONGODB_URI="mongodb://localhost:27018/PAIRUP_TEST" \
JWT_SECRET="tester-local-secret-0123456789abcdef0123456789abcdef" \
CF_R2_ENDPOINT=http://localhost:9000 CF_R2_ACCESS_KEY=test-access-key \
CF_R2_SECRET_KEY=test-secret-key CF_R2_BUCKET=test-bucket \
CF_R2_PUBLIC_URL=http://localhost:9000/test-bucket \
mvn -B clean test
```

| Classe | Total | Passou | Falhou | Erro |
|---|---|---|---|---|
| `PairUpApplicationTests` | 1 | 1 | 0 | 0 |
| `UserControllerTest` | 6 | 6 | 0 | 0 |
| `TagControllerTest` | 3 | 3 | 0 | 0 |
| `PessoaControllerTest` | 8 | 8 | 0 | 0 |
| `LikeControllerTest` | 5 | 5 | 0 | 0 |
| `MatchControllerTest` | 5 | 5 | 0 | 0 |
| `ImagemControllerTest` | 7 | 7 | 0 | 0 |
| **Total** | **35** | **35** | **0** | **0** |

Execução 1 e execução 2: ambas `Tests run: 35, Failures: 0, Errors: 0, Skipped: 0`.

Observações sobre a qualidade da suíte (não são falhas):
- `ImagemControllerTest` depende de um S3 compatível em `localhost:9000` (fixo em
  `src/test/resources/application.yml`, sobrescrevível só via `CLOUDFLARE_R2_ENDPOINT`);
  sem MinIO rodando os uploads dão `500`.
- `TagControllerTest.testDeleteTagDeveSerBloqueado` espera `403` e passa no MockMvc, mas
  **em runtime real a resposta é `302`** (ver item B abaixo) — o MockMvc não executa o
  dispatch de `/error`, então o teste não reflete o comportamento HTTP real.
- Nenhum teste cobre: like duplicado (sequencial ou concorrente), filtros com enum
  inválido, exclusão de conta, CORS, proxy de imagem com/sem match. (Ainda válido em
  2026-10-22.)

## Fluxos ponta a ponta testados (app real em `localhost:8081`, `curl`)

| # | Requisição | Resultado real | Esperado? |
|---|---|---|---|
| 1 | `POST /users` (Ana, Bruno, Caio) | `200`, `password:null` | sim |
| 2 | `POST /users` e-mail repetido | `409 {"erro":"Email já cadastrado"}` | sim |
| 3 | `POST /users/login` senha errada | `401 Credenciais inválidas.` | sim |
| 4 | `POST /users/login` correto | `200 {userId,email,token}`; `exp-iat = 900s` | sim (15 min) |
| 5 | `GET /users/me` sem token / token inválido / adulterado | `401` (`Token não fornecido` / `Token inválido`) | sim |
| 6 | `GET /users/me`, `PUT /users/me` | `200`, nome atualizado | sim |
| 7 | `POST /pessoas` com `cienciaResponsabilidade:false` | `400` "aceitar os termos" | sim (check agora só no controller; ainda funciona) |
| 8 | `POST /pessoas` com nascimento 2015 | `400` "pelo menos 18 anos" | sim |
| 9 | `POST /pessoas` válido | `200`, `Pessoa` com `usuarioId` | sim |
| 10 | `POST /pessoas` 2ª vez p/ mesmo usuário | `409` | sim |
| 11 | `GET /pessoas/me`, `PUT /pessoas/me`, `PATCH /pessoas/me/ciencia-responsabilidade` | `200` | sim |
| 12 | `GET /pessoas?genero=MASCULINO` / `?interesse=TODOS` | `200`, filtra corretamente | sim, com ressalvas (item F) |
| 13 | `GET /pessoas?genero=INVALIDO` | **`500`** (`MethodArgumentTypeMismatchException`) | não — deveria ser `400` |
| 14 | `GET /pessoas/{id}/perfil` existente / inexistente | `200` / `404` | sim |
| 15 | `GET /pessoas/{id}/redes-sociais` | `403` sempre | sim (stub intencional) |
| 16 | `POST /likes?destinoId=<própria Pessoa.id>` | `400` "like em si mesmo" | sim |
| 17 | `POST /likes` Ana→Bruno | `200 {"match":false}` | sim |
| 18 | `POST /likes` Ana→Bruno de novo (sequencial) | `400 "Você já curtiu essa pessoa"` | sim — **antes era `500`**, corrigido pelo diff |
| 19 | `POST /likes` Bruno→Ana | `200 {"match":true,"message":"É um match!"}` | sim |
| 20 | `GET /matches/meus` Ana e Bruno | ambos veem o mesmo match (`pessoaId1/2` = `Pessoa.id`) | sim |
| 21 | `GET /likes/dados`, `/likes/recebidos` | `200`, IDs em espaço `Pessoa.id` | sim |
| 22 | `GET /matches/{id}/redes-sociais` membro / não membro / inexistente | `200` / `403` / `404` | sim |
| 23 | `POST /api/imagens/upload` PNG válido | `200`, URL `/api/imagens/proxy/tinder_ufs/<pessoaId>/<uuid>.png` | sim |
| 24 | `GET /api/imagens/minhas-imagens` | `200`, lista do dono | sim |
| 25 | Proxy: dono / match / terceiro sem match / sem token | `302` / `302` / `403` / `401` | sim |
| 26 | Proxy: publicId inexistente / `..%2f..%2fetc/passwd` / `x.env` | `404` / `400` / `400` | sim |
| 27 | Seguir o `Location` pré-assinado | `200 image/png` 800x800 (com MinIO em modo virtual-host) | sim (ver item G) |
| 28 | `PUT /matches/desfazer/{id}` por terceiro / por membro | `403` / `200` | sim |
| 29 | Após desfazer: proxy p/ ex-match e redes sociais | `403` / `403 Match inativo.` | sim |
| 30 | `DELETE /api/imagens/{id}` de outro / última imagem / com 2 imagens / inexistente | `403` / `400` / `200` / `404` | sim |
| 31 | `GET /tags`, `/tags/ativas` sem token; `POST /tags` com/sem token | `200`; `200` / `401` | sim |
| 32 | `DELETE /tags/{id}` autenticado (sem ADMIN) | **`302 Location: /oauth2/authorization/google`** | não (item B) |
| 33 | Usuário sem perfil: like / matches / upload / `GET /pessoas/me` | `400` "Perfil não encontrado" / `400` / `400` / `404` | sim |
| 34 | `DELETE /pessoas/me`, `DELETE /users/me` | `204`; token do usuário excluído passa a `401` | sim, com ressalvas (item I) |
| 35 | `GET /api-docs`, `/swagger-ui.html` | `200`; `302` — **não** é o redirect do springdoc para a UI, é o entry point do OAuth (`Location: /oauth2/authorization/google`; confirmado em execução na revisão de 2026-10-03). `/swagger-ui/index.html` → `200` | `/api-docs` sim; `/swagger-ui.html` **não** (bloqueado — R8) |
| 36 | `GET /actuator/health` sem / com token | `401` / **`500`** | não (item A) |

## Confirmação empírica das inconsistências conhecidas (`docs/ARQUITETURA.md`)

- **1. [CRÍTICO] `User.id` vs `Pessoa.id` em likes/matches — REFUTADO em runtime (resolvido).**
  Fluxos 16-29: auto-like barrado, like recíproco gera match visível aos dois lados,
  proxy e redes sociais liberados pelo match. Resíduo: `destinoId` não é validado — um
  like para `User.id` ou para ID inexistente (`idquenaoexiste123`) responde `200 "Like
  enviado com sucesso"` e grava um `Like` órfão (`LikeController.java:59-88`).
- **2. [CRÍTICO] Healthcheck aponta para endpoint inexistente — CONFIRMADO.** Não há
  `spring-boot-starter-actuator` no `pom.xml`; `/actuator/health` sem token → `401`, com
  token → `500` (`NoResourceFoundException` engolida como erro interno).
  `docker-compose.yml:69` (`curl -f .../actuator/health`) vai marcar o container como
  unhealthy sempre.
- **3. Suíte 23/35 falhando — REFUTADO (resolvido).** 35/35 hoje.
- **4. [ALTO] Role `ADMIN` inatingível — CONFIRMADO, e com um agravante em runtime (item B).**
- **5. [ALTO] Expiração do JWT ignora config — CONFIRMADO, agora de forma coerente:** o token
  real tem `exp - iat = 900 s` (`JwtService.java:22`, 15 min). As chaves
  `jwt.expiration`/`refresh-expiration` foram removidas do YAML neste diff, então não há
  mais config "ignorada"; mas o valor continua hardcoded e não há endpoint de refresh
  (os métodos de refresh foram removidos por estarem mortos).
- **6. [ALTO] CORS ignora config — CONFIRMADO.** Com `FRONTEND_URL=http://localhost:3000`,
  um preflight de `Origin: http://localhost:5173` recebe `200` +
  `Access-Control-Allow-Origin: http://localhost:5173` (lista hardcoded em
  `SecurityConfig.java:90-93`). Origem fora da lista (`https://evil.example.com`) → `403`.
- **7. [MÉDIO] Like duplicado → `500` — PARCIALMENTE RESOLVIDO.** Sequencial agora dá
  `400` (`LikeService.java:91`). Concorrente continua quebrado (item 8).
- **8. [MÉDIO] Índices únicos nunca criados — CONFIRMADO, com impacto real.**
  `getIndexes()` em `users`, `pessoa`, `like`, `match`, `imagem` mostra **apenas `_id`**.
  Teste: 8 `POST /likes` simultâneos Ana→Caio → `200 x7, 500 x1`, **7 documentos `Like`
  duplicados** no banco. Depois disso, o par fica quebrado de vez: novo like Ana→Caio →
  `500`, e Caio→Ana (que deveria gerar match) → **`500`**, `IncorrectResultSizeDataAccessException:
  ... returned non unique result`. Match entre os dois fica impossível. Unicidade de
  e-mail (`User`, `Pessoa`) só é garantida pela checagem prévia no service (sujeita à
  mesma corrida, não testado concorrentemente).
- **9. [MÉDIO] `@Transactional` em Mongo standalone — não testado diretamente**; o
  teste de concorrência acima mostra que `darLike` não é atômico na prática.
- **11. `TagController.create` mascara erros — não exercitado** (o caminho feliz deu `200`).
- **12. Testes sem isolamento de banco — CONFIRMADO como risco de ambiente** (ver
  "Segurança do ambiente"): o default via `.env` é produção.

## Achados novos desta rodada (não estavam em `docs/ARQUITETURA.md`)

- **B. `DELETE /tags/{id}` com usuário autenticado responde `302` para
  `/oauth2/authorization/google`, não `403`.** O `AccessDeniedHandler` faz
  `sendError(403)`; o dispatch de `/error` passa de novo pelo `AuthorizationFilter`
  (`anyRequest().authenticated()`, `SecurityConfig.java:69`) sem o `JwtFilter`
  (OncePerRequestFilter não reexecuta no dispatch de erro), vira anônimo e cai no entry
  point do `oauth2Login`. O teste MockMvc (`403`) não reproduz isso. Provavelmente afeta
  qualquer `sendError` do Spring (ex.: os `404` vazios observados vieram de
  `ResponseEntity`, não de `sendError`, por isso não foram afetados).
- **C. Enum inválido em query param → `500`** (`GET /pessoas?genero=INVALIDO`); o handler
  global não trata `MethodArgumentTypeMismatchException`.
- **D. `destinoId` de like não é validado** (fluxo 1 acima) — likes para IDs inexistentes
  são aceitos e persistidos.
- **E. Upload aceita conteúdo que não é imagem.** Um arquivo texto (`nao sou imagem`) com
  `Content-Type: image/png` → `200 "Upload realizado com sucesso"` e vai para o bucket
  (`ImagemService.validarArquivo`, `ImagemService.java:188-199`, só olha o MIME declarado;
  a falha de compressão é engolida em `ImagemService.java:53-56`). A extensão da chave vem
  do nome enviado pelo cliente (`getExtensao`, `ImagemService.java:201-204`). Além disso,
  a compressão **amplia** imagens pequenas (PNG 50x50 / 136 B → 800x800 / 10.389 B).
- **F. Listagem `GET /pessoas` e `GET /pessoas/{id}/perfil` expõem `email` de outros
  usuários, incluem o próprio solicitante na lista e retornam
  `cienciaResponsabilidade:false` para perfis que têm `true`** (mapeamento do
  `PessoaPerfilDTO` em `PessoaService.convertToPerfilDTO`).
- **G. `S3Presigner` não usa path-style, diferente do `S3Client`**
  (`CloudflareR2Config.java:28-49`): a URL pré-assinada sai como
  `http://test-bucket.localhost:9000/...`. Com MinIO padrão isso dá `400
  InvalidBucketName`; com `MINIO_DOMAIN=localhost` dá `200`. Em R2 real o estilo
  virtual-host deve funcionar, então **não é bug confirmado em produção** — mas é um
  risco de ambiente local/MinIO e uma inconsistência de configuração.
- **H. Prefixo de chave legado `tinder_ufs/`** (`ImagemService.java:60`) sobreviveu ao
  rename para PairUp — cosmético, mas deve ser mantido se já houver objetos em produção.
- **I. Exclusões não cascateiam.** `DELETE /users/me` deixa a `Pessoa` do usuário ativa e
  **ainda listada em `GET /pessoas`** (perfil órfão de conta inexistente); `DELETE
  /pessoas/me` deixa os `Like` emitidos pela pessoa no banco.

## Regressões novas

**Nenhuma.** As mudanças do working tree foram cobertas:
- remoção do check de `cienciaResponsabilidade` em `PessoaService.create` → validação
  continua no controller (`PessoaController.java:93-98`), fluxo 7 confirma `400`;
- remoção do check de auto-like em `LikeService.darLike` → controller barra (fluxo 16);
- remoção de métodos mortos (`JwtService` refresh, `SecurityUtils.getCurrentRequest`,
  repositórios/DTOs/services) → compila e nenhuma rota quebrou;
- remoção das chaves `app.*`/`jwt.expiration` do YAML → nenhum `@Value` falhou no startup.

### Riscos de setup introduzidos pelo diff (não são falhas de teste)
- `mvnw`/`.mvn/wrapper` removidos: quem não tiver Maven instalado não consegue buildar
  localmente; verificar se `README.md`, `Dockerfile` e qualquer CI ainda chamam `./mvnw`.
  (`Dockerfile` builda em imagem própria — não testado nesta rodada.)
- `Dockerfile.test` removido: não há mais um caminho containerizado para rodar a suíte.

## O que não pôde ser testado

- **Login Google/OAuth2** — exige credenciais e navegador reais; só confirmado que
  `/oauth2/authorization/google` existe (é o destino do `302`).
- **R2/Cloudflare real e Mongo Railway** — deliberadamente não usados. Upload/proxy foram
  validados contra MinIO; comportamento de URL virtual-host no R2 real (item G) não
  verificado.
- **Build/execução via `docker-compose`/`Dockerfile`** — não executado (a stack sobe o
  app apontando para variáveis do `.env`, ou seja, produção).
- **Corrida em cadastro de e-mail** (`POST /users`/`POST /pessoas` concorrentes) — não
  testada; pela ausência de índices (item 8) provavelmente também duplica.
- **Expiração real do token (15 min)** — só verificado pelo `exp` do JWT, não esperado.
- **Limite de 5 MB e limite de imagens por usuário** — não exercitados.
