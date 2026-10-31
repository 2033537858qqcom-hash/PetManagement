<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api, ApiError } from '../lib/api'
const props=defineProps<{groomingId:number;currentUsername:string}>()
const emit=defineEmits<{saved:[]}>()
const open=ref(false), busy=ref(false), error=ref('')
const name=ref(''),reason=ref(''),amount=ref('')
const key=`petcare.pending.${props.currentUsername}.addon.${props.groomingId}`
onMounted(()=>{const saved=sessionStorage.getItem(key);if(saved){try{const body=JSON.parse(JSON.parse(saved).body);name.value=body.name;reason.value=body.reason;amount.value=String(body.amount);open.value=true}catch{error.value='有一笔待核对的加项申请，请刷新后联系管理员。'}}})
async function submit() {
 if(busy.value) return
 busy.value=true;error.value=''
 try {
  const payload={name:name.value.trim(),reason:reason.value.trim(),amount:Number(amount.value)}, body=JSON.stringify(payload), saved=sessionStorage.getItem(key);let requestId:string
  if(saved){const previous=JSON.parse(saved) as {body:string;requestId:string};if(previous.body!==body) throw new Error('上次申请结果尚未确认，请使用原内容重试。');requestId=previous.requestId}
  else {requestId=crypto.randomUUID();sessionStorage.setItem(key,JSON.stringify({body,requestId}))}
  await api(`/grooming/${props.groomingId}/addons`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({...payload,requestId})})
  sessionStorage.removeItem(key);name.value='';reason.value='';amount.value='';open.value=false;emit('saved')
 } catch(e){if(e instanceof ApiError && e.status && e.status>=400 && e.status<500) sessionStorage.removeItem(key);error.value=(e as Error).message}finally{busy.value=false}
}
</script>
<template>
 <section class="drawer-section"><div class="section-head"><h3>需要额外护理？</h3><button class="button secondary small" type="button" @click="open=!open">{{open?'收起':'申请加项'}}</button></div>
  <form v-if="open" class="booking-form" @submit.prevent="submit"><div v-if="error" class="inline-error" role="alert">{{error}}</div><label class="field">加项名称<input v-model="name" maxlength="100" required :disabled="busy" /></label><label class="field">申请原因<textarea v-model="reason" maxlength="300" required :disabled="busy" /></label><label class="field">加项费用（元）<input v-model="amount" type="number" min="0.01" max="2000" step="0.01" required :disabled="busy" /></label><button class="button primary full" type="submit" :disabled="busy">提交加项申请</button></form>
 </section>
</template>
