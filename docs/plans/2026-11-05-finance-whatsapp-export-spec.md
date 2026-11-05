# Spec — Compartilhar cobrança no WhatsApp

Data: 2026-11-05 · Status: aprovada (brainstorm) · Escopo: só frontend (`client/`)

## Objetivo

O gestor financeiro de uma pelada gera, a partir de uma cobrança aberta, uma mensagem pronta para colar no grupo do WhatsApp: a composição do valor, quem ainda falta pagar (quando a visibilidade permite) e, no final, o link que leva direto à cobrança no app, onde o membro vê o Pix e informa o pagamento.

## Decisões

| Tema | Decisão |
|------|---------|
| Composição | Os componentes da lista aparecem **uma vez no topo** (preço por pessoa + total); depois uma lista única de devedores com o valor que falta para cada um |
| Privacidade | Nomes **só no modo Transparente**. No modo Individual (padrão) a mensagem é anônima: contagens e total em aberto |
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
   - **Transparente:** `⏳ Faltam pagar ({n}):` e uma linha numerada por devedor `{n}. {nome} — R$ {outstanding}`, com ` (parcial)` quando `PARTIAL` e ` ⚠️ vencida` quando `overdue`; depois `✅ Pagos ({n}): {nomes separados por vírgula}`. Convidados entram pelo nome.
   - **Individual:** `⏳ {n} pessoas ainda não pagaram · R$ {total em aberto} em aberto` e `✅ {n} já pagaram` (singular quando 1).
   - Ninguém devendo: `✅ Todos pagaram!` no lugar dos blocos acima.
4. Pix da própria lista (`collection.pixKey` / `pixRecipient`, os que a lista gravou): `Pix: {chave} ({recebedor})`; a linha some sem chave.
5. `👉 Pague e informe aqui: {link}`.

**Regras dos dados:**

- Cobranças canceladas (`canceled`) nunca entram em nenhuma contagem.
- Quem tem um aviso de pagamento pendente (`notices` com `PENDING`) não entra em "Faltam pagar" (está sendo conferido, não deve ser cobrado à toa) nem em "Pagos"; no Transparente aparece a linha `🔎 Conferindo ({n}): {nomes}`, no Individual `🔎 {n} pagamentos em conferência`. Só aparece quando houver algum.
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
| Aviso | Só no modo Individual, `Alert` discreto: "Modo Individual: a mensagem não mostra quem deve. Para listar nomes, mude para Transparente em Configurações." |
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

- `utils/financeWhatsApp.ts`: função pura `financeWhatsAppMessage(detail, visibility, link, today)` → `string` (sem React), com testes ao lado.
- `components/finance/FinanceWhatsAppDialog.tsx`: o diálogo.
- Ajustes em `FinanceListDetail` (item do menu), `FinanceTab` / `useFinance` (seleção pela URL), `App.tsx` (rota), `utils/peladaAreas.ts`.

## 3. Testes, docs e fora de escopo

**Testes (Vitest):**

- `utils/financeWhatsApp.test.ts`: Transparente vs Individual; um componente vs vários; nota de valores diferentes; parcial; vencida; conferindo fora dos devedores; canceladas fora; convidados; "Todos pagaram"; sem Pix; sem vencimento / venceu; ordem sem acento; singular/plural; valores exatos em string.
- `utils/peladaAreas.test.ts`: `financeiro/42` reconhecido como Financeiro.

**Visual (Playwright):** snapshot do diálogo numa lista Aberta em modo Transparente (sem fixture nova: nenhum endpoint novo); o deep link abrindo o detalhe direto.

**Docs:** `client/docs/FRONTEND.md` — rota `/pelada/:id/financeiro/:listId` na tabela de rotas e em "Pelada navigation", o diálogo na seção Financeiro, o util e o componente na árvore de pastas. `core/docs/BACKEND.md` não muda.

**Fora de escopo:** página pública sem login; modelo de mensagem salvo por pelada; envio automático (WhatsApp Business API); mensagem para Rascunho/Encerrada/Cancelada; lembrete agendado; exportar várias cobranças de uma vez.
