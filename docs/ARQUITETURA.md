# PairUp — Arquitetura do back-end

Como a API é montada, por que foi montada assim e o que ainda está em aberto. O que cada rota
recebe e devolve está em [`API.md`](API.md); como rodar, testar e publicar, em
[`DESENVOLVIMENTO.md`](DESENVOLVIMENTO.md).

> Estado de 2026-11-17. As versões antigas deste documento (com o histórico completo dos
> riscos R1–R45 e as referências `arquivo:linha` de cada um) estão no histórico do git.

## 1. Visão geral

API REST de um app de paquera para a comunidade universitária. Ela é a única porta de acesso
ao banco e às fotos: o front (`tinder/`, SvelteKit) só fala com ela.

```
Navegador (front SvelteKit, Vercel)
   │  HTTPS + Authorization: Bearer <JWT>
   ▼
API Spring Boot (Railway) ──► MongoDB (dados)
   │                       └► Cloudflare R2 (fotos, API S3)
   │                       └► SMTP (e-mail de match, opcional)
   ▼
Google OAuth2 (login com Google)
```

## 2. Stack

| Camada | Tecnologia |
|---|---|
| Linguagem / framework | Java 21, Spring Boot 3.2.3 (Web, Validation, Security, OAuth2 Client, Mail) |
| Banco | MongoDB 7 via Spring Data MongoDB |
| Autenticação | JWT HS256 (jjwt 0.11.5), 15 min, sem refresh; login Google (OIDC); BCrypt custo 12 |
| Fotos | Cloudflare R2 via AWS SDK v2 (S3); MinIO nos testes |
| Imagens | Thumbnailator + ImageIO (validação e re-codificação) |
| Documentação da API | springdoc-openapi 2.3.0 (desligado em produção) |
| Configuração | `application.yml` + `.env` (spring-dotenv) |
| Build / container | Maven; Dockerfile multi-stage (JRE 21 Alpine, usuário não-root) |

## 3. Organização do código

Pacotes **por funcionalidade**. Dentro de cada um, o padrão é Controller → Service →
Repository: o controller recebe a requisição e descobre quem está agindo, o service aplica as
regras, o repository fala com o Mongo.

```
src/main/java/com/example/pairup/
├── PairUpApplication.java     ponto de entrada (@EnableAsync para o e-mail)
├── user/                      conta de acesso: cadastro, login, alterar e apagar conta
├── pessoa/                    perfil, feed e exclusão em cascata
├── match/                     likes, dislikes, matches e e-mail de match
├── imagem/                    upload, validação, proxy e remoção de fotos
├── tag/                       interesses que aparecem nos perfis
└── shared/
    ├── config/                SecurityConfig, OAuth2SuccessHandler, R2, OpenAPI
    ├── security/              JwtFilter, JwtService, RateLimitFilter, LoginAttemptService,
    │                          SecurityUtils, CustomUserDetailsService
    └── exception/             GlobalExceptionHandler
```

Os pacotes não são independentes: `pessoa` ↔ `imagem` e `pessoa` ↔ `match` importam um ao
outro (R45).

## 4. Modelo de dados

| Coleção | O que guarda | Campos principais |
|---|---|---|
| `users` | Conta de acesso | `email` (minúsculas), `password` (BCrypt; nulo em conta Google), `provider` (`google` ou nulo), `nome` |
| `pessoa` | Perfil | `usuarioId`, `nome`, `curso`, `dataNasc`, `genero`, `interesse`, `descricao`, `tags` (@DBRef), `instagram`, `whatsapp`, `telegram`, `cienciaResponsabilidade`, `ativo`, `email` (cópia do da conta) |
| `imagem` | Foto | `pessoa` (@DBRef lazy), `url`, `publicId` (chave no R2), `perfil` (principal), `ativa`, `mimeType`, `tamanhoBytes` |
| `like` | Curtida | `pessoaOrigemId`, `pessoaDestinoId`, `ativo` |
| `dislike` | Perfil dispensado | `pessoaOrigemId`, `pessoaDestinoId`, `data` |
| `match` | Match | `pessoaId1`, `pessoaId2`, `ativo` |
| `tag` | Interesse disponível | `nome`, `descricao`, `categoria`, `ativa` |

**Conta × perfil.** `users` responde "quem pode entrar"; `pessoa` responde "quem aparece no
app". O JWT carrega o `User.id`; likes, matches e fotos usam o `Pessoa.id`. O back sempre
encontra a `Pessoa` a partir do token (`findByUsuarioId`), então **nenhuma rota recebe do front
o id de quem está agindo**.

