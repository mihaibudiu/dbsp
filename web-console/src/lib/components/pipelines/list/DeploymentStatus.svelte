<script lang="ts">
  import { getDeploymentStatusLabel } from '$lib/functions/pipelines/status'
  import { type PipelineStatus } from '$lib/services/pipelineManager'
  import { match, P } from 'ts-pattern'

  const { status, class: _class = '' }: { status: PipelineStatus; class?: string } = $props()

  const chipClass = $derived(
    match(status)
      .with('Stopped', () => '')
      .with('Preparing', 'Provisioning', 'Initializing', () => 'preset-filled-tertiary-200-800')
      .with('Paused', () => 'bg-blue-200 dark:bg-blue-800')
      .with('Suspending', () => 'preset-filled-secondary-200-800')
      .with('Running', () => 'preset-tonal-success')
      .with('Pausing', () => 'preset-filled-secondary-200-800')
      .with('Resuming', () => 'preset-filled-tertiary-200-800')
      .with('Stopping', () => 'preset-filled-secondary-200-800')
      .with(
        { Queued: P.any },
        { CompilingSql: P.any },
        { SqlCompiled: P.any },
        { CompilingRust: P.any },
        () => ''
      )
      .with('Unavailable', () => 'bg-orange-300 dark:bg-orange-700')
      .with('SqlError', 'RustError', 'SystemError', () => '')
      .exhaustive()
  )
</script>

<div class={'chip pointer-events-none text-[0.66rem] uppercase ' + chipClass + ' ' + _class}>
  {getDeploymentStatusLabel(status)}
</div>
