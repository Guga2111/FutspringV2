# Termos de uso e Política de privacidade — spec

Data: 06/11/2026 · Branch: `feat/legal-pages` (worktree `../FutspringV2-legal`, a partir de `dev`)

## Por quê

O cadastro já tem a caixa "Li e concordo com os termos de uso" (`RegisterForm`, campo `terms` em `schemas/auth.ts`), mas:

- não existe texto de termos nem de política de privacidade;
- a caixa não tem link para nada;
- o aceite só existe no navegador: o servidor não sabe se a conta aceitou, nem qual versão, nem quando.

Pela LGPD, o app precisa informar com clareza quais dados trata, para quê, com quem compartilha e como o titular exerce seus direitos, e precisa conseguir provar o aceite. Esta entrega cobre isso, sem mexer na tela de configurações da conta, que outro agente está fazendo em paralelo (inclusive a exclusão de conta).

## Escopo

**Dentro**

1. Duas páginas públicas: `/termos` (Termos de uso) e `/privacidade` (Política de privacidade).
2. Links para elas no cadastro e no rodapé da landing.
3. O cadastro envia a versão dos termos que a pessoa viu; o servidor confere e grava versão + data do aceite na conta.
4. Documentação (`FRONTEND.md`, `BACKEND.md`) e testes.

**Fora**

- Pedir um novo aceite quando os termos mudarem (diálogo bloqueante para contas antigas). Fica para quando houver a primeira mudança de texto.
- Exclusão de conta e exportação de dados: são do outro agente / da spec LGPD separada. A política só descreve onde a pessoa faz isso.
- Link dentro do app (configurações da conta, menu do usuário): entra num follow-up pequeno depois que a branch das configurações for mergeada, para os dois agentes não editarem os mesmos arquivos.
- Banner de cookies: o app não usa cookies de rastreamento nem analytics (só `localStorage` necessário ao funcionamento), então não há consentimento de cookies a pedir.

## Comportamento

### Páginas `/termos` e `/privacidade`

- Públicas, abertas logado ou não (mesmo padrão de `/forgot-password`: `React.lazy`, `<ErrorBoundary>` → página).
- Layout próprio, `components/legal/LegalLayout.tsx`:
  - logo "Futspring" no topo, linkando para `/` (que manda para `/home` quem está logado);
  - título em Barlow ("Termos de uso" / "Política de privacidade");
  - "Vigente desde 06/11/2026 · versão 2026-11-06";
  - índice com as seções (âncoras `#conta`, `#dados-coletados`…), para dar para mandar o link de uma seção;
  - texto numa coluna de leitura de ~720 px, `text-body-lg`, tokens semânticos, claro e escuro, sem scroll horizontal em 375 px;
  - no fim: link para o outro documento e "Voltar ao início".
- Textos em `components/legal/content.ts`, como dados (`{ id, title, paragraphs }` por seção), um lugar só para editar. Sem Markdown nem biblioteca nova.
- Placeholders aparecem literalmente no texto até você preencher: `[NOME DO CONTROLADOR]`, `[E-MAIL DE CONTATO]`, `[CIDADE-UF]`, `[PROVEDOR DA VPS]`. **Não publicar em produção com placeholders.**

### Links

- Cadastro: o rótulo vira "Li e concordo com os **Termos de uso** e a **Política de privacidade**". Os dois são links que abrem em nova aba (`target="_blank" rel="noopener"`), para a pessoa não perder o que já preencheu. Clicar no link não marca a caixa.
- Rodapé da landing: "Termos" · "Privacidade" na linha do ©.
- Cada página linka a outra.

### Aceite gravado

- `lib/legal.ts` exporta `LEGAL_VERSION = "2026-11-06"` (uma versão para os dois documentos: mudam juntos ou a versão sobe mesmo assim).
- O cadastro continua exigindo a caixa no cliente; ao enviar, manda `termsVersion: LEGAL_VERSION` no `POST /auth/register`.
- O servidor tem a fonte da verdade: `LegalTerms.CURRENT_VERSION` em `domain/auth`.
  - sem `termsVersion` → 400 (validação, "Aceite os termos de uso para criar a conta");
  - versão diferente da atual (aba aberta com o bundle antigo) → 409 "Os termos foram atualizados. Recarregue a página para ler a nova versão." — aparece no `Alert` do formulário como os outros erros de raiz;
  - versão certa → grava `termsVersion` e `termsAcceptedAt` (`Instant.now(clock)`) junto com a conta.
