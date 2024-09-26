// Normalized representations:
// - Eagerly attach during parsing
// - Parser also attaches concrete representation
//   - Concrete representation is tagged with a hash of the abstract representation; concrete representation supersedes
//     abstract if hash matches.

import { LINE_BOUNDARIES } from 'enso-common/src/utilities/data/string'
import { xxHash128 } from './ffi'
import {
  ConcreteChild,
  ensureUnspaced,
  firstChild,
  preferUnspaced,
  RawConcreteChild,
  unspaced,
} from './print'
import { Token, TokenType } from './token'
import { DocLine, OwnedRefs, TextToken } from './tree'

function* yTextToTokens(yText: string, indent: string): IterableIterator<ConcreteChild<Token>> {
  const lines = yText.split(LINE_BOUNDARIES)
  for (const [i, value] of lines.entries()) {
    if (i) yield unspaced(Token.new('\n', TokenType.Newline))
    yield { whitespace: indent, node: Token.new(value, TokenType.TextSection) }
  }
}
export function markdownToConcrete(
  markdown: string,
  hash: string,
  indent: string,
): IterableIterator<ConcreteChild<Token>> | undefined {
  return xxHash128(markdown) === hash ? undefined : yTextToTokens(markdown, indent)
}
export function* docLineToConcrete(
  docLine: DocLine,
  indent: string,
): IterableIterator<RawConcreteChild> {
  yield firstChild(docLine.docs.open)
  let prevType = undefined
  let extraIndent = ''
  for (const { token } of docLine.docs.elements) {
    if (token.node.tokenType_ === TokenType.Newline) {
      yield ensureUnspaced(token, false)
    } else {
      if (prevType === TokenType.Newline) {
        yield { whitespace: indent + extraIndent, node: token.node }
      } else {
        if (prevType === undefined) {
          const leadingSpace = token.node.code_.match(/ */)
          extraIndent = '  ' + (leadingSpace ? leadingSpace[0] : '')
        }
        yield { whitespace: '', node: token.node }
      }
    }
    prevType = token.node.tokenType_
  }
  for (const newline of docLine.newlines) yield preferUnspaced(newline)
}

export function abstractMarkdown(elements: undefined | TextToken<OwnedRefs>[]) {
  let markdown = ''
  let newlines = 0
  ;(elements ?? []).forEach(({ token: { node } }, i) => {
    if (node.tokenType_ === TokenType.Newline) {
      if (newlines > 0) {
        markdown += '\n'
      }
      newlines += 1
    } else {
      if (i >= 0) {
        markdown += node.code()
      } else {
        markdown += node.code().trimStart()
      }
      newlines = 0
    }
  })
  const hash = xxHash128(markdown)
  return { markdown, hash }
}
