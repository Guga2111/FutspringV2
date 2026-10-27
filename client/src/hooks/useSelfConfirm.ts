import { useNowUntil } from "@/hooks/useNowUntil"
import { getSelfConfirmState, type SelfConfirmInput, type SelfConfirmState } from "@/utils/attendance"
import { formatInZone } from "@/utils/dates"

export interface SelfConfirm {
  state: SelfConfirmState
  // text of the disabled button when the caller can't confirm (window closed or session full)
  closedLabel: string | null
  // line under an enabled button (a mensalista into a full session)
  hint: string | null
}

// The caller's confirm button on the session cards (pelada page, home, session page)
export function useSelfConfirm(input: SelfConfirmInput, timeZone: string): SelfConfirm {
  const now = useNowUntil(input.confirmOpensAt)
  const state = getSelfConfirmState(input, now)
  return {
    state,
    closedLabel:
      state === "notYet" && input.confirmOpensAt
        ? `Abre ${formatInZone(input.confirmOpensAt, timeZone)}`
        : state === "full"
          ? "Sessão lotada"
          : null,
    hint: state === "priority" ? "Você entra no lugar do último diarista confirmado" : null,
  }
}
