<script lang="ts">
  import {
    postPipelineAction,
    type ExtendedPipeline,
    type Pipeline,
    type PipelineAction,
    type PipelineStatus
  } from '$lib/services/pipelineManager'
  import { match, P } from 'ts-pattern'
  import { deletePipeline as _deletePipeline } from '$lib/services/pipelineManager'
  import DeleteDialog, { deleteDialogProps } from '$lib/components/dialogs/DeleteDialog.svelte'
  import { useGlobalDialog } from '$lib/compositions/useGlobalDialog.svelte'
  import JSONDialog from '$lib/components/dialogs/JSONDialog.svelte'
  import JSONbig from 'true-json-bigint'
  import { goto } from '$app/navigation'
  import { base } from '$app/paths'
  import Tooltip from '$lib/components/common/Tooltip.svelte'

  let {
    pipeline,
    onDeletePipeline,
    pipelineBusy,
    unsavedChanges,
    onActionSuccess,
    class: _class = ''
  }: {
    pipeline: {
      current: ExtendedPipeline
      patch: (pipeline: Partial<Pipeline>) => Promise<ExtendedPipeline>
      optimisticUpdate: (newPipeline: Partial<ExtendedPipeline>) => Promise<void>
    }
    onDeletePipeline?: (pipelineName: string) => void
    pipelineBusy: boolean
    unsavedChanges: boolean
    onActionSuccess?: (pipelineName: string, action: PipelineAction | 'start_paused_start') => void
    class?: string
  } = $props()

  const globalDialog = useGlobalDialog()
  const deletePipeline = async (pipelineName: string) => {
    await _deletePipeline(pipelineName)
    onDeletePipeline?.(pipelineName)
    goto(`${base}/`)
  }

  const actions = {
    _start,
    _start_paused,
    _start_error,
    _start_pending,
    _pause,
    _shutdown,
    _delete,
    _spacer,
    _spinner,
    _configure
  }

  const active = $derived(
    match(pipeline.current.status)
      .returnType<(keyof typeof actions)[]>()
      .with('Shutdown', () => ['_start_paused', '_configure', '_delete'])
      .with('Queued', () => ['_start_pending', '_configure', '_delete'])
      .with('Starting up', () => ['_spinner', '_configure', '_shutdown'])
      .with('Initializing', () => ['_spinner', '_configure', '_shutdown'])
      .with('Running', () => ['_pause', '_configure', '_shutdown'])
      .with('Pausing', () => ['_spinner', '_configure', '_shutdown'])
      .with('Resuming', () => ['_spinner', '_configure', '_shutdown'])
      .with('Paused', () => ['_start', '_configure', '_shutdown'])
      .with('ShuttingDown', () => ['_spinner', '_configure', '_spacer'])
      .with({ PipelineError: P.any }, () => ['_spacer', '_configure', '_shutdown'])
      .with('Compiling sql', () => ['_start_pending', '_configure', '_delete'])
      .with('Compiling bin', () => ['_start_pending', '_configure', '_delete'])
      .with('Unavailable', () => ['_spinner', '_configure', '_shutdown'])
      .with({ SqlError: P.any }, { RustError: P.any }, { SystemError: P.any }, () => [
        '_start_error',
        '_configure',
        '_delete'
      ])
      .exhaustive()
  )

  const buttonClass = 'hover:brightness-90 dark:hover:brightness-150 text-[32px] w-9 h-9'
</script>

