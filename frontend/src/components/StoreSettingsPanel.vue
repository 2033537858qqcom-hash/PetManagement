<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { LoaderCircle } from 'lucide-vue-next'
import { api } from '../lib/api'

type StoreSettings = {
  storeName: string
  businessHours: string
  openTime: string
  closeTime: string
  bufferMinutes: number
  address: string
  phone: string
  updatedAt?: string
}
type AuditLog = {
  id: number
  action_type: string
  operator_name: string
  target_type: string
  target_id: string
  details?: string
  created_at: string
}

const emit = defineEmits<{ saved: [message: string] }>()
const settings = ref<StoreSettings>({
  storeName: '',
  businessHours: '',
  openTime: '09:00',
  closeTime: '21:00',
  bufferMinutes: 15,
  address: '',
  phone: '',
})
const logs = ref<AuditLog[]>([])
const busy = ref(false)
const error = ref('')
const notice = ref('')

const actionLabels: Record<string, string> = {
  MEMBER_RECHARGE: '会员充值',
  CARD_SALE: '办理次卡',
  GROOMING_REFUND: '洗护退款',
  GROOMING_VOID: '工单作废',
  STORE_SETTINGS_UPDATE: '门店设置',
  PAYMENT_COMPLETE: '在线支付',
  ORDER_SETTLE: '商品收款',
  ORDER_CANCEL: '取消订单',
  ORDER_REFUND: '商品退货',
}

function actionLabel(code: string) {
  return actionLabels[code] || code
}

async function load() {
  error.value = ''
  try {
    const [store, audit] = await Promise.all([
      api<StoreSettings>('/store/settings'),
      api<AuditLog[]>('/audit-logs?limit=80').catch(() => [] as AuditLog[]),
    ])
    settings.value = {
      storeName: store.storeName || '',
      businessHours: store.businessHours || '',
      openTime: String(store.openTime || '09:00').slice(0, 5),
      closeTime: String(store.closeTime || '21:00').slice(0, 5),
      bufferMinutes: Number(store.bufferMinutes ?? 15),
      address: store.address || '',
      phone: store.phone || '',
      updatedAt: store.updatedAt,
    }
    logs.value = audit
  } catch (reason) {
    error.value = (reason as Error).message
  }
}

async function save() {
  if (busy.value) return
  busy.value = true
  error.value = ''
  notice.value = ''
  try {
    const hours = `${settings.value.openTime} - ${settings.value.closeTime}`
    const saved = await api<StoreSettings>('/store/settings', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        storeName: settings.value.storeName.trim(),
        businessHours: hours,
        openTime: settings.value.openTime,
        closeTime: settings.value.closeTime,
        bufferMinutes: Number(settings.value.bufferMinutes),
        address: settings.value.address.trim(),
        phone: settings.value.phone.trim(),
      }),
    })
    settings.value = {
      ...settings.value,
      storeName: saved.storeName,
      businessHours: saved.businessHours,
      openTime: String(saved.openTime || settings.value.openTime).slice(0, 5),
      closeTime: String(saved.closeTime || settings.value.closeTime).slice(0, 5),
      bufferMinutes: Number(saved.bufferMinutes ?? settings.value.bufferMinutes),
      address: saved.address || '',
      phone: saved.phone || '',
      updatedAt: saved.updatedAt,
    }
    notice.value = '门店设置已保存。'
    logs.value = await api<AuditLog[]>('/audit-logs?limit=80').catch(() => logs.value)
    emit('saved', '门店设置已更新。')
  } catch (reason) {
    error.value = (reason as Error).message
  } finally {
    busy.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="settings-layout">
    <section class="surface">
      <div class="section-head">
        <div>
          <span class="eyebrow">STORE PROFILE</span>
          <h2>门店基础资料</h2>
        </div>
        <span v-if="settings.updatedAt" class="field-help">最近更新 {{ settings.updatedAt }}</span>
      </div>
      <form class="booking-form" @submit.prevent="save">
        <div v-if="error" class="inline-error" role="alert">{{ error }}</div>
        <p v-if="notice" class="field-help" role="status">{{ notice }}</p>
        <label class="field">门店名称<input v-model="settings.storeName" maxlength="100" required :disabled="busy" placeholder="例如 爪印宠护 · 旗舰店" /></label>
        <div class="form-columns">
          <label class="field">开始营业<input v-model="settings.openTime" type="time" required :disabled="busy" /></label>
          <label class="field">结束营业<input v-model="settings.closeTime" type="time" required :disabled="busy" /></label>
        </div>
        <label class="field">清洁缓冲（分钟）<input v-model.number="settings.bufferMinutes" type="number" min="0" max="60" step="5" required :disabled="busy" /></label>
        <label class="field">门店地址<input v-model="settings.address" maxlength="255" :disabled="busy" placeholder="选填" /></label>
        <label class="field">联系电话<input v-model="settings.phone" maxlength="50" :disabled="busy" placeholder="选填" /></label>
        <button class="button primary" type="submit" :disabled="busy"><LoaderCircle v-if="busy" class="spin" :size="17" />{{ busy ? '正在保存…' : '保存门店设置' }}</button>
      </form>
    </section>
    <section class="surface">
      <div class="section-head">
        <div>
          <span class="eyebrow">AUDIT TRAIL</span>
          <h2>操作审计</h2>
        </div>
        <span>{{ logs.length }} 条</span>
      </div>
      <div v-if="logs.length" class="table-scroll">
        <table>
          <thead><tr><th>时间</th><th>操作</th><th>操作人</th><th>对象</th><th>详情</th></tr></thead>
          <tbody>
            <tr v-for="log in logs" :key="log.id">
              <td>{{ String(log.created_at).slice(0, 16) }}</td>
              <td>{{ actionLabel(log.action_type) }}</td>
              <td>{{ log.operator_name }}</td>
              <td>{{ log.target_type }} #{{ log.target_id }}</td>
              <td>{{ log.details }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <p v-else class="empty-note">还没有审计记录。</p>
    </section>
  </div>
</template>
