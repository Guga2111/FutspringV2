import { HugeiconsIcon } from "@hugeicons/react"
import { Alert02Icon, LockIcon } from "@hugeicons/core-free-icons"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { useNowUntil } from "@/hooks/useNowUntil"
import { cn } from "@/lib/utils"
import type { SubscriptionSummary } from "@/types/subscription"
import { subscriptionNotice, subscriptionNoticeFrom } from "@/utils/subscription"

// the buttons disabled by a read-only pelada point at this notice with aria-describedby
export const SUBSCRIPTION_NOTICE_ID = "subscription-notice"

interface SubscriptionNoticeProps {
  subscription: SubscriptionSummary | null
  isCreator: boolean
  timeZone: string
}

// The pelada's subscription state for the caller's role (pelada layout and session page); nothing when all is fine
export function SubscriptionNotice({ subscription, isCreator, timeZone }: SubscriptionNoticeProps) {
  // re-read when the trial notice is due, so an open page shows it on time
  const now = useNowUntil(subscriptionNoticeFrom(subscription))
  const notice = subscriptionNotice(subscription, isCreator, now, timeZone)
  if (!notice) return null
  return (
    <Alert
      id={SUBSCRIPTION_NOTICE_ID}
      className={cn("flex items-center gap-3 py-2.5 [&>svg]:static [&>svg~*]:pl-0 [&>svg+div]:translate-y-0", notice.readOnly ? "border-destructive-muted" : "border-warning")}
    >
      <HugeiconsIcon
        icon={notice.readOnly ? LockIcon : Alert02Icon}
        className={cn("size-4 shrink-0", notice.readOnly ? "text-destructive-soft" : "text-warning")}
      />
      <AlertDescription className="flex-1 text-body-sm font-medium">{notice.text}</AlertDescription>
      {/* the Assinatura area arrives with the Pix (PR 7) */}
      {notice.action && (
        <Button variant="outline" size="sm" disabled className="shrink-0">
          {notice.action} · Em breve
        </Button>
      )}
    </Alert>
  )
}
