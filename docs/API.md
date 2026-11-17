# PairUp — Rotas da API

O que cada rota recebe, o que devolve e quais regras aplica. Serve também de contrato com o
front (`tinder/`): os tipos do front ficam em `tinder/src/lib/services/types.ts` e as chamadas
em `tinder/src/lib/services/api.ts`. **Mudou um DTO aqui? Mude `types.ts` também**, porque
nada gera esse arquivo.

Arquitetura e riscos: [`ARQUITETURA.md`](ARQUITETURA.md). Como rodar e testar:
[`DESENVOLVIMENTO.md`](DESENVOLVIMENTO.md).

## Regras gerais

**Autenticação.** Rotas marcadas com 🔒 exigem `Authorization: Bearer <token>`. O token sai de
`POST /users/login` ou do login Google e dura **15 minutos**, sem refresh. Sem token, ou com
token inválido, expirado ou de conta apagada → `401 {"erro": "..."}`. O front encerra a sessão
em qualquer 401 (menos no próprio login).

**Dois ids.** `User.id` é a conta; `Pessoa.id` é o perfil. Likes, dislikes, matches e
`destinoId` usam **`Pessoa.id`**. O próprio `Pessoa.id` vem em `GET /pessoas/me`. O id de quem
age nunca é enviado: sai do token.

**Limites.** Por IP: 10 requisições/min em `POST /users` e `POST /users/login`, 300/min no
resto. Acima disso → `429 {"erro": "Muitas requisições..."}` com `Retry-After: 60`.

**Paginação.** Listas paginadas aceitam `page` (a partir de 0), `size` (padrão 20) e `sort`
(ex.: `sort=id`) e devolvem o `Page` do Spring: `{content, totalElements, totalPages, number,
size, ...}`.

**Erros.** Nenhuma resposta traz mensagem interna, stack trace ou detalhe do banco.

| Origem | Corpo |
|---|---|
| Regras de negócio, segurança, 429 | `{"erro": "..."}` (o `GlobalExceptionHandler` acrescenta `status` e `timestamp`) |
| Validação (`@Valid`) | `{"erro": "Dados inválidos", "campos": ["campo: motivo"], ...}` |
| JSON malformado, parâmetro ausente ou de tipo errado | `400 {"erro": "Requisição inválida"}` |
| `PessoaController` | `{"error": "..."}` |
| Rotas de imagem | `{"sucesso": false, "mensagem": "..."}` |
| `MatchController` (403) | texto puro |

O front entende todos em `extrairErro` (`api.ts`). Também podem aparecer 404 (rota
inexistente), 405 (método errado), 413 (arquivo grande) e 415 (Content-Type errado).

## Resumo

| Método | Rota | Auth | Para quê |
|---|---|---|---|
| POST | `/users` | público | Criar conta |
| POST | `/users/login` | público | Entrar e receber o token |
| GET | `/users/me` | 🔒 | Ler a conta |
| PUT | `/users/me` | 🔒 | Alterar nome ou e-mail |
| DELETE | `/users/me` | 🔒 | Apagar conta e perfil |
| GET | `/oauth2/authorization/google` | público | Começar o login Google |
| GET | `/pessoas` | 🔒 | Feed paginado |
| GET | `/pessoas/{id}/perfil` | 🔒 | Perfil público de alguém |
| POST | `/pessoas` | 🔒 | Criar o próprio perfil |
| GET | `/pessoas/me` | 🔒 | Perfil completo próprio |
| PUT | `/pessoas/me` | 🔒 | Editar o perfil |
| PATCH | `/pessoas/me/ciencia-responsabilidade` | 🔒 | Aceitar os termos |
| DELETE | `/pessoas/me` | 🔒 | Apagar o perfil |
| POST | `/likes?destinoId=` | 🔒 | Curtir |
| GET | `/likes/dados` | 🔒 | Curtidas que dei |
| GET | `/likes/recebidos` | 🔒 | Curtidas que recebi |
| POST | `/dislikes?destinoId=` | 🔒 | Passar |
| GET | `/dislikes/dados` | 🔒 | Perfis que dispensei |
| GET | `/matches/meus` | 🔒 | Meus matches ativos |
| GET | `/matches/{matchId}/redes-sociais` | 🔒 | Contatos do match |
| PUT | `/matches/desfazer/{matchId}` | 🔒 | Desfazer match |
| POST | `/api/imagens/upload` | 🔒 | Enviar foto |
| GET | `/api/imagens/minhas-imagens` | 🔒 | Minhas fotos |
| DELETE | `/api/imagens/{imagemId}` | 🔒 | Apagar foto |
| GET | `/api/imagens/proxy/{publicId}` | 🔒 | Abrir foto (dono ou match) |
| GET | `/tags`, `/tags/ativas` | público | Listar tags |
| POST | `/tags` | ADMIN | Criar tag |
| DELETE | `/tags/{id}` | ADMIN | Bloqueado |

