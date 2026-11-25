import * as React from "react"
import * as SliderPrimitive from "@radix-ui/react-slider"

import { cn } from "@/lib/utils"

const Slider = React.forwardRef<
  React.ElementRef<typeof SliderPrimitive.Root>,
  React.ComponentPropsWithoutRef<typeof SliderPrimitive.Root> & {
    // e.g. bg-night-muted/40 on a dark field
    trackClassName?: string
    // e.g. bg-brand
    rangeClassName?: string
    thumbClassName?: string
    // the thumb carries role="slider": its accessible name and value text go here
    thumbProps?: React.ComponentPropsWithoutRef<typeof SliderPrimitive.Thumb>
  }
>(({ className, trackClassName, rangeClassName, thumbClassName, thumbProps, ...props }, ref) => (
  <SliderPrimitive.Root
    ref={ref}
    className={cn("relative flex w-full touch-none select-none items-center", className)}
    {...props}
  >
    <SliderPrimitive.Track className={cn("relative h-2 w-full grow overflow-hidden rounded-full bg-secondary", trackClassName)}>
      <SliderPrimitive.Range className={cn("absolute h-full bg-primary", rangeClassName)} />
    </SliderPrimitive.Track>
    <SliderPrimitive.Thumb
      {...thumbProps}
      className={cn(
        "block size-5 rounded-full border-2 border-primary bg-background ring-offset-background transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50",
        thumbClassName,
      )}
    />
  </SliderPrimitive.Root>
))
Slider.displayName = SliderPrimitive.Root.displayName

export { Slider }
