import { Link, useLocation, useSearchParams } from "react-router-dom"
import { AppDownloadLinks, AppDownloadPanel } from "@/components/auth/AppDownload"
import { AuthLayout } from "@/components/auth/AuthLayout"
import { LoginForm } from "@/components/auth/LoginForm"
import { RegisterForm } from "@/components/auth/RegisterForm"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { authReturnTo } from "@/utils/authReturn"

type Tab = "login" | "register"

const copy: Record<Tab, { title: string; description: string; switchPrompt: string; switchLabel: string; other: Tab }> = {
  login: {
    title: "Entrar no Futspring",
    description: "Suas peladas, jogos e números num só lugar.",
    switchPrompt: "Novo por aqui?",
    switchLabel: "Criar conta",
    other: "register",
  },
  register: {
    title: "Crie sua conta",
    description: "Presença, times, resultados e ranking da pelada.",
    switchPrompt: "Já tem conta?",
    switchLabel: "Entrar",
    other: "login",
  },
}

// The tab lives in ?tab= and the destination in ?returnTo= (kept when switching)
export default function AuthPage() {
  const [searchParams] = useSearchParams()
  const location = useLocation()
  const returnTo = authReturnTo(searchParams.get("returnTo"), location.state)
  const tab: Tab = searchParams.get("tab") === "register" ? "register" : "login"
  const text = copy[tab]

  const switchParams = new URLSearchParams(searchParams)
  switchParams.set("tab", text.other)
  switchParams.set("returnTo", returnTo)

  return (
    <AuthLayout aside={<AppDownloadPanel />}>
      <div className="flex w-full max-w-sm flex-col gap-8">
        <header className="flex flex-col gap-2 text-center">
          <h1 className="font-display text-display font-bold leading-none md:text-display-lg">{text.title}</h1>
          <p className="text-body-lg text-muted-foreground">{text.description}</p>
        </header>
        {searchParams.get("expired") === "1" && (
          <Alert role="status">
            <AlertDescription>Sua sessão expirou. Entre novamente para continuar.</AlertDescription>
          </Alert>
        )}
        {tab === "login" ? <LoginForm returnTo={returnTo} /> : <RegisterForm returnTo={returnTo} />}
        <p className="text-center text-body text-muted-foreground">
          {text.switchPrompt}{" "}
          <Link
            to={{ search: switchParams.toString() }}
            replace
            state={location.state}
            className="font-medium text-foreground underline-offset-4 hover:underline"
          >
            {text.switchLabel}
          </Link>
        </p>
        <AppDownloadLinks />
      </div>
    </AuthLayout>
  )
}
