<script lang="ts">
  import type { SystemError } from '$lib/compositions/health/systemErrors'
  import type { Loadable } from '@square/svelte-store'
  import InlineDropdown from '$lib/components/common/InlineDropdown.svelte'
  import JSONbig from 'true-json-bigint'
  import { clipboard } from '@svelte-bin/clipboard'
  import { fade, slide } from 'svelte/transition'

  const { systemErrors, close }: { systemErrors: Loadable<SystemError[]>; close?: () => void } =
    $props()
</script>

<div class="flex flex-col gap-2 p-4">
  <span class="h5 font-medium">Feldera Health</span>
  <div class="h-full">
    {#each $systemErrors as systemError}
      <div class="mb-5">
        <InlineDropdown>
          {#snippet header(open, toggle)}
            <div class="">
              <div
                class="flex w-full cursor-pointer items-center gap-2 py-2"
                onclick={toggle}
                role="presentation"
              >
                <div
                  class={'bx bx-chevron-down text-[24px] transition-transform ' +
                    (open ? 'rotate-180' : '')}
                ></div>
                <a
                  href={systemError.cause.source}
                  class="text-primary-500"
                  onclick={(e) => {
                    close?.()
                  }}
                >
                  {systemError.name}
                </a>
              </div>
              {#if !open}
                <div
                  class=" -mb-5 w-full overflow-x-hidden overflow-y-clip overflow-ellipsis whitespace-nowrap text-sm"
                >
                  {systemError.message}
                </div>
              {/if}
            </div>
          {/snippet}
          {#snippet content()}
            {@const text = JSONbig.stringify(systemError.cause.body, undefined, '\t')
              .replaceAll('\\n', '\n')
              .replaceAll('\\"', '"')}
            <div transition:slide={{ duration: 150 }} class="">
              <div class="text-sm">
                {systemError.message}
              </div>
              <div class="relative">
                <div
                  class="m-0 max-h-48 overflow-x-auto whitespace-pre p-2 pt-6 font-mono text-sm bg-surface-50-950"
                >
                  {text}
                </div>
                <button
                  class="btn-icon absolute right-4 top-2 text-[20px] preset-tonal-surface"
                  use:clipboard={text}
                >
                  <div class="bx bx-copy"></div>
                </button>
              </div>
            </div>
          {/snippet}
        </InlineDropdown>
      </div>
    {/each}
  </div>
</div>
