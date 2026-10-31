<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, useId, watch } from 'vue'
import { Check, ChevronDown } from 'lucide-vue-next'

type Value = string | number
type Option = { value: Value; label: string }
const props = withDefaults(defineProps<{ modelValue?: Value | null; options: Option[]; label: string; disabled?: boolean; placeholder?: string; compact?: boolean }>(), { disabled: false, placeholder: '请选择', compact: false })
const emit = defineEmits<{ 'update:modelValue': [value: Value]; change: [value: Value] }>()
const root = ref<HTMLElement>()
const trigger = ref<HTMLButtonElement>()
const list = ref<HTMLElement>()
const open = ref(false)
const upwards = ref(false)
const menuHeight = ref(256)
const activeIndex = ref(-1)
const id = `select-${useId()}`
const selectedIndex = computed(() => props.options.findIndex(option => option.value === props.modelValue))
const selectedLabel = computed(() => props.options[selectedIndex.value]?.label || props.placeholder)
const unavailable = computed(() => props.disabled || !props.options.length)
let search = ''
let searchTimeout: ReturnType<typeof setTimeout> | undefined

function updatePlacement() {
  if (!root.value) return
  const rect = root.value.getBoundingClientRect()
  const dialog = root.value.closest('.dialog-panel')
  const bounds = dialog?.getBoundingClientRect()
  const header = dialog?.querySelector('.dialog-header')?.getBoundingClientRect()
  const top = Math.max(8, header?.bottom ?? bounds?.top ?? 8)
  const bottom = Math.min(window.innerHeight - 8, (bounds?.bottom ?? window.innerHeight) - 8)
  const spaceBelow = bottom - rect.bottom - 8
  const spaceAbove = rect.top - top - 8
  const desired = Math.min(props.options.length * 44 + 16, 256)
  upwards.value = spaceBelow < desired && spaceAbove > spaceBelow
  menuHeight.value = Math.max(0, Math.min(256, upwards.value ? spaceAbove : spaceBelow))
}
async function show() {
  if (unavailable.value) return
  updatePlacement()
  activeIndex.value = selectedIndex.value >= 0 ? selectedIndex.value : 0
  open.value = true
  await nextTick()
  list.value?.querySelector<HTMLElement>('[data-active="true"]')?.scrollIntoView?.({ block: 'nearest' })
}
function close() { open.value = false; search = ''; clearTimeout(searchTimeout) }
function choose(index: number) {
  const option = props.options[index]
  if (!option || props.disabled) return
  if (option.value !== props.modelValue) { emit('update:modelValue', option.value); emit('change', option.value) }
  close()
  trigger.value?.focus({ preventScroll: true })
}
async function move(index: number) {
  activeIndex.value = Math.max(0, Math.min(props.options.length - 1, index))
  await nextTick()
  list.value?.querySelector<HTMLElement>('[data-active="true"]')?.scrollIntoView?.({ block: 'nearest' })
}
function onKeydown(event: KeyboardEvent) {
  if (props.disabled || event.isComposing) return
  if (event.key === 'Tab') { close(); return }
  if (event.key === 'Escape' && open.value) { event.preventDefault(); event.stopPropagation(); close(); return }
  if (['ArrowDown', 'ArrowUp', 'Home', 'End', 'Enter', ' '].includes(event.key)) {
    event.preventDefault()
    event.stopPropagation()
    if (!open.value) { show(); return }
    if (event.key === 'Enter' || event.key === ' ') { choose(activeIndex.value); return }
    if (event.key === 'Home') { move(0); return }
    if (event.key === 'End') { move(props.options.length - 1); return }
    move(activeIndex.value + (event.key === 'ArrowDown' ? 1 : -1))
    return
  }
  if (event.key.length === 1 && !event.ctrlKey && !event.metaKey && !event.altKey) {
    search += event.key.toLocaleLowerCase()
    clearTimeout(searchTimeout)
    searchTimeout = setTimeout(() => { search = '' }, 700)
    const match = props.options.findIndex(option => option.label.toLocaleLowerCase().startsWith(search))
    if (match >= 0) { event.preventDefault(); if (!open.value) { show().then(() => move(match)) } else move(match) }
  }
}
function outside(event: PointerEvent) { if (open.value && !root.value?.contains(event.target as Node)) close() }
function onFocusout(event: FocusEvent) { if (!root.value?.contains(event.relatedTarget as Node | null)) close() }
watch(unavailable, disabled => { if (disabled) close() })
watch(() => props.options, () => { if (open.value) { activeIndex.value = Math.max(0, selectedIndex.value); updatePlacement() } })
function onScroll(event: Event) { if (open.value && !list.value?.contains(event.target as Node)) updatePlacement() }
onMounted(() => { document.addEventListener('pointerdown', outside); document.addEventListener('scroll', onScroll, true); window.addEventListener('resize', updatePlacement) })
onUnmounted(() => { document.removeEventListener('pointerdown', outside); document.removeEventListener('scroll', onScroll, true); window.removeEventListener('resize', updatePlacement); clearTimeout(searchTimeout) })
</script>

<template>
  <div ref="root" class="app-select" :class="{ 'is-open': open, 'is-disabled': unavailable, 'is-placeholder': selectedIndex < 0, 'is-compact': compact, 'opens-up': upwards }" @focusout="onFocusout">
    <button ref="trigger" type="button" class="app-select-trigger" role="combobox" :aria-label="label" aria-haspopup="listbox" :aria-expanded="open" :aria-controls="open ? `${id}-list` : undefined" :aria-activedescendant="open && activeIndex >= 0 ? `${id}-option-${activeIndex}` : undefined" :disabled="unavailable" @click="open ? close() : show()" @keydown="onKeydown">
      <span class="app-select-value">{{ selectedLabel }}</span><ChevronDown class="app-select-chevron" :size="17" aria-hidden="true" />
    </button>
    <Transition name="select-menu">
      <ul v-if="open" :id="`${id}-list`" ref="list" class="app-select-menu" role="listbox" :aria-label="label" :style="{ maxHeight: `${menuHeight}px` }">
        <li v-for="(option, index) in options" :id="`${id}-option-${index}`" :key="option.value" class="app-select-option" :class="{ 'is-active': activeIndex === index, 'is-selected': option.value === modelValue }" role="option" :aria-selected="option.value === modelValue" :data-active="activeIndex === index" @mouseenter="activeIndex = index" @mousedown.prevent @click="choose(index)">
          <span>{{ option.label }}</span><Check v-if="option.value === modelValue" :size="16" aria-hidden="true" />
        </li>
      </ul>
    </Transition>
  </div>
</template>
