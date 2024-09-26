import { ensoMarkdown } from '@/components/MarkdownEditor/markdown'
import { EditorState } from '@codemirror/state'
import { Decoration, EditorView } from '@codemirror/view'
import { expect, test } from 'vitest'
import type { Component } from 'vue'
import { reactive } from 'vue'
import { TeleportationRegistry } from '../markdown/decoration'

function editorState(text: string) {
  interface Teleportation {
    component: Component
    props: object
  }
  const decorationContext = reactive(new Map<HTMLElement, Teleportation>())
  const teleporter: TeleportationRegistry = {
    register: decorationContext.set.bind(decorationContext),
    unregister: decorationContext.delete.bind(decorationContext),
  }
  return EditorState.create({
    doc: text,
    extensions: [ensoMarkdown({ teleporter })],
  })
}

function decorations<T>(
  source: string,
  recognize: (from: number, to: number, decoration: Decoration) => T | undefined,
) {
  const state = editorState(source)
  const view = new EditorView({ state })
  const decorationSets = state.facet(EditorView.decorations)
  const results = []
  for (const decorationSet of decorationSets) {
    const resolvedDecorations =
      decorationSet instanceof Function ? decorationSet(view) : decorationSet
    const cursor = resolvedDecorations.iter()
    while (cursor.value != null) {
      const recognized = recognize(cursor.from, cursor.to, cursor.value)
      if (recognized) results.push(recognized)
      cursor.next()
    }
  }
  return results
}

function links(source: string) {
  return decorations(source, (from, to, deco) => {
    if (deco.spec.tagName === 'a') {
      return {
        text: source.substring(from, to),
        href: deco.spec.attributes.href,
      }
    }
  })
}

function images(source: string) {
  return decorations(source, (from, to, deco) => {
    if ('widget' in deco.spec && 'props' in deco.spec.widget && 'src' in deco.spec.widget.props) {
      return {
        from,
        to,
        src: deco.spec.widget.props.src,
      }
    }
  })
}

test('Link decoration', () => {
  const text = 'Link text'
  const href = 'https://www.example.com/index.html'
  const source: string = `[${text}](${href})`
  expect(links(source)).toEqual([{ text, href }])
  expect(images(source)).toEqual([])
})

test('Image decoration', () => {
  const url = 'https://www.example.com/image.avif'
  const source: string = `![Image](${url})`
  expect(links(source)).toEqual([])
  expect(images(source)).toEqual([
    {
      from: source.length,
      to: source.length,
      src: url,
    },
  ])
})
