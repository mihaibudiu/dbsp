<script lang="ts">
  import { getStatusLabel } from '$lib/functions/pipelines/status'
  import { getPipelineStatus } from '$lib/services/pipelineManager'
  import { asyncDerived, asyncReadable, derived, readable } from '@square/svelte-store'
  import { match, P } from 'ts-pattern'

  const { pipelineName, class: _class = '' }: { pipelineName: string; class?: string } = $props()
  const status = asyncReadable(
    { status: 'Initializing' as const },
    () => getPipelineStatus(pipelineName),
    {
      reloadable: true
    }
  )
  $effect(() => {
    let interval = setInterval(() => status.reload?.(), 2000)
    return () => {
      clearInterval(interval)
    }
  })
  $effect(() => {
    pipelineName
    status.reload?.()
  })
  const chipClass = derived(status, (status) => {
    return match(status.status)
      .with('Shutdown', () => 'preset-tonal-success')
      .with('Starting up', () => 'preset-tonal-surface')
      .with('Initializing', () => 'preset-tonal-warning')
      .with('Paused', () => 'preset-tonal-success')
      .with('Running', () => 'preset-tonal-success')
      .with('ShuttingDown', () => 'preset-tonal-surface')
      .with({ PipelineError: P.any }, () => 'preset-tonal-error')
      .with('Compiling sql', () => 'preset-tonal-warning')
      .with('Queued', () => 'preset-tonal-warning')
      .with('Compiling bin', () => 'preset-tonal-warning')
      .with(
        { SqlError: P.any },
        { RustError: P.any },
        { SystemError: P.any },
        () => 'preset-tonal-error'
      )
      .exhaustive()
  })
</script>

<div class={'chip pointer-events-none h-6 w-24 uppercase ' + $chipClass + ' ' + _class}>
  {getStatusLabel($status.status)}
</div>
<!-- {#each ['preset-tonal-primary', 'preset-tonal-secondary', 'preset-tonal-tertiary', 'preset-tonal-success', 'preset-tonal-warning', 'preset-tonal-error', 'preset-tonal-surface'] as color}
  <div class={'chip ' + color}>fffffffff</div>
{/each} -->
