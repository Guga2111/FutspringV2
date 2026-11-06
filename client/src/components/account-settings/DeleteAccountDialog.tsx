import { AuthTextField } from "@/components/auth/AuthTextField"
import { SettingsRootError } from "@/components/SettingsCard"
import {
  AlertDialog,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog"
import { Button } from "@/components/ui/button"
import { useDeleteAccount } from "@/components/account-settings/hooks/useDeleteAccount"

interface DeleteAccountDialogProps {
  onClose: () => void
  // a blocker appeared meanwhile (409): the card reloads its list
  onBlocked: () => void
}

export function DeleteAccountDialog({ onClose, onBlocked }: DeleteAccountDialogProps) {
  const { form, onSubmit } = useDeleteAccount(onBlocked)
  const submitting = form.formState.isSubmitting

  return (
    <AlertDialog open onOpenChange={(open) => !open && !submitting && onClose()}>
      <AlertDialogContent className="max-w-md">
        <form onSubmit={onSubmit} noValidate className="flex flex-col gap-5">
          <AlertDialogHeader>
            <AlertDialogTitle>Excluir sua conta?</AlertDialogTitle>
            <AlertDialogDescription>
              Você sai de todas as peladas e seus dados pessoais são apagados. Partidas, prêmios e mensagens antigas
              ficam com o nome Ex-jogador. Digite sua senha para confirmar.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AuthTextField
            control={form.control}
            name="password"
            id="delete-account-password"
            label="Senha"
            type="password"
            autoComplete="current-password"
            autoFocus
          />
          <SettingsRootError message={form.formState.errors.root?.message} />
          <AlertDialogFooter className="gap-2">
            <AlertDialogCancel type="button" disabled={submitting}>
              Cancelar
            </AlertDialogCancel>
            <Button type="submit" variant="destructive" disabled={submitting}>
              {submitting ? "Excluindo..." : "Excluir minha conta"}
            </Button>
          </AlertDialogFooter>
        </form>
      </AlertDialogContent>
    </AlertDialog>
  )
}