---

## Conta (`/users`)

### `POST /users` — criar conta
Público. Corpo:

```json
{ "nome": "Ana", "email": "ana@ufs.br", "password": "minhasenha" }
```

- `nome` até 100; `email` válido até 254; `password` de **8 a 72** caracteres.
- O e-mail é gravado em minúsculas. `id` e `provider` não são aceitos.
- **200** com a conta (`{id, nome, email, password: null, provider: null}`).
- **409** `{"erro": "Email já cadastrado"}`; **400** se a validação falhar.
- Não cria o perfil: depois do login, chame `POST /pessoas`.

### `POST /users/login` — entrar
Público. Corpo `{ "email", "password" }` (e-mail até 254, senha até 128).

- **200** `{ "token", "userId", "email" }`.
- **401** `{"erro": "Credenciais inválidas."}` para senha errada, e-mail inexistente ou conta
  Google (sem senha). As três respostas são iguais e levam o mesmo tempo.
- **429** depois de 5 senhas erradas seguidas para o mesmo e-mail (bloqueio de 15 min).
- Contas antigas com senha de 6 caracteres continuam entrando.

### `GET /users/me` 🔒
**200** com a conta logada, sem senha; **404** se a conta não existe mais.

### `PUT /users/me` 🔒 — alterar conta
Corpo `{ "nome"?, "email"?, "senhaAtual"? }`. Campo ausente mantém o valor.

- Trocar o e-mail exige `senhaAtual` correta → senão **403**.
- E-mail já usado por outra conta → **409**.
- Conta Google não troca e-mail → **400**.
- **200** com a conta atualizada.

### `DELETE /users/me` 🔒 — apagar conta
Apaga o perfil (em cascata, ver `DELETE /pessoas/me`) e a conta. **204**.

## Login com Google

1. O front redireciona o navegador para `{API}/oauth2/authorization/google`.
2. O Google devolve para `{BASE_URL}/login/oauth2/code/google`, tratado pelo Spring.
3. O back redireciona para o front:

| Situação | Redirect |
|---|---|
| Sucesso | `{FRONTEND_URL}/auth/callback#token=<jwt>` |
| E-mail não confirmado pelo Google | `{FRONTEND_URL}/?erro=email_nao_verificado` |
| Já existe conta com senha para o e-mail | `{FRONTEND_URL}/?erro=conta_com_senha` |
| Falha no fluxo OAuth2 | `{FRONTEND_URL}/?erro=google_falhou` |

O token vem no **fragmento** (`#`). O callback do front lê `location.hash` (aceita `?token=` só
por compatibilidade) e limpa a URL antes de qualquer chamada.

No primeiro login, o back cria a conta e um **perfil mínimo** (só nome e e-mail, incompleto).
Por isso o front completa o cadastro com `PUT /pessoas/me` e
`PATCH /pessoas/me/ciencia-responsabilidade`; `POST /pessoas` daria 409.

## Perfil (`/pessoas`)

Todas 🔒.

### `GET /pessoas` — feed
Query: `interesse` (`HOMENS|MULHERES|TODOS|NAO_DEFINIDO`), `genero`
(`MASCULINO|FEMININO|NAO_BINARIO|OUTRO`), `page`, `size`, `sort`. Valor inválido → **400**.

- **200** `Page<PessoaPerfilDTO>`: `{id, nome, curso, idade, genero, interesse, descricao,
  tags (nomes), imagens[], fotoPerfilUrl}`. Sem e-mail, data de nascimento nem contatos.
