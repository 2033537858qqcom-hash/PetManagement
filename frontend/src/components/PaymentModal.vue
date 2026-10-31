<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { api } from '../lib/api'
import DialogShell from './DialogShell.vue'
import { CheckCircle2, AlertCircle, Clock, QrCode as QrIcon } from 'lucide-vue-next'

export interface PaymentInfo {
  paymentNo: string
  groomingId?: number
  orderId?: number
  customerId: number
  channel: 'ALIPAY' | string
  channelName?: string
  amount: number
  baseAmount?: number
  addonAmount?: number
  status: string
  qrCode: string
  expireAt: string
  petName?: string
  serviceName?: string
  pickupCode?: string
  sandbox?: boolean
  bizType?: string
  subject?: string
}

const props = defineProps<{
  payment: PaymentInfo
}>()

const emit = defineEmits<{
  close: []
  paid: [payment: PaymentInfo]
}>()

const status = ref(props.payment.status || 'PENDING')
const busy = ref(false)
const errorMsg = ref('')
const remainingSeconds = ref(15 * 60)
let timerId: ReturnType<typeof setInterval> | null = null
let pollId: ReturnType<typeof setInterval> | null = null

const channelColor = computed(() => '#1677ff')
const subjectLabel = computed(() => props.payment.bizType === 'RECHARGE' ? '会员充值' : props.payment.bizType === 'RETAIL' ? '商品订单' : '服务工单')
const subjectValue = computed(() => {
  if (props.payment.bizType === 'RECHARGE') return '账户储值'
  if (props.payment.petName) return `#${props.payment.groomingId} · ${props.payment.petName} · ${props.payment.serviceName || ''}`
  return props.payment.subject || props.payment.paymentNo
})
const successHint = computed(() => props.payment.bizType === 'RECHARGE' ? '余额将立即到账。' : props.payment.bizType === 'RETAIL' ? '商品订单已结清。' : '正在结清工单并同步提宠凭证…')
const channelTitle = computed(() => '支付宝收银台')
const isSuccess = computed(() => status.value === 'SUCCESS')
const isExpired = computed(() => status.value === 'CLOSED' || props.payment.status === 'CLOSED' || remainingSeconds.value <= 0)

function money(v: number | string | undefined | null) {
  return Number(v || 0).toFixed(2)
}

function parseDate(d: string | undefined): number {
  if (!d) return 0
  const parsed = Date.parse(d)
  if (!isNaN(parsed)) return parsed
  const iso = d.includes('T') ? d : d.replace(' ', 'T')
  const p2 = Date.parse(iso)
  if (!isNaN(p2)) return p2
  return Date.parse(d.replace(/-/g, '/')) || 0
}

function computeRemaining() {
  if (!props.payment.expireAt) return
  const expireTime = parseDate(props.payment.expireAt)
  if (isNaN(expireTime) || !expireTime) return
  const diff = Math.floor((expireTime - Date.now()) / 1000)
  remainingSeconds.value = Math.max(0, diff)
  if (diff <= 0 && status.value === 'PENDING') {
    status.value = 'CLOSED'
    stopPolling()
  }
}

const formattedCountdown = computed(() => {
  const m = Math.floor(remainingSeconds.value / 60)
  const s = remainingSeconds.value % 60
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
})

async function checkStatus() {
  if (isSuccess.value || isExpired.value) return
  try {
    const data = await api<PaymentInfo>(`/payment/${props.payment.paymentNo}`)
    if (data.status === 'SUCCESS') {
      status.value = 'SUCCESS'
      stopPolling()
      setTimeout(() => {
        emit('paid', data)
        emit('close')
      }, 1200)
    } else if (data.status === 'CLOSED') {
      status.value = 'CLOSED'
      stopPolling()
    }
  } catch {
    // transient network error, keep polling
  }
}

async function simulatePay() {
  if (busy.value || isSuccess.value || isExpired.value) return
  busy.value = true
  errorMsg.value = ''
  try {
    const data = await api<PaymentInfo>(`/payment/${props.payment.paymentNo}/mock-pay`, {
      method: 'POST',
    })
    status.value = 'SUCCESS'
    stopPolling()
    setTimeout(() => {
      emit('paid', data)
      emit('close')
    }, 1200)
  } catch (err) {
    errorMsg.value = (err as Error).message || '模拟支付失败'
  } finally {
    busy.value = false
  }
}

function startPolling() {
  computeRemaining()
  timerId = setInterval(() => {
    if (remainingSeconds.value > 0) {
      remainingSeconds.value--
    } else {
      status.value = 'CLOSED'
      stopPolling()
    }
  }, 1000)

  pollId = setInterval(() => {
    void checkStatus()
  }, 1500)
}

