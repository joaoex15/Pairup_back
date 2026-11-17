# Contrato entre front e back

Como o front (`tinder/`, SvelteKit) conversa com esta API. A referência interativa de cada
endpoint é o Swagger (`/swagger-ui.html`); este documento registra o que o Swagger não mostra.

No front, os tipos ficam em `src/lib/services/types.ts` e as chamadas em
`src/lib/services/api.ts`. **Mudou um DTO aqui? Mude `types.ts` também** — nada gera esse
arquivo automaticamente.

## Autenticação

- Login por senha: `POST /users/login` devolve `{ token, userId, email }`.
- Login Google: `GET /oauth2/authorization/google` → `OAuth2SuccessHandler` →
  `{FRONTEND_URL}/auth/callback#token=...`. O token vem no **fragmento** (`#`), que não chega a
  nenhum servidor nem ao `Referer`; o callback lê `location.hash` (aceita `?token=` só por
  compatibilidade) e limpa a URL antes de qualquer outra coisa. O primeiro login cria uma `Pessoa` mínima, com
  `perfilCompleto=false`; por isso o front completa o cadastro com `PUT /pessoas/me`, e não
  com `POST /pessoas` (que daria 409).
- O front guarda o token no `localStorage` e envia `Authorization: Bearer`.
- O token dura **15 minutos** e não há refresh. Um 401 limpa a sessão e volta para o login.
- CORS: as origens vêm de `CORS_ALLOWED_ORIGINS` no back (lista separada por vírgula). Um
  domínio novo do front só precisa entrar nessa variável. O CORS não libera credenciais: o
  front usa `credentials: 'omit'` e manda o token no header.
- Limites: o back responde **429** quando um IP passa de 10 requisições/min em login e
  cadastro ou de 300/min no resto, e quando um e-mail erra a senha 5 vezes seguidas (bloqueio
  de 15 min). O front mostra a mensagem de `erro`.

## Dois ids diferentes

`User.id` é a conta e `Pessoa.id` é o perfil. O JWT carrega o `User.id`; likes e matches usam
`Pessoa.id`. O front pega o próprio `Pessoa.id` em `GET /pessoas/me`.

## Endpoints usados pelo front

| Tela | Chamada | Retorno |
|---|---|---|
| Login | `POST /users/login`, `POST /users` (senha de 8 a 72) | token / `User` sem senha |
| Callback Google | `GET /users/me`, `GET /pessoas/me` | `User`, `PessoaCompletaDTO` |
| Onboarding | `GET /tags/ativas`, `POST /pessoas` ou `PUT /pessoas/me`, `PATCH /pessoas/me/ciencia-responsabilidade`, `POST /api/imagens/upload` | `Pessoa`, `ImagemUploadResponseDTO` |
| Home | `GET /pessoas/me`, `GET /pessoas`, `GET /matches/meus`, `GET /likes/dados`, `GET /likes/recebidos`, `POST /likes?destinoId=` | `Page<...>`, `{ match, message }` |
| Matches | `GET /pessoas/{id}/perfil`, `GET /matches/{id}/redes-sociais`, `PUT /matches/desfazer/{id}` | `PessoaPerfilDTO`, `PessoaRedesSociaisDTO` |
| Perfil | `PUT /pessoas/me`, `DELETE /pessoas/me`, `DELETE /users/me`, `DELETE /api/imagens/{id}` | — |

## Regras de dados

- **Senha**: o cadastro exige 8 a 72 caracteres (`RegisterForm` valida 8). O login aceita
  senhas antigas de 6 (`LoginForm` continua validando 6).
- **E-mail do perfil**: `POST /pessoas` ignora o `email` do corpo e usa o da conta.
- **Redes sociais**: só o usuário ou número, sem link. Instagram `@?[A-Za-z0-9._]{1,30}`,
  Telegram `@?[A-Za-z0-9_]{1,32}`, WhatsApp de 8 a 20 caracteres entre dígitos, espaço e
  `()+-` (a máscara `(00) 00000-0000` do front cabe). Fora disso → 400 com `campos`.
- **Tamanhos**: `nome` até 100, `curso` até 150, `descricao` até 500; `dataNasc` no passado e
  a no máximo 100 anos.
- **Fotos**: até 6 ativas (igual a `FotosUploader.maxFotos`), 5 MB cada, só JPEG, PNG ou WebP
  **de verdade** — o back olha os bytes, não o Content-Type. Resolução acima de 8000 px por
  lado → 400.
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
  busca páginas de 100 (`sort=id`, para a ordem ser estável) sob demanda, sempre que restam menos
  de 5 pessoas no deck, e filtra localmente. Matches, likes e dislikes são lidos por completo
  (todas as páginas) para tirar do deck quem já teve interação.
- **Dislike**: `POST /dislikes?destinoId=` responde 204 e é idempotente; 400 para si mesmo ou
  perfil inexistente. `GET /dislikes/dados` é paginado como os likes.
- **`PUT /users/me`**: corpo `{ nome?, email?, senhaAtual? }`. Trocar o e-mail exige
  `senhaAtual` (403 se errada), dá 409 se o e-mail já é de outra conta e 400 em conta Google.
- **Login Google com erro**: o back redireciona para `/?erro=conta_com_senha`,
  `/?erro=email_nao_verificado` ou `/?erro=google_falhou` (falha no próprio fluxo OAuth2), e a
  tela de login mostra a mensagem.
- **Exclusão**: `DELETE /pessoas/me` apaga fotos, likes, dislikes e matches da pessoa;
  `DELETE /users/me` também apaga o perfil.

## Aviso de match

Quando `POST /likes` responde `match: true`, o front mostra o aviso na tela para quem acabou de
curtir. O back envia um e-mail para a outra pessoa, no endereço da conta (`User.email`), de forma
assíncrona: uma falha no envio só aparece no log e não afeta o like.

## Formato de erro

Hoje há quatro formatos. `extrairErro` (`api.ts`) entende todos:

| Origem | Corpo |
|---|---|
| `GlobalExceptionHandler`, `JwtFilter`, `SecurityConfig` (401/403), `RateLimitFilter` (429), `UserController` | `{ "erro": "..." }` |
| Validação `@Valid` | `{ "erro": "Dados inválidos", "campos": ["campo: motivo"] }` |
| `PessoaController` | `{ "error": "..." }` |
| `ImagemController` | `{ "mensagem": "...", "sucesso": false }` |

## Configuração

| Lado | Variável | Para quê |
|---|---|---|
| Front | `VITE_API_BASE` | URL desta API |
| Back | `FRONTEND_URL` | Destino do redirect depois do login Google |
| Back | `CORS_ALLOWED_ORIGINS` | Origens do front que podem chamar a API |
| Back | `BASE_URL` | Monta o `redirect-uri` do Google |
| Back | `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM` | SMTP do aviso de match; sem `MAIL_HOST` ou `MAIL_FROM`, nada é enviado |
