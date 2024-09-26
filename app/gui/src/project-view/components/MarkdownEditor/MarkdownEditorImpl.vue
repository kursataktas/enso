<script setup lang="ts">
import EditorRoot from '@/components/MarkdownEditor/EditorRoot.vue'
import { highlightStyle } from '@/components/MarkdownEditor/highlight'
import {
  provideDocumentationImageUrlTransformer,
  UrlTransformer,
} from '@/components/MarkdownEditor/imageUrlTransformer'
import { ensoMarkdown } from '@/components/MarkdownEditor/markdown'
import { TeleportationRegistry } from '@/components/MarkdownEditor/markdown/decoration'
import { EditorState } from '@codemirror/state'
import { EditorView } from '@codemirror/view'
import { minimalSetup } from 'codemirror'
import { useObjectId } from 'enso-common/src/utilities/data/object'
import {
  type Component,
  ComponentInstance,
  onMounted,
  reactive,
  ref,
  toRef,
  useCssModule,
  watch,
} from 'vue'
import { yCollab } from 'y-codemirror.next'
import * as awarenessProtocol from 'y-protocols/awareness.js'
import * as Y from 'yjs'

const editorRoot = ref<ComponentInstance<typeof EditorRoot>>()

const props = defineProps<{
  yText: Y.Text
  transformImageUrl?: UrlTransformer | undefined
  toolbarContainer: HTMLElement | undefined
}>()

provideDocumentationImageUrlTransformer(toRef(props, 'transformImageUrl'))

const awareness = new awarenessProtocol.Awareness(new Y.Doc())

const editorView = new EditorView()

watch(
  () => props.yText,
  () => console.error('new yText?!?'),
)

interface Teleportation {
  component: Component
  props: object
}

const decorationContext = reactive(new Map<HTMLElement, Teleportation>())

const teleporter: TeleportationRegistry = {
  register: decorationContext.set.bind(decorationContext),
  unregister: decorationContext.delete.bind(decorationContext),
}

onMounted(() => {
  editorView.setState(
    EditorState.create({
      doc: props.yText.toString(),
      extensions: [
        minimalSetup,
        yCollab(props.yText, awareness),
        ensoMarkdown({ teleporter }),
        highlightStyle(useCssModule()),
        EditorView.lineWrapping,
      ],
    }),
  )
  const content = editorView.dom.getElementsByClassName('cm-content')[0]!
  content.addEventListener('focusin', () => (editing.value = true))
  editorRoot.value?.rootElement?.prepend(editorView.dom)
})

const editing = ref(false)

const { objectId } = useObjectId()
</script>

<template>
  <EditorRoot
    ref="editorRoot"
    class="MarkdownEditor"
    :class="{ editing }"
    @focusout="editing = false"
  />
  <template
    v-for="[slot, { component, props: componentProps }] in decorationContext.entries()"
    :key="objectId(componentProps)"
  >
    <Teleport :to="slot">
      <component :is="component" v-bind="componentProps" />
    </Teleport>
  </template>
</template>

<!--suppress CssUnusedSymbol -->
<style module>
/* === Syntax styles === */

.heading1 {
  font-weight: 700;
  font-size: 20px;
  line-height: 1.75;
}
.heading2 {
  font-weight: 700;
  font-size: 16px;
  line-height: 1.75;
}
.heading3,
.heading4,
.heading5,
.heading6 {
  font-size: 14px;
  line-height: 2;
}
.processingInstruction {
  opacity: 20%;
}
.emphasis:not(.processingInstruction) {
  font-style: italic;
}
.strong:not(.processingInstruction) {
  font-weight: bold;
}
.strikethrough:not(.processingInstruction) {
  text-decoration: line-through;
}
.monospace {
  /*noinspection CssNoGenericFontName*/
  font-family: var(--font-mono);
}

/* === Editing-mode === */

/* There are currently no style overrides for editing mode, so this is commented out to appease the Vue linter. */
/* :global(.MarkdownEditor):global(.editing) :global(.cm-line):global(.cm-has-cursor) {} */

/* === View-mode === */

:global(.MarkdownEditor):not(:global(.editing)) :global(.cm-line),
:global(.cm-line):not(:global(.cm-has-cursor)) {
  :global(.cm-image-markup) {
    display: none;
  }
  .processingInstruction {
    display: none;
  }
  .url {
    display: none;
  }
  a > .link {
    display: inline;
    cursor: pointer;
    color: #555;
    &:hover {
      text-decoration: underline;
    }
  }
  &:has(.list.processingInstruction) {
    display: list-item;
    list-style-type: disc;
    list-style-position: inside;
  }
}
</style>
