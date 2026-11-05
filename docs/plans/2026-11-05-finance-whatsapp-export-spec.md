# Spec — Compartilhar cobrança no WhatsApp

Data: 2026-11-05 · Status: implementada (`feat/finance-whatsapp-share`) · Escopo: só frontend (`client/`)

## Objetivo

O gestor financeiro de uma pelada gera, a partir de uma cobrança aberta, uma mensagem pronta para colar no grupo do WhatsApp: a composição do valor, quem ainda falta pagar (quando a visibilidade permite) e, no final, o link que leva direto à cobrança no app, onde o membro vê o Pix e informa o pagamento.

## Decisões

| Tema | Decisão |
|------|---------|
| Composição | Os componentes da lista aparecem **uma vez no topo** (preço por pessoa + total); depois uma lista única de devedores com o valor que falta para cada um |
| Privacidade | A mensagem **sempre lista nomes e valores**: só gestores a montam, e o gestor vê todas as cobranças. A visibilidade Individual / Transparente vale só para o que os membros veem no app (revisado em 2026-11-05; antes a mensagem era anônima no modo Individual) |
| Link | Deep link novo para a cobrança no app (`/pelada/:id/financeiro/:listId`), com login; nenhuma página pública |
| Onde a mensagem é montada | **No frontend**, a partir do `FinanceDetail` já carregado na tela: nenhum endpoint e nenhuma requisição nova (evitar carga no servidor) |
| Prévia | **Editável** antes de copiar/enviar; a edição não é salva |

Abordagens descartadas: o backend renderizar o texto (`GET /lists/{id}/share-text`: contrato novo para algo de apresentação, sem ganho de permissão, pois o gestor já recebe todos os dados) e um modelo de mensagem salvo por pelada (configuração, validação e migration para algo que o WhatsApp já deixa editar).

## 1. Escopo e regras da mensagem

**Quem e onde:** só gestores (`overview.permissions.manage`), no item **"Compartilhar no WhatsApp"** do menu ⋯ do detalhe da cobrança (`FinanceListDetail`), apenas com a lista **Aberta** (`OPEN`). Rascunho não é visível para membros pelo link; Encerrada e Cancelada não se cobram.

**Conteúdo, nesta ordem:**

1. `*{nome da cobrança}*` e, havendo `dueDate`, `Vence 15/11` (ou `Venceu 15/11` depois da data, no fuso da pelada), com `formatShortDate`.
2. Composição por pessoa, de `collection.components`:
   - vários componentes: `• {nome} — R$ {valor}` por linha e `*Total: R$ {soma}*`;
   - um componente: só `Valor: R$ {valor}`;
   - se algum participante ativo tiver composição/ajuste diferente da lista: a nota `Alguns participantes têm valor diferente.`
3. Situação dos pagamentos:
   - `⏳ Faltam pagar ({n}):` e uma linha numerada por devedor `{n}. {nome} — R$ {outstanding}`, com ` (parcial)` quando `PARTIAL` e ` ⚠️ vencida` quando `overdue`; depois `✅ Pagos ({n}):` e uma linha numerada por pago `{n}. {nome} — R$ {valor devido}`. Convidados entram pelo nome.
   - Ninguém devendo: `✅ Todos pagaram!` no lugar dos blocos acima.
4. Pix da própria lista (`collection.pixKey` / `pixRecipient`, os que a lista gravou): `Pix: {chave} ({recebedor})`; a linha some sem chave.
5. `👉 Pague e informe aqui: {link}`.

**Regras dos dados:**

- Cobranças canceladas (`canceled`) nunca entram em nenhuma contagem.
- Quem tem um aviso de pagamento pendente (`notices` com `PENDING`) não entra em "Faltam pagar" (está sendo conferido, não deve ser cobrado à toa) nem em "Pagos"; aparece a linha `🔎 Conferindo ({n}): {nomes}`. Só aparece quando houver algum.
- Devedor = não cancelado, sem aviso pendente, `outstanding` > 0. Pago = não cancelado, `paymentStatus` `PAID`.
- Devedores e pagos ordenados por nome, sem diferenciar acento/caixa (`normalizeSearch`).
- Valores com `formatFinanceMoney` e somas em string exata (helpers de `utils/finance.ts`), sem ponto flutuante.
- Negrito no formato do WhatsApp (`*texto*`); o texto é puro, sem HTML.

## 2. Deep link e diálogo

### Rota da cobrança

- Nova rota filha `financeiro/:listId` em `App.tsx`, com a mesma `PeladaFinancePage` (lazy).
- A URL passa a ser a fonte da cobrança aberta: `FinanceTab` lê `useParams().listId`; abrir uma cobrança navega para `…/financeiro/{id}`, "← Cobranças" (e a aba Cobranças) volta para `…/financeiro`. Voltar/avançar do navegador abrem e fecham o detalhe; recarregar mantém a cobrança aberta. O carregamento do detalhe continua no efeito existente de `useFinance` (o `selected` vem da URL); nenhuma requisição a mais.
- `areaFromPath` / `isPeladaPath` reconhecem `financeiro/{id}` como a área Financeiro (seletor de áreas, sidebar); o chat continua escondido.
- Lista não visível para quem abriu (403/404 do backend, p.ex. membro fora da lista no modo Individual, ou id de outra pelada): o estado de erro do detalhe já existente, com "← Cobranças". O backend não muda.
- Tour: continua iniciando sozinho apenas na lista de cobranças sem nada aberto; quem chega pelo link do grupo não vê o tour por cima da cobrança.
- Deslogado: o interceptor de 401 já leva a `/auth?expired=1&returnTo=…` preservando o caminho; após o login a pessoa cai na cobrança (membro: `FinancePixCard` + "Informar pagamento"; gestor: detalhe em "A receber").

