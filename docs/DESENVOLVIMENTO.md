# PairUp — Desenvolvimento

Como configurar, rodar, testar, publicar e mexer no back. Rotas em [`API.md`](API.md);
arquitetura e riscos em [`ARQUITETURA.md`](ARQUITETURA.md).

## 1. Pré-requisitos

- JDK 21 e Maven (`mvn`; o projeto não tem `mvnw`).
- Docker, para o Mongo local e o ambiente de testes.

## 2. Configuração (`.env`)

Copie `.env.example` para `.env` na raiz de `Tinder_ufs/`. O `spring-dotenv` lê esse arquivo.
**Nunca commite o `.env`.**

| Variável | Obrigatória | Para quê |
|---|---|---|
| `MONGODB_URI` | não (padrão `mongodb://localhost:27017/PAIRUP`) | Conexão com o banco |
| `JWT_SECRET` | sim | Assina os tokens; **mínimo 32 bytes**, senão a aplicação não sobe. Gere com `openssl rand -base64 48` |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | sim | Login Google |
| `BASE_URL` | sim | Endereço público do back; o Google chama `${BASE_URL}/login/oauth2/code/google` |
| `FRONTEND_URL` | não (padrão `http://localhost:5173`) | Para onde o login Google redireciona; **uma única URL** |
| `CORS_ALLOWED_ORIGINS` | não (padrão `http://localhost:5173,https://pairup-flax.vercel.app`) | Origens do front, separadas por vírgula, sem `*` |
| `CF_R2_ENDPOINT`, `CF_R2_ACCESS_KEY`, `CF_R2_SECRET_KEY`, `CF_R2_BUCKET` | sim | Acesso ao Cloudflare R2 |
| `CF_R2_PUBLIC_URL` | não | URL pública das fotos; sem ela o feed não mostra fotos antes do match |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM` | não | E-mail de match; sem `MAIL_HOST` ou `MAIL_FROM`, nada é enviado |
| `SWAGGER_ENABLED` | não (padrão `false`) | `true` liga o Swagger; só em desenvolvimento |
| `RATE_LIMIT_ENABLED`, `RATE_LIMIT_AUTH`, `RATE_LIMIT_GERAL` | não (padrão `true`, 10, 300) | Limite por IP por minuto |
| `MONGO_ROOT_USER`, `MONGO_ROOT_PASSWORD` | só no `docker-compose` | Usuário e senha do Mongo; a senha é obrigatória |
| `PORT` | não (padrão 8080) | Porta HTTP |

Ajustes que não são variáveis ficam em `src/main/resources/application.yml`: bloqueio de login
(`seguranca.login`, 5 falhas e 15 min) e compressão das fotos (`file.compress`, 800×800 e
qualidade 0.7).

## 3. Rodar localmente

```bash
cd Tinder_ufs
docker compose up -d mongodb        # Mongo em 127.0.0.1:27017 (exige MONGO_ROOT_PASSWORD)
mvn spring-boot:run                 # API em http://localhost:8080
```

Com o compose, use no `.env`:
`MONGODB_URI=mongodb://admin:<senha>@localhost:27017/PAIRUP?authSource=admin`.

> **Cuidado:** a aplicação conecta no `MONGODB_URI` do `.env`. Se ele apontar para o banco de
> produção (Railway), o que você fizer localmente vai para produção. Para desenvolver, use um
> `.env` com o Mongo local.

Para ver a API no navegador, ponha `SWAGGER_ENABLED=true` no `.env` e abra
`http://localhost:8080/swagger-ui.html`. Para rotas protegidas, cole o token de
`POST /users/login` no botão **Authorize**.

O front (`tinder/`) roda com `npm run dev` em `http://localhost:5173`, com
`VITE_API_BASE=http://localhost:8080`.

## 4. Testes

Os testes usam o perfil `test` (`src/test/resources/application-test.yml`), com valores
literais: Mongo em `localhost:27018`, S3 em `localhost:9000` (bucket `test-bucket`), JWT e
Google falsos, e-mail e rate limit desligados. Por isso **o `.env` não é usado nos testes**:
sem o ambiente abaixo de pé, eles só falham ao conectar.

```bash
# Mongo e MinIO descartáveis
docker run -d --rm --name pairup_test_mongo -p 127.0.0.1:27018:27017 mongo:7.0
docker run -d --rm --name pairup_test_minio -p 127.0.0.1:9000:9000 \
  -e MINIO_ROOT_USER=test-access-key -e MINIO_ROOT_PASSWORD=test-secret-key \
  quay.io/minio/minio server /data
docker run --rm --network host --entrypoint sh quay.io/minio/mc -c \
  "mc alias set t http://127.0.0.1:9000 test-access-key test-secret-key && mc mb -p t/test-bucket"

mvn clean test

docker stop pairup_test_mongo pairup_test_minio     # apaga tudo (--rm)
```

