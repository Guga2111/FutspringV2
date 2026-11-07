# PWA + Web Push — SPEC

Data: 2026-11-06 · Base: `dev` · Branch de integração: `feat/pwa-push` (3 PRs, ver [Entrega](#entrega))

## Objetivo

Deixar o Futspring instalável no celular (iPhone e Android) como app da tela de início e mandar as notificações dele também como **push do sistema**, além do sino (STOMP) e do e-mail que já existem. No iPhone, só um app instalado na tela de início recebe Web Push (iOS 16.4+), então a instalação precisa ser guiada: Safari → Compartilhar → "Adicionar à Tela de Início".

O PWA é uma **ponte** até os apps nativos: o painel do `/auth` continua mostrando App Store / Google Play como "Em breve" (`lib/mobileApps.ts`) e ganha a opção de instalar pelo navegador.

Fora do escopo: dados da API offline, sincronização em segundo plano, digest de e-mail, app nativo, push no desktop como foco (funciona por tabela, mas a UI e os testes miram o celular).

## Decisões

| Decisão | Escolha | Por quê |
|---|---|---|
| Ferramenta do front | **`vite-plugin-pwa`** com `strategies: "injectManifest"` e SW próprio em `src/sw.ts` | Precache do build com hashes e versionamento resolvidos pelo Workbox; o SW é nosso, então os handlers de `push` e `notificationclick` ficam no código |
| Protocolo de push | **Web Push padrão (VAPID, RFC 8030/8291/8292)** | Cada navegador usa o próprio push service (Apple, FCM, Mozilla); sem SDK de terceiros nem console extra. Firebase seria um intermediário desnecessário |
| Offline | **Só o shell**: o build fica em precache; nenhum GET da API vai para cache | Presença, vagas e finanças desatualizadas causam erro real; o shell offline já faz o app abrir rápido e mostrar o estado de erro/sem conexão |
| Preferência de push | **Por categoria**, uma coluna "Push" ao lado de "E-mail" | Mesmo modelo que o usuário já conhece em `/settings#notificacoes` |
| Padrão de push | **Ligado nas 4 categorias** | O usuário já optou ao ativar o push no aparelho; push desligado por padrão faria a ativação parecer não funcionar |
| Push × e-mail | **Canais independentes** | Cada canal obedece à sua preferência; quem quiser só push desliga o e-mail da categoria (e poupa a cota do Resend). Suprimir o e-mail com base no push seria opaco: o push não confirma entrega ao usuário |
| Atualização do SW | **Prompt**: toast "Nova versão disponível · Atualizar" | Todo push na `main` gera deploy; auto-update recarregaria a página no meio de um formulário |
| Instalação | Rota pública **`/instalar`** com o tutorial da plataforma detectada | Um único lugar para o QR do `/auth`, o card da Home e o link das configurações; funciona deslogado |

## Frontend

### Shell PWA

- **Dependência:** `vite-plugin-pwa` (dev) e `workbox-precaching` / `workbox-routing` (usados pelo `src/sw.ts`), com `bun add`.
- **`vite.config.ts`:** `VitePWA({ strategies: "injectManifest", srcDir: "src", filename: "sw.ts", registerType: "prompt", injectRegister: false, manifest: {...}, injectManifest: { globPatterns: ["**/*.{js,css,html,woff2,webp,png,svg}"] } })`. As fotos de `public/landing/` ficam **fora** do precache (grandes e só da landing).
- **Manifest** (gerado pelo plugin):

  | Campo | Valor |
  |---|---|
  | `id`, `scope` | `/` |
  | `start_url` | `/home` (deslogado, o `PrivateRoute` manda para `/auth`) |
  | `name` / `short_name` | `Futspring` |
  | `display` | `standalone` |
  | `background_color` / `theme_color` | a cor da página no tema claro (o `<meta name="theme-color">` do `index.html` continua seguindo o tema em tempo real) |
  | `icons` | `pwa-192.png`, `pwa-512.png`, `pwa-maskable-512.png` (`purpose: "maskable"`, com área segura) |
  | `lang` | `pt-BR` |

- **`index.html`:** `<link rel="apple-touch-icon" href="/apple-touch-icon.png">` (180 px), `<meta name="apple-mobile-web-app-title" content="Futspring">`, `<meta name="apple-mobile-web-app-capable" content="yes">` e `apple-mobile-web-app-status-bar-style="default"`. O iOS ignora parte do manifest para ícone e splash.
- **Ícones:** gerados a partir do logo em `public/` (PNG, porque o iOS não aceita WebP no `apple-touch-icon`), cada um com poucos KB.
- **`src/sw.ts`:** `precacheAndRoute(self.__WB_MANIFEST)`, `NavigationRoute` para `index.html` com `denylist` de `/api`, `/ws` e `/files`; nenhum `registerRoute` para a API. Também os handlers de push (ver [Push no front](#push-no-front)). Tem `tsconfig` próprio com `lib: ["WebWorker"]`.
- **Registro:** `components/layout/PwaUpdater.tsx` (montado em `main.tsx`, também nas rotas públicas) usa `useRegisterSW` de `virtual:pwa-register/react`. Quando `needRefresh`, mostra o toast persistente "Nova versão disponível" com a ação "Atualizar" (`updateServiceWorker(true)`). Em `bun run dev` o SW fica desligado (padrão do plugin).
- **Sem conexão:** `useOnline()` (`src/hooks/`, `online`/`offline` do `window`) alimenta um `Alert` fino no topo do `AppLayout`: "Sem conexão. Algumas informações podem não carregar.". As telas mantêm os estados de erro que já têm.
- **nginx (VPS, manual, registrado no README de deploy):** `location = /sw.js` e `/manifest.webmanifest` com `Cache-Control: no-cache`; `index.html` também `no-cache`; os assets com hash continuam com cache longo. Sem isso o celular pode ficar preso numa versão antiga.

### Detecção de plataforma (`utils/pwaPlatform.ts`)

Função pura `detectInstallContext({ userAgent, maxTouchPoints, standalone, hasInstallPrompt })` → `InstallContext`, com testes:

| `InstallContext` | Como detecta | O que a UI faz |
|---|---|---|
| `installed` | `matchMedia("(display-mode: standalone)")` ou `navigator.standalone` | "Você já está no app" / segue para as notificações |
| `ios-safari` | iPhone/iPad (iPadOS se identifica como Mac: `maxTouchPoints > 1`), Safari | Passos do iPhone |
| `ios-other` | iOS em Chrome/Firefox/Edge (`CriOS`, `FxiOS`, `EdgiOS`) | Desde o iOS 16.4 também dá para adicionar à tela de início pelo menu Compartilhar; mesmos passos, com o ícone de compartilhar na barra de endereço |
| `in-app` | WebView do WhatsApp, Instagram, Facebook (`FBAN`, `FBAV`, `Instagram`, `WhatsApp`, `; wv)`) | "Abra no navegador" primeiro (••• → Abrir no Safari/Chrome) e botão "Copiar link". **É o caso mais comum**: os convites chegam pelo WhatsApp |
| `android-prompt` | evento `beforeinstallprompt` recebido | Botão **"Instalar app"** → `prompt()` |
| `android-manual` | Android sem o evento (Samsung Internet, Firefox, ou o Chrome ainda não liberou) | ⋮ → "Instalar app" / "Adicionar à tela inicial" |
| `desktop` | o resto | QR Code para `/instalar` + "Abra este link no celular" |

`usePwaInstall()` (`src/hooks/`) guarda o `beforeinstallprompt` (registrado cedo, em `main.tsx`, porque o evento pode disparar antes da página montar), expõe `context`, `promptInstall()` e escuta `appinstalled`.

### Tutorial `/instalar`

Página pública `InstallPage` em `AuthLayout` (logo, card central). Título Barlow "Instale o Futspring", um bloco por contexto e uma lista numerada de passos, cada um com o ícone Hugeicons do botão que a pessoa vai tocar (`Share01Icon`, `PlusSignSquareIcon`, `MoreVerticalIcon`…). Ilustrações em componentes (como os fragmentos da landing), não screenshots, porque screenshots envelhecem a cada versão do iOS.

**iPhone (Safari):**
1. Toque em **Compartilhar** (o quadrado com a seta). No iOS 26, com a barra compacta, ele fica no menu **•••**.
2. Role e toque em **Adicionar à Tela de Início** (se não aparecer, toque em "Ver mais").
3. Deixe **Abrir como app Web** ligado e toque em **Adicionar**.
4. Abra o Futspring **pelo ícone** na tela de início e ative as notificações. Só o app aberto pelo ícone recebe notificações.

**Android (Chrome):** botão "Instalar app"; sem ele: ⋮ → **Instalar app** (ou "Adicionar à tela inicial") → **Instalar**.

Links para a página: o QR do `/auth`, o card da Home, o bloco "Neste aparelho" das configurações. Um seletor "Mostrar passos do iPhone / Android" no rodapé cobre a detecção errada.

### Pontos de entrada

- **`/auth` (`AppDownloadPanel`):** o `ToggleGroup` iOS/Android continua; o QR passa a apontar para `${origin}/instalar` (válido para as duas plataformas) com o texto "Instale pelo navegador, sem loja". As lojas continuam abaixo como "Em breve" enquanto `MOBILE_APPS[*].url` for `null`; quando houver URL, a loja volta a ter o QR principal. `AppDownloadLinks` (mobile) ganha "Instalar pelo navegador" → `/instalar`.
- **Home:** `InstallAppCard` (celular, contexto ≠ `installed` e ≠ `desktop`): "Leve o Futspring na tela de início", "Ver como instalar" (ou "Instalar app" no `android-prompt`) e ✕. O ✕ grava `localStorage.install_card_dismissed` (com a data; volta depois de 30 dias). No app instalado, com push suportado e ainda não ativado neste aparelho: `EnablePushCard` "Ative as notificações" com o botão que pede a permissão (mesmo ✕).

### Push no front

- **`api/push.ts`:** `getVapidPublicKey()` (`GET /api/v1/push/vapid-public-key`, 204 → `null`), `subscribePush(sub)` (`POST /api/v1/push/subscriptions`), `unsubscribePush(endpoint)` (`POST /api/v1/push/subscriptions/remove`). Tipos em `types/push.ts`; schema zod do corpo em `schemas/push.ts`.
- **`usePushSubscription()`** (`src/hooks/`): `status` = `unsupported | needsInstall | unconfigured | denied | inactive | active`, `pending`, `enable()`, `disable()`.
  - `needsInstall`: iOS fora do standalone (o `PushManager` não existe lá).
  - `enable()` roda **no clique** (o iOS só pede permissão com gesto do usuário): `Notification.requestPermission()` → `registration.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey })` → `subscribePush(sub.toJSON())`.
  - Ao abrir o app logado com `active`, reenvia a inscrição atual (upsert idempotente): cobre troca de conta no mesmo aparelho e endpoint renovado pelo navegador.
- **Logout (`AuthContext.logout`):** antes de limpar o token, `pushManager.getSubscription()` → `unsubscribePush(endpoint)` (best effort, sem esperar mais que ~2 s) → `subscription.unsubscribe()`. No logout pelo 401 o token já não vale: só o `unsubscribe()` local; o servidor apaga a linha no primeiro 410.
- **`src/sw.ts`:**
  - `push`: `event.waitUntil(self.registration.showNotification(title, { body, icon: "/pwa-192.png", badge: "/pwa-badge-96.png", tag, data: { url } }))` e `navigator.setAppBadge?.(unreadCount)` (`clearAppBadge` com 0). **Sempre mostra** a notificação, mesmo com o app aberto: o Safari revoga a inscrição de quem recebe push sem mostrar nada, e o Chrome mostra um aviso genérico.
  - `notificationclick`: fecha a notificação; se houver uma janela do app, `focus()` + `navigate(url)`; senão `clients.openWindow(url)`.
  - `pushsubscriptionchange` não é tratado: o reenvio ao abrir o app cobre o caso, e o navegador nem sempre dispara o evento.
- **Marcar como lida:** a `url` do payload leva `?notificacao={id}`. `useNotifications` lê o parâmetro ao montar, chama `markRead(id)` e o remove com `navigate(..., { replace: true })`. É o mesmo efeito de clicar no item do sino. O SW não marca nada, porque não tem o token (que fica no `localStorage`).
- **Badge do ícone:** o app também chama `setAppBadge(unreadCount)` quando o contador do sino muda (`useNotifications`) e `clearAppBadge()` no logout.

### Configurações (`/settings#notificacoes`)

`NotificationPreferencesCard` vira "Notificações":

- **Bloco "Neste aparelho"** (no topo), conforme `usePushSubscription().status`:

  | status | Mostra |
  |---|---|
  | `active` | "Notificações ativas neste aparelho" + "Desativar neste aparelho" (outline) |
  | `inactive` | "Receba avisos de sessão, vaga e times no celular" + **"Ativar notificações"** (brand) |
  | `needsInstall` | "No iPhone, as notificações só chegam no app instalado" + link "Como instalar" → `/instalar` |
  | `denied` | `Alert`: "As notificações estão bloqueadas" + como reativar (iPhone: Ajustes → Notificações → Futspring; Android: cadeado/ⓘ → Permissões) |
  | `unconfigured` / `unsupported` | "Este navegador não recebe notificações" (sem botão) |

- **Tabela de categorias:** cada categoria (Presença e vaga, Sessões, Membros, Prêmios) ganha duas colunas de `Switch`, **Push** e **E-mail**, com um `aria-label` por switch ("Push de Presença e vaga"). Os dois salvam na hora (otimista, como hoje). Abaixo de md, as colunas viram duas linhas com rótulo dentro da categoria. A coluna Push fica desabilitada (com o texto do bloco acima) enquanto este aparelho não estiver `active`, mas mostra o valor salvo, que vale para os outros aparelhos.
- Rodapé: "As notificações dentro do app ficam sempre ativas."
- `useNotificationPreferences` passa a tratar `pushEnabled` junto com `emailEnabled`; `toggle(category, channel, next)`.

## Backend

### Migração V18 (`V18__create_push_subscriptions.sql`)

Confirmar o número na `dev` antes de criar.

```sql
CREATE TABLE push_subscriptions (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    endpoint         VARCHAR(1024) NOT NULL,
    p256dh           VARCHAR(200) NOT NULL,
    auth             VARCHAR(50)  NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL,
    last_success_at  TIMESTAMPTZ  NULL,
    CONSTRAINT uq_push_subscriptions_endpoint UNIQUE (endpoint)
);
CREATE INDEX idx_push_subscriptions_user_id ON push_subscriptions(user_id);

ALTER TABLE notification_preferences ADD COLUMN push_enabled BOOLEAN NULL;
```

Aditiva, sem backfill. `push_enabled` nulo = padrão da categoria (`defaultPush`), então as linhas existentes (que só guardam o e-mail) não mudam de significado. O `ON DELETE CASCADE` é só rede de segurança: as exclusões são explícitas nos serviços.

### Domínio (`domain/notification`)

- **`PushSubscription`** (entity, `push_subscriptions`) + `PushSubscriptionRepository` (`findByUserIdIn`, `findByEndpoint`, `deleteByEndpoint`, `deleteAllByUser`, `countByUser`).
- **`NotificationCategory`** ganha `defaultPush` (`true` nas quatro).
- **`NotificationType`** ganha `pushable`: `true` em todos, exceto `ADMIN_REVOKED`. `TEAMS_SORTED` e `ATTENDANCE_CONFIRMED_BY_ADMIN` são push mesmo sem e-mail: no celular, "os times saíram" é o aviso mais esperado.
- **`NotificationPreference`:** campo `Boolean pushEnabled` (nulo = padrão). A regra "só guarda o que difere do padrão" passa a valer para os dois canais: a linha some quando o e-mail é igual ao padrão e o push é nulo ou igual ao padrão.
- **`NotificationPreferenceDTO`** → `{category, emailEnabled, pushEnabled}`. O `PUT /notifications/preferences` aceita os dois opcionais por item (pelo menos um, senão 400) e continua parcial. Os tokens de unsubscribe do e-mail continuam mexendo só no e-mail.
- **`NotificationEmailContent` → `NotificationContent`** (renomeado: o texto passa a servir aos dois canais; `NotificationEmail` e o dispatcher de e-mail continuam usando). O push usa `title` como título e `body` como corpo (cortado em 180 caracteres com "…"), e `ctaPath` + `?notificacao={id}` como `url`.
- **`WebPushDispatcher`** (`@Async`, executor padrão de virtual threads, como o `NotificationPushService`; os push services não têm cota parecida com a do Resend):
  1. Uma transação de leitura curta: as notificações com refs (`findAllByIdInWithRefs`), as inscrições dos destinatários (`findByUserIdIn`), as preferências (uma consulta) e o `unreadCount` agrupado (a mesma consulta do STOMP). Número de consultas constante.
  2. Filtra por `type.pushable()` e pela preferência de push da categoria.
  3. Fora da transação, envia para cada inscrição: payload JSON `{title, body, url, tag, unreadCount}` criptografado (aes128gcm), `TTL` de 24 h (`SESSION_REMINDER` e `DIARISTA_WINDOW_OPEN`: até o início da sessão, mínimo de 1 h), `Urgency: high` para Presença e `normal` para o resto, `Topic` = `tag` (o push service substitui uma mensagem pendente do mesmo tópico).
  4. `tag` = `"{type}-{dailyId ou peladaId}"`: o colapso de `TEAMS_SORTED` / `SESSION_FINISHED` substitui a notificação já mostrada no aparelho.
  5. Resposta 201: atualiza `last_success_at` (em lote). 404/410: apaga a inscrição. 413/400: loga como erro (bug nosso). 429/5xx: loga, sem retry.
- **`NotificationDelivery.deliver`** chama `webPushDispatcher.dispatch(ids)` depois do push STOMP e do e-mail. As três falhas são independentes.
- **Sem chave VAPID** (`VAPID_PRIVATE_KEY` vazio): `WebPushSender` no-op, como o `NoopEmailSender`; o `GET /push/vapid-public-key` responde 204 e a UI mostra `unconfigured`.
- **Biblioteca:** um spike no PR 2 escolhe entre `com.zerodeplibs:zerodep-web-push-java` (sem dependências, usa o `HttpClient` do JDK; preferida) e `nl.martijndwars:web-push` (BouncyCastle + Apache HttpClient). Critério: compatível com Java 25, sem trazer um segundo cliente HTTP, VAPID ES256 funcionando contra Chrome e Safari. A escolha fica isolada atrás de `WebPushSender` (interface com a implementação real e a no-op, como `EmailSender`).

### Endpoints (`PushController`, `/api/v1/push`)

| Método | Path | Corpo | Auth | Resposta |
|---|---|---|---|---|
| GET | `/push/vapid-public-key` | — | autenticado | `{publicKey}` (base64url), ou 204 sem configuração |
| POST | `/push/subscriptions` | `PushSubscriptionRequestDTO {endpoint @NotBlank ≤ 1024, keys: {p256dh @NotBlank ≤ 200, auth @NotBlank ≤ 50}}` | autenticado | 204. Upsert pelo `endpoint`: se já existe para outra conta, **passa para o chamador** (o mesmo aparelho, outra conta logada) e troca as chaves. Mais de 10 inscrições por usuário: apaga a mais antiga (`last_success_at`, depois `created_at`) |
| POST | `/push/subscriptions/remove` | `{endpoint}` | autenticado | 204; apaga só se for do chamador (de outra conta ou inexistente: 204 sem efeito, sem revelar nada). POST porque DELETE com corpo é mal suportado (mesma razão de `/players/remove`) |

**Segurança (obrigatório):**

- **Anti-SSRF:** o servidor faz POST no URL que o cliente envia. O `endpoint` precisa ser `https`, sem porta explícita fora da 443, sem credenciais no URL, e o host precisa estar numa allowlist (`PushEndpointPolicy`): `*.push.apple.com`, `fcm.googleapis.com`, `*.googleapis.com` do FCM (`android.googleapis.com`), `updates.push.services.mozilla.com`, `*.notify.windows.com`. Fora disso: 400 "Endpoint de notificação inválido". O cliente HTTP de envio não segue redirects.
- As chaves são validadas no recebimento: `p256dh` decodifica em base64url para 65 bytes (ponto P-256 não comprimido) e `auth` para 16 bytes; senão 400.
- O dono vem do token; nenhum `userId` no corpo.
- **Reset de senha, troca de senha e exclusão de conta** apagam todas as inscrições do usuário na mesma transação (`PasswordResetService`, `UserService.changePassword`, `AccountDeletionService`), porque esses fluxos revogam as sessões (`tokenVersion`) e o aparelho antigo não pode continuar recebendo. Na troca de senha, o próprio aparelho se reinscreve ao reabrir (`usePushSubscription` reenvia quando a permissão continua `granted`).
- O conteúdo da notificação vai criptografado ponta a ponta (RFC 8291); o push service vê só o tamanho. Nada de e-mail ou dado financeiro no payload além do que o sino já mostra.
- Rate limit: o `POST /push/subscriptions` entra no `AuthRateLimitFilter` (10/min por IP), porque cada chamada pode gerar envios futuros.

### Configuração

| Variável | Uso | Padrão |
|---|---|---|
| `VAPID_PUBLIC_KEY`, `VAPID_PRIVATE_KEY` | par P-256 em base64url (gerado uma vez; comando do spike documentado no BACKEND.md) | — (sem elas, push desligado) |
| `VAPID_SUBJECT` | `mailto:` de contato exigido pelos push services | `mailto:contato@futspring.luisgosampaio.com` |

Novos secrets no GitHub (`VAPID_PUBLIC_KEY`, `VAPID_PRIVATE_KEY`) escritos no `.env` pelo `deploy.yml`, listados em `core/.env.example` e no `docker-compose.dev.yml`. **Trocar o par invalida todas as inscrições** (os aparelhos se reinscrevem ao abrir o app); não trocar sem motivo.

### Métricas

`futspring.webpush.sent{result=ok|gone|failed}` (contador por envio), incluída no dashboard Futspring do Grafana.

## Testes

**Backend**

- `PushControllerTest`: sem token 401; upsert (cria, atualiza chaves, transfere de outra conta); limite de 10; `remove` de inscrição alheia não apaga; validação (endpoint fora da allowlist, `http`, porta, chaves com tamanho errado → 400); `vapid-public-key` 204 sem configuração.
- `PushEndpointPolicyTest`: allowlist, subdomínios, hosts parecidos (`push.apple.com.evil.com`), IP literal, porta.
- `WebPushDispatcherTest` (sender falso): matriz tipo × `pushable` × preferência × padrão; payload (`title`, `body` cortado, `url` com `?notificacao=`, `tag`, `unreadCount`); TTL/urgência; 410 apaga e 500 só loga; uma falha não para as outras; nada enviado sem commit; consultas constantes para 2 e 20 destinatários (`QueryCounter`).
- `NotificationControllerTest`: `GET/PUT preferences` com `pushEnabled` (parcial, linha apagada quando os dois voltam ao padrão, item sem canal → 400); unsubscribe por e-mail não mexe no push.
- `AccountSettingsTest` / testes de reset de senha: as inscrições somem.
- `ContractSnapshotTest`: `NotificationPreferenceDTO` e os DTOs novos; `FlywayMigrationTest` e `MigrationDataTest` (linha de preferência antiga continua com o mesmo significado de e-mail e push padrão).

**Frontend**

- `utils/pwaPlatform.test.ts`: user agents de iPhone Safari, iPadOS (Mac + touch), Chrome iOS, WhatsApp/Instagram/Facebook, Android Chrome, Samsung Internet, desktop, standalone.
- `api/push.test.ts`, `schemas/push.test.ts`.
- Visual (Playwright): `/instalar` nos contextos iPhone, Android e in-app (UA emulado por projeto ou por teste); `/settings#notificacoes` nos estados `inactive`, `active`, `needsInstall` e `denied` (`Notification.permission` e `PushManager` mockados no init script); `/auth` com o QR de `/instalar`. Fixtures novas em `e2e/mockApi.ts` (`/push/vapid-public-key`).
- Manual, no aparelho (checklist do PR 3): iPhone (iOS 16.4+ e 26) instalar → ativar → receber `TEAMS_SORTED` com o app fechado → tocar abre a sessão e marca como lida → badge; Android Chrome o mesmo; logout para de receber; troca de conta no mesmo aparelho passa a receber da conta nova.

## Entrega

Branch de integração `feat/pwa-push` (a partir de `dev`), três PRs para ela e depois um PR dela para `dev`:

1. [x] **`feat/pwa-shell`** — manifest, ícones, `src/sw.ts` (precache + navegação), `PwaUpdater`, `useOnline`, `utils/pwaPlatform`, `usePwaInstall`, `/instalar`, `InstallAppCard`, QR do `/auth`, nota de nginx. Já entrega valor sozinho: o app instala.

   Decisões da implementação:
   - **Ícones:** não há logo em alta resolução (o maior é `public/gerrard.webp`, 192 px); os PNGs foram gerados ampliando ele com `sips` (borda de 2 px cortada, fundo `#9E9C96` do próprio desenho). O 512 fica menos nítido; regerar quando houver um logo vetorial (comando no FRONTEND.md, seção PWA).
   - **Nota do nginx:** a spec citava um "README de deploy" que não existe; foi para a seção CI/CD do `core/docs/BACKEND.md`, com o bloco de configuração.
   - **Precache:** além de `landing/**`, ficam fora os ícones de 512 (lidos só na instalação) e os subsets do Inter que não são Latin (só baixados por `unicode-range`). 133 entradas, ~2,4 MB sem compressão.
   - **`android-prompt` só no Android:** o Chrome do desktop também dispara `beforeinstallprompt`; sem a restrição o card da Home apareceria no computador. O desktop fica com o QR Code.
   - **`installPrompt` em `lib/`:** o evento capturado em `main.tsx` fica num store de módulo (`lib/installPrompt.ts`) lido por `usePwaInstall` com `useSyncExternalStore`.
   - **`src/sw.ts`** tem `tsconfig.sw.json` (lib WebWorker) e sai do `tsconfig.app.json`; o ESLint usa os globals de service worker nele. O SW trata a mensagem `SKIP_WAITING` (exigida pelo `updateServiceWorker(true)` no modo prompt).
   - **Testes visuais:** `serviceWorkers: "block"` no Playwright (o SW responderia navegações pelo precache, fora do `page.route`); `/instalar` em quatro contextos por `userAgent` e o card da Home com UA de iPhone.
2. [x] **`feat/web-push-backend`** — spike da biblioteca, V18, `PushSubscription`, `PushController`, `PushEndpointPolicy`, `WebPushDispatcher`/`WebPushSender`, preferências com `pushEnabled`, renomeação para `NotificationContent`, limpeza nas mudanças de senha e na exclusão, envs, métrica e testes.

   Decisões da implementação:
   - **Biblioteca: `zerodep-web-push-java` 2.1.5.** No spike (JDK 25): zero dependências transitivas (OkHttp, httpclient5, Jetty e Vert.x são opcionais no pom dela), JWT VAPID `ES256` com assinatura conferida pela chave pública, `aes128gcm`, `TTL`/`Urgency`/`Topic`. Ela só monta o `java.net.http.HttpRequest`; quem envia é o nosso `HttpClient` (`Redirect.NEVER`, connect 5 s, request 10 s). `nl.martijndwars:web-push` foi descartada (BouncyCastle obrigatório + cliente Apache). O envio real para Apple/FCM só é provado no PR 3, no aparelho.
   - **Chaves VAPID** em base64url cru (o formato do `npx web-push generate-vapid-keys`). A biblioteca não aceita o escalar privado cru, então `WebPushConfig` o converte em `ECPrivateKey` (JCA) e, na subida, confere que a privada assina o que a pública verifica: par incompleto ou trocado derruba a subida em vez de falhar cada envio.
   - **`Topic`:** o header aceita no máximo 32 caracteres (RFC 8030) e uma tag como `ATTENDANCE_CONFIRMED_BY_ADMIN-123` passa disso; o `Topic` é o base64url de 24 bytes do SHA-256 da tag (32 caracteres). A tag inteira vai no payload para o SW.
   - **Limite de 10:** sai a de menor `COALESCE(last_success_at, created_at)`, nunca a que acabou de ser enviada (uma inscrição antiga reenviada não se apaga sozinha).
   - **`pushEnabled` no PUT:** os dois canais são opcionais por item; item sem nenhum → 400 "Informe e-mail ou push" (no service). Um push igual ao padrão é gravado como `null`, então a regra "a linha some quando os dois voltam ao padrão" vale sem caso especial. O unsubscribe do e-mail devolve o `pushEnabled` real e não mexe nele.
   - **Consultas constantes:** o dispatcher faz 4 consultas por lote (preferências e não lidas só para quem tem aparelho). O teste com `QueryCounter` compara `statements` (2 × 20 destinatários, dois aparelhos cada), não `reads`: `reads` conta cada linha hidratada, e o dispatch carrega uma inscrição por aparelho.
   - **Corpo do push** cortado em 180 com "…" sem partir emoji (`NotificationContent.pushBody()`).
   - **Client:** só o contrato: `pushEnabled` em `NotificationPreference`, `NotificationPreferenceChange` (canais opcionais) no `updateNotificationPreferences`, fixtures do e2e e o teste da api. A coluna Push e o resto ficam para o PR 3.
   - **Grafana:** painel "Web Push" (`futspring_webpush_sent_total` por `result`) no dashboard Futspring.
3. **`feat/web-push-client`** — `api/push`, `usePushSubscription`, handlers `push`/`notificationclick` no SW, `?notificacao=`, badge, logout, `EnablePushCard`, configurações com a coluna Push e testes visuais.

Docs no mesmo PR de cada parte: FRONTEND.md (stack, rotas, hooks, SW, PWA), BACKEND.md (domínio, endpoints, V18, envs, segurança, métricas).

## Riscos

- **iOS:** o push só funciona no app aberto pela tela de início; uma reinstalação gera um endpoint novo; o iOS pode revogar a inscrição sem avisar (o reenvio ao abrir o app cobre isso). Os nomes dos botões mudam entre versões do iOS, então o tutorial descreve os ícones e não só os nomes.
- **Notificação duplicada com o app aberto:** o toast do STOMP e a notificação do sistema aparecem juntos. Fica aceito na V1; se incomodar, o SW pode pular a notificação quando houver um cliente `visible` (no Chrome) e manter o comportamento no iOS.
- **Cache velho:** sem o `no-cache` do `sw.js` no nginx, um aparelho pode ficar dias numa versão antiga. O passo de deploy é obrigatório antes do merge do PR 1 em `main`.
- **Instância única:** o envio roda em `@Async` em memória, como o e-mail; uma notificação ainda não enviada se perde num restart (aceito, igual ao e-mail).