- Só perfis **completos e ativos**. Inclui o próprio usuário e não filtra compatibilidade nem
  quem já foi avaliado: isso é feito no front.
- O front busca páginas de 100 com `sort=id` (ordem estável), sob demanda, quando restam menos
  de 5 pessoas no deck.

### `GET /pessoas/{id}/perfil`
`id` é um `Pessoa.id`. **200** `PessoaPerfilDTO` ou **404**. Não mostra contatos.

### `POST /pessoas` — criar meu perfil
Corpo (`PessoaRequestDTO`):

```json
{
  "nome": "Ana", "curso": "Direito", "dataNasc": "2000-05-20",
  "genero": "FEMININO", "interesse": "TODOS", "descricao": "...",
  "instagram": "@ana.silva", "whatsapp": "(79) 99999-0000", "telegram": "@ana_s",
  "tags": ["<id da tag>", "..."],
  "cienciaResponsabilidade": true
}
```

| Campo | Regra |
|---|---|
| `nome` | até 100 |
| `curso` | até 150 (o back aceita vazio, mas sem curso o perfil não fica completo) |
| `dataNasc` | `yyyy-MM-dd`, no passado; 18 anos ou mais e no máximo 100 |
| `descricao` | até 500 |
| `instagram` | vazio ou `@?[A-Za-z0-9._]{1,30}` (só o usuário, sem link) |
| `whatsapp` | vazio ou 8 a 20 caracteres entre dígitos, espaço, `(`, `)`, `+`, `-` |
| `telegram` | vazio ou `@?[A-Za-z0-9_]{1,32}` |
| `tags` | até 5 **IDs** de tags ativas (resposta traz os **nomes**) |
| `cienciaResponsabilidade` | precisa ser `true` |
| `email` | ignorado: o perfil usa o e-mail da conta |

- **200** com a entidade `Pessoa` criada.
- **400**: termos não aceitos, menor de idade, data implausível, tag inválida ou campo fora do
  formato (com `campos`).
- **409**: a conta já tem perfil (inclusive o mínimo do Google).

### `GET /pessoas/me`
**200** `PessoaCompletaDTO`: o perfil público mais `email`, `dataNasc`, `instagram`,
`whatsapp`, `telegram`, `perfilCompleto`, `quantidadeGaleria` e `imagensGaleria`. **404** sem
perfil. Com `perfilCompleto: false`, o front manda a pessoa para `/onboarding`.

### `PUT /pessoas/me` — editar
Mesmo corpo e regras do `POST`, mas tudo opcional:

- campo ausente mantém o valor;
- string vazia apaga uma rede social;
- lista vazia remove todas as tags;
- não altera `email` nem `cienciaResponsabilidade` (use o `PATCH`).

**200** com a `Pessoa`; **404** sem perfil; **400** em regra violada.

### `PATCH /pessoas/me/ciencia-responsabilidade`
Marca o aceite dos termos. **200** com a `Pessoa`; **404** sem perfil. Não dá para desmarcar.

### `DELETE /pessoas/me`
Apaga o perfil e tudo ligado a ele: fotos (R2 e banco), likes e dislikes dados e recebidos e
matches. **204**, mesmo sem perfil. Não é atômico; chamar de novo termina a limpeza.

### `GET /pessoas/{id}/redes-sociais`
Sempre **403**. Os contatos só saem por `GET /matches/{matchId}/redes-sociais`.

## Curtidas, dislikes e matches

Todas 🔒. Curtir e passar exigem **perfil completo** (senão **403**); sem perfil → **400**.

### `POST /likes?destinoId=<Pessoa.id>` — curtir
- **200** `{"match": false, "message": "Like enviado com sucesso"}`, ou
  `{"match": true, "message": "É um match!"}` quando a outra pessoa já tinha curtido.
- No match, a outra pessoa recebe um e-mail (se o SMTP estiver configurado); quem curtiu vê o
  aviso na tela.
- **400**: `destinoId` vazio ou de perfil inexistente, curtir a si mesmo, ou já curtiu.

### `POST /dislikes?destinoId=<Pessoa.id>` — passar
**204**. Idempotente: passar de novo na mesma pessoa não é erro. **400** para si mesmo ou
perfil inexistente. Quem foi dispensado não fica sabendo.