Para usar outras portas, sobrescreva com `SPRING_DATA_MONGODB_URI` e
`CLOUDFLARE_R2_ENDPOINT` / `CLOUDFLARE_R2_PUBLIC_URL` (as `CF_R2_*` não valem nos testes).

**Estado em 2026-11-17: 80 testes, 0 falhas.**

| Classe | O que cobre |
|---|---|
| `UserControllerTest` | Cadastro, login, `/users/me`, troca de e-mail, cadastro que tenta sobrescrever conta |
| `PessoaControllerTest` | Perfil, feed, filtros, redes sociais fora do feed, termos, tags |
| `LikeControllerTest`, `LikeServiceTest` | Like, auto-like, like duplicado, match |
| `MatchControllerTest` | Matches, contatos só para participantes, desfazer |
| `DislikeEExclusaoTest` | Passar e exclusão em cascata |
| `MatchEmailServiceTest` | E-mail de match (SMTP mockado) |
| `ImagemControllerTest` | Upload, SVG disfarçado recusado, foto principal, apagar, dono |
| `TagControllerTest` | Leitura pública; usuário comum não cria nem sobrescreve tag |
| `OAuth2SuccessHandlerTest` | Login Google: e-mail verificado, conta com senha, `#token=` |
| `SegurancaTest` | Erros sem detalhes internos, JWT, cabeçalhos, validação, senha |
| `OpenApiDocsTest` | Documentação da API |
| `PairUpApplicationTests` | Contexto sobe |

Os testes de controller usam MockMvc com JWT real (`jwtService.generateToken`), passando pelo
`JwtFilter` de verdade. O banco não é limpo entre execuções: os testes geram e-mails únicos.

## 5. Publicar

**Não há CI.** Antes do push, rode os testes acima.

O back roda no **Railway** a partir do `Dockerfile`:

1. **Build:** `maven:3.9.6-eclipse-temurin-21` roda `mvn package -DskipTests` (os testes não
   rodam no deploy).
2. **Runtime:** `eclipse-temurin:21-jre-alpine`, usuário não-root, `-Xmx512m`.

As variáveis da seção 2 ficam no painel do Railway. Em produção:

- `JWT_SECRET` com 32 bytes ou mais;
- `CORS_ALLOWED_ORIGINS` com os domínios reais do front;
- **não** defina `SWAGGER_ENABLED`;
- `FRONTEND_URL` com uma única URL.

**Ordem entre back e front.** Endpoint novo → publique o back primeiro. Mudança que o front
novo entende nos dois formatos → publique o front primeiro. Variável nova → configure no
Railway antes do push do back.

Como commitar e as mensagens usadas no projeto: `pairup/COMO_COMMITAR.md` (fora do
repositório).

## 6. Mexendo no código

### Convenções
- **Pacote por funcionalidade** (`user/`, `pessoa/`, `match/`, `imagem/`, `tag/`); o que é
  transversal vai em `shared/`.
- **Controller → Service → Repository.** Controller só traduz HTTP; regras ficam no service.
- **Entrada sempre por DTO** com Bean Validation (`@Size`, `@Pattern`, `@NotBlank`...), nunca
  pela entidade: assim o cliente não consegue mandar `id` e sobrescrever registros.
- **Quem age vem do token:** `SecurityUtils.getUserIdOrThrow()` → `User.id` →
  `pessoaService.getPessoaIdOrThrow(...)` → `Pessoa.id`. Nunca receba esse id do cliente.
- **Erros:** lance `ResponseStatusException(status, "mensagem para o usuário")` e deixe o
  `GlobalExceptionHandler` montar a resposta. Não devolva `e.getMessage()` de exceções
  genéricas.
- **Logs:** valores vindos do cliente passam por `SecurityUtils.paraLog(...)`. Sem senhas,
  tokens nem dados pessoais desnecessários.
- Código e comentários em português, como o resto do projeto.

### Checklist para uma rota nova
1. DTO de entrada com validação; DTO de saída sem dados privados.
2. Rota protegida por padrão; se for pública, inclua em `SecurityConfig` **e** em
   `JwtFilter.PUBLIC_ROUTES`.
3. Confira se quem pede é dono ou participante do recurso.
4. Anotações do Swagger (`@Operation`, `@ApiResponses`).
5. Teste de controller com JWT real, incluindo o caso de outra pessoa tentando acessar.
6. Documente em [`API.md`](API.md) e no `types.ts` do front.

### Dependências
O Spring Boot está na 3.2.3, que tem CVEs conhecidas. A 3.2.12 foi testada e quebra
`ImagemRepository.findByPessoaInAndAtivaTrue` (as fotos somem do feed). Atualize junto com a
reescrita dessa consulta e rode a suíte inteira.