- Contas criadas antes ficam com os dois campos nulos (não há como provar o aceite delas; o novo aceite fica para o follow-up).
- Nada disso aparece em DTO de resposta.

Trocar a versão no futuro = mudar `LEGAL_VERSION`, `LegalTerms.CURRENT_VERSION` e o texto no mesmo PR.

## Backend

| Arquivo | Mudança |
|---|---|
| `domain/auth/LegalTerms.java` (novo) | `CURRENT_VERSION = "2026-11-06"` |
| `domain/auth/dto/RegisterRequestDTO.java` | `@NotBlank String termsVersion` |
| `domain/auth/AuthService.java` | confere a versão (409) e grava os dois campos; `Clock` injetado |
| `domain/user/User.java` | `termsVersion` (`terms_version`), `termsAcceptedAt` (`terms_accepted_at`, `Instant`) |
| `db/migration/V<n>__add_user_terms_acceptance.sql` | `ALTER TABLE users ADD COLUMN terms_version VARCHAR(20), ADD COLUMN terms_accepted_at TIMESTAMPTZ;` — aditiva, nula, sem backfill |
| `test/resources/contract/requests/RegisterRequestDTO.json` | com `termsVersion` |

**Número da migração:** `dev` vai até V14, `feat/finance-responsaveis` traz V15 e a branch das configurações pode trazer outra. O número é escolhido na hora de abrir o PR, olhando `dev`/`main` (regra 1 das migrações).

A exclusão de conta do outro agente apaga/anonimiza o usuário; os dois campos novos somem junto com a linha ou são limpos pela anonimização — conferir na revisão da branch dele.

## Frontend

| Arquivo | Mudança |
|---|---|
| `lib/legal.ts` (novo) | `LEGAL_VERSION` |
| `components/legal/LegalLayout.tsx`, `content.ts` (novos) | layout e textos |
| `pages/TermsPage.tsx`, `pages/PrivacyPage.tsx` (novos) | páginas (default export) |
| `App.tsx` | duas rotas públicas |
| `api/auth.ts` | `RegisterData` com `termsVersion` |
| `components/auth/hooks/useAuthForms.ts` | envia `termsVersion: LEGAL_VERSION` |
| `components/auth/RegisterForm.tsx` | rótulo com os dois links |
| `components/landing/LandingFooter.tsx` | links Termos · Privacidade |
| `e2e/visual.spec.ts` + snapshots | `/termos` e `/privacidade` nos 4 projetos |

## Conteúdo dos textos (esboço)

Só o que o app de fato faz hoje. Linguagem simples, em segunda pessoa ("você").

### Termos de uso

1. **Quem somos** — o Futspring é um app gratuito para organizar peladas, mantido por `[NOME DO CONTROLADOR]`. Contato: `[E-MAIL DE CONTATO]`.
2. **Aceite** — ao criar a conta você aceita estes termos e a Política de privacidade.
3. **Sua conta** — dados verdadeiros, senha pessoal, você responde pelo que for feito com ela. Idade mínima: **18 anos, ou 13 com autorização do responsável** *(confirmar na revisão)*.
4. **Peladas e papéis** — o criador e os admins configuram a pelada, convidam e removem membros, montam times, lançam resultados e dão estrelas; membros confirmam presença, conversam no chat e veem os números da pelada. O criador pode transferir a pelada para um admin.
5. **Conteúdo que você envia** — fotos de perfil, da pelada e de campeão, mensagens do chat e comprovantes. Você continua dono; nos dá permissão só para guardar e mostrar aos membros da pelada. Não envie conteúdo ilegal, ofensivo ou de terceiros sem autorização.
6. **Financeiro** — o app só **registra** cobranças e pagamentos combinados entre os membros. Não processa pagamentos, não guarda dinheiro, não é instituição financeira. O Pix é do recebedor indicado pelos responsáveis financeiros; acertos e divergências são entre os membros.
7. **Uso proibido** — tentar acessar dados de outras peladas, burlar limites, automatizar cadastros, assediar outros usuários.
8. **Suspensão** — podemos suspender contas que violem estes termos.
9. **Encerrar a conta** — você pode excluir a conta em Configurações › Zona de perigo.
10. **Serviço "como está"** — gratuito, sem garantia de disponibilidade; não respondemos por desentendimentos entre membros nem por valores combinados no financeiro.
11. **Mudanças** — avisaremos mudanças relevantes no app ou por e-mail; a versão e a data ficam no topo.
12. **Lei e foro** — leis brasileiras; foro de `[CIDADE-UF]`.

