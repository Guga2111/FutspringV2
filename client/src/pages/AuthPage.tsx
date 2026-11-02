import { useLocation, useSearchParams } from "react-router-dom"
import { AuthLayout } from "@/components/auth/AuthLayout"
import { LoginForm } from "@/components/auth/LoginForm"
import { RegisterForm } from "@/components/auth/RegisterForm"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { authReturnTo } from "@/utils/authReturn"

type Tab = "login" | "register"

const heading: Record<Tab, { title: string; description: string }> = {
  login: { title: "Bem-vindo de volta", description: "Entre para confirmar presença e ver suas peladas." },
  register: { title: "Crie sua conta", description: "Organize sua pelada: presença, times, resultados e ranking." },
}

// The tab lives in ?tab= and the destination in ?returnTo= (kept when switching tabs)
export default function AuthPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const location = useLocation()
  const returnTo = authReturnTo(searchParams.get("returnTo"), location.state)
  const tab: Tab = searchParams.get("tab") === "register" ? "register" : "login"

  function changeTab(value: string) {
    const next = new URLSearchParams(searchParams)
    next.set("tab", value)
    next.set("returnTo", returnTo)
    setSearchParams(next, { state: location.state, replace: true })
  }

  return (
    <AuthLayout>
      <div className="flex w-full max-w-sm flex-col gap-6">
        <header className="flex flex-col gap-2">
          <h1 className="font-display text-display font-bold leading-none md:text-display-lg">{heading[tab].title}</h1>
          <p className="text-body-lg text-muted-foreground">{heading[tab].description}</p>
        </header>
        {searchParams.get("expired") === "1" && (
          <Alert role="status">
            <AlertDescription>Sua sessão expirou. Entre novamente para continuar.</AlertDescription>
          </Alert>
        )}
        <Tabs value={tab} onValueChange={changeTab} className="flex flex-col gap-6">
          <TabsList variant="pill" className="md:flex md:w-full" aria-label="Entrar ou criar conta">
            <TabsTrigger variant="pill" value="login" className="md:flex-1">Entrar</TabsTrigger>
            <TabsTrigger variant="pill" value="register" className="md:flex-1">Criar conta</TabsTrigger>
          </TabsList>
          <TabsContent value="login" className="mt-0">
            <LoginForm returnTo={returnTo} />
          </TabsContent>
          <TabsContent value="register" className="mt-0">
            <RegisterForm returnTo={returnTo} />
          </TabsContent>
        </Tabs>
      </div>
    </AuthLayout>
  )
}
