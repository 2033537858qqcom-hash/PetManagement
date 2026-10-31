<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ArrowRight, LoaderCircle, PawPrint } from 'lucide-vue-next'
import DialogShell from './DialogShell.vue'
import AppSelect from './AppSelect.vue'
import { api } from '../lib/api'
import type { Customer, Pet } from '../lib/workspace'
import { ages, allergies, breedsFor, coats, groomingHistories, sizes, skinHistories, speciesOptions } from '../lib/catalog'

const CUSTOM = '__custom__'
const props = defineProps<{ pet: Pet | null; customers?: Customer[]; owner?: boolean; customerId?: number }>()
const emit = defineEmits<{ close: []; saved: [message: string] }>()
const selectedCustomerId = ref<number | undefined>(props.pet?.customer_id || props.customerId || props.customers?.[0]?.id)
const name = ref(props.pet?.name || '')
const knownSpecies = speciesOptions.some(item => item.value === props.pet?.species)
const species = ref(props.pet ? (knownSpecies ? props.pet.species : CUSTOM) : 'DOG')
const customSpecies = ref(knownSpecies ? '' : (props.pet?.species || ''))
const breedList = computed(() => breedsFor(species.value))
const breed = ref((() => {
  const list = breedsFor(props.pet?.species === 'CAT' ? 'CAT' : props.pet?.species === 'DOG' ? 'DOG' : props.pet?.species || 'DOG')
  if (props.pet?.breed && list.includes(props.pet.breed)) return props.pet.breed
  if (props.pet?.breed) return CUSTOM
  return list[0] || CUSTOM
})())
const customBreed = ref(breed.value === CUSTOM ? (props.pet?.breed || '') : '')
const ageBand = ref(ages.some(item => item.value === props.pet?.age_band) ? props.pet?.age_band || 'YOUNG' : (props.pet?.age_band ? CUSTOM : 'YOUNG'))
const customAge = ref(ageBand.value === CUSTOM ? (props.pet?.age_band || '') : '')
const sizeType = ref(props.pet?.size_type || 'SMALL')
const coat = ref(coats.includes(props.pet?.coat || '') ? props.pet?.coat || coats[0] : (props.pet?.coat ? CUSTOM : coats[0]))
const customCoat = ref(coat.value === CUSTOM ? (props.pet?.coat || '') : '')
const allergy = ref(allergies.some(item => item.value === props.pet?.allergy) ? props.pet?.allergy || 'NONE' : (props.pet?.allergy ? CUSTOM : 'NONE'))
const customAllergy = ref(allergy.value === CUSTOM ? (props.pet?.allergy || '') : '')
const skinHistory = ref(skinHistories.some(item => item.value === props.pet?.skin_history) ? props.pet?.skin_history || 'HEALTHY' : (props.pet?.skin_history ? CUSTOM : 'HEALTHY'))
const customSkin = ref(skinHistory.value === CUSTOM ? (props.pet?.skin_history || '') : '')
const groomingHistory = ref(groomingHistories.some(item => item.value === props.pet?.grooming_history) ? props.pet?.grooming_history || 'NONE' : (props.pet?.grooming_history ? CUSTOM : 'NONE'))
const customGrooming = ref(groomingHistory.value === CUSTOM ? (props.pet?.grooming_history || '') : '')
const weight = ref(String(props.pet ? Number(props.pet.weight) : 4.5))
const busy = ref(false)
const error = ref('')
const photoFile = ref<File | null>(null)
const photoPreview = ref(props.pet?.photo_url || '')
const speciesChoices = computed(() => {
  const options = speciesOptions.map(item => ({ value: item.value, label: item.label }))
  if (props.pet?.species && !options.some(item => item.value === props.pet?.species) && species.value === CUSTOM) {
    options.push({ value: props.pet.species, label: props.pet.species })
  }
  options.push({ value: CUSTOM, label: '其他（自行填写）' })
  return options
})
const breedChoices = computed(() => [...breedList.value.map(item => ({ value: item, label: item })), { value: CUSTOM, label: '其他（自行填写）' }])
const ageChoices = computed(() => [...ages, { value: CUSTOM, label: '其他（自行填写）' }])
const coatChoices = computed(() => [...coats.map(item => ({ value: item, label: item })), { value: CUSTOM, label: '其他（自行填写）' }])
const allergyChoices = computed(() => [...allergies, { value: CUSTOM, label: '其他（自行填写）' }])
const skinChoices = computed(() => [...skinHistories, { value: CUSTOM, label: '其他（自行填写）' }])
const groomingChoices = computed(() => [...groomingHistories, { value: CUSTOM, label: '其他（自行填写）' }])

function changeSpecies() {
  if (!breedList.value.length) {
    breed.value = CUSTOM
    customBreed.value = ''
    return
  }
  if (!breedList.value.includes(breed.value)) {
    breed.value = breedList.value[0]
    customBreed.value = ''
  }
}
watch(species, changeSpecies)
function picked(select: string, custom: string) {
  return (select === CUSTOM ? custom : select).trim()
}
function onPhoto(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return
  photoFile.value = file
  photoPreview.value = URL.createObjectURL(file)
}

