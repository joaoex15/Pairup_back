# PairUp — API

API REST (Spring Boot 3.2.3 / Java 21 / MongoDB) do PairUp, app de paquera para a comunidade
universitária. O front fica no repositório `tinder`.

## Funcionalidades

- Cadastro e login com e-mail e senha ou com Google; autenticação por JWT
- Perfil com gênero, interesse, curso, até 5 tags e redes sociais
- Feed paginado, likes, "passar" e match automático quando a curtida é recíproca
- Aviso de match por e-mail (opcional)
- Até 6 fotos por perfil no Cloudflare R2, validadas pelo conteúdo
- Contatos liberados só entre pessoas com match
- Rate limit, bloqueio de login, cabeçalhos de segurança e CORS configurável

## Começo rápido

```bash
cp .env.example .env               # preencha; ver docs/DESENVOLVIMENTO.md
docker compose up -d mongodb
mvn spring-boot:run                # http://localhost:8080
```

## Documentação

| Arquivo | Conteúdo |
|---|---|
| [`docs/ARQUITETURA.md`](docs/ARQUITETURA.md) | Como o sistema é montado, modelo de dados, segurança, decisões e riscos em aberto |
| [`docs/API.md`](docs/API.md) | Cada rota: o que recebe, o que devolve e as regras; contrato com o front |
| [`docs/DESENVOLVIMENTO.md`](docs/DESENVOLVIMENTO.md) | Variáveis, rodar local, testes, deploy e convenções de código |