### Listas
| Rota | Devolve |
|---|---|
| `GET /likes/dados` | `Page<Like>` das curtidas que dei: `{id, pessoaOrigemId, pessoaDestinoId, ativo}` |
| `GET /likes/recebidos` | `Page<Like>` das curtidas que recebi |
| `GET /dislikes/dados` | `Page<Dislike>`: `{id, pessoaOrigemId, pessoaDestinoId, data}` |
| `GET /matches/meus` | `Page<Match>` só dos ativos: `{id, pessoaId1, pessoaId2, ativo}` |

O front lê todas as páginas dessas listas (`buscarTodasPaginas`) para tirar do deck quem já
teve interação.

### `GET /matches/{matchId}/redes-sociais`
**200** `{id, instagram, whatsapp, telegram}` da **outra** pessoa do match. **403** se o match
está inativo ou se quem pede não faz parte dele; **404** se não existe.

### `PUT /matches/desfazer/{matchId}`
Marca o match como inativo. **200** (texto); **403** para quem não faz parte; **404** se não
existe. Depois disso os contatos e as fotos pelo proxy deixam de ser liberados (URLs já
assinadas valem até 15 min).

## Fotos (`/api/imagens`)

Todas 🔒. Sem perfil criado: upload e remoção dão **400**, a listagem devolve lista vazia e o
proxy dá **403**.

### `POST /api/imagens/upload` — enviar
`multipart/form-data` com `file` e `perfil` (`true` torna a foto a principal).

- Até **5 MB** e **6 fotos ativas** por perfil.
- Só **JPEG, PNG ou WebP de verdade**: o tipo é lido dos bytes; Content-Type e nome do arquivo
  são ignorados. Um SVG ou HTML disfarçado de imagem é recusado.
- JPEG e PNG são re-codificados em até 800×800 (sem EXIF/GPS). Resolução acima de 8000 px por
  lado → recusada. WebP é guardado como veio.
- Sem nenhuma foto principal, a enviada vira principal.
- **200** `{id, url: "/api/imagens/proxy/...", nomeOriginal, perfil, tamanhoBytes, sucesso:
  true, mensagem}`.
- **400** formato, tamanho, resolução ou limite de fotos; **413** acima do limite do servidor.

### `GET /api/imagens/minhas-imagens`
**200** lista `{id, url (proxy), perfil, tamanhoBytes, sucesso}` das fotos ativas; lista vazia
sem perfil.

### `DELETE /api/imagens/{imagemId}`
**200** `{sucesso: true, mensagem}`. **400** se for a última foto; **403** se a foto é de
outra pessoa; **404** se não existe. Apagar a principal promove a primeira foto ativa. Não há
rota para trocar a principal de uma foto já enviada.

### `GET /api/imagens/proxy/{publicId}`
Só para o **dono** ou quem tem **match ativo** com o dono.

- **302** para uma URL assinada do R2, válida por 15 min (`Cache-Control: private,
  max-age=600`).
- **400** id malformado ou com tentativa de *path traversal*; **403** sem permissão; **404** não
  existe; **410** foto apagada.

**Qual URL o front usa.** Com `CF_R2_PUBLIC_URL` no back, `imagens[].url` e `fotoPerfilUrl`
do feed são URLs públicas do R2, e o `<img>` carrega direto (é assim que o deck mostra fotos
antes do match). Sem ela, as URLs são do proxy, que dá 403 para quem não tem match.

## Tags (`/tags`)

| Rota | Auth | Comportamento |
|---|---|---|
| `GET /tags` | público | Todas as tags `{id, nome, descricao, categoria, ativa}` |
| `GET /tags/ativas` | público | Só as ativas; é a lista do onboarding |
| `POST /tags` | ADMIN | Corpo `{nome, descricao?, categoria, ativa?}` (sem `id`). Usuário comum → 403 |
| `DELETE /tags/{id}` | ADMIN | Sempre bloqueado (403) |

Ninguém tem o papel ADMIN hoje: as tags são cadastradas direto no banco.

## Documentação interativa

Com `SWAGGER_ENABLED=true` (só em desenvolvimento): `/swagger-ui.html` e `/api-docs`. Para as
rotas 🔒, cole o token no botão **Authorize**. Desligado, essas rotas dão 401.
