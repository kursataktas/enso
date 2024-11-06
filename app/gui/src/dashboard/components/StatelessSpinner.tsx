/** @file A spinner that does not expose its {@link SpinnerState}. */
import * as React from 'react'

import type { SpinnerProps, SpinnerState } from '#/components/Spinner'
import Spinner from '#/components/Spinner'

// ========================
// === StatelessSpinner ===
// ========================

export { SpinnerState } from './Spinner'

/** Props for a {@link StatelessSpinner}. */
export type StatelessSpinnerProps = SpinnerProps

/**
 * A spinner that does not expose its {@link SpinnerState}. Instead, it begins at
 * {@link SpinnerState.initial} and immediately changes to the given state.
 */
export default function StatelessSpinner(props: StatelessSpinnerProps) {
  const { state: rawState, ...spinnerProps } = props

  const [state, setState] = React.useState<SpinnerState>('initial')

  React.useEffect(() => {
    const id = requestAnimationFrame(() => {
      // consider this as a low-priority update
      React.startTransition(() => {
        setState(rawState)
      })
    })

    return () => {
      cancelAnimationFrame(id)
    }
  }, [rawState])

  return <Spinner state={state} {...spinnerProps} />
}
