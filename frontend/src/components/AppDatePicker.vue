<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, useId, watch } from 'vue'
import { CalendarDays, ChevronDown, ChevronLeft, ChevronRight } from 'lucide-vue-next'
import { localDate } from '../lib/workspace'

const props = withDefaults(defineProps<{ modelValue: string; label: string; min?: string; max?: string; disabled?: boolean; invalid?: boolean; describedBy?: string; displayLabel?: string; inline?: boolean }>(), { disabled: false, invalid: false, inline: false })
const emit = defineEmits<{ 'update:modelValue': [value: string]; change: [value: string] }>()
const root = ref<HTMLElement>(), trigger = ref<HTMLButtonElement>(), panel = ref<HTMLElement>()
const open = ref(false), upwards = ref(false), maxHeight = ref(360)
const today = localDate()
const parse = (value: string) => {
  const d = new Date(`${value}T12:00:00`)
  return /^\d{4}-\d{2}-\d{2}$/.test(value) && !Number.isNaN(d.getTime()) && localDate(d) === value ? d : new Date(`${today}T12:00:00`)
}
const firstAllowed = () => props.min && today < props.min ? props.min : props.max && today > props.max ? props.max : today
const initial = props.modelValue || firstAllowed()
const focusedDate = ref(localDate(parse(initial)))
const month = ref(new Date(parse(initial).getFullYear(), parse(initial).getMonth(), 1, 12))
const id = `calendar-${useId()}`
const weekdays = ['一', '二', '三', '四', '五', '六', '日']
const monthLabel = computed(() => new Intl.DateTimeFormat('zh-CN', { year: 'numeric', month: 'long' }).format(month.value))
const labelText = computed(() => props.displayLabel || (props.modelValue ? props.modelValue.replace(/-/g, '/') : '选择日期'))
const days = computed(() => {
  const first = new Date(month.value.getFullYear(), month.value.getMonth(), 1, 12)
  const mondayOffset = (first.getDay() + 6) % 7
  first.setDate(first.getDate() - mondayOffset)
  return Array.from({ length: 42 }, (_, i) => {
    const date = new Date(first); date.setDate(first.getDate() + i)
    const value = localDate(date)
    return { value, day: date.getDate(), other: date.getMonth() !== month.value.getMonth(), disabled: !allowed(value) }
  })
})
const rows = computed(() => Array.from({ length: 6 }, (_, i) => days.value.slice(i * 7, i * 7 + 7)))
function allowed(value: string) { return (!props.min || value >= props.min) && (!props.max || value <= props.max) }
function canMoveMonth(delta: number) {
  const first = new Date(month.value.getFullYear(), month.value.getMonth() + delta, 1, 12)
  const last = new Date(first.getFullYear(), first.getMonth() + 1, 0, 12)
  return (!props.min || localDate(last) >= props.min) && (!props.max || localDate(first) <= props.max)
}
function changeMonth(delta: number) {
  if (!canMoveMonth(delta)) return
  const target = new Date(month.value.getFullYear(), month.value.getMonth() + delta, 1, 12)
  const day = Math.min(parse(focusedDate.value).getDate(), new Date(target.getFullYear(), target.getMonth() + 1, 0).getDate())
  target.setDate(day)
  const value = localDate(target)
  focusedDate.value = props.min && value < props.min ? props.min : props.max && value > props.max ? props.max : value
  month.value = new Date(target.getFullYear(), target.getMonth(), 1, 12)
}
function place() {
  if (!root.value) return
  const rect = root.value.getBoundingClientRect()
  const parent = root.value.closest('.dialog-panel')?.getBoundingClientRect()
  const upper = Math.max(8, parent?.top ?? 8), lower = Math.min(window.innerHeight - 8, parent?.bottom ?? window.innerHeight - 8)
  const below = lower - rect.bottom - 8, above = rect.top - upper - 8
  upwards.value = below < 355 && above > below
  maxHeight.value = Math.max(140, Math.min(390, upwards.value ? above : below))
}
async function show() {
  if (props.disabled) return
  const value = props.modelValue && allowed(props.modelValue) ? props.modelValue : firstAllowed()
  const date = parse(value)
  focusedDate.value = localDate(date)
  month.value = new Date(date.getFullYear(), date.getMonth(), 1, 12)
  place(); open.value = true
  await nextTick()
  panel.value?.querySelector<HTMLElement>(`[data-date="${focusedDate.value}"]`)?.focus({ preventScroll: true })
}
function close(restore = false) { open.value = false; if (restore) trigger.value?.focus({ preventScroll: true }) }
function choose(value: string) {
  if (!allowed(value) || props.disabled) return
  if (value !== props.modelValue) { emit('update:modelValue', value); emit('change', value) }
  close(true)
}
async function focusDate(value: string) {
  if (!allowed(value)) return
  const date = parse(value)
  focusedDate.value = localDate(date)
  month.value = new Date(date.getFullYear(), date.getMonth(), 1, 12)
  await nextTick()
  panel.value?.querySelector<HTMLElement>(`[data-date="${focusedDate.value}"]`)?.focus({ preventScroll: true })
}
function keydown(event: KeyboardEvent) {
  if (event.key === 'Escape' && open.value) { event.preventDefault(); event.stopPropagation(); close(true); return }
  const offsets: Record<string, number> = { ArrowLeft: -1, ArrowRight: 1, ArrowUp: -7, ArrowDown: 7 }
  if (event.key in offsets) {
    event.preventDefault(); event.stopPropagation()
    const date = parse(focusedDate.value); date.setDate(date.getDate() + offsets[event.key]); focusDate(localDate(date)); return
  }
  if (event.key === 'Home' || event.key === 'End') {
    event.preventDefault(); event.stopPropagation()
    const date = parse(focusedDate.value), offset = (date.getDay() + 6) % 7
    date.setDate(date.getDate() + (event.key === 'Home' ? -offset : 6 - offset)); focusDate(localDate(date)); return
  }
  if (event.key === 'PageUp' || event.key === 'PageDown') {
    event.preventDefault(); event.stopPropagation()
    const date = parse(focusedDate.value), wantedDay = date.getDate(), delta = (event.key === 'PageUp' ? -1 : 1) * (event.shiftKey ? 12 : 1)
    date.setDate(1); date.setMonth(date.getMonth() + delta)
    date.setDate(Math.min(wantedDay, new Date(date.getFullYear(), date.getMonth() + 1, 0).getDate()))
    focusDate(localDate(date))
  }
}
function outside(event: PointerEvent) { if (open.value && !root.value?.contains(event.target as Node)) close() }
function focusout(event: FocusEvent) { if (!root.value?.contains(event.relatedTarget as Node | null)) close() }
watch(() => props.disabled, disabled => { if (disabled) close() })
onMounted(() => { document.addEventListener('pointerdown', outside); window.addEventListener('resize', place) })
onUnmounted(() => { document.removeEventListener('pointerdown', outside); window.removeEventListener('resize', place) })
</script>

