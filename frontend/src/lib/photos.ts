import { computed, ref } from 'vue'
import { api } from './api'

export const petPhotoById = ref<Record<number, string>>({})
export function syncPetPhotos(list: { id: number; photo_url?: string | null }[]) {
  const next = { ...petPhotoById.value }
  for (const pet of list) {
    if (pet.photo_url) next[pet.id] = pet.photo_url
  }
  petPhotoById.value = next
}

export interface PhotoAsset {
  key: string
  label: string
  category: string
  description: string
  defaultUrl: string
  url: string
  customized: boolean
}
const assets = ref<PhotoAsset[]>([])
let revision = 0
export const photoAssets = computed(() => assets.value)
const sources = computed(() => new Map(assets.value.map(photo => [photo.defaultUrl, photo.url])))
export function photoUrl(defaultUrl: string) { return sources.value.get(defaultUrl) || defaultUrl }
export async function loadPhotos() {
  const request = ++revision
  const result = await api<PhotoAsset[]>('/media/photos')
  if (!Array.isArray(result)) throw new Error('图片列表暂时无法加载，请稍后重试。')
  if (request === revision) assets.value = result
}
export function applyPhoto(photo: PhotoAsset) {
  revision++
  const index = assets.value.findIndex(item => item.key === photo.key)
  if (index < 0) assets.value.push(photo)
  else assets.value[index] = photo
}