{#snippet deleteDialog()}
  <DeleteDialog
    {...deleteDialogProps(
      'Delete',
      (name) => `${name} pipeline`,
      (name: string) => {
        deletePipeline(name)
      }
    )(pipeline.current.name)}
    onClose={() => (globalDialog.dialog = null)}
  ></DeleteDialog>
{/snippet}

<div class={'flex flex-nowrap gap-2 ' + _class}>
  {#each active as name}
    {@render actions[name]()}
  {/each}
</div>

{#snippet _delete()}
  <button
    class="{buttonClass} fd fd-delete bg-surface-50-950"
    onclick={() => (globalDialog.dialog = deleteDialog)}
  >
  </button>
{/snippet}
{#snippet start(
  action: (alt: boolean) => PipelineAction | 'start_paused_start',
  status: PipelineStatus
)}
  <button
    aria-label={action(false)}
    class:disabled={unsavedChanges}
    class="{buttonClass} fd fd-play_arrow text-[36px] !bg-success-200-800"
    onclick={async (e) => {
      const _action = action(e.ctrlKey || e.shiftKey || e.metaKey)
      const pipelineName = pipeline.current.name
      const success = await postPipelineAction(
        pipeline.current.name,
        _action === 'start_paused_start' ? 'start_paused' : _action
      )
      pipeline.optimisticUpdate({ status })
      await success()
      onActionSuccess?.(pipelineName, _action)
    }}
  >
  </button>
{/snippet}
{#snippet _start()}
  {@render start(() => 'start', 'Resuming')}
  {#if unsavedChanges}
    <Tooltip class="z-20 bg-white text-surface-950-50 dark:bg-black" placement="top">
      Save the pipeline before running
    </Tooltip>
  {/if}
{/snippet}
{#snippet _start_paused()}
  {@render start((alt) => (alt ? 'start_paused' : 'start_paused_start'), 'Starting up')}
  {#if unsavedChanges}
    <Tooltip class="z-20 bg-white text-surface-950-50 dark:bg-black" placement="top">
      Save the pipeline before running
    </Tooltip>
  {/if}
{/snippet}
{#snippet _start_disabled()}
  <div class="h-9">
    <button class="{buttonClass} fd fd-play_arrow disabled text-[36px]"></button>
  </div>
{/snippet}
{#snippet _start_error()}
  {@render _start_disabled()}
  <Tooltip class="z-20 bg-white text-surface-950-50 dark:bg-black" placement="top">
    Resolve errors before running
  </Tooltip>
{/snippet}
{#snippet _start_pending()}
  {@render _start_disabled()}
  <Tooltip class="z-20 bg-white text-surface-950-50 dark:bg-black" placement="top">
    Wait for compilation to complete
  </Tooltip>
{/snippet}
{#snippet _pause()}
  <button
    class="{buttonClass} fd fd-pause bg-surface-50-950"
    onclick={() => {
      const pipelineName = pipeline.current.name
      postPipelineAction(pipelineName, 'pause').then(() => {
        onActionSuccess?.(pipelineName, 'pause')
        pipeline.optimisticUpdate({ status: 'Pausing' })
      })
    }}
  >
  </button>
{/snippet}
{#snippet _shutdown()}
  <button
    class="{buttonClass} fd fd-stop bg-surface-50-950"
    onclick={() => {
      const pipelineName = pipeline.current.name
      postPipelineAction(pipelineName, 'shutdown').then(() => {
        onActionSuccess?.(pipelineName, 'shutdown')
        pipeline.optimisticUpdate({ status: 'ShuttingDown' })
      })
    }}
  >
  </button>
{/snippet}
{#snippet _configure()}
  {#snippet pipelineResourcesDialog()}
    <JSONDialog
      disabled={pipelineBusy}
      json={JSONbig.stringify(pipeline.current.runtimeConfig, undefined, '  ')}
      onApply={async (json) => {
        await pipeline.patch({
          runtimeConfig: JSONbig.parse(json)
        })
      }}
      onClose={() => (globalDialog.dialog = null)}
    >
      {#snippet title()}
        <div class="h5 text-center font-normal">
          {`Configure ${pipeline.current.name} runtime resources`}
        </div>
      {/snippet}
    </JSONDialog>
  {/snippet}
  <button
    onclick={() => (globalDialog.dialog = pipelineResourcesDialog)}
    class="{buttonClass} fd fd-settings bg-surface-50-950"
  >
  </button>
  {#if pipelineBusy}
    <Tooltip class="z-20 bg-white text-surface-950-50 dark:bg-black" placement="top">
      Stop the pipeline to edit configuration
    </Tooltip>
  {/if}
{/snippet}
{#snippet _spacer()}
  <div class="w-9"></div>
{/snippet}
{#snippet _spinner()}
  <div class="gc gc-loader-alt pointer-events-none h-9 w-9 animate-spin !text-[36px]"></div>
{/snippet}
