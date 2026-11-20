import { Link, useNavigate } from "react-router-dom"
import { HugeiconsIcon } from "@hugeicons/react"
import { DashboardSquare01Icon, Logout01Icon } from "@hugeicons/core-free-icons"
import { Button } from "@/components/ui/button"
import { AppearanceCard } from "@/components/account-settings/AppearanceCard"
import { DeleteAccountCard } from "@/components/account-settings/DeleteAccountCard"
import { ProfileCard } from "@/components/account-settings/ProfileCard"
import { SecurityCard } from "@/components/account-settings/SecurityCard"
import { PageContainer } from "@/components/layout/PageContainer"
import { PageHeader } from "@/components/layout/PageHeader"
import { NotificationPreferencesCard } from "@/components/notification-settings/NotificationPreferencesCard"
import { SettingsNav, type SettingsSection } from "@/components/pelada-settings/SettingsNav"
import { SettingsGroup } from "@/components/SettingsCard"
import { useAuth } from "@/hooks/useAuth"
import { useScrollToHash } from "@/hooks/useScrollToHash"

const SECTIONS: SettingsSection[] = [
  { id: "perfil", label: "Perfil" },
  { id: "seguranca", label: "Senha" },
  { id: "aparencia", label: "Aparência" },
  { id: "notificacoes", label: "Notificações" },
  { id: "excluir-conta", label: "Excluir conta" },
]

// /settings: everything about the account, one card per section, each saved on its own.
// Opened from the UserMenu, the gear in the bell's panel (#notificacoes), "Editar perfil" (#perfil) and, below md, the
// gear of the own profile. Ends with "Painel admin" (platform admins) and "Sair": the UserMenu's items, which the
// mobile shell has no sidebar for.
export default function AccountSettingsPage() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  useScrollToHash(user !== null)
  if (!user) return null

  function handleLogout() {
    logout()
    navigate("/auth", { replace: true })
  }

  return (
    <div className="page-enter flex flex-1 flex-col">
      <PageContainer>
        <PageHeader
          breadcrumb={[{ label: "Início", to: "/home" }, { label: "Configurações" }]}
          title="Configurações"
          description="Seu perfil, sua senha, o tema e o que chega no seu e-mail."
        />
        <div className="grid grid-cols-1 gap-8 lg:grid-cols-[minmax(0,1fr)_200px]">
          <div className="flex min-w-0 flex-col gap-8">
            <SettingsGroup label="Conta">
              {/* key: a session for another account starts from its own values */}
              <ProfileCard key={user.id} user={user} />
              <SecurityCard />
            </SettingsGroup>
            <SettingsGroup label="Preferências">
              <AppearanceCard />
              <NotificationPreferencesCard id="notificacoes" />
            </SettingsGroup>
            <SettingsGroup label="Zona de perigo">
              <DeleteAccountCard />
            </SettingsGroup>
            <div className="flex flex-wrap items-center gap-3">
              {user.isPlatformAdmin && (
                <Button asChild variant="outline">
                  <Link to="/admin">
                    <HugeiconsIcon icon={DashboardSquare01Icon} className="size-4" />
                    Painel admin
                  </Link>
                </Button>
              )}
              <Button variant="destructive-ghost" onClick={handleLogout} className="ml-auto">
                <HugeiconsIcon icon={Logout01Icon} className="size-4" />
                Sair
              </Button>
            </div>
          </div>
          <aside className="hidden lg:block">
            <div className="sticky top-6">
              <SettingsNav sections={SECTIONS} />
            </div>
          </aside>
        </div>
      </PageContainer>
    </div>
  )
}