### Diálogo "Compartilhar no WhatsApp"

`Dialog` shadcn aberto pelo item do ⋯.

| Peça | Comportamento |
|------|---------------|
| Mensagem | `Textarea` com `Label` "Mensagem", preenchido com o texto gerado ao abrir; cresce com o conteúdo até uma altura máxima, depois rola. **Editável** |
| Restaurar texto original | Botão `ghost`, visível só depois de uma edição; volta ao texto gerado |
| **Abrir no WhatsApp** | `Button variant="brand"`; abre `https://wa.me/?text={encodeURIComponent(texto)}` em nova aba (no celular abre o app para escolher o grupo; o texto chega editável) |
| Copiar | `outline`; `navigator.clipboard.writeText`, toast "Mensagem copiada" (erro: toast "Não foi possível copiar") |
| Compartilhar | Botão de ícone (`aria-label="Compartilhar"`), só onde `navigator.share` existe, como em `InviteLinkCard` |

- Os três botões usam o **conteúdo atual** do campo e ficam desabilitados com o campo vazio (só espaços).
- A edição não é salva: fechar e abrir gera de novo a partir dos dados atuais (evita guardar valores desatualizados).
- Mensagem longa: acima de um limite de caracteres do texto atual (a URL do `wa.me` é cortada por volta de 2 mil caracteres codificados), "Abrir no WhatsApp" fica desabilitado com a dica "Mensagem longa: use Copiar". Nada é truncado em silêncio.
- Link: `window.location.origin` + `/pelada/{peladaId}/financeiro/{listId}` (app e API na mesma origem em produção).
- O texto é gerado do detalhe na tela no momento em que o diálogo abre (o detalhe já é recarregado quando a aba volta a ficar visível).
- Estado local controlado (`useState`), **sem react-hook-form + zod**: não há envio ao servidor nem validação além de "não vazio". Desvio consciente da regra de [Forms] do FRONTEND.md, registrado aqui.
- Funciona em claro/escuro e em 375 px; textos em pt-BR; ícones Hugeicons.

### Código

- `utils/financeWhatsApp.ts`: função pura `financeWhatsAppMessage(detail, link, today)` → `string` (sem React), com testes ao lado.
- `components/finance/FinanceWhatsAppDialog.tsx`: o diálogo.
- Ajustes em `FinanceListDetail` (item do menu), `FinanceTab` / `useFinance` (seleção pela URL), `App.tsx` (rota), `utils/peladaAreas.ts`.

## 3. Testes, docs e fora de escopo

**Testes (Vitest):**

- `utils/financeWhatsApp.test.ts`: um componente vs vários; nota de valores diferentes; parcial; vencida; conferindo fora dos devedores; canceladas fora; convidados; "Todos pagaram"; sem Pix; sem vencimento / venceu; ordem sem acento; singular/plural; valores exatos em string.
- `utils/peladaAreas.test.ts`: `financeiro/42` reconhecido como Financeiro.

**Visual (Playwright):** snapshot do diálogo numa lista Aberta (sem fixture nova: nenhum endpoint novo); o deep link abrindo o detalhe direto.

**Docs:** `client/docs/FRONTEND.md` — rota `/pelada/:id/financeiro/:listId` na tabela de rotas e em "Pelada navigation", o diálogo na seção Financeiro, o util e o componente na árvore de pastas. `core/docs/BACKEND.md` não muda.

**Fora de escopo:** página pública sem login; modelo de mensagem salvo por pelada; envio automático (WhatsApp Business API); mensagem para Rascunho/Encerrada/Cancelada; lembrete agendado; exportar várias cobranças de uma vez.

## Implementação

- [x] Deep link, gerador da mensagem, diálogo, testes e docs (2026-11-05)

**Decisões de implementação:**

- `useFinance(peladaId, selected, select)`: a cobrança aberta vem da URL (`FinanceTab` lê `listId`); detalhe e erro ficam guardados por id de cobrança, então trocar de cobrança mostra o skeleton e nunca a anterior. Um resultado de comando de outra cobrança (criar, duplicar) navega para ela (entra no histórico do navegador).
- Trocar para Caixa / Configurações / Histórico com uma cobrança aberta substitui a URL por `…/financeiro` (`replace`), para a aba e a URL não se contradizerem. Um `listId` não numérico abre a lista de cobranças.
- `areaFromPath` só aceita segundo segmento em `financeiro/<dígitos>`; qualquer outro continua desconhecido.
- "Hoje" no fuso da pelada: novo `todayInZone(timeZone)` em `utils/dates.ts`; "Venceu" quando `dueDate` é anterior a hoje (no dia do vencimento ainda é "Vence").
- Blocos vazios somem: sem pagos não há "✅ Pagos" / "já pagaram"; sem conferência, sem "🔎"; sem participantes ativos, sem bloco de situação. "✅ Todos pagaram!" só quando ninguém deve **e** ninguém está em conferência.
- Pix sem recebedor: `Pix: {chave}`.
- Limite do "Abrir no WhatsApp": a **URL codificada** acima de 2000 caracteres (`WHATSAPP_URL_MAX`), porque emoji e acentos ocupam vários caracteres codificados.
- O diálogo é montado só enquanto aberto (como `FinanceReceiveAllDialog`), então reabrir sempre gera o texto de novo. O campo cresce com `field-sizing: content` até 50vh; navegadores sem suporte ficam com 10 linhas e rolagem.
- Snapshot visual `finance-whatsapp.png` (4 projetos): abre `/financeiro/50` direto, com as fixtures existentes.
- Revisão (2026-11-05): a mensagem passou a listar nomes e valores sempre (também os pagos, com o valor devido) e o aviso do modo Individual saiu do diálogo; a visibilidade controla só o app dos membros.
