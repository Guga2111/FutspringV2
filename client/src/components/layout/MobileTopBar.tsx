import { Link } from "react-router-dom"
import { Menu } from "lucide-react"
import { Button } from "@/components/ui/button"
import { useSidebar } from "@/components/ui/sidebar-context"

// Below md the sidebar lives in a Sheet; this bar opens it
export function MobileTopBar() {
  const { setOpenMobile } = useSidebar()
  return (
    <header className="sticky top-0 z-30 flex h-14 shrink-0 items-center gap-2.5 border-b border-sidebar-border bg-sidebar pl-4 pr-3 md:hidden">
      <Link to="/home" className="flex flex-1 items-center gap-2.5">
        <img src="/logo-64.png" alt="" width={28} height={28} className="size-7 rounded-full object-cover" />
        <span className="text-gradient-primary text-[17px] font-bold">FutSpring</span>
      </Link>
      <Button
        variant="ghost"
        size="icon"
        onClick={() => setOpenMobile(true)}
        aria-label="Abrir menu"
        className="size-10 rounded-[10px] hover:bg-accent"
      >
        <Menu className="size-5" />
      </Button>
    </header>
  )
}
