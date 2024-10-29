/**
 * @file
 * This file provides a zustand store that contains the state of the Enso devtools.
 */
import type { PaywallFeatureName } from '#/hooks/billing'
import * as React from 'react'
import { createStore, useStore } from 'zustand'
import { useShallow } from 'zustand/shallow'

/** Configuration for a paywall feature. */
export interface PaywallDevtoolsFeatureConfiguration {
  readonly isForceEnabled: boolean | null
}

// =========================
// === EnsoDevtoolsStore ===
// =========================

/** The state of this zustand store. */
interface EnsoDevtoolsStore {
  readonly showDevtools: boolean
  readonly setShowDevtools: (showDevtools: boolean) => void
  readonly toggleDevtools: () => void
  readonly showVersionChecker: boolean | null
  readonly paywallFeatures: Record<PaywallFeatureName, PaywallDevtoolsFeatureConfiguration>
  readonly setPaywallFeature: (feature: PaywallFeatureName, isForceEnabled: boolean | null) => void
  readonly setEnableVersionChecker: (showVersionChecker: boolean | null) => void
}

export const ensoDevtoolsStore = createStore<EnsoDevtoolsStore>((set) => ({
  showDevtools: import.meta.env.DEV,
  setShowDevtools: (showDevtools) => {
    set({ showDevtools })
  },
  toggleDevtools: () => {
    set(({ showDevtools }) => ({ showDevtools: !showDevtools }))
  },
  showVersionChecker: false,
  paywallFeatures: {
    share: { isForceEnabled: null },
    shareFull: { isForceEnabled: null },
    userGroups: { isForceEnabled: null },
    userGroupsFull: { isForceEnabled: null },
    inviteUser: { isForceEnabled: null },
    inviteUserFull: { isForceEnabled: null },
  },
  setPaywallFeature: (feature, isForceEnabled) => {
    set((state) => ({
      paywallFeatures: { ...state.paywallFeatures, [feature]: { isForceEnabled } },
    }))
  },
  setEnableVersionChecker: (showVersionChecker) => {
    set({ showVersionChecker })
  },
}))

// ===============================
// === useEnableVersionChecker ===
// ===============================

/** A function to set whether the version checker is forcibly shown/hidden. */
export function useEnableVersionChecker() {
  return useStore(ensoDevtoolsStore, (state) => state.showVersionChecker)
}

// ==================================
// === useSetEnableVersionChecker ===
// ==================================

/** A function to set whether the version checker is forcibly shown/hidden. */
export function useSetEnableVersionChecker() {
  return useStore(ensoDevtoolsStore, (state) => state.setEnableVersionChecker)
}

/** A hook that provides access to the paywall devtools. */
export function usePaywallDevtools() {
  return useStore(
    ensoDevtoolsStore,
    useShallow((state) => ({
      features: state.paywallFeatures,
      setFeature: state.setPaywallFeature,
    })),
  )
}

/** A hook that provides access to the show devtools state. */
export function useShowDevtools() {
  return useStore(ensoDevtoolsStore, (state) => state.showDevtools)
}

// =================================
// === DevtoolsProvider ===
// =================================

/**
 * Provide the Enso devtools to the app.
 */
export function DevtoolsProvider(props: { children: React.ReactNode }) {
  React.useEffect(() => {
    window.toggleDevtools = ensoDevtoolsStore.getState().toggleDevtools
  }, [])

  return <>{props.children}</>
}
