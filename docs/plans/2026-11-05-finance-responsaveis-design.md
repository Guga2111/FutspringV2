# Financeiro: gestão só para responsáveis financeiros

Data: 2026-11-05 · Estado: implementado

## Problema

Admin, criador e responsável financeiro tinham a mesma visão de gestão no Financeiro: ser admin já dava acesso a cobranças, caixa, histórico e configurações. A ideia é separar os papéis: o admin **escolhe** quem cuida do dinheiro, e **só os responsáveis designados** têm a visão e as ações de gestão.

## Decisões

| Tema | Decisão |
|---|---|
| Quem gere o financeiro | só os responsáveis designados (`finance_managers`); ser admin ou criador não basta |
| Admin/criador que não é responsável | vê o Financeiro como um membro comum (Individual ou Transparente) e um aviso "Você não é responsável financeiro", com um botão para Configurações |
| Onde designar | Financeiro > Configurações; o admin que não é responsável vê essa aba só com o card "Responsáveis financeiros" |
| Quem designa | criador, admins **e** responsáveis (mesmo os que não são admin) |
| Quem pode ser designado | qualquer membro atual, admins inclusive |
| Peladas existentes | a migration V15 designa todo admin e criador atual, então ninguém perde acesso |
| Pelada nova | o criador já nasce responsável |
| Virar admin / receber a pelada por transferência | não designa ninguém automaticamente |
| Mínimo | sempre 1 ou mais: salvar a lista vazia → 409; remover da pelada o último responsável (individual ou em lote) → 409 "Fulano é o único responsável financeiro. Designe outro antes de removê-lo." |

## Backend

- `FinanceAccess`: `manage` = é responsável designado; `appoint` = `manage` ou criador ou admin. `viewCash` continua `manage || TRANSPARENT`. `requireManager` diz "Apenas responsáveis financeiros podem fazer isso".
- `PUT /finance/managers` não exige mais `manage`, só `appoint`; recusa a lista vazia; `settings.managerIds` é devolvido a quem pode designar. A resposta traz as permissões do chamador **depois** da mudança (`FinanceAccess.refresh`), então quem se retira já vê a visão de membro.
- `FinancePreservation.removeMembers` recusa a remoção que deixaria a pelada sem responsável, antes de qualquer alteração. `FinancePreservation.appointCreator` designa o criador em `PeladaService.createPelada` e no seed de dev.
- `V15__finance_managers_from_admins.sql`: insere em `finance_managers` cada admin e criador que é membro (`ON CONFLICT DO NOTHING`). Não é destrutiva; o rollback do binário antigo continua funcionando, já que o código antigo também tratava os admins como gestores.

## Frontend

- `FinanceTab`: a aba Configurações aparece para `manage || appointManagers`; Histórico só para `manage`. Mostra o `Alert` quando `appointManagers && !manage`.
- `FinanceSettings`: Pix, visibilidade e modelos só para `manage`; o card Responsáveis para `appointManagers`, com todos os membros na lista (admins marcados "Admin") e o schema com `.min(1)`.
- O tour já segue `manage`: o admin que não é responsável recebe o tour de membro.

## Testes

- `FinanceServiceTest`: admin sem designação (manage falso, appoint verdadeiro, 403 na gestão); um responsável que não é admin designa outros; quem se retira recebe as permissões novas; lista vazia → 409; remover o último responsável → 409 sem nenhuma alteração; o criador de uma pelada nova é responsável.
- `MigrationDataTest.v15_…`: admins e criador viram responsáveis, os delegados existentes ficam, sem duplicata, e quem não é membro fica de fora.
- Visual: `finance-settings` atualizado e o novo `finance-admin-not-manager`.
