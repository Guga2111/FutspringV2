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
// Opened from the UserMenu, the gear in the bell's panel (#notificacoes) and "Editar perfil" (#perfil).
export default function AccountSettingsPage() {
  const { user } = useAuth()
  useScrollToHash(user !== null)
  if (!user) return null

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
