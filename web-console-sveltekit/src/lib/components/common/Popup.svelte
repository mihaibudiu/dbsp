<script lang="ts">
  import type { Snippet } from 'svelte'

  const { trigger, content }: { trigger: Snippet<[() => void]>; content: Snippet<[() => void]> } =
    $props()
  let show = $state(false)
  let contentNode: HTMLElement
  const onclick = (e: MouseEvent) => {
    if (contentNode.contains(e.target as any)) {
      return
    }
    e.stopPropagation()
    show = false
  }
  $effect(() => {
    if (show) {
      window.addEventListener('click', onclick, { capture: true })
    } else {
      window.removeEventListener('click', onclick)
    }
  })
</script>

<div class="relative">
  {@render trigger(() => {
    show = !show
  })}
  {#if show}
    <div bind:this={contentNode}>
      {@render content(() => (show = false))}
    </div>
  {/if}
</div>
