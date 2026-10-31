<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, useId, watch } from 'vue'
import { Clock3 } from 'lucide-vue-next'

const props = withDefaults(defineProps<{ modelValue: string; label: string; min?: string; max?: string; disabled?: boolean; invalid?: boolean; describedBy?: string }>(), { min: '09:00', max: '20:00', disabled: false, invalid: false })
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
const root = ref<HTMLElement>(), trigger = ref<HTMLButtonElement>(), list = ref<HTMLElement>()
const open = ref(false), upwards = ref(false), maxHeight = ref(280), activeIndex = ref(0)
const id = `time-${useId()}`
function toMinutes(value: string) { const [h, m] = value.split(':').map(Number); return h * 60 + m }
const options = computed(() => {
  const start = Math.ceil(toMinutes(props.min) / 15) * 15, end = toMinutes(props.max)
  if (!Number.isFinite(start) || !Number.isFinite(end) || start > end) return []
  return Array.from({ length: Math.floor((end - start) / 15) + 1 }, (_, i) => {
    const n = start + i * 15
    return `${String(Math.floor(n / 60)).padStart(2, '0')}:${String(n % 60).padStart(2, '0')}`
  })
})
function place() {
  if (!root.value) return
  const rect = root.value.getBoundingClientRect(), parent = root.value.closest('.dialog-panel')?.getBoundingClientRect()
  const top = Math.max(8, parent?.top ?? 8), bottom = Math.min(window.innerHeight - 8, parent?.bottom ?? window.innerHeight - 8)
  const below = bottom - rect.bottom - 8, above = rect.top - top - 8
  upwards.value = below < 280 && above > below
  maxHeight.value = Math.max(120, Math.min(280, upwards.value ? above : below))
}
async function show() {
  if (props.disabled || !options.value.length) return
  place()
  const index = options.value.indexOf(props.modelValue)
  activeIndex.value = index >= 0 ? index : 0
  open.value = true
  await nextTick()
  list.value?.querySelector<HTMLElement>('[data-active="true"]')?.scrollIntoView?.({ block: 'nearest' })
}
function close() { open.value = false }
function choose(index: number) {
  const value = options.value[index]
  if (!value || props.disabled) return
  if (value !== props.modelValue) emit('update:modelValue', value)
  close(); trigger.value?.focus({ preventScroll: true })
}
async function move(index: number) {
  activeIndex.value = Math.max(0, Math.min(options.value.length - 1, index))
  await nextTick()
  list.value?.querySelector<HTMLElement>('[data-active="true"]')?.scrollIntoView?.({ block: 'nearest' })
}
function keydown(event: KeyboardEvent) {
  if (props.disabled) return
  if (event.key === 'Tab') { close(); return }
  if (event.key === 'Escape' && open.value) { event.preventDefault(); event.stopPropagation(); close(); return }
  if (['ArrowDown', 'ArrowUp', 'ArrowLeft', 'ArrowRight', 'Home', 'End', 'Enter', ' '].includes(event.key)) {
    event.preventDefault(); event.stopPropagation()
    if (!open.value) { show(); return }
    if (event.key === 'Enter' || event.key === ' ') { choose(activeIndex.value); return }
    if (event.key === 'Home') { move(0); return }
    if (event.key === 'End') { move(options.value.length - 1); return }
    const steps: Record<string, number> = { ArrowDown: 4, ArrowUp: -4, ArrowLeft: -1, ArrowRight: 1 }
    move(activeIndex.value + steps[event.key])
  }
}
function outside(event: PointerEvent) { if (open.value && !root.value?.contains(event.target as Node)) close() }
function focusout(event: FocusEvent) { if (!root.value?.contains(event.relatedTarget as Node | null)) close() }
watch(() => props.disabled, disabled => { if (disabled) close() })
watch(options, () => { if (open.value) { activeIndex.value = Math.max(0, options.value.indexOf(props.modelValue)); place() } })
onMounted(() => { document.addEventListener('pointerdown', outside); window.addEventListener('resize', place) })
onUnmounted(() => { document.removeEventListener('pointerdown', outside); window.removeEventListener('resize', place) })
</script>

<template>
  <div ref="root" class="app-time" :class="{ 'is-open': open, 'opens-up': upwards }" @focusout="focusout">
    <div class="app-time-field" :class="{ 'is-invalid': invalid }">
      <input type="time" :value="modelValue" :aria-label="label" :aria-invalid="invalid || undefined" :aria-describedby="describedBy" :min="min" :max="max" :disabled="disabled" required @focus="close" @input="emit('update:modelValue', ($event.target as HTMLInputElement).value)" />
      <button ref="trigger" type="button" role="combobox" class="app-time-trigger" :aria-label="`选择${label}时段`" aria-haspopup="listbox" :aria-expanded="open" :aria-controls="open ? `${id}-list` : undefined" :aria-activedescendant="open ? `${id}-${activeIndex}` : undefined" :disabled="disabled" @click="open ? close() : show()" @keydown="keydown"><Clock3 :size="17" aria-hidden="true"/></button>
    </div>
    <Transition name="select-menu"><div v-if="open" class="app-time-menu" :style="{maxHeight:`${maxHeight}px`}"><div class="app-time-menu-header"><strong>选择时段</strong><span>也可直接输入时间</span></div><ul :id="`${id}-list`" ref="list" class="app-time-options" role="listbox" :aria-label="`${label}可选时段`"><li v-for="(option,index) in options" :id="`${id}-${index}`" :key="option" role="option" :aria-selected="option===modelValue" :data-active="index===activeIndex" :class="{ 'is-active': index===activeIndex, 'is-selected': option===modelValue }" @mouseenter="activeIndex=index" @mousedown.prevent @click="choose(index)">{{option}}</li></ul></div></Transition>
  </div>
</template>
