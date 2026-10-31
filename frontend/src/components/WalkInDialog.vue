<script setup lang="ts">
import { computed, ref } from 'vue'
import { ArrowRight, LoaderCircle, ShieldCheck } from 'lucide-vue-next'
import DialogShell from './DialogShell.vue'
import AppSelect from './AppSelect.vue'
import { api } from '../lib/api'
import type { Employee, Pet, Service } from '../lib/workspace'
import { sizeLabels } from '../lib/workspace'

const props = defineProps<{ pets: Pet[]; services: Service[]; employees: Employee[] }>()
const emit = defineEmits<{ close: []; saved: [message: string] }>()
const petId = ref(props.pets[0]?.id)
const serviceId = ref<number | undefined>()
const employeeId = ref<number | undefined>()
const busy = ref(false)
const error = ref('')
const pet = computed(() => props.pets.find(item => item.id === petId.value))
const services = computed(() => props.services.filter(item => item.enabled !== false && (!pet.value || item.species === 'ALL' || item.species === pet.value.species)))
const employees = computed(() => props.employees.filter(item => pet.value && (item.enabled !== false && item.enabled !== 0) && (item.species === pet.value.species || item.species === 'ALL') && String(item.sizes || '').includes(pet.value.size_type)))

function changePet() {
  if (!services.value.some(item => item.id === serviceId.value)) serviceId.value = undefined
  if (!employees.value.some(item => item.id === employeeId.value)) employeeId.value = undefined
}

async function submit() {
  if (!pet.value || !serviceId.value || !employeeId.value || busy.value) return
  busy.value = true
  error.value = ''
  try {
    const result = await api<{ pet_name: string; employee_name: string }>('/grooming', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ petId: petId.value, serviceId: serviceId.value, employeeId: employeeId.value }) })
    emit('saved', `${result.pet_name}的到店工单已交给${result.employee_name}。`)
  } catch (reason) {
    error.value = (reason as Error).message
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <DialogShell title="到店开单" subtitle="为已经到店的小客人建立洗护工单，开单前先看禁忌和皮肤病史。" :busy="busy" @close="emit('close')">
    <form class="booking-form" @submit.prevent="submit">
      <div v-if="error" class="inline-error" role="alert">{{ error }}</div>
      <div class="field"><span>到店宠物</span><AppSelect v-model="petId" label="到店宠物" :options="pets.map(item => ({ value: item.id, label: `${item.name} · ${item.breed} · ${sizeLabels[item.size_type]}` }))" :disabled="busy" :placeholder="pets.length ? '请选择到店宠物' : '暂无宠物档案'" @change="changePet" /></div>
      <div v-if="pet?.risks?.length" class="risk-panel" role="alert"><p v-for="risk in pet.risks" :key="risk"><ShieldCheck :size="14" /> {{ risk }}</p></div>
      <p v-else-if="pet" class="field-help"><ShieldCheck :size="14" />未发现过敏或皮肤病史，可按常规流程开单。</p>
      <div class="field"><span>洗护项目</span><AppSelect v-model="serviceId" label="洗护项目" :options="services.map(item => ({ value: item.id, label: `${item.name} · ${item.duration} 分钟` }))" :disabled="busy" :placeholder="services.length ? '请选择项目' : '暂无适用项目'" /></div>
      <div class="field"><span>负责洗护师</span><AppSelect v-model="employeeId" label="负责洗护师" :options="employees.map(item => ({ value: item.id, label: `${item.name} · ${item.title}` }))" :disabled="busy" :placeholder="employees.length ? '请选择洗护师' : '暂无可用洗护师'" /></div>
      <p v-if="pet && !employees.length" class="inline-error">当前没有可负责这类宠物的洗护师。</p>
      <button class="button primary full" type="submit" :disabled="busy || !serviceId || !employeeId"><LoaderCircle v-if="busy" class="spin" :size="17" />{{ busy ? '正在开单…' : '生成到店工单' }}<ArrowRight v-if="!busy" :size="17" /></button>
    </form>
  </DialogShell>
</template>
