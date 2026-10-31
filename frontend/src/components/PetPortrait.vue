<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { PawPrint } from 'lucide-vue-next'
import { portraits } from '../lib/workspace'
import { petPhotoById, photoUrl } from '../lib/photos'
const props = defineProps<{ id: number; name?: string; src?: string }>()
const failed = ref(false)
const positions: Record<number, string> = { 1: '65% 52%', 2: '50% 74%', 3: '50% 35%', 4: '50% 30%' }
const source = computed(() => props.src || petPhotoById.value[props.id] || (portraits[props.id] ? photoUrl(portraits[props.id]) : ''))
const customized = computed(() => !!(props.src || petPhotoById.value[props.id]) || source.value !== portraits[props.id])
watch([() => props.id, source], () => { failed.value = false })
</script>
<template>
  <span class="pet-portrait">
    <img v-if="source && !failed" :src="source" :alt="`${name || '爱宠'}的${customized ? '' : '演示'}照片`" :style="{ objectPosition: customized ? '50% 50%' : positions[id] }" decoding="async" @error="failed = true" />
    <PawPrint v-else :size="24" aria-label="宠物照片暂不可用" />
  </span>
</template>
