<script lang="ts">
  import type { GlobalDialogContent } from '$lib/compositions/useGlobalDialog.svelte'

  let { content, onClose }: { content: GlobalDialogContent; onClose?: () => void } = $props()
</script>

<div class="p-4 sm:p-8">
  <div class="flex flex-col gap-4">
    <div class="flex flex-nowrap justify-between">
      <div class="h5">{content.title}</div>
      <button
        class="fd fd-x btn btn-icon -m-4 text-[20px]"
        onclick={onClose}
        aria-label="Confirm dangerous action"
      ></button>
    </div>
    {content.description}
  </div>
  <div class="flex flex-col-reverse gap-4 pt-4 sm:flex-row sm:justify-end">
    <button class="btn px-4 preset-filled-surface-50-950" onclick={onClose}> Cancel </button>
    <button
      class="btn px-4 font-semibold preset-filled-error-500"
      onclick={async () => {
        await content!.onSuccess.callback()
        onClose?.()
      }}
      data-testid={content.onSuccess['data-testid']}
    >
      {content.onSuccess.name}
    </button>
  </div>
</div>
