import { useState } from "react"
import { useNavigate, useSearchParams, useLocation } from "react-router-dom"
import { toast } from "sonner"
import { registerUser, loginUser } from "@/api/auth"
import { useAuth } from "@/hooks/useAuth"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Tabs, TabsList, TabsTrigger, TabsContent } from "@/components/ui/tabs"
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card"
import { Checkbox } from "@/components/ui/checkbox"
import { Separator } from "@/components/ui/separator"

type Tab = "login" | "register"

interface RegisterFormData {
  username: string
  email: string
  password: string
  confirmPassword: string
}

interface LoginFormData {
  email: string
  password: string
}

interface FieldErrors {
  username?: string
  email?: string
  password?: string
  confirmPassword?: string
}

export default function AuthPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const navigate = useNavigate()
  const location = useLocation()
  const { login } = useAuth()

  const from = (location.state as { from?: { pathname?: string } } | null)?.from?.pathname ?? "/home"

  const activeTab: Tab = searchParams.get("tab") === "register" ? "register" : "login"

  const [registerData, setRegisterData] = useState<RegisterFormData>({
    username: "",
    email: "",
    password: "",
    confirmPassword: "",
  })
  const [loginData, setLoginData] = useState<LoginFormData>({ email: "", password: "" })
  const [registerErrors, setRegisterErrors] = useState<FieldErrors>({})
  const [registerLoading, setRegisterLoading] = useState(false)
  const [loginLoading, setLoginLoading] = useState(false)
  const [termsAccepted, setTermsAccepted] = useState(false)

  function handleTabChange(value: string) {
    setSearchParams({ tab: value })
  }

  function validateRegister(): boolean {
    const errors: FieldErrors = {}
    if (!registerData.username) errors.username = "Nome de usuário é obrigatório"
    if (!registerData.email) errors.email = "E-mail é obrigatório"
    if (!registerData.password) {
      errors.password = "Senha é obrigatória"
    } else if (registerData.password.length < 8) {
      errors.password = "A senha deve ter pelo menos 8 caracteres"
    }
    if (!registerData.confirmPassword) {
      errors.confirmPassword = "Por favor, confirme sua senha"
    } else if (registerData.password !== registerData.confirmPassword) {
      errors.confirmPassword = "As senhas não coincidem"
    }
    setRegisterErrors(errors)
    return Object.keys(errors).length === 0
  }

  async function handleRegister(e: React.FormEvent) {
    e.preventDefault()
    if (!validateRegister()) return
    setRegisterLoading(true)
    try {
      const response = await registerUser({
        username: registerData.username,
        email: registerData.email,
        password: registerData.password,
      })
      login(response.token, response.user)
      navigate(from, { replace: true })
    } catch (err: unknown) {
      const message = extractErrorMessage(err) ?? "Falha no cadastro"
      toast.error(message)
    } finally {
      setRegisterLoading(false)
    }
  }

  async function handleLogin(e: React.FormEvent) {
    e.preventDefault()
    setLoginLoading(true)
    try {
      const response = await loginUser(loginData)
      login(response.token, response.user)
      navigate(from, { replace: true })
    } catch (err: unknown) {
      const message = extractErrorMessage(err) ?? "Falha no login"
      toast.error(message)
    } finally {
      setLoginLoading(false)
    }
  }

  return (
    <div className="page-enter flex min-h-screen bg-background">
      {/* Left — form */}
      <div className="flex flex-1 flex-col px-8 py-8">
        <div
          className="flex items-center gap-2 cursor-pointer"
          onClick={() => navigate("/")}
        >
          <img
            src="/gerrard.png"
            alt="Gerrard"
            className="w-8 h-8 rounded-full object-cover shadow"
          />
          <span className="text-xl font-bold text-gradient-primary">Futspring</span>
        </div>
        <div className="flex flex-1 flex-col items-center justify-center gap-6">
        <Card className="w-full max-w-md border-0 shadow-none">
          <CardHeader className="text-center">
            <CardTitle>
              {activeTab === "login" ? "Bem-vindo novamente" : "Crie sua conta"}
            </CardTitle>
            <CardDescription>
              {activeTab === "login"
                ? "Entre em sua conta"
                : "Preencha o formulário abaixo para criar sua conta"}
            </CardDescription>
          </CardHeader>
          <CardContent>
            <Tabs value={activeTab} onValueChange={handleTabChange}>
              <TabsList className="w-full mb-6">
                <TabsTrigger value="login" className="flex-1">Login</TabsTrigger>
                <TabsTrigger value="register" className="flex-1">Registro</TabsTrigger>
              </TabsList>

              <TabsContent value="login">
                <form onSubmit={handleLogin} className="space-y-4">
                  <div className="space-y-2">
                    <Label htmlFor="login-email">Email</Label>
                    <Input
                      id="login-email"
                      type="email"
                      placeholder="salah@futspring.com"
                      required
                      value={loginData.email}
                      onChange={(e) => setLoginData((prev) => ({ ...prev, email: e.target.value }))}
                    />
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="login-password">Senha</Label>
                    <Input
                      id="login-password"
                      type="password"
                      placeholder="••••••••"
                      required
                      value={loginData.password}
                      onChange={(e) => setLoginData((prev) => ({ ...prev, password: e.target.value }))}
                    />
                  </div>
                  <Button type="submit" className="w-full bg-gradient-primary text-white border-0" disabled={loginLoading}>
                    {loginLoading ? "Entrando..." : "Entrar"}
                  </Button>
                  <div className="relative my-4">
                    <div className="absolute inset-0 flex items-center">
                      <Separator />
                    </div>
                    <div className="relative flex justify-center text-xs uppercase">
                      <span className="bg-card px-2 text-muted-foreground">Ou continue com</span>
                    </div>
                  </div>
                  <Button
                    variant="outline"
                    className="w-full"
                    type="button"
                    onClick={() => toast.info("Sign-in com Google em breve")}
                  >
                    <GoogleIcon /> Continue com Google
                  </Button>
                  <p className="text-sm text-muted-foreground text-center mt-4">
                    Nao tem uma conta?{" "}
                    <button
                      type="button"
                      onClick={() => handleTabChange("register")}
                      className="underline underline-offset-4 hover:text-primary"
                    >
                      Cadastre-se
                    </button>
                  </p>
                </form>
              </TabsContent>

              <TabsContent value="register">
                <form onSubmit={handleRegister} className="space-y-4">
                  <div className="space-y-2">
                    <Label htmlFor="register-username">Nome de Usuário</Label>
                    <Input
                      id="register-username"
                      type="text"
                      placeholder="salah"
                      required
                      value={registerData.username}
                      onChange={(e) => setRegisterData((prev) => ({ ...prev, username: e.target.value }))}
                    />
                    {registerErrors.username && (
                      <p className="text-sm text-destructive">{registerErrors.username}</p>
                    )}
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="register-email">Email</Label>
                    <Input
                      id="register-email"
                      type="email"
                      placeholder="salah@futspring.com"
                      required
                      value={registerData.email}
                      onChange={(e) => setRegisterData((prev) => ({ ...prev, email: e.target.value }))}
                    />
                    {registerErrors.email ? (
                      <p className="text-sm text-destructive">{registerErrors.email}</p>
                    ) : (
                      <p className="text-[0.8rem] text-muted-foreground">Nos vamos usar para contactar voce. Nao vamos compartilhar seu email com ninguem.</p>
                    )}
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="register-password">Senha</Label>
                    <Input
                      id="register-password"
                      type="password"
                      placeholder="••••••••"
                      required
                      value={registerData.password}
                      onChange={(e) => setRegisterData((prev) => ({ ...prev, password: e.target.value }))}
                    />
                    {registerErrors.password ? (
                      <p className="text-sm text-destructive">{registerErrors.password}</p>
                    ) : (
                      <p className="text-[0.8rem] text-muted-foreground">Devem ter 8 caracteres.</p>
                    )}
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="register-confirm">Confirme a senha</Label>
                    <Input
                      id="register-confirm"
                      type="password"
                      placeholder="••••••••"
                      required
                      value={registerData.confirmPassword}
                      onChange={(e) => setRegisterData((prev) => ({ ...prev, confirmPassword: e.target.value }))}
                    />
                    {registerErrors.confirmPassword ? (
                      <p className="text-sm text-destructive">{registerErrors.confirmPassword}</p>
                    ) : (
                      <p className="text-[0.8rem] text-muted-foreground">Por favor confirme sua senha.</p>
                    )}
                  </div>
                  <div className="flex items-center gap-2">
                    <Checkbox
                      id="terms"
                      checked={termsAccepted}
                      onCheckedChange={(v) => setTermsAccepted(v === true)}
                    />
                    <label htmlFor="terms" className="text-sm text-muted-foreground cursor-pointer">
                      Eu li e concordo com os{" "}
                      <a
                        href="/terms"
                        className="text-gradient-primary underline"
                        onClick={(e) => e.stopPropagation()}
                      >
                        Termos de uso
                      </a>
                    </label>
                  </div>
                  <Button type="submit" className="w-full bg-gradient-primary text-white border-0" disabled={registerLoading || !termsAccepted}>
                    {registerLoading ? "Criando conta..." : "Criar conta"}
                  </Button>
                  <div className="relative my-4">
                    <div className="absolute inset-0 flex items-center">
                      <Separator />
                    </div>
                    <div className="relative flex justify-center text-xs uppercase">
                      <span className="bg-card px-2 text-muted-foreground">Ou continue com</span>
                    </div>
                  </div>
                  <Button
                    variant="outline"
                    className="w-full"
                    type="button"
                    onClick={() => toast.info("Sign-in Google em breve")}
                  >
                    <GoogleIcon /> Continue com Google
                  </Button>
                  <p className="text-sm text-muted-foreground text-center mt-4">
                    Ja tem uma conta?{" "}
                    <button
                      type="button"
                      onClick={() => handleTabChange("login")}
                      className="underline underline-offset-4 hover:text-primary"
                    >
                      Entre
                    </button>
                  </p>
                </form>
              </TabsContent>
            </Tabs>
          </CardContent>
        </Card>

        </div>
      </div>

      {/* Right — image */}
      <div className="hidden lg:block flex-1 relative overflow-hidden">
        <img
          src="/pele.jpg"
          alt="FutSpring"
          className="absolute inset-0 w-full h-full object-cover"
        />
      </div>
    </div>
  )
}

function GoogleIcon() {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      viewBox="0 0 24 24"
      width="18"
      height="18"
      className="mr-2"
    >
      <path
        d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"
        fill="#4285F4"
      />
      <path
        d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"
        fill="#34A853"
      />
      <path
        d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z"
        fill="#FBBC05"
      />
      <path
        d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"
        fill="#EA4335"
      />
    </svg>
  )
}

function extractErrorMessage(err: unknown): string | null {
  if (
    err &&
    typeof err === "object" &&
    "response" in err &&
    err.response &&
    typeof err.response === "object" &&
    "data" in err.response &&
    err.response.data &&
    typeof err.response.data === "object" &&
    "message" in err.response.data &&
    typeof (err.response.data as { message: unknown }).message === "string"
  ) {
    return (err.response.data as { message: string }).message
  }
  return null
}
