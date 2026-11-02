import type { TourStepDetails } from "@ark-ui/react/tour"
import type { TourKey } from "@/types/tour"

// The elements the finance tour points at, marked with data-tour in FinanceTab
export type FinanceTourTarget = "new-collection" | "scope" | "collections" | "cash-tab" | "settings-tab" | "help"

export function financeTourKey(manage: boolean): TourKey {
  return manage ? "FINANCE_MANAGER" : "FINANCE_MEMBER"
}

interface StepText {
  id: string
  target?: FinanceTourTarget
  title: string
  description: string
}

const welcomeManager: StepText = { id: "welcome", title: "Bem-vindo ao financeiro", description: "Em poucos passos, veja onde criar cobranças, receber pagamentos e acompanhar o caixa da pelada." }
const welcomeMember: StepText = { id: "welcome", title: "O financeiro da pelada", description: "Aqui você vê o que falta pagar, paga com Pix e avisa quando pagou." }
const help: StepText = { id: "help", target: "help", title: "Reveja quando quiser", description: "Este botão abre o tour de novo." }

// The steps of each role; Caixa only for whoever sees the cash (managers, or members in transparent mode)
export function financeTourSteps(manage: boolean, viewCash: boolean): TourStepDetails[] {
  const texts: StepText[] = manage
    ? [
        welcomeManager,
        { id: "new-collection", target: "new-collection", title: "Crie uma cobrança", description: "Defina o valor por pessoa, escolha quem paga e publique: mensalidade, diária ou evento." },
        { id: "scope", target: "scope", title: "Em aberto e encerradas", description: "Cobranças publicadas ficam em aberto até você encerrar. As encerradas e canceladas ficam guardadas." },
        { id: "collections", target: "collections", title: "Receba e confira", description: "Abra uma cobrança para registrar quem pagou e conferir os pagamentos que os membros informarem." },
        ...(viewCash ? [{ id: "cash", target: "cash-tab", title: "Caixa", description: "Saldo, despesas e outras entradas. Só o dinheiro que entrou de verdade conta no saldo." } satisfies StepText] : []),
        { id: "settings", target: "settings-tab", title: "Configurações", description: "Cadastre o Pix e o QR Code, escolha o que os membros veem, nomeie responsáveis e crie modelos de cobrança." },
        help,
      ]
    : [
        welcomeMember,
        { id: "collections", target: "collections", title: "Suas cobranças", description: "Abra uma cobrança para ver o valor, copiar o Pix da pelada e informar o pagamento com o comprovante." },
        { id: "scope", target: "scope", title: "Em aberto e encerradas", description: "O que ainda vale fica em aberto; o histórico fica nas encerradas." },
        ...(viewCash ? [{ id: "cash", target: "cash-tab", title: "Caixa da pelada", description: "Nesta pelada o financeiro é transparente: você vê o saldo e as movimentações." } satisfies StepText] : []),
        help,
      ]

  return texts.map((text, index): TourStepDetails => {
    const first = index === 0
    const last = index === texts.length - 1
    const { target } = text
    return {
      id: text.id,
      title: text.title,
      description: text.description,
      type: target ? "tooltip" : "dialog",
      placement: target === "help" ? "bottom-end" : "bottom",
      target: target ? () => document.querySelector<HTMLElement>(`[data-tour="${target}"]`) : undefined,
      actions: first
        ? [{ label: "Pular", action: "skip" }, { label: "Começar", action: "next" }]
        : [{ label: "Voltar", action: "prev" }, { label: last ? "Concluir" : "Próximo", action: "next" }],
    }
  })
}
