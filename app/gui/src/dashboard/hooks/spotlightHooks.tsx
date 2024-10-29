/** @file Hooks for showing an overlay with a cutout for a rectangular element. */
import { useEffect, useLayoutEffect, useState, type CSSProperties } from 'react'

import Portal from '#/components/Portal'
import { convertCSSUnitString } from '#/utilities/convertCSSUnits'
import { useEventCallback } from './eventCallbackHooks'
import { useMeasure } from './measureHooks'

/** Default padding around the spotlight element. */
const DEFAULT_PADDING_PX = 8

// eslint-disable-next-line no-restricted-syntax
const BACKGROUND_ELEMENT = document.getElementsByClassName('enso-spotlight')[0] as
  | HTMLElement
  | SVGElement
  | undefined

/** Props for {@link useSpotlight}. */
export interface SpotlightOptions {
  readonly enabled: boolean
  readonly close: () => void
  readonly backgroundElement?: HTMLElement
  readonly paddingPx?: number | undefined
}

/** A hook for showing an overlay with a cutout for a rectangular element. */
export function useSpotlight(options: SpotlightOptions) {
  const { enabled, close, backgroundElement: backgroundElementRaw } = options
  const { paddingPx = DEFAULT_PADDING_PX } = options
  const backgroundElement = backgroundElementRaw ?? BACKGROUND_ELEMENT

  const [refElement, setRefElement] = useState<HTMLElement | SVGElement | null>(null)

  const refCallback = useEventCallback((node: HTMLElement | SVGElement | null) => {
    if (node) {
      setRefElement(node)
    } else {
      setRefElement(null)
    }
  })

  const spotlightElement =
    !enabled || !backgroundElement ?
      null
    : <Spotlight
        close={close}
        element={refElement}
        backgroundElement={backgroundElement}
        paddingPx={paddingPx}
      />
  const style = { position: 'relative', zIndex: 3 } satisfies CSSProperties
  return { spotlightElement, props: { style, ref: refCallback } }
}

/** Props for a {@link Spotlight}. */
interface SpotlightProps {
  readonly element: HTMLElement | SVGElement | null
  readonly close: () => void
  readonly backgroundElement: HTMLElement | SVGElement
  readonly paddingPx?: number | undefined
}

/** A spotlight element. */
function Spotlight(props: SpotlightProps) {
  const { element, close, paddingPx = 0 } = props

  const [dimensionsRef, { top: topRaw, left: leftRaw, height, width }] = useMeasure()

  const top = topRaw - paddingPx
  const left = leftRaw - paddingPx
  const [borderRadius, setBorderRadius] = useState(0)

  const r = Math.min(borderRadius, height / 2 + paddingPx, width / 2 + paddingPx)
  const straightWidth = Math.max(0, width + paddingPx * 2 - borderRadius * 2)
  const straightHeight = Math.max(0, height + paddingPx * 2 - borderRadius * 2)

  useEffect(() => {
    if (element) {
      dimensionsRef(element)
    }
  }, [dimensionsRef, element])

  useLayoutEffect(() => {
    if (element) {
      const sizeString = getComputedStyle(element).borderRadius
      setBorderRadius(convertCSSUnitString(sizeString, 'px', element).number)
    }
  }, [element])

  const clipPath =
    // A rectangle covering the entire screen
    'path(evenodd, "M0 0L3840 0 3840 2160 0 2160Z' +
    // Move to top left
    `M${left + r} ${top}` +
    // Top edge
    `h${straightWidth}` +
    // Top right arc
    (r !== 0 ? `a${r} ${r} 0 0 1 ${r} ${r}` : '') +
    // Right edge
    `v${straightHeight}` +
    // Bottom right arc
    (r !== 0 ? `a${r} ${r} 0 0 1 -${r} ${r}` : '') +
    // Bottom edge
    `h-${straightWidth}` +
    // Bottom left arc
    (r !== 0 ? `a${r} ${r} 0 0 1 -${r} -${r}` : '') +
    // Left edge
    `v-${straightHeight}` +
    // Top left arc
    (r !== 0 ? `a${r} ${r} 0 0 1 ${r} -${r}` : '') +
    'Z")'

  return (
    <Portal>
      <div
        onClick={close}
        style={{
          position: 'absolute',
          zIndex: 2,
          height: '100vh',
          width: '100vw',
          backgroundColor: 'lch(0 0 0 / 25%)',
          clipPath,
        }}
      />
    </Portal>
  )
}
