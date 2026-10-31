<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, ref, useId, watch } from 'vue'
import { X } from 'lucide-vue-next'
import { acquireDialogLock } from '../lib/dialogLock'
const props = defineProps<{ title: string; subtitle?: string; drawer?: boolean; busy?: boolean }>()
const emit = defineEmits<{ close: [] }>()
const panel = ref<HTMLElement>()
const titleId = `dialog-title-${useId()}`
let releaseLock: (() => void) | undefined
function close() { if (!props.busy) emit('close') }
function keydown(event: KeyboardEvent) {
  if (event.key === 'Escape') { event.preventDefault(); event.stopPropagation(); close() }
  if (event.key !== 'Tab' || !panel.value) return
  const elements = Array.from(panel.value.querySelectorAll<HTMLElement>('button:not(:disabled),input:not(:disabled),select:not(:disabled),a[href],[tabindex="0"]')).filter(e => e.offsetParent !== null)
  const first = elements[0], last = elements[elements.length - 1]
  if (!first) { event.preventDefault(); panel.value.focus(); return }
  if (event.shiftKey && (document.activeElement === first || document.activeElement === panel.value)) { event.preventDefault(); last?.focus() }
  else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus() }
}
onMounted(() => {
  releaseLock = acquireDialogLock()
  const first = panel.value?.querySelector<HTMLElement>('[autofocus]') || panel.value?.querySelector<HTMLElement>('button:not(:disabled), input:not(:disabled)')
  ;(first || panel.value)?.focus()
  document.addEventListener('keydown', keydown)
})
onUnmounted(() => {
  document.removeEventListener('keydown', keydown)
  releaseLock?.()
})
watch(() => props.title, async () => {
  await nextTick()
  panel.value?.scrollTo({ top: 0 })
})
</script>
<template>
  <Teleport to="body">
    <div class="dialog-backdrop" :class="{ 'is-drawer': drawer }" @click.self="close">
      <section ref="panel" class="dialog-panel" :class="{ 'drawer-panel': drawer }" role="dialog" aria-modal="true" :aria-labelledby="titleId" :aria-busy="busy || undefined" tabindex="-1">
        <header class="dialog-header"><div><h2 :id="titleId">{{ title }}</h2><p v-if="subtitle">{{ subtitle }}</p></div><button class="icon-button" :disabled="busy" :aria-label="busy ? '预约正在提交' : '关闭窗口'" @click="close"><X :size="20" /></button></header>
        <slot />
      </section>
    </div>
  </Teleport>
</template>
