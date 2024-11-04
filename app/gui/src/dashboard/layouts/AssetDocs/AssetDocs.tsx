/**
 * @file
 * Display the docs for an asset.
 */
import { MarkdownViewer } from '#/components/MarkdownViewer'
import { Result } from '#/components/Result'
import { useEventCallback } from '#/hooks/eventCallbackHooks'
import { useText } from '#/providers/TextProvider'
import type Backend from '#/services/Backend'
import type { AnyAsset, Asset } from '#/services/Backend'
import { AssetType } from '#/services/Backend'
import { useSuspenseQuery } from '@tanstack/react-query'
import * as ast from 'ydoc-shared/ast'
import { splitFileContents } from 'ydoc-shared/ensoFile'
import { versionContentQueryOptions } from '../AssetDiffView/useFetchVersionContent'

/**
 * Props for a {@link AssetDocs}.
 */
export interface AssetDocsProps {
  readonly backend: Backend
  readonly item: AnyAsset | null
}

/**
 * Display the docs for an asset.
 */
export function AssetDocs(props: AssetDocsProps) {
  const { backend, item } = props

  const { getText } = useText()

  if (item?.type !== AssetType.project) {
    return <Result status="info" title={getText('assetDocs.notProject')} centered />
  }

  // This is safe because we already checked that the item is a project above.

  return <AssetDocsContent backend={backend} item={item} />
}

/**
 * Props for {@link AssetDocsContent}.
 */
interface AssetDocsContentProps {
  readonly backend: Backend
  readonly item: Asset<AssetType.project>
}

/**
 * Display the docs for an asset.
 */
export function AssetDocsContent(props: AssetDocsContentProps) {
  const { backend, item } = props
  const { getText } = useText()

  const { data: docs } = useSuspenseQuery({
    ...versionContentQueryOptions({ backend, projectId: item.id, metadata: false }),
    select: (data) => {
      const withoutMeta = splitFileContents(data)
      const module = ast.parseModule(withoutMeta.code)

      for (const statement of module.statements()) {
        if (statement instanceof ast.MutableFunction && statement.name.code() === 'main') {
          return statement.documentationText() ?? ''
        }
      }

      return ''
    },
  })

  const resolveProjectAssetPath = useEventCallback((relativePath: string) =>
    backend.resolveProjectAssetPath(item.id, relativePath),
  )

  if (docs === '') {
    return <Result status="info" title={getText('assetDocs.noDocs')} centered />
  }

  return <MarkdownViewer text={docs} imgUrlResolver={resolveProjectAssetPath} />
}
