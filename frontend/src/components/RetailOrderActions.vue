<script setup lang="ts">
import { ref } from 'vue'
import { api } from '../lib/api'
import type { ShopOrder } from '../lib/workspace'
const props=defineProps<{order:ShopOrder;owner:boolean;canRefund:boolean}>()
const emit=defineEmits<{changed:[message:string]; pay:[info: Record<string, unknown>]}>()
const busy=ref(false), error=ref(''), armed=ref(false), reason=ref('')
async function payAlipay() {
 if(busy.value) return
 busy.value=true;error.value=''
 try {
  const info=await api<Record<string, unknown>>('/payment/create',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({orderId:props.order.id,channel:'ALIPAY'})})
  emit('pay', info)
 } catch(e){error.value=(e as Error).message} finally {busy.value=false}
}
async function act(action:'settle'|'cancel'|'refund', method = 'CASH') {
 if(busy.value) return
 if(action==='cancel'&&!armed.value){armed.value=true;return}
 if(action==='refund'&&!reason.value.trim()) return
 busy.value=true;error.value=''
 try {
  const options:RequestInit={method:'POST'}
  if(action==='refund'){options.headers={'Content-Type':'application/json'};options.body=JSON.stringify({reason:reason.value.trim()})}
  else if(action==='settle'&&method==='BALANCE'){options.headers={'Content-Type':'application/json'};options.body=JSON.stringify({method:'BALANCE'})}
  await api(`/orders/${props.order.id}/${action}`,options)
  armed.value=false;reason.value='';emit('changed',action==='settle'?(method==='BALANCE'?'已扣减会员余额。':'收款已记录。'):action==='cancel'?'订单已取消。':'退款和退货已记录。')
 } catch(e){error.value=(e as Error).message} finally {busy.value=false}
}
</script>
<template>
 <div v-if="error || order.status==='SUBMITTED' || (order.status==='PAID' && canRefund)" class="retail-actions">
  <div v-if="error" class="inline-error" role="alert">{{error}}</div>
  <template v-if="order.status==='SUBMITTED' && owner">
   <button type="button" class="button primary small" :disabled="busy" @click="act('settle', 'BALANCE')">使用会员余额支付</button>
   <button type="button" class="button secondary small" :disabled="busy" @click="payAlipay">支付宝支付</button>
   <button type="button" class="button small retail-cancel" :class="{'is-armed':armed}" :disabled="busy" @click="act('cancel')">{{armed?'确认取消订单':'取消订单'}}</button>
  </template>
  <template v-if="order.status==='SUBMITTED' && !owner">
   <button type="button" class="button primary small" :disabled="busy" @click="act('settle', 'CASH')">确认已收现金</button>
   <button type="button" class="button quiet small" :disabled="busy" @click="act('settle', 'BALANCE')">扣减会员余额</button>
   <button type="button" class="button secondary small" :disabled="busy" @click="payAlipay">支付宝扫码收银</button>
   <button type="button" class="button small retail-cancel" :class="{'is-armed':armed}" :disabled="busy" @click="act('cancel')">{{armed?'确认取消订单':'取消订单'}}</button>
  </template>
  <form v-if="order.status==='PAID' && canRefund" class="retail-refund" @submit.prevent="act('refund')"><label class="field">商品退款原因<input v-model="reason" maxlength="255" required :disabled="busy" placeholder="填写退货原因" /></label><button class="button secondary small" type="submit" :disabled="busy">确认已退款并退货</button></form>
 </div>
</template>
