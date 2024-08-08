/** @file A list of previous versions of an asset. */
import { useSuspenseQuery } from '@tanstack/react-query'

import { ErrorBoundary } from '#/components/ErrorBoundary'
import { Suspense } from '#/components/Suspense'
import ProjectSession from '#/layouts/AssetPanel/ProjectSession'
import type Backend from '#/services/Backend'
import type { ProjectAsset } from '#/services/Backend'
import type AssetTreeNode from '#/utilities/AssetTreeNode'

// =======================
// === ProjectSessions ===
// =======================

/** Props for a {@link ProjectSessions}. */
export interface ProjectSessionsProps {
  readonly backend: Backend
  readonly item: AssetTreeNode<ProjectAsset>
}

/** A list of previous versions of an asset. */
export default function ProjectSessions(props: ProjectSessionsProps) {
  return (
    <ErrorBoundary>
      <Suspense>
        <ProjectSessionsInternal {...props} />
      </Suspense>
    </ErrorBoundary>
  )
}

// ====================================
// === ProjectSessionsInternal ===
// ====================================

/** Props for a {@link ProjectSessionsInternal}. */
interface ProjectSessionsInternalProps extends ProjectSessionsProps {}

/** A list of previous versions of an asset. */
function ProjectSessionsInternal(props: ProjectSessionsInternalProps) {
  const { backend, item } = props

  const projectSessionsQuery = useSuspenseQuery({
    queryKey: ['listProjectSessions', item.item.id, item.item.title],
    queryFn: async () => {
      const sessions = await backend.listProjectSessions(item.item.id, item.item.title)
      return [...sessions].reverse()
    },
  })

  return (
    <div className="pointer-events-auto flex flex-col items-center overflow-y-auto overflow-x-hidden">
      {projectSessionsQuery.data.map((session) => (
        <ProjectSession
          key={session.projectSessionId}
          backend={backend}
          project={item.item}
          projectSession={session}
        />
      ))}
    </div>
  )
}
