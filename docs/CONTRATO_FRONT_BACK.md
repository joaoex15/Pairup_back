# Contrato entre front e back

Como o front (`tinder/`, SvelteKit) conversa com esta API. A referência interativa de cada
endpoint é o Swagger (`/swagger-ui.html`); este documento registra o que o Swagger não mostra.

No front, os tipos ficam em `src/lib/services/types.ts` e as chamadas em
`src/lib/services/api.ts`. **Mudou um DTO aqui? Mude `types.ts` também** — nada gera esse
arquivo automaticamente.

## Autenticação

- Login por senha: `POST /users/login` devolve `{ token, userId, email }`.
- Login Google: `GET /oauth2/authorization/google` → `OAuth2SuccessHandler` →
  `{FRONTEND_URL}/auth/callback?token=...`. O primeiro login cria uma `Pessoa` mínima, com
  `perfilCompleto=false`; por isso o front completa o cadastro com `PUT /pessoas/me`, e não
  com `POST /pessoas` (que daria 409).
- O front guarda o token no `localStorage` e envia `Authorization: Bearer`.
- O token dura **15 minutos** e não há refresh. Um 401 limpa a sessão e volta para o login.
- CORS: as origens estão fixas em `SecurityConfig.corsConfigurationSource()`. Um domínio novo
  do front precisa entrar nessa lista.

## Dois ids diferentes

`User.id` é a conta e `Pessoa.id` é o perfil. O JWT carrega o `User.id`; likes e matches usam
`Pessoa.id`. O front pega o próprio `Pessoa.id` em `GET /pessoas/me`.

## Endpoints usados pelo front

| Tela | Chamada | Retorno |
|---|---|---|
| Login | `POST /users/login`, `POST /users` | token / `User` sem senha |
| Callback Google | `GET /users/me`, `GET /pessoas/me` | `User`, `PessoaCompletaDTO` |
| Onboarding | `GET /tags/ativas`, `POST /pessoas` ou `PUT /pessoas/me`, `PATCH /pessoas/me/ciencia-responsabilidade`, `POST /api/imagens/upload` | `Pessoa`, `ImagemUploadResponseDTO` |
| Home | `GET /pessoas/me`, `GET /pessoas`, `GET /matches/meus`, `GET /likes/dados`, `GET /likes/recebidos`, `POST /likes?destinoId=` | `Page<...>`, `{ match, message }` |
| Matches | `GET /pessoas/{id}/perfil`, `GET /matches/{id}/redes-sociais`, `PUT /matches/desfazer/{id}` | `PessoaPerfilDTO`, `PessoaRedesSociaisDTO` |
| Perfil | `PUT /pessoas/me`, `DELETE /pessoas/me`, `DELETE /users/me`, `DELETE /api/imagens/{id}` | — |

## Regras de dados

- **Tags**: o envio usa **IDs** (até 5, só ativas; senão 400). As respostas trazem **nomes**.
- **`PUT /pessoas/me`**: campo ausente mantém o valor; string vazia apaga a rede social; lista
  vazia remove as tags. Esse PUT não altera `cienciaResponsabilidade`: use o `PATCH`.
- **`perfilCompleto`**: calculado em `PessoaCompletaDTO`. Exige nome, curso, data de
  nascimento, gênero, interesse e ciência dos termos. Sem isso, o front manda para `/onboarding`.
- **`POST /pessoas` e `PUT /pessoas/me`** devolvem a entidade `Pessoa`, não `PessoaPerfilDTO`,
  embora o front só use os campos em comum.
- **Foto principal**: só é escolhida no upload (`perfil=true`). Não existe endpoint para trocar
  a principal de uma foto já salva. Sem principal, a foto enviada assume o lugar, e ao apagar a
  principal a primeira foto ativa é promovida.
- **Curso**: o back aceita perfil sem curso, mas `perfilCompleto` exige. Por isso o front o trata
  como obrigatório no onboarding e no perfil.
- **Recuperar senha**: não existe endpoint, e o front não oferece a opção.
- **Fotos no feed**: aparecem antes do match. Exige `CF_R2_PUBLIC_URL` no back; sem ela, as URLs
  são do proxy, que responde 403 a quem não tem match (R30).
- **Feed**: `GET /pessoas` não filtra por compatibilidade nem por quem já foi avaliado. O front
  busca uma página de 100 e filtra localmente.

## Aviso de match

Quando `POST /likes` responde `match: true`, o front mostra o aviso na tela para quem acabou de
curtir. O back envia um e-mail para a outra pessoa, no endereço da conta (`User.email`), de forma
assíncrona: uma falha no envio só aparece no log e não afeta o like.

## Formato de erro

Hoje há quatro formatos. `extrairErro` (`api.ts`) entende todos:

| Origem | Corpo |
|---|---|
| `GlobalExceptionHandler`, `JwtFilter`, `UserController` | `{ "erro": "..." }` |
| Validação `@Valid` | `{ "erro": "Dados inválidos", "campos": ["campo: motivo"] }` |
| `PessoaController` | `{ "error": "..." }` |
| `ImagemController` | `{ "mensagem": "...", "sucesso": false }` |

## Configuração

| Lado | Variável | Para quê |
|---|---|---|
| Front | `VITE_API_BASE` | URL desta API |
| Back | `FRONTEND_URL` | Destino do redirect depois do login Google |
| Back | `BASE_URL` | Monta o `redirect-uri` do Google |
| Back | `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM` | SMTP do aviso de match; sem `MAIL_HOST` ou `MAIL_FROM`, nada é enviado |
