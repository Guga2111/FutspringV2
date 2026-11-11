# Revisão de segurança do backend — 2026-11-06

Os 10 problemas de segurança mais relevantes do `core/`, encontrados lendo o código (SecurityConfig, rate limit, auth, uploads, busca de usuários, STOMP). Ordem: do mais grave ao menos grave.

Severidade: **Alta** (exploração simples, impacto em dados ou contas), **Média** (exige condição extra ou tem impacto limitado), **Baixa** (endurecimento).

| # | Problema | Severidade | Status |
|---|----------|-----------|--------|
| 1 | Busca de usuários lista todas as contas e funciona como oráculo de e-mail | Alta | Corrigido (PR A1) |
| 2 | Rate limit de login contornável com variação de URL | Alta | Confirmado e corrigido (PR A2) |
| 3 | IP do rate limit forjável por `X-Forwarded-For` | Alta / Média | Confirmado no nginx da VPS; corrigido (PR A2: `RemoteIpValve` + `ops/nginx/futspring.conf`, nginx aplicado à mão) |
| 4 | Membro removido continua recebendo o chat | Média | Confirmado |
| 5 | JWT de 7 dias em `localStorage`, sem revogação por sessão | Média | Confirmado |
| 6 | Upload público confia no Content-Type e não remove EXIF | Média | Confirmado |
| 7 | `/files/**` público e permanente | Média / Baixa | Confirmado |
| 8 | Enumeração de contas no cadastro e no login | Baixa / Média | Login corrigido (PR A3: hash BCrypt dummy); 409 do cadastro aceito (spec de lançamento) |
| 9 | Actuator aberto com `threaddump` na rede `observability` | Baixa | Corrigido (PR A4: só `health` e `prometheus`, health sem detalhes); sem autenticação básica |
| 10 | Senha sem tamanho máximo e e-mail sensível a maiúsculas | Baixa | Corrigido (PR A3: ≤ 72 bytes, e-mail normalizado, V22); sem limite por conta |

---

## 1. Busca de usuários lista todas as contas e funciona como oráculo de e-mail

**Severidade:** Alta

**Onde:** `core/src/main/java/com/futspring/backend/domain/user/UserRepository.java` (`searchByUsernameOrEmail`), usado por `GET /api/v1/users/search?q=`.

**Problema:** a consulta faz `LIKE '%q%'` sobre `username` **e** `email`, e não escapa `%` nem `_` vindos do usuário.

```java
WHERE LOWER(u.username) LIKE LOWER(CONCAT('%', :q, '%'))
   OR LOWER(u.email) LIKE LOWER(CONCAT('%', :q, '%'))
```

- `q=%%%` (3 caracteres, passa na validação) casa com qualquer usuário: dá para varrer a base inteira de contas, 10 por vez.
- Como o resultado mostra o `username`, buscar trechos (`joao@`, `@empresa.com`, `silva1`) revela qual conta tem qual e-mail, mesmo que o DTO não devolva o e-mail. Qualquer conta autenticada consegue fazer isso, sem compartilhar pelada.

**Solução:**
1. Buscar por e-mail só com **igualdade exata** (normalizada em minúsculas), nunca por substring: `LOWER(u.email) = LOWER(:q)`. Isso mantém o caso de uso "convidar pelo e-mail" sem virar oráculo.
2. Escapar curingas no `username`: substituir `\`, `%` e `_` por `\\`, `\%`, `\_` no service e usar `LIKE ... ESCAPE '\'`.
3. Opcional: rate limit por usuário nessa rota (mesmo padrão do `InvitationRateLimiter`).
4. Testes: `q=%%%` não retorna ninguém; trecho de e-mail não casa; e-mail exato casa.

---

## 2. Rate limit de login contornável com variação de URL

**Severidade:** Alta (se confirmado)