**Perfil completo.** Nome, curso, idade ≥ 18, gênero, interesse e aceite dos termos
(`Pessoa.temCadastroCompleto()`). Só perfis completos aparecem no feed e podem curtir. O login
Google cria um perfil mínimo, que o front completa no onboarding.

## 5. Caminho de uma requisição

```
requisição
  │
  ├─ CorsFilter               origem em CORS_ALLOWED_ORIGINS? preflight responde aqui
  ├─ RateLimitFilter          IP passou do limite? → 429
  ├─ JwtFilter                rota pública? senão valida o Bearer → 401
  │                           coloca o User.id no SecurityContext
  ├─ Autorização (Security)   rota exige papel? → 401/403 em JSON
  ├─ Controller               SecurityUtils.getUserIdOrThrow() → User.id
  │                           PessoaService → Pessoa.id de quem age
  ├─ Service                  regras: idade, termos, duplicidade, match...
  ├─ Repository               Spring Data MongoDB
  └─ GlobalExceptionHandler   exceção → JSON com o status certo, sem detalhes internos
```

## 6. Fluxos principais

**Cadastro e login por senha.** `POST /users` grava a conta com BCrypt; `POST /users/login`
devolve o JWT. O perfil é criado depois, com `POST /pessoas`.

**Login Google.** O Spring faz o fluxo OAuth2 (o `state` fica numa sessão HTTP temporária). O
`OAuth2SuccessHandler` exige e-mail confirmado pelo Google, **não entra em conta criada com
senha** (o cadastro local não confirma e-mail, então vincular entregaria a conta a quem a
registrou), cria conta e perfil mínimo se faltarem, invalida a sessão e redireciona para
`{FRONTEND_URL}/auth/callback#token=...`. O token vai no fragmento para não aparecer em logs
nem no `Referer`.

**Like e match.** `POST /likes` grava a curtida; se a curtida inversa existe, cria o `Match` e
dispara um e-mail assíncrono para a outra pessoa. Contatos (redes sociais) só saem por
`GET /matches/{id}/redes-sociais`, para quem está num match ativo.

**Fotos.** O upload descobre o tipo pelos bytes, re-codifica e grava no R2 em
`tinder_ufs/{pessoaId}/{uuid}.{ext}`. Com `CF_R2_PUBLIC_URL`, o feed usa a URL pública direto
(é o que permite mostrar fotos antes do match, já que `<img>` não manda token). Sem ela, a URL
é a do proxy, que só libera para o dono ou quem tem match.

**Exclusão.** Apagar o perfil remove fotos (R2 e banco), likes, dislikes e matches dados e
recebidos; apagar a conta apaga também o perfil. Não é atômico (sem transações), mas repetir a
chamada termina a limpeza.

## 7. Segurança

| Ameaça | Defesa | Onde |
|---|---|---|
| Agir em nome de outra pessoa (IDOR) | Id de quem age sai sempre do JWT; checagem de dono/participante em fotos e matches | `SecurityUtils`, controllers |
| Força bruta de senha | 10 req/min por IP em login e cadastro; 5 falhas por e-mail → bloqueio de 15 min | `RateLimitFilter`, `LoginAttemptService` |
| Descobrir quais e-mails têm conta | Login sempre roda um BCrypt e dá a mesma resposta | `UserService.login` |
| Abuso geral (scraping, spam) | 300 req/min por IP | `RateLimitFilter` |
| Token forjado ou de outro sistema | HS256, emissor `pairup-api`, segredo ≥ 32 bytes checado na subida | `JwtService` |
| Token vazado por URL | `#token=` no fragmento; front limpa a URL | `OAuth2SuccessHandler` |
| Tomada de conta pelo Google | Só e-mail verificado; nunca vincula a conta com senha | `OAuth2SuccessHandler` |
| Arquivo malicioso no upload (XSS, bomba de descompressão) | Tipo pelos bytes; re-codificação; limite de resolução e de 6 fotos; Content-Type do servidor | `ImageCompressionUtil`, `ImagemService` |
| Vazamento de dados privados | DTOs de saída sem e-mail, nascimento e contatos de terceiros | `PessoaPerfilDTO`, `MatchController` |
| Mass assignment | DTOs de entrada sem `id`/`provider` | `UserCreateDTO`, `TagRequestDTO`, `PessoaRequestDTO` |
| Links maliciosos em redes sociais | Só usuário/número, por regex | `PessoaRequestDTO` |
| Vazamento de detalhes internos | `include-message: never`, handlers 4xx, mensagens genéricas | `GlobalExceptionHandler`, `application.yml` |
| Clickjacking, *sniffing*, Referer | `X-Frame-Options: DENY`, `nosniff`, HSTS, `Referrer-Policy: no-referrer` | `SecurityConfig` |
| CORS aberto | Lista em `CORS_ALLOWED_ORIGINS`, sem `*`, sem credenciais | `SecurityConfig` |
| Log forging | Valores do cliente sanitizados | `SecurityUtils.paraLog` |
| Mapa da API exposto | Swagger só com `SWAGGER_ENABLED=true` | `application.yml` |

