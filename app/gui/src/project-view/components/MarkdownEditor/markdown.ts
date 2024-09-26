import {
  markdownDecorators,
  TeleportationRegistry,
} from '@/components/MarkdownEditor/markdown/decoration'
import { markdown } from '@/components/MarkdownEditor/markdown/parse'
import { Extension } from '@codemirror/state'

/** Markdown extension, with customizations for Enso. */
export function ensoMarkdown({ teleporter }: { teleporter: TeleportationRegistry }): Extension {
  return [markdown(), markdownDecorators({ teleporter })]
}
