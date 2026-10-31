<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import AppSelect from './AppSelect.vue'
import { api, ApiError } from '../lib/api'
import type { Customer, Service } from '../lib/workspace'
import { money } from '../lib/workspace'
type BalanceFlow={id:number;amount:number;created_at:string;operator_name:string}
type CardSale={request_id:string;amount:number;created_at:string}
type CardUsage={id:number;card_id:number;change_times:number;created_at:string}
type History={balance:BalanceFlow[];cardSales:CardSale[];cardUsage:CardUsage[]}
const props=defineProps<{customers:Customer[];services:Service[];owner:boolean;ownerId:number;currentUsername:string}>()
const emit=defineEmits<{changed:[]; pay:[info: Record<string, unknown>]}>()
const customerId=ref<number|undefined>(props.owner?props.ownerId:props.customers[0]?.id)
const history=ref<History>({balance:[],cardSales:[],cardUsage:[]})
const rechargeAmount=ref(''), cardName=ref(''), cardTotal=ref(''), cardPrice=ref(''), selectedServices=ref<number[]>([])
const busy=ref(false), error=ref(''), notice=ref('')
const availableServices=computed(()=>props.services.filter(s=>s.enabled!==false))
function keyFor(action:string,id:number) { return `petcare.pending.${props.currentUsername}.${action}.${id}` }
function pending(action:string,id:number,payload:Record<string,unknown>) {
 const key=keyFor(action,id), serialized=JSON.stringify(payload), previous=sessionStorage.getItem(key)
 if(previous) { const saved=JSON.parse(previous) as {body:string;requestId:string}; if(saved.body!==serialized) throw new Error('上次操作结果尚未确认，请使用原金额和内容重试。'); return saved.requestId }
 const requestId=crypto.randomUUID(); sessionStorage.setItem(key,JSON.stringify({body:serialized,requestId})); return requestId
}
async function loadHistory(id:number) { history.value=await api<History>(`/customers/${id}/history`) }
watch(customerId,async id=>{
 history.value={balance:[],cardSales:[],cardUsage:[]}; rechargeAmount.value='';cardName.value='';cardTotal.value='';cardPrice.value='';selectedServices.value=[]
 if(id) try {
  const recharge=sessionStorage.getItem(keyFor('recharge',id)), card=sessionStorage.getItem(keyFor('card',id))
  if(recharge) {const body=JSON.parse(JSON.parse(recharge).body);rechargeAmount.value=String(body.amount)}
  if(card) {const body=JSON.parse(JSON.parse(card).body);cardName.value=body.name;cardTotal.value=String(body.total);cardPrice.value=String(body.price);selectedServices.value=body.serviceIds}
  await loadHistory(id)
 } catch(e) {error.value=(e as Error).message}
},{immediate:true})
async function recharge() {
 if(!customerId.value || busy.value) return
 busy.value=true;error.value='';notice.value='';const id=customerId.value
 try {const payload={amount:Number(rechargeAmount.value)};const requestId=pending('recharge',id,payload);await api(`/customers/${id}/recharge`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({...payload,requestId})});sessionStorage.removeItem(keyFor('recharge',id));emit('changed');rechargeAmount.value='';notice.value='充值已记录。';try{await loadHistory(id)}catch{error.value='充值已记录，流水暂未同步，请刷新后核对。'}}
 catch(e){if(e instanceof ApiError && e.status && e.status>=400 && e.status<500) sessionStorage.removeItem(keyFor('recharge',id));error.value=(e as Error).message}finally{busy.value=false}
}
async function rechargeOnline() {
 if(!customerId.value || busy.value) return
 busy.value=true;error.value='';notice.value=''
 try {
  const info=await api<Record<string, unknown>>('/payment/create',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({amount:Number(rechargeAmount.value),channel:'ALIPAY'})})
  emit('pay', info)
 } catch(e){error.value=(e as Error).message}finally{busy.value=false}
}
async function issueCard() {
 if(!customerId.value || busy.value || !selectedServices.value.length) return
 busy.value=true;error.value='';notice.value='';const id=customerId.value
 try {const payload={name:cardName.value.trim(),total:Number(cardTotal.value),price:Number(cardPrice.value),serviceIds:[...selectedServices.value].sort((a,b)=>a-b)};const requestId=pending('card',id,payload);await api(`/customers/${id}/cards`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({...payload,requestId})});sessionStorage.removeItem(keyFor('card',id));emit('changed');cardName.value='';cardTotal.value='';cardPrice.value='';selectedServices.value=[];notice.value='次卡已办理。';try{await loadHistory(id)}catch{error.value='次卡已办理，流水暂未同步，请刷新后核对。'}}
 catch(e){if(e instanceof ApiError && e.status && e.status>=400 && e.status<500) sessionStorage.removeItem(keyFor('card',id));error.value=(e as Error).message}finally{busy.value=false}
}
</script>
<template>
 <section class="surface membership-management">
  <div class="section-head"><div><h2>{{owner?'会员流水':'会员办理与流水'}}</h2><p class="field-help">{{owner?'可用支付宝给账户充值，并查看次卡办理和核销记录。':'登记现金充值和次卡办理，并查看历史记录。'}}</p></div></div>
  <div v-if="!owner" class="field"><span>选择宠主</span><AppSelect v-model="customerId" label="会员办理选择宠主" :options="customers.map(c=>({value:c.id,label:`${c.name} · ${c.phone}`}))" :disabled="busy" /></div>
  <div v-if="error" class="inline-error" role="alert">{{error}}</div><p v-if="notice" role="status" class="field-help">{{notice}}</p>
  <div v-if="owner" class="inventory-controls">
   <form class="booking-form inventory-form" @submit.prevent="rechargeOnline"><h3>支付宝充值</h3><label class="field">充值金额<input v-model="rechargeAmount" type="number" min="0.01" max="9999999.99" step="0.01" required :disabled="busy" /></label><button class="button primary" type="submit" :disabled="busy || !rechargeAmount">打开支付宝收银台</button></form>
  </div>
  <div v-if="!owner" class="inventory-controls">
   <form class="booking-form inventory-form" @submit.prevent="recharge"><h3>现金充值</h3><label class="field">充值金额<input v-model="rechargeAmount" type="number" min="0.01" max="9999999.99" step="0.01" required :disabled="busy" /></label><button class="button primary" type="submit" :disabled="busy || !customerId">确认已收现金并充值</button></form>
   <form class="booking-form inventory-form" @submit.prevent="issueCard"><h3>办理次卡</h3><label class="field">次卡名称<input v-model="cardName" required maxlength="100" :disabled="busy" /></label><div class="form-columns"><label class="field">护理次数<input v-model="cardTotal" type="number" min="1" max="1000" step="1" required :disabled="busy" /></label><label class="field">办卡金额<input v-model="cardPrice" type="number" min="0.01" max="9999999.99" step="0.01" required :disabled="busy" /></label></div><fieldset :disabled="busy"><legend>适用服务</legend><div class="setup-checks"><label v-for="service in availableServices" :key="service.id"><input v-model="selectedServices" type="checkbox" :value="service.id" /> {{service.name}}</label></div><p v-if="!availableServices.length" class="field-help">请先添加可用的服务项目。</p></fieldset><button class="button primary" type="submit" :disabled="busy || !customerId || !selectedServices.length">确认已收现金并办卡</button></form>
  </div>
  <div class="inventory-controls"><section><h3>余额变动</h3><div v-if="history.balance.length" class="table-scroll"><table><thead><tr><th>金额</th><th>时间</th><th>操作人</th></tr></thead><tbody><tr v-for="flow in history.balance" :key="flow.id"><td>{{Number(flow.amount)>0?'+':''}}{{money(Number(flow.amount))}}</td><td>{{flow.created_at}}</td><td>{{flow.operator_name}}</td></tr></tbody></table></div><p v-else class="empty-note">暂无余额流水。</p></section><section><h3>次卡记录</h3><p class="field-help">已办理 {{history.cardSales.length}} 次 · 核销或退回 {{history.cardUsage.length}} 次</p><div v-if="history.cardUsage.length" class="table-scroll"><table><thead><tr><th>次卡编号</th><th>次数变化</th><th>时间</th></tr></thead><tbody><tr v-for="item in history.cardUsage" :key="item.id"><td>{{item.card_id}}</td><td>{{item.change_times>0?'+':''}}{{item.change_times}}</td><td>{{item.created_at}}</td></tr></tbody></table></div></section></div>
 </section>
</template>
