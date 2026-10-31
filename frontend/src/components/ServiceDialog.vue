<script setup lang="ts">
import { computed, ref } from 'vue'
import { ArrowRight, LoaderCircle } from 'lucide-vue-next'
import DialogShell from './DialogShell.vue'
import AppSelect from './AppSelect.vue'
import { api } from '../lib/api'
import { estimatePrice, type Service } from '../lib/workspace'
import { serviceCategories, serviceDurations, setupSpeciesOptions, sizes } from '../lib/catalog'

const CUSTOM = '__custom__'
const props = defineProps<{ service: Service | null }>()
const emit = defineEmits<{ close: []; saved: [message: string] }>()
const name = ref(props.service?.name || '')
const knownCategory = serviceCategories.includes(props.service?.category || '')
const category = ref(knownCategory ? (props.service?.category || serviceCategories[0]) : (props.service?.category ? CUSTOM : serviceCategories[0]))
const customCategory = ref(knownCategory ? '' : (props.service?.category || ''))
const price = ref(props.service ? String(props.service.price) : '98')
const knownDuration = serviceDurations.includes(props.service?.duration || 60)
const duration = ref(knownDuration ? (props.service?.duration || 60) : CUSTOM)
const customDuration = ref(knownDuration ? '' : String(props.service?.duration || 60))
const knownSpecies = [...setupSpeciesOptions.map(item => item.value)].includes(props.service?.species || 'ALL')
const species = ref(props.service ? (knownSpecies ? props.service.species : CUSTOM) : 'ALL')
const customSpecies = ref(knownSpecies ? '' : (props.service?.species || ''))
const sizeInputs = ref<Record<string, { enabled: boolean; price: string; duration: string }>>(
  Object.fromEntries(sizes.map(s => {
    const existing = props.service?.size_rules?.find(r => r.size_type === s.value)
    return [s.value, {
      enabled: !!existing,
      price: existing ? String(existing.price) : String(estimatePrice(props.service?.price || 98, s.value)),
      duration: existing ? String(existing.duration) : String(props.service?.duration || 60),
    }]
  }))
)
const busy = ref(false)
const error = ref('')
const categoryChoices = computed(() => [...serviceCategories.map(item => ({ value: item, label: item })), { value: CUSTOM, label: '其他（自行填写）' }])
const speciesChoices = computed(() => {
  const options = setupSpeciesOptions.map(item => ({ value: item.value, label: item.label }))
  if (props.service?.species && !options.some(item => item.value === props.service?.species)) options.push({ value: props.service.species, label: props.service.species })
  options.push({ value: CUSTOM, label: '其他（自行填写）' })
  return options
})
const durationChoices = computed(() => [...serviceDurations.map(item => ({ value: item, label: `${item} 分钟` })), { value: CUSTOM, label: '其他时长' }])
function picked(select: string | number, custom: string) {
  return String(select === CUSTOM ? custom : select).trim()
}

async function submit() {
  if (busy.value) return
  busy.value = true
  error.value = ''
  const durationValue = Number(picked(duration.value, customDuration.value))
  try {
    let targetId = props.service?.id
    const body = { name: name.value.trim(), category: picked(category.value, customCategory.value), price: Number(price.value), duration: durationValue, species: picked(species.value, customSpecies.value), enabled: props.service?.enabled !== false }
    if (props.service) {
      await api(`/services/${props.service.id}`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) })
    } else {
      const created = await api<Service>('/services', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) })
      targetId = created.id
    }
    const items: { sizeType: string; price: number; duration: number }[] = sizes
      .filter(s => sizeInputs.value[s.value]?.enabled)
      .map(s => ({
        sizeType: s.value,
        price: Number(sizeInputs.value[s.value].price),
        duration: Number(sizeInputs.value[s.value].duration),
      }))
    if (targetId && (items.length > 0 || (props.service?.size_rules?.length ?? 0) > 0)) {
      await api(`/services/${targetId}/prices`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ items }) })
    }
    emit('saved', props.service ? '价格已更新。' : '服务项目已添加。')
  } catch (reason) {
    error.value = (reason as Error).message
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <DialogShell :title="service ? '调整价格' : '新增服务项目'" :subtitle="service ? service.name : '填写项目、价格和适用宠物，保存后即可预约。'" :busy="busy" @close="emit('close')">
    <form class="booking-form" @submit.prevent="submit">
      <div v-if="error" class="inline-error" role="alert">{{ error }}</div>
      <label class="field">项目名称<input v-model="name" maxlength="20" required :autofocus="!service" :disabled="busy" placeholder="例如幼犬洗护" /></label>
      <div class="form-columns">
        <div class="field"><span>分类</span><AppSelect v-model="category" label="分类" :options="categoryChoices" :disabled="busy" /></div>
        <div class="field"><span>适用宠物</span><AppSelect v-model="species" label="适用宠物" :options="speciesChoices" :disabled="busy" /></div>
      </div>
      <label v-if="category === CUSTOM" class="field">自定义分类<input v-model="customCategory" maxlength="20" required :disabled="busy" placeholder="例如 兔子护理" /></label>
      <label v-if="species === CUSTOM" class="field">自定义适用种类<input v-model="customSpecies" maxlength="20" required :disabled="busy" placeholder="例如 兔子、龙猫" /></label>
      <div class="form-columns">
        <label class="field">价格（元）<input v-model="price" type="number" min="1" max="2000" step="0.01" required :autofocus="!!service" :disabled="busy" /></label>
        <div class="field"><span>护理时长</span><AppSelect v-model="duration" label="护理时长" :options="durationChoices" :disabled="busy" /></div>
      </div>
      <label v-if="duration === CUSTOM" class="field">自定义时长（分钟）<input v-model="customDuration" type="number" min="15" max="705" step="5" required :disabled="busy" placeholder="15 到 705，且为 5 的倍数" /></label>
      <fieldset :disabled="busy">
        <legend>体型差异定价与时长（可选）</legend>
        <div v-for="s in sizes" :key="s.value" class="form-columns" style="align-items:center;margin-bottom:0.5rem">
          <label style="display:flex;align-items:center;gap:0.4rem"><input v-model="sizeInputs[s.value].enabled" type="checkbox" /> {{ s.label }}</label>
          <template v-if="sizeInputs[s.value].enabled">
            <label class="field">{{ s.label }}价格（元）<input v-model="sizeInputs[s.value].price" type="number" min="1" max="2000" step="0.01" required /></label>
            <label class="field">{{ s.label }}时长（分钟）<input v-model="sizeInputs[s.value].duration" type="number" min="15" max="705" step="5" required /></label>
          </template>
        </div>
      </fieldset>
      <button class="button primary full" type="submit" :disabled="busy"><LoaderCircle v-if="busy" class="spin" :size="17" />{{ busy ? '正在保存…' : '保存' }}<ArrowRight v-if="!busy" :size="17" /></button>
    </form>
  </DialogShell>
</template>