function stopPolling() {
  if (timerId) { clearInterval(timerId); timerId = null }
  if (pollId) { clearInterval(pollId); pollId = null }
}

onMounted(() => {
  startPolling()
})

onUnmounted(() => {
  stopPolling()
})

// Deterministic QR Matrix Generator for SVG rendering
const qrMatrix = computed(() => {
  const size = 25
  const grid: boolean[][] = Array.from({ length: size }, () => Array(size).fill(false))

  // Helper to mark finder pattern
  function markFinder(r: number, c: number) {
    for (let i = -1; i <= 7; i++) {
      for (let j = -1; j <= 7; j++) {
        const row = r + i, col = c + j
        if (row >= 0 && row < size && col >= 0 && col < size) {
          if (i >= 0 && i <= 6 && j >= 0 && j <= 6) {
            grid[row][col] = (i === 0 || i === 6 || j === 0 || j === 6 || (i >= 2 && i <= 4 && j >= 2 && j <= 4))
          } else {
            grid[row][col] = false
          }
        }
      }
    }
  }

  markFinder(0, 0)
  markFinder(0, size - 7)
  markFinder(size - 7, 0)

  // Timing patterns
  for (let i = 8; i < size - 8; i++) {
    grid[6][i] = i % 2 === 0
    grid[i][6] = i % 2 === 0
  }

  // Pseudo-random data dots based on paymentNo hash
  let hash = 0
  const str = props.payment.qrCode || props.payment.paymentNo || 'PAW_PURE'
  for (let k = 0; k < str.length; k++) {
    hash = (hash * 31 + str.charCodeAt(k)) & 0xffffffff
  }

  for (let r = 0; r < size; r++) {
    for (let c = 0; c < size; c++) {
      // Don't overwrite finders or timing
      const inTopLeft = r <= 7 && c <= 7
      const inTopRight = r <= 7 && c >= size - 8
      const inBottomLeft = r >= size - 8 && c <= 7
      const inCenterLogo = r >= 10 && r <= 14 && c >= 10 && c <= 14
      if (!inTopLeft && !inTopRight && !inBottomLeft && !inCenterLogo && r !== 6 && c !== 6) {
        hash = (hash * 1103515245 + 12345) & 0x7fffffff
        grid[r][c] = (hash % 3) !== 0
      }
    }
  }

  return grid
})
</script>

<template>
  <DialogShell :title="channelTitle" subtitle="PAW & PURE · 线上扫码安全自付" @close="emit('close')">
    <div class="payment-modal-body">
      <!-- Channel Brand Banner -->
      <div class="channel-badge" :style="{ borderColor: channelColor, background: '#f0f7ff' }">
        <span class="channel-indicator" :style="{ background: channelColor }"></span>
        <strong :style="{ color: channelColor }">支付宝个人开发者 · 扫码付</strong>
        <span class="security-tag">已接入统一安全收银</span>
      </div>

      <!-- Order Summary Card -->
      <section class="payment-order-summary">
        <div class="summary-row">
          <span class="summary-label">{{ subjectLabel }}</span>
          <span class="summary-value"><strong>{{ subjectValue }}</strong></span>
        </div>
        <div class="summary-row">
          <span class="summary-label">商户支付单号</span>
          <span class="summary-mono">{{ payment.paymentNo }}</span>
        </div>
        <div class="summary-price-row">
          <span class="price-label">待支付总额</span>
          <span class="price-value" :style="{ color: channelColor }">
            <small>¥</small>{{ money(payment.amount) }}
          </span>
        </div>
      </section>

      <!-- QR Code / Status Section -->
      <div class="qr-container-card">
        <!-- Success State -->
        <div v-if="isSuccess" class="payment-status-overlay is-success" role="status">
          <CheckCircle2 :size="56" color="#07c160" />
          <h3>支付成功！</h3>
          <p>{{ successHint }}</p>
        </div>

        <!-- Expired State -->
        <div v-else-if="isExpired" class="payment-status-overlay is-expired" role="status">
          <AlertCircle :size="52" color="#e11d48" />
          <h3>二维码已过期</h3>
          <p>订单支付已超时关闭，请重新结算</p>
        </div>

        <!-- Active QR State -->
        <div v-else class="qr-display-wrapper">
          <div class="qr-frame">
            <svg viewBox="0 0 25 25" class="qr-svg" role="img" aria-label="支付宝支付收款二维码">
              <!-- Render Matrix -->
              <template v-for="(row, r) in qrMatrix" :key="r">
                <template v-for="(cell, c) in row" :key="c">
                  <rect v-if="cell" :x="c" :y="r" width="1" height="1" :fill="channelColor" />
                </template>
              </template>
              <!-- Center Brand Box -->
              <rect x="9.5" y="9.5" width="6" height="6" rx="1.2" fill="#fff" :stroke="channelColor" stroke-width="0.3" />
            </svg>
            <div class="qr-center-logo" :style="{ background: channelColor }">
              <QrIcon :size="16" color="#fff" />
            </div>
          </div>

          <p class="qr-instruction">
            请使用手机<strong>支付宝</strong>扫一扫
          </p>

          <div class="qr-countdown" :class="{ 'is-warning': remainingSeconds < 120 }">
            <Clock :size="15" />
            <span>支付倒计时 <strong>{{ formattedCountdown }}</strong></span>
          </div>
        </div>
      </div>

      <!-- Error hint if any -->
      <p v-if="errorMsg" class="inline-error" style="margin-top: 0.5rem">{{ errorMsg }}</p>

      <!-- Sandbox Developer / Testing Action -->
      <div v-if="payment.sandbox === true && !isSuccess && !isExpired" class="payment-sandbox-panel">
        <p class="sandbox-note">
          <small>💡 演示环境：无需真实扣款，可点击下方按钮直接模拟扫码成功回调</small>
        </p>
        <button
          type="button"
          class="button secondary full sandbox-button"
          :disabled="busy || isSuccess || isExpired"
          @click="simulatePay"
        >
          {{ busy ? '正在模拟支付…' : '⚡ 模拟扫码支付成功（演示环境）' }}
        </button>
      </div>

      <button type="button" class="button quiet full" style="margin-top: 0.75rem" @click="emit('close')">
        {{ isSuccess ? '关闭' : '取消支付' }}
      </button>
    </div>
  </DialogShell>
