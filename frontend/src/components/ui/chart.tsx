import * as React from "react"
import * as RechartsPrimitive from "recharts"
import type { TooltipContentProps } from "recharts"

import { cn } from "@/lib/utils"

// Chart configuration type
export type ChartConfig = {
  [k: string]: {
    label?: React.ReactNode
    icon?: React.ComponentType
    color?: string
  }
}

type ChartContextProps = {
  config: ChartConfig
}

const ChartContext = React.createContext<ChartContextProps | null>(null)

function useChart() {
  const context = React.useContext(ChartContext)
  if (!context) {
    throw new Error("useChart must be used within a <ChartContainer />")
  }
  return context
}

type ChartContainerProps = React.ComponentProps<"div"> & {
  config: ChartConfig
  children: React.ComponentProps<
    typeof RechartsPrimitive.ResponsiveContainer
  >["children"]
}

const ChartContainer = React.forwardRef<HTMLDivElement, ChartContainerProps>(
  ({ id, className, children, config, ...props }, ref) => {
    const uniqueId = React.useId()
    const chartId = `chart-${id || uniqueId.replace(/:/g, "")}`

    // Build CSS vars for colors
    const colorStyles = Object.entries(config)
      .filter(([, cfg]) => cfg.color)
      .map(([key, cfg]) => `[data-chart="${chartId}"] { --color-${key}: ${cfg.color}; }`)
      .join("\n")

    return (
      <ChartContext.Provider value={{ config }}>
        <div
          data-chart={chartId}
          ref={ref}
          className={cn("flex aspect-video justify-center text-xs", className)}
          {...props}
        >
          {colorStyles && (
            <style dangerouslySetInnerHTML={{ __html: colorStyles }} />
          )}
          <RechartsPrimitive.ResponsiveContainer>
            {children}
          </RechartsPrimitive.ResponsiveContainer>
        </div>
      </ChartContext.Provider>
    )
  }
)
ChartContainer.displayName = "ChartContainer"

// Re-export recharts Tooltip as ChartTooltip
const ChartTooltip = RechartsPrimitive.Tooltip

// Styled tooltip content component
const ChartTooltipContent = React.forwardRef<
  HTMLDivElement,
  React.ComponentProps<"div"> &
    Partial<TooltipContentProps> & {
      hideLabel?: boolean
      nameKey?: string
    }
>(({ active, payload, label, className, hideLabel = false, nameKey }, ref) => {
  const { config } = useChart()

  if (!active || !payload?.length) {
    return null
  }

  return (
    <div
      ref={ref}
      className={cn(
        "grid min-w-[8rem] items-start gap-1.5 rounded-lg border border-border/50 bg-background px-2.5 py-1.5 text-xs shadow-xl",
        className
      )}
    >
      {!hideLabel && label && (
        <div className="font-medium">{String(label)}</div>
      )}
      <div className="grid gap-1.5">
        {payload.map((item, index) => {
          const key = nameKey || String(item.dataKey || item.name || "value")
          const itemConfig = config[key]
          const indicatorColor = item.color ?? item.fill ?? "currentColor"

          return (
            <div
              key={`${String(item.dataKey)}-${index}`}
              className="flex items-center gap-2"
            >
              <div
                className="h-2 w-2 shrink-0 rounded-[2px]"
                style={{ backgroundColor: indicatorColor }}
              />
              <div className="flex flex-1 items-center justify-between leading-none">
                <span className="text-muted-foreground">
                  {itemConfig?.label ?? item.name ?? key}
                </span>
                {item.value !== undefined && (
                  <span className="font-mono font-medium tabular-nums text-foreground ml-2">
                    {typeof item.value === "number"
                      ? item.value.toLocaleString()
                      : String(item.value)}
                  </span>
                )}
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
})
ChartTooltipContent.displayName = "ChartTooltipContent"

const ChartLegend = RechartsPrimitive.Legend

type LegendPayloadItem = {
  value?: string | number
  type?: string
  id?: string
  color?: string
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  dataKey?: string | number | ((obj: any) => any)
}

const ChartLegendContent = React.forwardRef<
  HTMLDivElement,
  React.ComponentProps<"div"> & {
    payload?: LegendPayloadItem[]
    verticalAlign?: "top" | "bottom" | "middle"
    hideIcon?: boolean
    nameKey?: string
  }
>(
  (
    { className, hideIcon = false, payload, verticalAlign = "bottom", nameKey },
    ref
  ) => {
    const { config } = useChart()

    if (!payload?.length) {
      return null
    }

    return (
      <div
        ref={ref}
        className={cn(
          "flex items-center justify-center gap-4",
          verticalAlign === "top" ? "pb-3" : "pt-3",
          className
        )}
      >
        {payload.map((item) => {
          const key = nameKey || String(item.dataKey || "value")
          const itemConfig = config[key]

          return (
            <div
              key={String(item.value)}
              className="flex items-center gap-1.5"
            >
              {itemConfig?.icon && !hideIcon ? (
                <itemConfig.icon />
              ) : (
                <div
                  className="h-2 w-2 shrink-0 rounded-full"
                  style={{ backgroundColor: item.color }}
                />
              )}
              <span>{itemConfig?.label ?? String(item.value)}</span>
            </div>
          )
        })}
      </div>
    )
  }
)
ChartLegendContent.displayName = "ChartLegendContent"

export {
  ChartContainer,
  ChartTooltip,
  ChartTooltipContent,
  ChartLegend,
  ChartLegendContent,
}
