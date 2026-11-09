import { Alert, AlertDescription } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"

export function PlatformLoadError({ message, onRetry }: { message: string; onRetry: () => void }) {
  return (
    <Alert variant="destructive">
      <AlertDescription className="flex flex-wrap items-center justify-between gap-3">
        {message}
        <Button variant="outline" size="sm" onClick={onRetry}>
          Tentar de novo
        </Button>
      </AlertDescription>
    </Alert>
  )
}
