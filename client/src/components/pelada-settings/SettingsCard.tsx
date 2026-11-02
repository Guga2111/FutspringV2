import type { FormEventHandler, ReactNode } from "react"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card"
import { cn } from "@/lib/utils"

interface SettingsCardProps {
  // anchor of the "Nesta página" index
  id: string
  title: string
  description?: ReactNode
  children?: ReactNode
  // a card with a form gets its own footer (SettingsCardFooter) inside the <form>
  onSubmit?: FormEventHandler<HTMLFormElement>
  footer?: ReactNode
  destructive?: boolean
}

export function SettingsCard({ id, title, description, children, onSubmit, footer, destructive }: SettingsCardProps) {
  const body = (
    <>
      {children && <CardContent className="flex flex-col gap-5 p-5 pt-0 md:p-6 md:pt-0">{children}</CardContent>}
      {footer}
    </>
  )
  return (
    <Card
      id={id}
      className={cn("scroll-mt-20 overflow-hidden rounded-tile shadow-none md:scroll-mt-6", destructive && "border-destructive/40")}
    >
      <CardHeader className="p-5 md:p-6">
        <CardTitle className={cn("text-base font-semibold", destructive && "text-destructive")}>{title}</CardTitle>
        {description && <CardDescription>{description}</CardDescription>}
      </CardHeader>
      {onSubmit ? (
        <form onSubmit={onSubmit} noValidate>
          {body}
        </form>
      ) : (
        body
      )}
    </Card>
  )
}

interface SettingsCardFooterProps {
  dirty: boolean
  submitting: boolean
  onCancel: () => void
}

// "Tudo salvo." until a field changes; Cancelar puts the saved values back
export function SettingsCardFooter({ dirty, submitting, onCancel }: SettingsCardFooterProps) {
  return (
    <CardFooter className="flex flex-col items-stretch gap-3 border-t bg-card-elevated px-5 py-4 md:flex-row md:items-center md:justify-between md:px-6">
      <p className="text-sm text-muted-foreground" aria-live="polite">
        {submitting ? "Salvando..." : dirty ? "Alterações não salvas" : "Tudo salvo."}
      </p>
      <div className="flex gap-2">
        <Button type="button" variant="outline" className="flex-1 md:flex-none" disabled={!dirty || submitting} onClick={onCancel}>
          Cancelar
        </Button>
        <Button type="submit" variant="brand" className="flex-1 md:flex-none" disabled={!dirty || submitting}>
          Salvar alterações
        </Button>
      </div>
    </CardFooter>
  )
}

export function SettingsRootError({ message }: { message: string | undefined }) {
  if (!message) return null
  return (
    <Alert variant="destructive">
      <AlertDescription>{message}</AlertDescription>
    </Alert>
  )
}