CSRF fica desligado de propósito: a API não usa cookie de autenticação, só o header
`Authorization`.

## 8. Decisões de projeto

- **O feed não filtra compatibilidade nem quem já foi avaliado.** `GET /pessoas` devolve
  perfis completos e ativos; o front tira os incompatíveis, já curtidos, dispensados e com
  match. Simples, mas não escala para muitos usuários.
- **Fotos do feed são públicas** quando `CF_R2_PUBLIC_URL` está definida: quem tiver a URL vê
  a foto sem login (R6). Foi a forma escolhida de mostrar fotos antes do match.
- **Sem refresh token.** O JWT dura 15 min; depois disso a pessoa entra de novo (R11).
- **Rate limit em memória.** Basta para uma instância; com várias réplicas, cada uma conta
  separado.
- **Tags fixas.** Só ADMIN cria tags, e ninguém tem esse papel: elas são cadastradas no banco.
- **E-mail de match assíncrono.** Falha no SMTP só vai para o log e não afeta o like.

## 9. Riscos em aberto

Numeração mantida da análise original (os que faltam já foram resolvidos).

| # | Gravidade | Risco | Efeito |
|---|---|---|---|
| R3 | Alta | `@Transactional` não tem efeito: não há transaction manager e o Mongo não é replica set | Operações de várias etapas (like + match, exclusão) não são atômicas |
| R4 | Alta | Índices únicos não são criados (`auto-index-creation` desligado) | Concorrência pode duplicar e-mails, likes e matches; depois `findBy...` dá 500 |
| R37 | Média | `Match` não tem índice único | Dois likes simultâneos podem criar dois matches para o mesmo par |
| R43 | Alta | `tokens/StoredCredential` (credencial antiga do Google Drive) está no histórico do git | Revogar o token na conta Google |
| — | Alta | Spring Boot 3.2.3 tem CVEs conhecidas; a 3.2.12 quebra `findByPessoaInAndAtivaTrue` (fotos do feed por `@DBRef`) | Atualizar junto com a reescrita dessa consulta |
| R6 | Média | Fotos acessíveis pela URL pública do R2 | Ver §8 |
| R11 | Média | Sem refresh token | Login de novo a cada 15 min |
| R12/R31 | Média | Login Google cria perfil incompleto direto pelo repositório; `POST /pessoas` dá 409 para quem entrou pelo Google | O front usa `PUT /pessoas/me` nesse caso |
| R40 | Baixa | Ex-match ainda vê fotos por até 15 min (URL assinada) | — |
| R7 | Baixa | O healthcheck do Docker chama `/actuator/health`, que não existe | Container marcado como unhealthy |
| R15 | Baixa | Ninguém tem `ROLE_ADMIN` | `POST`/`DELETE /tags` inacessíveis |
| R16 | Baixa | Quatro formatos de erro (`erro`, `error`, `mensagem`, texto puro) | O front precisa entender todos (`extrairErro`) |
| R21 | Baixa | `GET /pessoas/{id}/redes-sociais` é um stub que sempre dá 403 | Aparece no Swagger |
| R22 | Baixa | Fuso: `TZ=America/Sao_Paulo` no Dockerfile × `-Duser.timezone=UTC` no compose | Idade pode variar um dia perto do aniversário |
| R26 | Baixa | Métodos auxiliares viram campos JSON (`quantidadeGaleria`, `imagensGaleria`, `maiorDeIdade`) | Campos não documentados nas respostas |
| R27 | Baixa | Like nunca é desativado | Ramo de reativação morto |
| R39 | Baixa | `PessoaPerfilDTO.cienciaResponsabilidade` sempre `false`; o feed inclui o próprio usuário | O front filtra |
| R42 | Baixa | `S3Presigner` sem path-style | URLs assinadas não funcionam com MinIO local padrão |
| R44 | Baixa | `_class` de documentos antigos aponta para pacotes antigos | Deve funcionar, mas não foi testado com dados reais |
| R45 | Baixa | Ciclos de import entre pacotes | Módulos não extraíveis |

Sem moderação: não há denúncia nem bloqueio de usuários, nem confirmação de e-mail no cadastro
local.