</template>

<style scoped>
.payment-modal-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.channel-badge {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  border: 1px solid;
  border-radius: 12px;
  font-size: 13px;
}

.channel-indicator {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  animation: pulse 1.8s infinite ease-in-out;
}

@keyframes pulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.4; transform: scale(0.85); }
}

.security-tag {
  margin-left: auto;
  font-size: 11px;
  color: #64748b;
  background: rgba(0, 0, 0, 0.04);
  padding: 3px 8px;
  border-radius: 6px;
}

.payment-order-summary {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  padding: 14px 18px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.summary-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
  gap: 12px;
}

.summary-label {
  color: #64748b;
  white-space: nowrap;
}

.summary-value {
  color: #1e293b;
  text-align: right;
  overflow: hidden;
  text-overflow: ellipsis;
}

.summary-mono {
  font-family: monospace;
  font-size: 12px;
  color: #475569;
}

.summary-price-row {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  padding-top: 10px;
  margin-top: 4px;
  border-top: 1px dashed #cbd5e1;
}

.price-label {
  font-size: 14px;
  font-weight: 600;
  color: #334155;
}

.price-value {
  font-size: 26px;
  font-weight: 700;
  letter-spacing: -0.02em;
}

.price-value small {
  font-size: 16px;
  margin-right: 2px;
}

.qr-container-card {
  position: relative;
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 16px;
  padding: 24px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 280px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.04);
}

.qr-display-wrapper {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  width: 100%;
}

.qr-frame {
  position: relative;
  width: 180px;
  height: 180px;
  padding: 12px;
  background: #fff;
  border: 1px solid #cbd5e1;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.qr-svg {
  width: 100%;
  height: 100%;
  shape-rendering: crispEdges;
}

.qr-center-logo {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 28px;
  height: 28px;
  border-radius: 7px;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.2);
}

.qr-instruction {
  font-size: 13px;
  color: #475569;
  margin: 0;
}

.qr-countdown {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #64748b;
  background: #f1f5f9;
  padding: 5px 12px;
  border-radius: 20px;
}

.qr-countdown.is-warning {
  color: #e11d48;
  background: #ffe4e6;
}

.payment-status-overlay {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  text-align: center;
  padding: 20px;
}

.payment-status-overlay h3 {
  font-size: 18px;
  margin: 0;
}

.payment-status-overlay p {
  font-size: 13px;
  color: #64748b;
  margin: 0;
}

.payment-status-overlay.is-success h3 {
  color: #07c160;
}

.payment-status-overlay.is-expired h3 {
  color: #e11d48;
}

.payment-sandbox-panel {
  background: #fffbeb;
  border: 1px dashed #fcd34d;
  border-radius: 12px;
  padding: 12px 14px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.sandbox-note {
  color: #92400e;
  margin: 0;
  line-height: 1.4;
}

.sandbox-button {
  background: #fef3c7;
  color: #92400e;
  border-color: #fde68a;
  font-weight: 600;
}

.sandbox-button:hover:not(:disabled) {
  background: #fde68a;
}
</style>
