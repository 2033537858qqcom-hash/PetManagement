<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { ArrowRight, Check, Clock3, LoaderCircle, ShieldCheck } from 'lucide-vue-next'
import DialogShell from './DialogShell.vue'
import AppSelect from './AppSelect.vue'
import AppDatePicker from './AppDatePicker.vue'
import AppTimePicker from './AppTimePicker.vue'
import PetPortrait from './PetPortrait.vue'
import { api } from '../lib/api'
import type { Customer, Employee, Pet, Service } from '../lib/workspace'
import { bookingTiming, estimatePrice, localDate, money, serviceDuration, shiftedDate, sizeLabels, suggestedTime } from '../lib/workspace'
const props = defineProps<{ pets: Pet[]; services: Service[]; customers: Customer[]; employees?: Employee[]; customerId: number; owner: boolean; initialPetId?: number; initialDate?: string; initialServiceId?: number }>()
const emit = defineEmits<{ close: []; booked: [message: string] }>()
const initial = props.pets.find(p => p.id === props.initialPetId)
const customerId = ref(initial?.customer_id || props.customerId)
const petId = ref(initial?.id || props.pets.find(p => p.customer_id === customerId.value)?.id)
const serviceId = ref<number | undefined>(props.initialServiceId)
const preferredEmployeeId = ref<number>(0)
const date = ref(props.initialDate && props.initialDate >= localDate() ? props.initialDate : shiftedDate(localDate(), 1))
const time = ref(suggestedTime(date.value))
const busy = ref(false), error = ref('')
const errorPanel = ref<HTMLElement>()
const familyPets = computed(() => props.pets.filter(p => p.customer_id === customerId.value))
const pet = computed(() => familyPets.value.find(p => p.id === petId.value))
const compatibleServices = computed(() => props.services.filter(s => s.enabled !== false && (s.species === 'ALL' || s.species === pet.value?.species)))
const compatibleEmployees = computed(() => (props.employees || []).filter(e => e.enabled !== false && e.enabled !== 0 && (!pet.value || (((pet.value.species === 'DOG' || pet.value.species === 'CAT') ? e.species === pet.value.species : true) && String(e.sizes || '').includes(pet.value.size_type)))))
const selectedService = computed(() => compatibleServices.value.find(s => s.id === serviceId.value))
const price = computed(() => selectedService.value && pet.value ? estimatePrice(selectedService.value.price, pet.value.size_type, selectedService.value.size_rules) : null)
const duration = computed(() => selectedService.value ? serviceDuration(selectedService.value, pet.value?.size_type) : null)
const timing = computed(() => selectedService.value && duration.value != null ? bookingTiming(date.value, time.value, duration.value) : null)
const sizeSurcharge = computed(() => {
  if (!pet.value || !selectedService.value) return 0
  if (selectedService.value.size_rules?.some(r => r.size_type === pet.value?.size_type)) return 0
  return ['LARGE', 'GIANT'].includes(pet.value.size_type) ? 50 : 0
})
function selectPet(id?: number) {
  if (busy.value) return
  petId.value = id
  if (!compatibleServices.value.some(s => s.id === serviceId.value)) serviceId.value = undefined
  if (preferredEmployeeId.value && !compatibleEmployees.value.some(e => e.id === preferredEmployeeId.value)) preferredEmployeeId.value = 0
}
function changeCustomer() { selectPet(familyPets.value[0]?.id) }
watch([customerId, petId, serviceId, preferredEmployeeId, date, time], () => { error.value = '' })
async function showError(message: string) {
  error.value = message
  await nextTick()
  errorPanel.value?.focus({ preventScroll: true })
  errorPanel.value?.scrollIntoView({ block: 'nearest', behavior: 'smooth' })
}
async function submit() {
  if (!pet.value || !selectedService.value || duration.value == null || busy.value) return
  const currentTiming = bookingTiming(date.value, time.value, duration.value)
  if (currentTiming.error) { await showError(currentTiming.error); return }
  busy.value = true; error.value = ''
  try {
    const payload: Record<string, unknown> = { customerId: customerId.value, petId: petId.value, serviceId: serviceId.value, date: date.value, startTime: time.value }
    if (preferredEmployeeId.value > 0) payload.preferredEmployeeId = preferredEmployeeId.value
    const result = await api<{ employee: string; resource: string; price: number; endTime: string }>('/appointments', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) })
    emit('booked', `预约成功 · ${result.employee} · ${result.resource} · 预计 ¥${money(result.price)}`)
  } catch (e) { await showError((e as Error).message) } finally { busy.value = false }
}
</script>
<template>
  <DialogShell title="预约一次精心护理" subtitle="选择爱宠与项目，门店将自动安排洗护师和工位。" :busy="busy" @close="emit('close')">
    <form class="booking-form" @submit.prevent="submit">
      <div v-if="error" ref="errorPanel" class="inline-error" role="alert" tabindex="-1">{{ error }}</div>
      <div v-if="!owner" class="field"><span>选择宠主</span><AppSelect v-model="customerId" label="选择宠主" :options="customers.map(c => ({ value: c.id, label: `${c.name} · ${c.phone}` }))" :disabled="busy" @change="changeCustomer" /></div>
      <fieldset :disabled="busy"><legend><span class="step-number">1</span>这次照顾谁？</legend><div class="pet-options"><button v-for="p in familyPets" :key="p.id" type="button" class="pet-option" :class="{ selected: petId === p.id }" :aria-pressed="petId === p.id" @click="selectPet(p.id)"><PetPortrait :id="p.id" :name="p.name" /><span><strong>{{ p.name }}</strong><small>{{ p.breed }} · {{ sizeLabels[p.size_type] }}</small></span><Check v-if="petId === p.id" :size="17" /></button></div><p v-if="!familyPets.length" class="empty-note">这位宠主还没有宠物档案。</p><div v-if="pet?.risks?.length" class="risk-panel"><p v-for="risk in pet.risks" :key="risk">{{ risk }}</p></div><p v-else-if="pet" class="booking-pet-note"><ShieldCheck :size="14"/><span>{{pet.name}}的护理提醒：{{pet.health_note}}</span></p></fieldset>
      <fieldset :disabled="busy"><legend><span class="step-number">2</span>选择护理项目</legend><div class="service-options"><label v-for="s in compatibleServices" :key="s.id" class="service-option" :class="{ selected: serviceId === s.id }"><input v-model="serviceId" type="radio" name="service" :value="s.id" required /><span><strong>{{ s.name }}</strong><small><Clock3 :size="12" />约 {{ serviceDuration(s, pet?.size_type || 'SMALL') }} 分钟</small></span><b>¥{{ money(estimatePrice(s.price, pet?.size_type || 'SMALL', s.size_rules)) }}</b></label></div></fieldset>
      <fieldset :disabled="busy"><legend><span class="step-number">3</span>安排到店时间</legend><div class="form-columns"><div class="field"><span>预约日期</span><AppDatePicker v-model="date" label="预约日期" :min="localDate()" :disabled="busy" :invalid="timing?.field==='date'" :described-by="timing?.field==='date' ? 'booking-timing-error' : undefined" /></div><div class="field"><span>开始时间</span><AppTimePicker v-model="time" label="开始时间" min="09:00" :max="timing?.latestStart || '20:00'" :disabled="busy" :invalid="timing?.field==='time'" :described-by="timing?.field==='time' ? 'booking-timing-error' : undefined" /></div></div><div v-if="compatibleEmployees.length" class="field"><span>指定洗护师（可选）</span><AppSelect v-model="preferredEmployeeId" label="指定洗护师" :options="[{ value: 0, label: '系统自动分配' }, ...compatibleEmployees.map(e => ({ value: e.id, label: `${e.name} · ${e.title}` }))]" :disabled="busy" /></div><p v-if="timing?.error" id="booking-timing-error" class="field-error" role="status">{{timing.error}}</p><div v-else-if="timing" class="booking-time-preview"><span><Clock3 :size="15"/>护理 {{time}}–{{timing.endTime}}</span><span>清洁缓冲至 <strong>{{timing.cleanupEndTime}}</strong></span></div><p class="field-help"><ShieldCheck :size="15" />提交时检查洗护师与工位占用。</p></fieldset>
      <div class="booking-footer"><div aria-live="polite"><small>本次预估费用</small><strong>{{ price === null ? '请选择项目' : `¥${money(price)}` }}</strong><span v-if="sizeSurcharge && selectedService" class="price-explanation">含大型宠体型调整 ¥{{money(sizeSurcharge)}}</span></div><button class="button primary" type="submit" :disabled="!selectedService || !pet || !!timing?.error || busy"><LoaderCircle v-if="busy" class="spin" :size="17" /><span>{{busy ? '正在确认…' : '确认预约'}}</span><ArrowRight v-if="!busy" :size="17" /></button></div>
    </form>
  </DialogShell>
</template>