**Onde:** `core/src/main/java/com/futspring/backend/domain/auth/AuthRateLimitFilter.java` (`shouldNotFilter`, chave do bucket).

**Problema:** o filtro compara `request.getRequestURI()` (cru, sem decodificar) com um `Set` de strings exatas, e usa a mesma URI na chave do bucket. O Spring MVC decodifica o caminho antes de casar a rota, então `POST /api/v1/auth/%6Cogin` provavelmente chega em `AuthController.login` sem passar pelo limite. Cada variação ainda ganharia um bucket próprio. Resultado: força bruta de senha sem o limite de 10/min.

**Como confirmar:** teste de integração com servidor real (`RANDOM_PORT` + `RestClient`, não MockMvc, que trata a URI de outro jeito) enviando 11 logins para `/api/v1/auth/%6Cogin` e esperando 429.

**Solução:**
1. Comparar o caminho **normalizado e decodificado**: usar `ServletRequestPathUtils`/`UrlPathHelper.getPathWithinApplication` (decodificado) ou, melhor, um `PathPatternRequestMatcher` do Spring Security (o mesmo mecanismo que casa as rotas), em vez de `getRequestURI()`.
2. A chave do bucket deve usar o nome lógico da rota (`login`, `register`…), nunca a URI recebida.
3. Manter o teste acima como regressão.

---

## 3. IP do rate limit forjável por `X-Forwarded-For`

**Severidade:** Alta / Média (depende do nginx)

**Onde:** `core/src/main/resources/application.properties` (`server.forward-headers-strategy=framework`) + configuração do nginx na VPS (fora do repositório).

**Problema:** com `framework`, o `ForwardedHeaderFilter` usa o **primeiro** valor de `X-Forwarded-For` como `getRemoteAddr()`. Se o nginx usa `proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;` (o padrão comum), ele **acrescenta** ao valor enviado pelo cliente, e o cliente controla o primeiro valor. Trocando o cabeçalho a cada requisição, cada tentativa cai num bucket novo, e o rate limit de login, cadastro, reset e preview de convite deixa de existir.

**Como confirmar:** ler o `server`/`location /api/` do nginx na VPS.

**Solução (uma das duas):**
- No nginx, **sobrescrever** o cabeçalho com o IP real: `proxy_set_header X-Forwarded-For $remote_addr;` e `proxy_set_header X-Real-IP $remote_addr;`.
- Ou trocar para `server.forward-headers-strategy=native` com o `RemoteIpValve` do Tomcat configurado com `server.tomcat.remoteip.internal-proxies=127\.0\.0\.1`, que lê da direita para a esquerda e ignora o que o cliente injetou.

Versionar o trecho relevante do nginx (ex.: `ops/nginx/futspring.conf`) para que isso seja revisável.

---

## 4. Membro removido continua recebendo o chat

**Severidade:** Média

**Onde:** `core/src/main/java/com/futspring/backend/domain/auth/JwtChannelInterceptor.java` (autoriza só no `SUBSCRIBE`), `PeladaService` (remoção de membros), `PasswordResetService`.

**Problema:** a permissão de `/topic/pelada/{id}` só é verificada no momento da inscrição. Um membro removido (ou um token revogado por reset de senha) continua inscrito e recebendo todas as mensagens até desconectar, o que pode levar horas com o app aberto. O `SEND` está protegido (`ChatService` chama `requireMember`), mas a leitura não.

**Solução:**
1. Publicar um evento após o commit da remoção (já existe `MembersRemovedEvent`) e, num listener, encerrar as sessões STOMP do usuário removido: localizar as sessões via `SimpUserRegistry` e fechá-las (guardar as `WebSocketSession` num `WebSocketHandlerDecorator` por `sessionId`). O cliente reconecta e não consegue mais assinar o tópico.
2. Fazer o mesmo no incremento de `tokenVersion` (reset de senha): fechar todas as sessões do usuário.
3. Teste: removido não recebe a mensagem seguinte publicada no tópico.

