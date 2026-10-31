import { lazy, Suspense } from "react"
import { Routes, Route } from "react-router-dom"
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
const PeladaSettingsPage = lazy(() => import("@/pages/PeladaSettingsPage"))
const DailyDetailPage = lazy(() => import("@/pages/DailyDetailPage"))
const ProfilePage = lazy(() => import("@/pages/ProfilePage"))
const NotificationSettingsPage = lazy(() => import("@/pages/NotificationSettingsPage"))
const NotificationUnsubscribePage = lazy(() => import("@/pages/NotificationUnsubscribePage"))
const InvitationPage = lazy(() => import("@/pages/InvitationPage"))
const JoinPeladaPage = lazy(() => import("@/pages/JoinPeladaPage"))
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
          <Route path="/join/:token" element={<ErrorBoundary><JoinPeladaPage /></ErrorBoundary>} />
          {/* Private pages share the sidebar shell */}
          <Route element={<ErrorBoundary><PrivateRoute><AppLayout /></PrivateRoute></ErrorBoundary>}>
            <Route path="/invitations/:id" element={<ErrorBoundary><InvitationPage /></ErrorBoundary>} />
            <Route path="/home" element={<ErrorBoundary><HomePage /></ErrorBoundary>} />
            <Route path="/pelada/:id" element={<ErrorBoundary><PeladaDetailPage /></ErrorBoundary>} />
            <Route path="/pelada/:id/settings" element={<ErrorBoundary><PeladaSettingsPage /></ErrorBoundary>} />
            <Route path="/daily/:id" element={<ErrorBoundary><DailyDetailPage /></ErrorBoundary>} />
            <Route path="/profile/:id" element={<ErrorBoundary><ProfilePage /></ErrorBoundary>} />
            <Route path="/settings/notifications" element={<ErrorBoundary><NotificationSettingsPage /></ErrorBoundary>} />
          </Route>
          <Route path="*" element={<ErrorBoundary><NotFoundPage /></ErrorBoundary>} />
        </Routes>
      </Suspense>
    </>
  )
}

export default App
