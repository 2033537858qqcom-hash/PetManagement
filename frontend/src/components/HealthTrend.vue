<script setup lang="ts">
import { computed } from 'vue'
import type { HealthRecord } from '../lib/workspace'

const props = defineProps<{ records: HealthRecord[] }>()
const ordered = computed(() => [...props.records].sort((a, b) => a.recorded_on.localeCompare(b.recorded_on) || a.id - b.id))
const latest = computed(() => ordered.value[ordered.value.length - 1])

function series(values: number[]) {
  const width = 300
  const height = 62
  if (!values.length) return []
  const min = Math.min(...values)
  const max = Math.max(...values)
  const span = max - min || 1
  return values.map((value, index) => ({
    x: values.length === 1 ? width / 2 : (index / (values.length - 1)) * width,
    y: height - 8 - ((value - min) / span) * (height - 16),
    value,
  }))
}
const weightDots = computed(() => series(ordered.value.map(record => Number(record.weight))))
const scoreDots = computed(() => series(ordered.value.map(record => Number(record.score))))
const line = (dots: { x: number; y: number }[]) => dots.map(dot => `${dot.x},${dot.y}`).join(' ')
</script>

<template>
  <section class="drawer-section">
    <h3>健康趋势</h3>
    <p v-if="!ordered.length" class="empty-note">完成洗护评分后，这里会画出体重和健康评分的变化。</p>
    <template v-else>
      <svg class="trend-chart" viewBox="0 0 300 176" role="img" :aria-label="`健康趋势，最近体重 ${latest?.weight} 千克，健康评分 ${latest?.score}`">
        <text x="0" y="12" class="trend-svg-label">体重 kg</text>
        <g transform="translate(0,16)">
          <polyline :points="line(weightDots)" class="trend-line weight" />
          <circle v-for="(dot, index) in weightDots" :key="`w${index}`" :cx="dot.x" :cy="dot.y" r="3.5" class="trend-dot weight" />
        </g>
        <text x="0" y="100" class="trend-svg-label">健康评分</text>
        <g transform="translate(0,104)">
          <polyline :points="line(scoreDots)" class="trend-line score" />
          <circle v-for="(dot, index) in scoreDots" :key="`s${index}`" :cx="dot.x" :cy="dot.y" r="3.5" class="trend-dot score" />
        </g>
      </svg>
      <p v-if="ordered.length < 2" class="field-help">再有一次记录后，折线会把变化连起来。</p>
      <ul class="trend-log">
        <li v-for="record in ordered" :key="record.id"><span>{{ record.recorded_on.slice(0, 10) }}</span><span>{{ record.weight }} kg</span><span>{{ record.score }} 分</span><small v-if="record.groomer_note">{{ record.groomer_note }}</small></li>
      </ul>
    </template>
  </section>
</template>
