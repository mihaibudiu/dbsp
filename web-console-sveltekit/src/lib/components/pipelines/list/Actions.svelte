<script lang="ts">
  import { useDeleteDialog } from '$lib/compositions/useGlobalDialog.svelte'
  import {
    getPipelineStatus,
    pipelineAction,
    type PipelineStatus
  } from '$lib/services/pipelineManager'
  import { asyncDerived, asyncReadable, derived, readable, writable } from '@square/svelte-store'
  import { match, P } from 'ts-pattern'
  import { deletePipeline as _deletePipeline } from '$lib/services/pipelineManager'

  let {
    pipelineName,
    reloadPipelines,
    class: _class = ''
  }: {
    pipelineName: string
    reloadPipelines?: () => void
    class?: string
  } = $props()
  // let initial = writable<PipelineStatus>('Initializing')
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

  const { showDeleteDialog } = useDeleteDialog()
  const deletePipeline = (pipelineName: string) => {
    _deletePipeline(pipelineName)
    reloadPipelines?.()
  }

  const actions = {
    _start,
    _pause,
    _shutdown,
    _delete,
    _spacer,
    _spinner,
    _configure
  }

  const active = derived(status, ({ status }) =>
    match(status)
      .returnType<(keyof typeof actions)[]>()
      .with('Shutdown', () => ['_start', '_configure', '_delete'])
      .with('Queued', () => ['_spacer', '_configure', '_delete'])
      .with('Starting up', () => ['_spinner', '_configure', '_spacer'])
      .with('Initializing', () => ['_spinner', '_configure'])
      .with('Running', () => ['_pause', '_configure', '_shutdown'])
      // .with('Pausing', () => ['spinner', 'edit'])
      .with('Paused', () => ['_start', '_configure', '_shutdown'])
      .with('ShuttingDown', () => ['_spinner', '_configure', '_spacer'])
      .with({ PipelineError: P.any }, () => ['_spacer', '_configure', '_shutdown'])
      .with('Compiling sql', () => ['_spinner', '_configure', '_delete'])
      .with('Compiling bin', () => ['_spinner', '_configure', '_delete'])
      .with({ SqlError: P.any }, { RustError: P.any }, { SystemError: P.any }, () => [
        '_spacer',
        '_configure',
        '_delete'
      ])
      .exhaustive()
  )

  const buttonClass = 'btn-icon preset-tonal-surface text-[24px]'
  const reload = () => {
    setTimeout(() => status.reload?.(), 300)
    setTimeout(() => status.reload?.(), 500)
  }
</script>

<div class={'flex flex-nowrap ' + _class}>
  {#each $active as name}
    {@render actions[name]()}
  {/each}
</div>

{#snippet _delete()}
  <button
    class={'bx bx-trash-alt ' + buttonClass}
    onclick={() =>
      showDeleteDialog('Delete', (name) => `${name} pipeline`, deletePipeline)(pipelineName)}
  >
  </button>
{/snippet}
{#snippet _start()}
  <button
    class={'bx bx-play-circle ' + buttonClass}
    onclick={() => pipelineAction(pipelineName, 'start').then(reload)}
  >
  </button>
{/snippet}
{#snippet _pause()}
  <button
    class={'bx bx-pause-circle ' + buttonClass}
    onclick={() => pipelineAction(pipelineName, 'pause').then(reload)}
  >
  </button>
{/snippet}
{#snippet _shutdown()}
  <button
    class={'bx bx-stop-circle ' + buttonClass}
    onclick={() => pipelineAction(pipelineName, 'shutdown').then(reload)}
  >
  </button>
{/snippet}
{#snippet _configure()}
  <button class={'bx bx-cog ' + buttonClass}> </button>
{/snippet}
{#snippet _spacer()}
  <div class="w-9"></div>
{/snippet}
{#snippet _spinner()}
  <div class={'pointer-events-none h-9 ' + buttonClass}>
    <div class="bx bx-loader-alt btn-icon animate-spin"></div>
  </div>
{/snippet}
