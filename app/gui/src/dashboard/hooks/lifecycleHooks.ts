/** @file Hooks related to component lifecycle. */

import { useEffect } from 'react'

import { IS_DEV_MODE } from 'enso-common/src/detect'

import { useEventCallback } from '#/hooks/eventCallbackHooks'

/** Ensure that an unmount function is run only once. */
export function useSingleUnmount(callback: () => void) {
  const currentCallback = useEventCallback(callback)

  let isRealRun = !IS_DEV_MODE
  useEffect(() => {
    return () => {
      if (isRealRun) {
        currentCallback()
      }
      // This is INTENTIONAL. The first time this hook runs, when in Strict Mode, is *after* the ref
      // has already been set. This makes the focus root immediately unset itself,
      // which is incorrect behavior.
      // eslint-disable-next-line react-compiler/react-compiler
      // eslint-disable-next-line react-hooks/exhaustive-deps
      isRealRun = true
    }
  }, [])
}