Remove o item correspondente de "Known gaps" no `BACKEND.md`.

---

## 5. JWT de 7 dias em `localStorage`, sem revogação por sessão

**Severidade:** Média

**Onde:** `core/.../domain/auth/JwtService.java`, `JWT_EXPIRATION_MS` (padrão 7 dias); `client/src/context/AuthContext.tsx`.

**Problema:** qualquer XSS no frontend lê o token e mantém a sessão por até uma semana. O logout não invalida nada no servidor; a única revogação é o reset de senha, que derruba todas as sessões.

**Solução (em etapas):**
1. Curto prazo: reduzir o access token (ex.: 1 dia) e adicionar `Content-Security-Policy` estrita no nginx para reduzir a superfície de XSS.
2. Médio prazo: access token curto (15 min) em memória + refresh token rotativo em cookie `HttpOnly; Secure; SameSite=Strict`, guardado com hash no banco (tabela `refresh_tokens`, migração nova). Logout revoga o refresh; reuso de um refresh já rotacionado revoga a família inteira.
3. Exige mudança de contrato nos dois lados (`api/auth.ts`, interceptor de 401, docs), então é uma feature própria.

---

## 6. Upload público confia no Content-Type e não remove EXIF

**Severidade:** Média

**Onde:** `core/.../domain/file/FileUploadService.java` (`uploadImage`), usado por avatar, background, imagem da pelada e foto do campeão.

**Problema:**
- O tipo é decidido por `file.getContentType()`, que vem do cliente. Qualquer conteúdo (HTML, SVG, arquivo poliglota) é aceito se o cliente declarar `image/png`. Hoje o risco é contido porque o arquivo é servido como `image/*` com `nosniff`, mas o servidor guarda e distribui conteúdo arbitrário.
- Nenhuma imagem é decodificada nem regravada: metadados EXIF (incluindo **GPS**) das fotos de celular ficam públicos, expondo onde a foto foi tirada (casa do jogador, local da pelada).

**Solução:**
1. Reaproveitar a validação do módulo financeiro (`FinanceFiles`: decodifica o conteúdo, confere tipo declarado × real, limite de pixels) para os uploads públicos.
2. Regravar a imagem a partir dos pixels decodificados (ImageIO/`BufferedImage` → novo arquivo), o que descarta EXIF. Para WebP, que o ImageIO não grava nativamente, converter para JPEG/PNG ou usar uma biblioteca (ex.: TwelveMonkeys) para leitura e regravar em formato suportado.
3. Testes: arquivo de texto com `image/png` → 400; JPEG com GPS → arquivo salvo sem EXIF.

---

## 7. `/files/**` público e permanente

**Severidade:** Média / Baixa

**Onde:** `core/.../shared/config/SecurityConfig.java` (`/api/v1/files/**` `permitAll`), `FileController`.

**Problema:** fotos de perfil, peladas e campeões são servidas sem autenticação, para sempre. O nome é um UUID (não adivinhável), mas um link vazado (print, grupo de WhatsApp, histórico do navegador) continua válido, inclusive depois que a pessoa sai da pelada. Fotos de campeão costumam mostrar rostos de terceiros.

**Solução:** avaliar o custo. Opções, da mais simples à mais completa:
1. Aceitar o risco e documentar (UUID ≈ link secreto), adicionando `Cache-Control: private` e removendo o arquivo quando a pelada/sessão é excluída (já ocorre na substituição).
2. Servir fotos de pelada e campeão por rota autenticada com checagem de membro, carregadas como blob (mesmo padrão de `useFinanceImageUrl` no frontend); avatares podem continuar públicos.
3. URLs assinadas com expiração (HMAC, como `InviteLinkTokens`), mantendo `<img src>` simples no frontend.

---

## 8. Enumeração de contas no cadastro e no login

**Severidade:** Baixa / Média

