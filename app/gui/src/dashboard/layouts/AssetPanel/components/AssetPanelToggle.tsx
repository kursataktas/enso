/**
 * @file
 * Toggle for opening the asset panel.
 */
import RightPanelIcon from '#/assets/right_panel.svg'
import { Button } from '#/components/AriaComponents'

import { Provider, TabsContext } from '#/components/aria'
import { useIsAssetPanelHidden, useSetIsAssetPanelHidden } from '#/providers/DriveProvider'
import { useText } from '#/providers/TextProvider'
import type { Spring } from 'framer-motion'
import { AnimatePresence, motion } from 'framer-motion'
import { useId } from 'react'

/**
 * Props for a {@link AssetPanelToggle}.
 */
export interface AssetPanelToggleProps {
  readonly className?: string
  readonly showWhen?: 'collapsed' | 'expanded'
}

const DEFAULT_TRANSITION_OPTIONS: Spring = {
  type: 'spring',
  // eslint-disable-next-line @typescript-eslint/no-magic-numbers
  stiffness: 200,
  // eslint-disable-next-line @typescript-eslint/no-magic-numbers
  damping: 30,
  mass: 1,
  velocity: 0,
}

/**
 * Toggle for opening the asset panel.
 */
export function AssetPanelToggle(props: AssetPanelToggleProps) {
  const { className, showWhen = 'collapsed' } = props

  const isAssetPanelHidden = useIsAssetPanelHidden()
  const setIsAssetPanelHidden = useSetIsAssetPanelHidden()

  const shouldShow = showWhen === 'collapsed' ? isAssetPanelHidden : !isAssetPanelHidden

  const id = useId()

  const { getText } = useText()

  return (
    <AnimatePresence mode="sync">
      {shouldShow && (
        <motion.div
          className={className}
          layout
          layoutId={`asset-panel-toggle-${id}`}
          initial={{ opacity: 0, filter: 'blur(8px)', x: showWhen === 'collapsed' ? 16 : -16 }}
          animate={{ opacity: 1, filter: 'blur(0px)', x: 0 }}
          exit={{ opacity: 0, filter: 'blur(4px)', x: showWhen === 'collapsed' ? 16 : -16 }}
          transition={DEFAULT_TRANSITION_OPTIONS}
        >
          <Button
            size="medium"
            variant="custom"
            isActive={!isAssetPanelHidden}
            icon={RightPanelIcon}
            aria-label={getText('openAssetPanel')}
            onPress={() => {
              setIsAssetPanelHidden(!isAssetPanelHidden)
            }}
          />
        </motion.div>
      )}
    </AnimatePresence>
  )
}
