import { markdownDecorators } from '@/components/MarkdownEditor/markdown/decoration'
import { markdown } from '@/components/MarkdownEditor/markdown/parse'
import { VueHost } from '@/components/VueComponentHost.vue'
import { Extension } from '@codemirror/state'

/** Markdown extension, with customizations for Enso. */
export function ensoMarkdown({ vueHost }: { vueHost: VueHost }): Extension {
  return [markdown(), markdownDecorators({ vueHost })]
}
