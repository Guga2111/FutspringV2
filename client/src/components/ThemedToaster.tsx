import { Toaster } from "sonner"
import { useTheme } from "@/hooks/useTheme"

// Sonner renders light toasts unless told otherwise, so it follows the app theme here
export function ThemedToaster() {
  const { isDark } = useTheme()
  // top-center: bottom-right would cover the chat button
  return <Toaster position="top-center" theme={isDark ? "dark" : "light"} />
}