**Onde:** `core/.../domain/auth/AuthService.java` (`register`, `login`).

**Problema:**
- `register` responde 409 "Este e-mail já está cadastrado", revelando quem tem conta.
- `login` com e-mail inexistente responde sem executar o BCrypt, então é mensuravelmente mais rápido (diferença de timing).

O fluxo de "esqueci a senha" foi feito para não revelar contas; essas duas rotas desfazem isso.

**Solução:**
1. Login: quando o usuário não existe, comparar a senha contra um hash BCrypt fixo (`passwordEncoder.matches(password, DUMMY_HASH)`) para igualar o tempo.
2. Cadastro: a forma completa é confirmação por e-mail (resposta igual para e-mail novo ou existente, e um e-mail "você já tem conta" no segundo caso). Se não valer o custo agora, aceitar o 409 e confiar no rate limit (depois de corrigir #2 e #3).

---

## 9. Actuator aberto com `threaddump` na rede `observability`

**Severidade:** Baixa

**Onde:** `SecurityConfig.actuatorFilterChain` (`permitAll`), `application.properties` (`management.endpoints.web.exposure.include=...threaddump`, `show-details=always`).

**Problema:** a porta 9091 não é publicada, mas qualquer container da rede `observability` (Grafana, cAdvisor, node-exporter) alcança `threaddump` e detalhes de health (pool, banco, disco). Se um deles for comprometido, vira ponto de reconhecimento.

**Solução:**
1. Expor só o que o Prometheus usa: `health,prometheus`. Habilitar `threaddump`/`metrics` apenas sob demanda.
2. `management.endpoint.health.show-details=never` na porta de gerenciamento (o Grafana não precisa).
3. Opcional: autenticação básica no `actuatorFilterChain` com credencial em variável de ambiente, configurada no scrape do Prometheus.

---

## 10. Senha sem tamanho máximo e e-mail sensível a maiúsculas

**Severidade:** Baixa

**Onde:** `RegisterRequestDTO`, `ResetPasswordRequestDTO`, `AuthService`, `UserRepository.findByEmail`.

**Problema:**
- BCrypt só considera 72 bytes. Versões recentes do Spring Security lançam exceção acima disso, o que vira 500; nas antigas, a senha é truncada silenciosamente.
- `findByEmail` compara exato: `Ana@x.com` e `ana@x.com` viram contas diferentes, e o login falha se a pessoa digitar com outra caixa.
- Não há bloqueio por conta, só por IP (enfraquecido por #2 e #3).

**Solução:**
1. `@Size(min = 8, max = 72)` nas senhas (e no schema zod do frontend, no mesmo PR).
2. Normalizar e-mail com `trim().toLowerCase(Locale.ROOT)` no cadastro, login, reset e busca. Antes, verificar em produção se existem e-mails que só diferem por maiúsculas; depois, migração com índice único em `LOWER(email)`.
3. Opcional: limite por conta (ex.: 20 falhas/hora por e-mail) no mesmo `AuthRateLimitFilter`.

---

## Fora do top 10

- `core/docker-compose.dev.yml` tem senhas/segredo fixos e expõe o Postgres na 5432. São valores de desenvolvimento, já listados como conhecidos (antipattern #5 do `BACKEND.md`); vale só ligar a porta em `127.0.0.1:5432`.

## O que já está bem

Autorização nos services com escopo de ids filhos, `SEND` do chat checando membro, tokens HMAC nos links de convite e de descadastro, proteção contra path traversal em `/files`, reset de senha sem enumeração e com revogação via `tokenVersion`, arquivos financeiros privados e validados.

## Ordem sugerida

1. #1, #2 e #3 (baratos e de maior impacto; confirmar #2 com teste e #3 no nginx antes).
2. #4 e #6.
3. #9 e #10 (configuração e validação).
4. #5, #7 e #8 (mudanças de contrato ou de produto; cada um vira uma feature).
