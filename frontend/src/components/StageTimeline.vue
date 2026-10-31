<script setup lang="ts">
import { computed } from 'vue'
import { careStages } from '../lib/workspace'

const props = withDefaults(defineProps<{ stage: string; detailed?: boolean }>(), { detailed: false })
const stages = computed(() => careStages(props.stage))
const currentIndex = computed(() => stages.value.findIndex(item => item.state === 'current'))
const completed = computed(() => stages.value.every(item => item.state === 'done'))
const current = computed(() => stages.value[currentIndex.value])
const next = computed(() => stages.value[currentIndex.value + 1])
const known = computed(() => completed.value || currentIndex.value >= 0)
const ordinal = computed(() => completed.value ? '05' : current.value ? String(currentIndex.value + 1).padStart(2, '0') : '—')
const completedCount = computed(() => stages.value.filter(item => item.state === 'done').length)
</script>

<template>
  <section class="care-progress" :class="{ 'is-complete': completed, 'is-unknown': !known }" aria-label="五阶段护理进度">
    <div class="care-progress-main">
      <div class="care-progress-index" aria-hidden="true"><span>{{ ordinal }}</span><small>/ 05</small></div>
      <div class="care-progress-current">
        <span class="care-progress-caption">{{ completed ? '本次护理' : current ? '当前阶段' : '护理进度' }}</span>
        <h3>{{ completed ? '护理已完成' : current ? current.label : '阶段待更新' }}</h3>
        <p v-if="current">下一步<span>{{ next?.label }}</span></p>
        <p v-else-if="completed">五个护理阶段已完成</p>
        <p v-else>有新进度时将在这里显示</p>
      </div>
    </div>
    <ol class="care-progress-records" :class="{ 'sr-only': !detailed }">
      <li v-for="(item, index) in stages" :key="item.code" :class="item.state" :aria-current="item.state === 'current' ? 'step' : undefined">
        <span class="care-record-index" aria-hidden="true">{{ String(index + 1).padStart(2, '0') }}</span>
        <span>{{ item.label }}</span>
        <small>{{ !known ? '待更新' : item.state === 'done' ? '已完成' : item.state === 'current' ? '进行中' : '待进行' }}</small>
      </li>
    </ol>
    <span v-if="!detailed && known" class="sr-only">共五个阶段，已完成 {{ completedCount }} 个</span>
  </section>
</template>
