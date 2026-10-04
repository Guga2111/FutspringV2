import { Suspense, useMemo, useState } from "react"
import { Outlet } from "react-router-dom"
import { SidebarInset, SidebarProvider } from "@/components/ui/sidebar"
import { MyPeladasContext, type MyPeladasContextValue } from "@/context/my-peladas-context-value"
import { useMyPeladas } from "@/hooks/useMyPeladas"
import CreatePeladaModal from "@/components/CreatePeladaModal"
import { AppSidebar } from "@/components/layout/AppSidebar"
import { MobileTopBar } from "@/components/layout/MobileTopBar"

const SIDEBAR_OPEN_KEY = "sidebar_open"

function readSidebarOpen(): boolean {
  try {
    return localStorage.getItem(SIDEBAR_OPEN_KEY) !== "false"
  } catch {
    return true
  }
}

function saveSidebarOpen(open: boolean) {
  try {
    localStorage.setItem(SIDEBAR_OPEN_KEY, String(open))
  } catch {
    // storage unavailable: the sidebar just opens expanded next time
  }
}

const pageFallback = <div className="flex flex-1 items-center justify-center p-8 text-sm text-muted-foreground">Carregando…</div>

// Shell of every private page: sidebar with the user's peladas (Sheet on mobile) and the page in <Outlet/>
export function AppLayout() {
  const [sidebarOpen, setSidebarOpen] = useState(readSidebarOpen)
  const [createOpen, setCreateOpen] = useState(false)
  const { peladas, loading, error, reload, updateNextDaily } = useMyPeladas()

  const contextValue = useMemo<MyPeladasContextValue>(
    () => ({ peladas, loading, error, reload, updateNextDaily, openCreatePelada: () => setCreateOpen(true) }),
    [peladas, loading, error, reload, updateNextDaily],
  )

  function handleSidebarOpenChange(open: boolean) {
    setSidebarOpen(open)
    saveSidebarOpen(open)
  }

  return (
    <MyPeladasContext.Provider value={contextValue}>
      <SidebarProvider open={sidebarOpen} onOpenChange={handleSidebarOpenChange}>
        <AppSidebar />
        <SidebarInset className="min-w-0">
          <MobileTopBar />
          <Suspense fallback={pageFallback}>
            <Outlet />
          </Suspense>
        </SidebarInset>
      </SidebarProvider>
      {createOpen && (
        <CreatePeladaModal
          onClose={() => setCreateOpen(false)}
          onCreated={() => {
            setCreateOpen(false)
            reload()
          }}
        />
      )}
    </MyPeladasContext.Provider>
  )
}
