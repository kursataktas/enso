/** @file Hooks related to context menus. */
import * as React from 'react'

import * as modalProvider from '#/providers/ModalProvider'

import ContextMenu from '#/components/ContextMenu'
import ContextMenus from '#/components/ContextMenus'
import { useEventCallback } from '#/hooks/eventCallbackHooks'
import { useSyncRef } from '#/hooks/syncRefHooks'

// ======================
// === contextMenuRef ===
// ======================

/**
 * Return a ref that attaches a context menu event listener.
 * Should be used ONLY if the element does not expose an `onContextMenu` prop.
 */
export function useContextMenuRef(
  key: string,
  label: string,
  createEntriesRaw: (
    position: Pick<React.MouseEvent, 'pageX' | 'pageY'>,
  ) => React.JSX.Element | null,
  options: { enabled?: boolean } = {},
) {
  const { setModal } = modalProvider.useSetModal()
  const createEntries = useEventCallback(createEntriesRaw)
  const optionsRef = useSyncRef(options)
  const cleanupRef = React.useRef(() => {})
  const contextMenuRef = React.useMemo(
    () => (element: HTMLElement | null) => {
      cleanupRef.current()
      if (element == null) {
        cleanupRef.current = () => {}
      } else {
        const onContextMenu = (event: MouseEvent) => {
          const { enabled = true } = optionsRef.current
          if (enabled) {
            const position = { pageX: event.pageX, pageY: event.pageY }
            const children = createEntries(position)
            if (children != null) {
              event.preventDefault()
              event.stopPropagation()
              setModal(
                <ContextMenus
                  ref={(contextMenusElement) => {
                    if (contextMenusElement != null) {
                      const rect = contextMenusElement.getBoundingClientRect()
                      position.pageX = rect.left
                      position.pageY = rect.top
                    }
                  }}
                  key={key}
                  event={event}
                >
                  <ContextMenu aria-label={label}>{children}</ContextMenu>
                </ContextMenus>,
              )
            }
          }
        }
        element.addEventListener('contextmenu', onContextMenu)
        cleanupRef.current = () => {
          element.removeEventListener('contextmenu', onContextMenu)
        }
      }
    },
    [createEntries, key, label, optionsRef, setModal],
  )
  return contextMenuRef
}