### Política de privacidade

1. **Controlador e contato** — `[NOME DO CONTROLADOR]`, `[E-MAIL DE CONTATO]` (também o canal do encarregado).
2. **Dados que tratamos**
   - Conta: nome de usuário, e-mail, senha (guardada só como hash BCrypt), posição, foto.
   - Pelada: participação, admin/mensalista, presença e horário da confirmação, times, gols, assistências, vitórias, prêmios, estrelas dadas pelos admins, mensagens do chat, fotos.
   - Financeiro: cobranças, valores, pagamentos, avisos de pagamento, comprovantes (imagem), chave Pix e QR Code dos responsáveis, histórico de alterações.
   - Notificações e preferências de e-mail; guias que você já viu.
   - Técnicos: endereço IP (limite de tentativas de login e de links públicos), registros do servidor.
3. **Para que e com qual base legal (art. 7º)**
   - Fazer o app funcionar (contas, peladas, sessões, estatísticas, financeiro, e-mails de senha): execução do contrato.
   - Segurança e prevenção de abuso (IP, limites, logs): legítimo interesse.
   - E-mails de notificação: legítimo interesse, com desativação em um clique em cada e-mail e em Configurações.
4. **Quem vê seus dados**
   - Membros das peladas em que você está: nome, foto, posição, presença, estatísticas, mensagens.
   - Seu perfil só é visto por quem divide uma pelada com você; o e-mail só por você.
   - Financeiro: no modo individual, cada um vê só as próprias cobranças; no transparente, os membros veem participantes, totais e caixa. Comprovantes: só você e os responsáveis financeiros.
5. **Operadores** — Supabase (banco de dados), Resend (envio de e-mails), `[PROVEDOR DA VPS]` (servidor). Não vendemos dados, não temos anúncios nem analytics de terceiros.
6. **Transferência internacional** — esses provedores podem guardar dados fora do Brasil, com as garantias contratuais deles.
7. **Armazenamento no seu navegador** — usamos `localStorage` só para manter a sessão e lembrar preferências de tela (tema, barra lateral, visualização). Sem cookies de rastreamento.
8. **Por quanto tempo**
   - Conta e dados da pelada: enquanto a conta existir.
   - Comprovantes: acesso por 7 dias (cobranças de sessão) ou 30 dias (mensais e outras), depois o arquivo é apagado.
   - Notificações: lidas apagadas após 90 dias; todas após 180.
   - Links de troca de senha: valem 30 minutos.
   - Cópias de segurança do banco: as últimas 10.
   - Ao excluir a conta: seus dados pessoais são apagados e nas sessões passadas você aparece como "Ex-jogador"; registros financeiros já publicados são mantidos de forma a não identificar você, para não alterar as contas da pelada *(conferir com a spec da exclusão)*.
9. **Seus direitos (art. 18)** — confirmação, acesso, correção (você mesmo edita o perfil), exclusão (Configurações › Zona de perigo), portabilidade, informação sobre compartilhamento, revogação de consentimento. Pedidos por `[E-MAIL DE CONTATO]`, respondidos em até 15 dias. Você também pode reclamar à ANPD.
10. **Segurança** — conexão HTTPS, senha com hash, sessões que podem ser encerradas trocando a senha, arquivos financeiros em área privada.
11. **Menores** — mesma regra de idade dos termos.
12. **Mudanças** — versão e data no topo; mudanças relevantes avisadas no app ou por e-mail.

## Testes e verificação

- Backend (`./mvnw test`): `AuthControllerTest` — cadastro grava `termsVersion` e `termsAcceptedAt` (clock fixo); versão diferente → 409; sem versão → 400. `ContractSnapshotTest` com o request novo. `FlywayMigrationTest` (Docker) valida a migração contra as entidades.
- Frontend (`bun run lint && bun run build && bun run test`): teste do `registerSchema` continua; teste do payload de `register` com `termsVersion`.
- Visual: `/termos` e `/privacidade` em `e2e/visual.spec.ts`, snapshots gerados no Docker e revisados.
- Manual: criar conta pelo formulário e conferir as colunas no banco; abrir os links do cadastro e do rodapé em 375 px, claro e escuro.

## Pontos para você decidir na revisão

1. Idade mínima: 18, ou 13 com autorização do responsável?
2. Base legal dos e-mails de notificação: legítimo interesse (proposto) ou consentimento?
3. O que a exclusão de conta mantém (financeiro anonimizado?) — alinhar com o outro agente.
4. Valores dos placeholders, quando quiser publicar.