async function submit() {
  if (busy.value) return
  busy.value = true
  error.value = ''
  const body = {
    customerId: selectedCustomerId.value,
    name: name.value.trim(),
    species: picked(species.value, customSpecies.value),
    breed: picked(breed.value, customBreed.value),
    ageBand: picked(ageBand.value || 'YOUNG', customAge.value),
    sizeType: sizeType.value,
    weight: Number(weight.value),
    coat: picked(coat.value || coats[0], customCoat.value),
    allergy: picked(allergy.value || 'NONE', customAllergy.value),
    skinHistory: picked(skinHistory.value || 'HEALTHY', customSkin.value),
    groomingHistory: picked(groomingHistory.value || 'NONE', customGrooming.value),
  }
  try {
    const saved = await api<Pet>(props.pet ? `/pets/${props.pet.id}` : '/pets', { method: props.pet ? 'PUT' : 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) })
    if (photoFile.value && saved?.id) {
      const data = new FormData()
      data.append('file', photoFile.value)
      await api(`/pets/${saved.id}/photo`, { method: 'PUT', body: data })
    }
    emit('saved', props.pet ? `${body.name}的档案已更新。` : `${body.name}的档案已建立。`)
  } catch (reason) {
    error.value = (reason as Error).message
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <DialogShell :title="pet ? '编辑爱宠档案' : '添加爱宠'" subtitle="可从常用词条里选，也可以自己填写。每只宠物都能上传专属照片。" :busy="busy" @close="emit('close')">
    <form class="booking-form" @submit.prevent="submit">
      <div v-if="error" class="inline-error" role="alert">{{ error }}</div>
      <div v-if="owner === false && customers?.length" class="field"><span>所属宠主</span><AppSelect v-model="selectedCustomerId" label="所属宠主" :options="customers.map(c => ({ value: c.id, label: `${c.name}${c.phone ? ` · ${c.phone}` : ''}` }))" :disabled="busy" /></div>
      <label class="field pet-photo-field">爱宠照片
        <span class="pet-photo-picker">
          <img v-if="photoPreview" :src="photoPreview" alt="" />
          <PawPrint v-else :size="28" />
          <input type="file" accept="image/jpeg,image/png" :disabled="busy" @change="onPhoto" />
          <em>{{ photoPreview ? '点击更换照片' : '点击上传照片' }}</em>
        </span>
      </label>
      <label class="field">宠物昵称<input v-model="name" maxlength="12" required autofocus :disabled="busy" placeholder="请输入宠物昵称" /></label>
      <div class="form-columns">
        <div class="field"><span>种类</span><AppSelect v-model="species" label="种类" :options="speciesChoices" :disabled="busy" @change="changeSpecies" /></div>
        <label v-if="species === CUSTOM" class="field">自定义种类<input v-model="customSpecies" maxlength="20" required :disabled="busy" placeholder="例如 龙猫、刺猬" /></label>
        <div v-else-if="breedList.length" class="field"><span>品种</span><AppSelect v-model="breed" label="品种" :options="breedChoices" :disabled="busy" /></div>
      </div>
      <label v-if="species === CUSTOM || breed === CUSTOM || !breedList.length" class="field">自定义品种<input v-model="customBreed" maxlength="30" required :disabled="busy" placeholder="请填写品种" /></label>
      <div class="form-columns">
        <div class="field"><span>年龄</span><AppSelect v-model="ageBand" label="年龄" :options="ageChoices" :disabled="busy" /></div>
        <div class="field"><span>体型</span><AppSelect v-model="sizeType" label="体型" :options="sizes" :disabled="busy" /></div>
      </div>
      <label v-if="ageBand === CUSTOM" class="field">自定义年龄<input v-model="customAge" maxlength="20" required :disabled="busy" placeholder="例如 8个月" /></label>
      <div class="form-columns">
        <label class="field">体重（kg）<input v-model="weight" type="number" min="0.1" max="99.99" step="0.01" required :disabled="busy" placeholder="例如 4.5" /></label>
        <div class="field"><span>毛发</span><AppSelect v-model="coat" label="毛发" :options="coatChoices" :disabled="busy" /></div>
      </div>
      <label v-if="coat === CUSTOM" class="field">自定义毛发<input v-model="customCoat" maxlength="20" required :disabled="busy" placeholder="例如 无毛" /></label>
      <div class="field"><span>过敏史</span><AppSelect v-model="allergy" label="过敏史" :options="allergyChoices" :disabled="busy" /></div>
      <label v-if="allergy === CUSTOM" class="field">自定义过敏史<input v-model="customAllergy" maxlength="30" required :disabled="busy" placeholder="请填写过敏情况" /></label>
      <div class="field"><span>皮肤病史</span><AppSelect v-model="skinHistory" label="皮肤病史" :options="skinChoices" :disabled="busy" /></div>
      <label v-if="skinHistory === CUSTOM" class="field">自定义皮肤病史<input v-model="customSkin" maxlength="30" required :disabled="busy" placeholder="请填写皮肤情况" /></label>
      <div class="field"><span>既往洗护记录</span><AppSelect v-model="groomingHistory" label="既往洗护记录" :options="groomingChoices" :disabled="busy" /></div>
      <label v-if="groomingHistory === CUSTOM" class="field">自定义洗护记录<input v-model="customGrooming" maxlength="30" required :disabled="busy" placeholder="请填写既往洗护" /></label>
      <p class="field-help">常用词条会在洗护前变成风险提示；自定义内容也会一并展示给洗护师。</p>
      <button class="button primary full" type="submit" :disabled="busy"><LoaderCircle v-if="busy" class="spin" :size="17" />{{ busy ? '正在保存…' : pet ? '保存档案' : '建立档案' }}<ArrowRight v-if="!busy" :size="17" /></button>
    </form>
  </DialogShell>
</template>
