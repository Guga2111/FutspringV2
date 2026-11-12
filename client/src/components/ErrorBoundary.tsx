import { Component } from "react"
import type { ErrorInfo, ReactNode } from "react"
import { Button } from "@/components/ui/button"
import { reportError } from "@/lib/errorReporting"

interface Props {
  children: ReactNode
}

interface State {
  hasError: boolean
}

class ErrorBoundary extends Component<Props, State> {
  state: State = { hasError: false }

  static getDerivedStateFromError(): State {
    return { hasError: true }
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error(error, info)
    reportError(error, "BOUNDARY")
  }

  render() {
    if (this.state.hasError) {
      return (
        <div className="flex flex-col items-center justify-center min-h-screen gap-4">
          <p className="text-lg font-semibold">Algo ocorreu errado</p>
          <Button onClick={() => window.location.reload()}>Recarregue</Button>
        </div>
      )
    }
    return this.props.children
  }
}

export default ErrorBoundary
