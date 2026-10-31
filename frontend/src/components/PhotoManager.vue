<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { ImagePlus, LoaderCircle, RotateCcw, Upload } from 'lucide-vue-next'
import { api } from '../lib/api'
import { applyPhoto, loadPhotos, photoAssets, type PhotoAsset } from '../lib/photos'

const category = ref('全部'), busyKey = ref(''), loading = ref(true), error = ref(''), notice = ref('')
const picker = ref<HTMLInputElement>(), errorElement = ref<HTMLElement>()
const selected = ref<PhotoAsset>()
const categories = computed(() => ['全部', ...new Set(photoAssets.value.map(photo => photo.category))])
const visible = computed(() => photoAssets.value.filter(photo => category.value === '全部' || photo.category === category.value))
const customizedCount = computed(() => photoAssets.value.filter(photo => photo.customized).length)
async function refresh() {
  loading.value = true; error.value = ''
  try { await loadPhotos() } catch (reason) { error.value = (reason as Error).message }
  finally { loading.value = false }
}
function choose(photo: PhotoAsset) {
  if (busyKey.value) return
  selected.value = photo
  if (picker.value) { picker.value.value = ''; picker.value.click() }
}
async function upload() {
  const file = picker.value?.files?.[0], photo = selected.value
  if (!file || !photo || busyKey.value) return
  error.value = ''; notice.value = ''
  if (!['image/jpeg', 'image/png'].includes(file.type)) { error.value = '请选择 JPG 或 PNG 图片。'; return }
  if (!file.size || file.size > 5 * 1024 * 1024) { error.value = '请选择大小不超过 5 MB 的非空图片。'; return }
  busyKey.value = photo.key
  const body = new FormData(); body.append('file', file)
  try {
    applyPhoto(await api<PhotoAsset>(`/media/photos/${photo.key}`, { method: 'PUT', body }))
    notice.value = `${photo.label}已更新，相关页面会同步使用新图片。`
  } catch (reason) { error.value = (reason as Error).message }
  finally { busyKey.value = ''; if (picker.value) picker.value.value = '' }
}
async function restore(photo: PhotoAsset) {
  if (busyKey.value) return
  busyKey.value = photo.key; error.value = ''; notice.value = ''
  try {
    applyPhoto(await api<PhotoAsset>(`/media/photos/${photo.key}`, { method: 'DELETE' }))
    notice.value = `${photo.label}已恢复默认。`
  } catch (reason) { error.value = (reason as Error).message }
  finally { busyKey.value = '' }
}
watch(error, message => { if (message) nextTick(() => errorElement.value?.focus()) })
onMounted(refresh)
</script>

<template>
 <section class="photo-management" aria-label="网站图片管理">
  <div class="surface photo-library-header">
   <span class="photo-library-icon"><ImagePlus :size="24" :stroke-width="1.6"/></span>
   <div><h2>让每一张照片，都贴近你的门店。</h2><p>更换后会同步到使用该图片的位置，也可以随时恢复默认。</p></div>
   <span class="photo-library-count">{{photoAssets.length}} 张图片<small>{{customizedCount}} 张已更换</small></span>
  </div>
  <div class="photo-toolbar"><div class="segmented" role="group" aria-label="图片分类"><button v-for="item in categories" :key="item" type="button" :class="{selected:category===item}" :aria-pressed="category===item" @click="category=item">{{item}}</button></div><span>JPG、PNG · 最大 5 MB</span></div>
  <input ref="picker" type="file" class="sr-only" tabindex="-1" accept="image/jpeg,image/png" aria-label="选择替换照片" :disabled="!!busyKey" @change="upload" />
  <div v-if="error" ref="errorElement" class="inline-error photo-feedback" role="alert" tabindex="-1">{{error}}<button v-if="!photoAssets.length" type="button" class="text-link" @click="refresh">重新加载</button></div>
  <p v-if="notice" class="photo-notice" role="status">{{notice}}</p>
  <div v-if="loading" class="photo-loading" role="status"><LoaderCircle class="spin" :size="18"/>正在加载图片…</div>
  <div v-else class="photo-grid">
   <article v-for="photo in visible" :key="photo.key" class="surface photo-card" :aria-label="photo.label">
    <div class="photo-preview" :class="{'is-product':photo.category==='商品图片'}"><img :src="photo.url" :alt="`${photo.label}预览`" loading="lazy"/><span v-if="photo.customized" class="photo-customized">已更换</span></div>
    <div class="photo-card-body"><span class="photo-category">{{photo.category}}</span><h3>{{photo.label}}</h3><p>{{photo.description}}</p><div class="photo-card-actions"><button type="button" class="button secondary small" :aria-label="`更换${photo.label}`" :disabled="!!busyKey" @click="choose(photo)"><LoaderCircle v-if="busyKey===photo.key" class="spin" :size="14"/><Upload v-else :size="14"/>{{busyKey===photo.key?'正在保存…':'更换图片'}}</button><button v-if="photo.customized" type="button" class="photo-reset" :aria-label="`恢复${photo.label}默认图片`" :disabled="!!busyKey" @click="restore(photo)"><RotateCcw :size="13"/>恢复默认</button><span v-else class="photo-default">默认图片</span></div></div>
   </article>
  </div>
 </section>
</template>
