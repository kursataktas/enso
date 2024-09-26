import { Tree, TreeBuffer } from '@lezer/common'
import { Element } from '@lezer/markdown'

declare module '@lezer/markdown' {
  export interface BlockContext {
    block: CompositeBlock
    stack: CompositeBlock[]
    readonly buffer: Buffer

    addNode: (block: Type | Tree, from: number, to?: number) => void
    startContext: (type: Type, start: number, value?: number) => void
  }

  export interface MarkdownParser {
    getNodeType: (name: string) => number
  }

  export interface CompositeBlock {
    readonly type: number
    // Used for indentation in list items, markup character in lists
    readonly value: number
    readonly from: number
    readonly hash: number
    end: number
    readonly children: (Tree | TreeBuffer)[]
    readonly positions: number[]
  }

  export interface TreeElement {}

  export interface Buffer {
    content: number[]
    nodes: Tree[]

    write: (type: Type, from: number, to: number, children?: number) => Buffer
    writeElements: (elts: readonly (Element | TreeElement)[], offset?: number) => Buffer
    finish: (type: Type, length: number) => Tree
  }
}

export enum Type {
  // noinspection JSUnusedGlobalSymbols
  Document = 1,

  CodeBlock,
  FencedCode,
  Blockquote,
  HorizontalRule,
  BulletList,
  OrderedList,
  ListItem,
  ATXHeading1,
  ATXHeading2,
  ATXHeading3,
  ATXHeading4,
  ATXHeading5,
  ATXHeading6,
  SetextHeading1,
  SetextHeading2,
  HTMLBlock,
  LinkReference,
  Paragraph,
  CommentBlock,
  ProcessingInstructionBlock,

  // Inline
  Escape,
  Entity,
  HardBreak,
  Emphasis,
  StrongEmphasis,
  Link,
  Image,
  InlineCode,
  HTMLTag,
  Comment,
  ProcessingInstruction,
  Autolink,

  // Smaller tokens
  HeaderMark,
  QuoteMark,
  ListMark,
  LinkMark,
  EmphasisMark,
  CodeMark,
  CodeText,
  CodeInfo,
  LinkTitle,
  LinkLabel,
  URL,
}