<template>
  <div ref="root" class="app-date" :class="{ 'is-open': open, 'opens-up': upwards, 'is-inline': inline }" @focusout="focusout" @keydown="keydown">
    <button ref="trigger" type="button" class="app-date-trigger" :aria-label="label" aria-haspopup="dialog" :aria-expanded="open" :aria-controls="open ? id : undefined" :aria-invalid="invalid || undefined" :aria-describedby="describedBy" :disabled="disabled" @click="open ? close() : show()">
      <CalendarDays :size="17" aria-hidden="true"/><span>{{labelText}}</span><ChevronDown :size="16" class="app-date-chevron" aria-hidden="true"/>
    </button>
    <Transition name="select-menu">
      <section v-if="open" :id="id" ref="panel" class="calendar-menu" role="dialog" :aria-label="`${label}日历`" :style="{maxHeight:`${maxHeight}px`}">
        <header class="calendar-header"><div><span>选择日期</span><strong aria-live="polite">{{monthLabel}}</strong></div><div class="calendar-navigation"><button type="button" aria-label="上个月" :disabled="!canMoveMonth(-1)" @click="changeMonth(-1)"><ChevronLeft :size="17"/></button><button type="button" aria-label="下个月" :disabled="!canMoveMonth(1)" @click="changeMonth(1)"><ChevronRight :size="17"/></button></div></header>
        <div class="calendar-grid" role="grid" :aria-label="monthLabel"><div class="calendar-week" role="row"><span v-for="day in weekdays" :key="day" role="columnheader">{{day}}</span></div><div v-for="(row,index) in rows" :key="index" class="calendar-week" role="row"><button v-for="day in row" :key="day.value" type="button" class="calendar-day" :class="{'is-other':day.other,'is-today':day.value===today,'is-selected':day.value===modelValue}" role="gridcell" :data-date="day.value" :aria-label="day.value" :aria-selected="day.value===modelValue" :disabled="day.disabled" :tabindex="day.value===focusedDate ? 0 : -1" @focus="focusedDate=day.value" @click="choose(day.value)">{{day.day}}</button></div></div>
        <footer class="calendar-footer"><span>周一为一周的开始</span><button type="button" :disabled="!allowed(today)" @click="choose(today)">今天</button></footer>
      </section>
    </Transition>
  </div>
</template>
