import { StrictMode } from "react"
import { createRoot } from "react-dom/client"
import { BrowserRouter } from "react-router-dom"
// Self-hosted fonts (bundled by Vite); unicode-range makes the browser fetch only the Latin files
import "@fontsource-variable/inter/wght.css"
import "@fontsource/barlow-condensed/latin-700.css"
import "@fontsource/barlow-condensed/latin-800.css"
import "./index.css"
import App from "./App.tsx"
import { AuthProvider } from "./context/AuthContext.tsx"
import { ThemedToaster } from "./components/ThemedToaster.tsx"
import { PwaUpdater } from "./components/layout/PwaUpdater.tsx"
import { captureInstallPrompt } from "./lib/installPrompt.ts"

// The theme class is applied by the inline script in index.html, before the first paint

// Chrome can offer the install prompt before any page mounts
captureInstallPrompt()

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <BrowserRouter>
      <AuthProvider>
        <App />
        <ThemedToaster />
        <PwaUpdater />
      </AuthProvider>
    </BrowserRouter>
  </StrictMode>,
)
