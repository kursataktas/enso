/** @file Rendering for an {@link SettingsFormEntryData}. */
import { ButtonGroup, Form } from '#/components/AriaComponents'
import { useSyncRef } from '#/hooks/syncRefHooks'
import { useText } from '#/providers/TextProvider'
import { useEffect, useMemo, useState } from 'react'
import SettingsInput from './Input'
import type { SettingsContext, SettingsFormEntryData } from './data'

// =========================
// === SettingsFormEntry ===
// =========================

/** Props for a {@link SettingsFormEntry}. */
export interface SettingsFormEntryProps<T extends Record<keyof T, string>> {
  readonly context: SettingsContext
  readonly data: SettingsFormEntryData<T>
}

/** Rendering for an {@link SettingsFormEntryData}. */
export function SettingsFormEntry<T extends Record<keyof T, string>>(
  props: SettingsFormEntryProps<T>,
) {
  const { context, data } = props
  const { schema: schemaRaw, getValue, inputs, onSubmit, getVisible } = data
  const { getText } = useText()
  const [dirty, setDirty] = useState(false)
  const visible = getVisible?.(context) ?? true
  const value = getValue(context)
  const schema = useMemo(
    () => (typeof schemaRaw === 'function' ? schemaRaw(context) : schemaRaw),
    [context, schemaRaw],
  )

  const form = Form.useForm({
    // @ts-expect-error This is SAFE, as the type `T` is statically known.
    schema,
    defaultValues: value,
    onSubmit: async (newValue) => {
      // @ts-expect-error This is SAFE, as the type `T` is statically known.
      await onSubmit(context, newValue)
      setDirty(true)
    },
  })

  const dirtyEffectDeps = useSyncRef({ context, form, getValue })

  useEffect(() => {
    const deps = dirtyEffectDeps.current
    if (dirty) {
      setDirty(false)
      deps.form.reset(deps.getValue(deps.context))
    }
  }, [dirty, dirtyEffectDeps])

  return !visible ? null : (
      <Form form={form} gap="none">
        {inputs.map((input) => (
          <SettingsInput key={input.name} context={context} data={input} />
        ))}
        <ButtonGroup>
          <Form.Submit>{getText('save')}</Form.Submit>
          <Form.Reset>{getText('cancel')}</Form.Reset>
        </ButtonGroup>
        <Form.FormError />
      </Form>
    )
}
