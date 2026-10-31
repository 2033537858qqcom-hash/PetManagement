<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { CalendarDays, Clock3 } from 'lucide-vue-next'
import type { Appointment, Employee } from '../lib/workspace'
import { clock, labels, localDate, scheduleBlock } from '../lib/workspace'
const props = defineProps<{ appointments: Appointment[]; employees: Employee[]; date: string }>()
const emit = defineEmits<{ select: [appointment: Appointment] }>()
const hours = Array.from({ length: 13 }, (_, i) => i + 9)
const bookings = computed(() => props.appointments.filter(a => a.appointment_date === props.date && !['CANCELLED', 'NO_SHOW'].includes(a.status)).map(a => ({ a, block: scheduleBlock(a.start_time, a.end_time) })).filter(a => a.block !== null))
const now = ref(new Date())
const current = computed(() => ((now.value.getHours() * 60 + now.value.getMinutes() - 540) / 720) * 100)
const showNow = computed(() => props.date === localDate(now.value) && current.value >= 0 && current.value <= 100)
const hovered = ref<Appointment>(), focused = ref<Appointment>()
const inspected = computed(() => {
 const candidate = focused.value || hovered.value
 return bookings.value.some(b=>b.a.id===candidate?.id) ? candidate : undefined
})
let timer: ReturnType<typeof setInterval> | undefined
onMounted(() => { timer=setInterval(()=>{now.value=new Date()},60000) })
onUnmounted(() => clearInterval(timer))
function cleanupEnd(time:string) {
 const [h,m]=time.split(':').map(Number)
 const minutes=h*60+m+15
 return `${String(Math.floor(minutes/60)).padStart(2,'0')}:${String(minutes%60).padStart(2,'0')}`
}
</script>
<template>
  <div class="schedule-scroll">
    <div class="gantt" aria-label="员工与时段预约排班表">
      <div class="gantt-header"><span>洗护师 / 工位</span><div class="hour-scale"><span v-for="h in hours" :key="h" :style="{ left: `${(h - 9) * 100 / 12}%` }">{{ h }}:00</span></div></div>
      <div v-for="(employee, index) in employees" :key="employee.id" class="gantt-row">
        <div class="gantt-employee"><span class="employee-avatar" :class="`tone-${index % 3}`">{{ employee.name.slice(-2) }}</span><div><strong>{{ employee.name }}</strong><small>{{ employee.title }}</small></div></div>
        <div class="gantt-track">
          <div v-if="showNow" class="now-line" :style="{ left: `${current}%` }"><span v-if="index === 0">现在</span></div>
          <template v-for="{ a, block } in bookings.filter(b => b.a.employee_id === employee.id)" :key="a.id">
            <button v-if="block" class="schedule-booking" :class="[a.status, `tone-${index % 3}`]" :style="{ left: `${block.left}%`, width: `${block.width}%` }" :aria-label="`${a.pet_name}，${clock(a.start_time)} 至 ${clock(a.end_time)}，${a.resource_name}，查看预约详情`" :title="`${a.service_name} · ${a.resource_name} · ${labels[a.status] || a.status}`" @mouseenter="hovered=a" @mouseleave="hovered=undefined" @focus="focused=a" @blur="focused=undefined" @click="emit('select', a)"><strong>{{ a.pet_name }}</strong><small>{{ clock(a.start_time) }}–{{ clock(a.end_time) }}</small></button>
            <div v-if="block && block.bufferWidth > 0" class="cleanup-block" :style="{ left: `${block.left + block.width}%`, width: `${block.bufferWidth}%` }" :title="`清洁缓冲 ${clock(a.end_time)}–${cleanupEnd(a.end_time)}`" />
          </template>
        </div>
      </div>
    </div>
  </div>
  <div v-if="!bookings.length" class="schedule-empty"><CalendarDays :size="19" />这一天还没有预约，可以从「新建预约」开始安排。</div>
  <div class="schedule-legend"><span><i class="legend-service" />预约服务</span><span><i class="legend-cleanup" />15 分钟清洁缓冲</span><span class="legend-hint">点击预约块查看详情</span></div>
  <div class="schedule-inspector"><Clock3 :size="15"/><template v-if="inspected"><strong>{{inspected.pet_name}}</strong><span>{{inspected.service_name}}</span><span>{{inspected.resource_name}}</span><span>清洁缓冲至 {{cleanupEnd(inspected.end_time)}}</span></template><span v-else>将鼠标移到预约块，或用 Tab 聚焦，查看服务与清洁时段。</span></div>
</template>
