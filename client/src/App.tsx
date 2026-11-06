import { lazy, Suspense } from "react"
import { Navigate, Routes, Route } from "react-router-dom"
import PrivateRoute from "@/components/PrivateRoute"
import ErrorBoundary from "@/components/ErrorBoundary"
import ScrollToTop from "@/components/ScrollToTop"
import { AppLayout } from "@/components/layout/AppLayout"

const LandingPage = lazy(() => import("@/pages/LandingPage"))
const AuthPage = lazy(() => import("@/pages/AuthPage"))
const ForgotPasswordPage = lazy(() => import("@/pages/ForgotPasswordPage"))
const ResetPasswordPage = lazy(() => import("@/pages/ResetPasswordPage"))
const HomePage = lazy(() => import("@/pages/HomePage"))
const PeladaDetailPage = lazy(() => import("@/pages/PeladaDetailPage"))
const PeladaOverviewPage = lazy(() => import("@/pages/PeladaOverviewPage"))
const PeladaMembersPage = lazy(() => import("@/pages/PeladaMembersPage"))
const PeladaSessionsPage = lazy(() => import("@/pages/PeladaSessionsPage"))
const PeladaRankingPage = lazy(() => import("@/pages/PeladaRankingPage"))
const PeladaAwardsPage = lazy(() => import("@/pages/PeladaAwardsPage"))
const PeladaFinancePage = lazy(() => import("@/pages/PeladaFinancePage"))
const PeladaInvitationsPage = lazy(() => import("@/pages/PeladaInvitationsPage"))
const PeladaSettingsPage = lazy(() => import("@/pages/PeladaSettingsPage"))
const DailyDetailPage = lazy(() => import("@/pages/DailyDetailPage"))
const ProfilePage = lazy(() => import("@/pages/ProfilePage"))
const AccountSettingsPage = lazy(() => import("@/pages/AccountSettingsPage"))
const NotificationUnsubscribePage = lazy(() => import("@/pages/NotificationUnsubscribePage"))
const InvitationPage = lazy(() => import("@/pages/InvitationPage"))
const JoinPeladaPage = lazy(() => import("@/pages/JoinPeladaPage"))
const TermsPage = lazy(() => import("@/pages/TermsPage"))
const PrivacyPage = lazy(() => import("@/pages/PrivacyPage"))
const NotFoundPage = lazy(() => import("@/pages/NotFoundPage"))

const fallback = <div className="flex items-center justify-center min-h-screen">Carregando…</div>

function App() {
  return (
    <>
      <ScrollToTop />
      <Suspense fallback={fallback}>
        <Routes>
          <Route path="/" element={<ErrorBoundary><LandingPage /></ErrorBoundary>} />
          <Route path="/auth" element={<ErrorBoundary><AuthPage /></ErrorBoundary>} />
          <Route path="/forgot-password" element={<ErrorBoundary><ForgotPasswordPage /></ErrorBoundary>} />
          <Route path="/reset-password" element={<ErrorBoundary><ResetPasswordPage /></ErrorBoundary>} />
          <Route path="/notifications/unsubscribe" element={<ErrorBoundary><NotificationUnsubscribePage /></ErrorBoundary>} />
          <Route path="/termos" element={<ErrorBoundary><TermsPage /></ErrorBoundary>} />
          <Route path="/privacidade" element={<ErrorBoundary><PrivacyPage /></ErrorBoundary>} />
          <Route path="/join/:token" element={<ErrorBoundary><JoinPeladaPage /></ErrorBoundary>} />
          {/* Private pages share the sidebar shell */}
          <Route element={<ErrorBoundary><PrivateRoute><AppLayout /></PrivateRoute></ErrorBoundary>}>
            <Route path="/invitations/:id" element={<ErrorBoundary><InvitationPage /></ErrorBoundary>} />
            <Route path="/home" element={<ErrorBoundary><HomePage /></ErrorBoundary>} />
            {/* Layout of a pelada: header, shared dialogs and chat; one child route per area */}
            <Route path="/pelada/:id" element={<ErrorBoundary><PeladaDetailPage /></ErrorBoundary>}>
              <Route index element={<ErrorBoundary><PeladaOverviewPage /></ErrorBoundary>} />
              <Route path="membros" element={<ErrorBoundary><PeladaMembersPage /></ErrorBoundary>} />
              <Route path="sessoes" element={<ErrorBoundary><PeladaSessionsPage /></ErrorBoundary>} />
              <Route path="ranking" element={<ErrorBoundary><PeladaRankingPage /></ErrorBoundary>} />
              <Route path="premios" element={<ErrorBoundary><PeladaAwardsPage /></ErrorBoundary>} />
              <Route path="financeiro" element={<ErrorBoundary><PeladaFinancePage /></ErrorBoundary>} />
              {/* deep link to one collection (the WhatsApp message links here) */}
              <Route path="financeiro/:listId" element={<ErrorBoundary><PeladaFinancePage /></ErrorBoundary>} />
              <Route path="convites" element={<ErrorBoundary><PeladaInvitationsPage /></ErrorBoundary>} />
            </Route>
            <Route path="/pelada/:id/settings" element={<ErrorBoundary><PeladaSettingsPage /></ErrorBoundary>} />
            <Route path="/daily/:id" element={<ErrorBoundary><DailyDetailPage /></ErrorBoundary>} />
            <Route path="/profile/:id" element={<ErrorBoundary><ProfilePage /></ErrorBoundary>} />
            <Route path="/settings" element={<ErrorBoundary><AccountSettingsPage /></ErrorBoundary>} />
            {/* old links and e-mails already sent */}
            <Route path="/settings/notifications" element={<Navigate to="/settings#notificacoes" replace />} />
          </Route>
          <Route path="*" element={<ErrorBoundary><NotFoundPage /></ErrorBoundary>} />
        </Routes>
      </Suspense>
    </>
  )
}

export default App
