<script setup lang="ts">
import { computed, ref } from 'vue'
import { ArrowRight, LoaderCircle } from 'lucide-vue-next'
import DialogShell from './DialogShell.vue'
import AppSelect from './AppSelect.vue'
import { api } from '../lib/api'
import type { Grooming } from '../lib/workspace'
import { coatScores, earScores, healthScore, skinScores } from '../lib/catalog'

const props = defineProps<{ grooming: Grooming }>()
const emit = defineEmits<{ close: []; saved: [] }>()
const weight = ref('')
const skin = ref('CLEAR')
const coat = ref('SMOOTH')
const ear = ref('CLEAN')
const groomerNote = ref('')
const busy = ref(false)
const error = ref('')
const score = computed(() => healthScore(skin.value, coat.value, ear.value))

async function submit() {
  if (busy.value || score.value == null || !weight.value || Number(weight.value) <= 0 || Number(weight.value) > 999.99) return
  busy.value = true
  error.value = ''
  try {
    await api(`/grooming/${props.grooming.id}/assessment`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ weight: Number(weight.value), skin: skin.value, coat: coat.value, ear: ear.value, groomerNote: groomerNote.value.trim() || undefined }) })
    emit('saved')
  } catch (reason) {
    error.value = (reason as Error).message
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <DialogShell title="记录健康评分" :subtitle="`${grooming.pet_name} · ${grooming.service_name}`" :busy="busy" @close="emit('close')">
    <form class="booking-form" @submit.prevent="submit">
      <div v-if="error" class="inline-error" role="alert">{{ error }}</div>
      <label class="field">本次体重（kg）<input v-model="weight" type="number" min="0.01" max="999.99" step="0.01" required :disabled="busy" placeholder="请输入实测体重" /></label>
      <div class="field"><span>皮肤</span><AppSelect v-model="skin" label="皮肤" :options="skinScores" :disabled="busy" /></div>
      <div class="field"><span>毛发</span><AppSelect v-model="coat" label="毛发" :options="coatScores" :disabled="busy" /></div>
      <div class="field"><span>耳道</span><AppSelect v-model="ear" label="耳道" :options="earScores" :disabled="busy" /></div>
      <label class="field">洗护师护理寄语（选填）<input v-model="groomerNote" maxlength="300" :disabled="busy" placeholder="例如：毛发柔顺，耳道已清洁，建议两周后复查" /></label>
      <div class="score-preview" aria-live="polite"><small>系统计算的健康评分</small><strong>{{ score }}</strong><span>皮肤 40% · 毛发 35% · 耳道 25%</span></div>
      <button class="button primary full" type="submit" :disabled="busy || !weight || Number(weight) <= 0 || Number(weight) > 999.99"><LoaderCircle v-if="busy" class="spin" :size="17" />{{ busy ? '正在记录…' : '完成洗护并保存评分' }}<ArrowRight v-if="!busy" :size="17" /></button>
    </form>
  </DialogShell>
</template>
