Cole o texto abaixo no Claude Code, na raiz do repositório, com a pasta `design_handoff_futspring_redesign/` copiada para dentro do repo (por exemplo, em `docs/design/`).

---

Quero implementar o redesign do client FutSpring descrito em `docs/design/design_handoff_futspring_redesign/README.md`. Os arquivos `.dc.html` dessa pasta são protótipos HTML de referência visual: abra e leia o código deles para tirar medidas exatas, mas **não copie o HTML**. Recrie tudo em React/TypeScript seguindo o projeto.

Antes de qualquer código:
1. Leia `client/docs/FRONTEND.md` e `core/docs/BACKEND.md` inteiros. Siga as regras deles (shadcn obrigatório, tokens semânticos em vez de hex, react-hook-form + zod em formulários, imports `@/`, textos em PT-BR, hooks por feature, `Record<Status, …>` para status, e o restante).
2. Leia o README do handoff e os três `.dc.html`.
3. Me apresente um plano dividido em etapas pequenas (uma PR por etapa), com os arquivos que vai criar ou alterar, os componentes shadcn que precisa adicionar (rode o CLI com `--dry-run` antes) e os tokens novos de `index.css` (`:root` e `.dark`). Espere minha aprovação.

Ordem sugerida das etapas:
1. Tokens novos em `index.css` e `tailwind.config.js`, e os mapas de status em `types/daily.ts`.
2. App shell: `AppLayout` com sidebar (shadcn `sidebar`), substituindo o `NavBar` nas rotas privadas, com a versão mobile em Sheet. Atualize a seção Routing do FRONTEND.md.
3. Pelada: banner, tabs (sem scroll no mobile), Membros, Sessões, Ranking, Prêmios (com destaque do líder) e chat em botão flutuante + dialog/drawer.
4. Diária: os três estados (Agendada, Em andamento, Finalizada) e o novo dialog "Lançar resultados" com react-hook-form + zod + `useFieldArray`, substituindo `ResultsModal`/`ResultsForm`.
5. Home: banner com saudação, carrossel de próximas sessões no mobile, Seus números e Minhas peladas.
6. Backend para a Home: endpoint ou campo com a próxima sessão do usuário em cada pelada (id, status, confirmados, capacidade, se o usuário confirmou), seguindo BACKEND.md (DTO, serviço com checagem de membro, testes, tabela da API) e atualizando `src/api` e `src/types`.

Regras de fidelidade:
- **Pixel-perfect:** respeite os tamanhos, pesos, gaps, paddings, raios e larguras de grid que estão no README e nos protótipos. Quando o README e o protótipo divergirem, vale o protótipo.
- **Cores:** cada hex do protótipo vira um token (veja a tabela "Design Tokens"). O light mode precisa funcionar.
- **Mobile:** confira todas as telas em 375px e em desktop de 1440px.
- **Etapas:** ao fim de cada uma, rode `bun run lint && bun run build && bun run test` e, se mexer no backend, `./mvnw test`.
- **Dados:** não invente. Tudo vem da API. Onde o design pede um dado que a API ainda não tem, me avise e proponha a mudança no backend em vez de fazer um mock.
