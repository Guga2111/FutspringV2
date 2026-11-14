import { z } from "@/lib/zod"

// mirrors PushSubscriptionRequestDTO; parses the browser's PushSubscription.toJSON() (keys are optional there)
export const pushSubscriptionSchema = z.object({
  endpoint: z.string().min(1, "Informe o endpoint").max(1024, "Endpoint longo demais"),
  keys: z.object({
    p256dh: z.string().min(1, "Informe a chave p256dh").max(200, "Chave p256dh longa demais"),
    auth: z.string().min(1, "Informe a chave auth").max(50, "Chave auth longa demais"),
  }),
})
export type PushSubscriptionRequest = z.infer<typeof pushSubscriptionSchema>
