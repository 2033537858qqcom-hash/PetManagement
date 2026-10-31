<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import AppSelect from './AppSelect.vue'
import { api, ApiError } from '../lib/api'
import type { Product, Service } from '../lib/workspace'
type Flow = { id:number; delta:number; stock_after:number; action:string; reason:string; operator_name:string; created_at:string }
type Rule = { productId:number; quantity:number }
const props = defineProps<{ products:Product[]; services:Service[]; currentUsername:string }>()
const emit = defineEmits<{ changed:[] }>()
const productId=ref<number|undefined>(props.products[0]?.id), serviceId=ref<number|undefined>(props.services[0]?.id)
const selected=computed(()=>props.products.find(item=>item.id===productId.value))
const flows=ref<Flow[]>([]), rules=ref<Rule[]>([])
const kind=ref('IN'), quantity=ref(''), reason=ref(''), saleUnits=ref('1'), price=ref('0')
const busy=ref(false), error=ref(''), notice=ref('')
async function loadFlows(id:number) { flows.value=await api<Flow[]>(`/inventory/${id}/flows`) }
async function loadRules(id:number) { const data=await api<{product_id:number;quantity:number}[]>(`/services/${id}/consumption`); rules.value=data.map(row=>({productId:row.product_id,quantity:row.quantity})) }
watch(productId, async id => {
 flows.value=[]; const item=props.products.find(p=>p.id===id); saleUnits.value=String(item?.sale_units ?? 1); price.value=String(item?.price ?? 0)
 if(id) try { const saved=sessionStorage.getItem(`petcare.pending.${props.currentUsername}.inventory.${id}`); if(saved){const body=JSON.parse(JSON.parse(saved).body);kind.value=body.kind;quantity.value=String(body.quantity);reason.value=body.reason}; await loadFlows(id) } catch(e) { error.value=(e as Error).message }
}, {immediate:true})
watch(serviceId, async id => { rules.value=[]; if(id) try { await loadRules(id) } catch(e) { error.value=(e as Error).message } }, {immediate:true})
async function adjust() {
 if(!productId.value || busy.value) return
 busy.value=true; error.value=''; notice.value=''; const id=productId.value
 try {
  const payload={kind:kind.value,quantity:Number(quantity.value),reason:reason.value.trim()}, body=JSON.stringify(payload), key=`petcare.pending.${props.currentUsername}.inventory.${id}`
  const existing=sessionStorage.getItem(key); let requestId:string
  if(existing) {const saved=JSON.parse(existing) as {body:string;requestId:string};if(saved.body!==body) throw new Error('上次库存操作结果尚未确认，请使用原内容重试。');requestId=saved.requestId}
  else {requestId=crypto.randomUUID();sessionStorage.setItem(key,JSON.stringify({body,requestId}))}
  await api(`/inventory/${id}/adjust`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({...payload,requestId})})
  sessionStorage.removeItem(key); emit('changed'); quantity.value=''; reason.value=''; notice.value='库存已更新。'
  try { await loadFlows(id) } catch { error.value='库存已更新，流水暂未同步，请刷新后核对。' }
 } catch(e) { if(e instanceof ApiError && e.status && e.status>=400 && e.status<500) sessionStorage.removeItem(`petcare.pending.${props.currentUsername}.inventory.${id}`);error.value=(e as Error).message } finally {busy.value=false}
}
async function saveRetail() {
 if(!productId.value || busy.value) return
 busy.value=true; error.value=''; notice.value=''
 try { await api(`/inventory/${productId.value}/retail`,{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify({saleUnits:Number(saleUnits.value),price:Number(price.value)})}); emit('changed'); notice.value='商品包装已更新。' }
 catch(e) {error.value=(e as Error).message} finally {busy.value=false}
}
function addRule() { const item=props.products.find(p=>!rules.value.some(r=>r.productId===p.id)); if(item) rules.value.push({productId:item.id,quantity:1}) }
async function saveRules() {
 if(!serviceId.value || busy.value) return
 busy.value=true; error.value=''; notice.value=''
 try { await api(`/services/${serviceId.value}/consumption`,{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify({items:rules.value.map(r=>({productId:r.productId,quantity:Number(r.quantity)}))})}); await loadRules(serviceId.value); notice.value='耗材规则已保存。' }
 catch(e) {error.value=(e as Error).message} finally {busy.value=false}
}
</script>
<template>
 <section class="surface inventory-management">
  <div class="section-head"><div><h2>库存操作与耗材规则</h2><p class="field-help">记录库存变化，并为服务设置每次护理的耗材用量。</p></div></div>
  <div v-if="error" class="inline-error" role="alert">{{error}}</div><p v-if="notice" role="status" class="field-help">{{notice}}</p>
  <div class="inventory-controls">
   <section><div class="field"><span>选择耗材</span><AppSelect v-model="productId" label="选择耗材" :options="products.map(p=>({value:p.id,label:`${p.name} · ${p.unit}`}))" :disabled="busy" /></div>
    <form class="booking-form inventory-form" @submit.prevent="adjust">
     <div class="field"><span>操作类型</span><AppSelect v-model="kind" label="操作类型" :options="[{value:'IN',label:'入库'},{value:'OUT',label:'出库'},{value:'SET',label:'盘点设定'}]" :disabled="busy" /></div>
     <div class="form-columns"><label class="field">库存数量<input v-model="quantity" type="number" :min="kind==='SET'?0:1" max="1000000" step="1" required :disabled="busy" /></label><label class="field">调整原因<input v-model="reason" maxlength="255" required :disabled="busy" placeholder="例如采购入库" /></label></div>
     <p class="field-help">{{kind==='SET'?'数量为盘点后的实际库存':'数量以库存单位 '+(selected?.unit || '')+' 计算'}}</p><button class="button primary" type="submit" :disabled="busy || !selected">确认调整库存</button>
    </form>
    <form class="booking-form inventory-form" @submit.prevent="saveRetail"><h3>零售包装</h3><p class="field-help">每售出一件，按包装数量扣减库存；价格为零时不对外出售。</p><div class="form-columns"><label class="field">每件库存单位数<input v-model="saleUnits" type="number" min="1" max="1000000" step="1" required :disabled="busy" /></label><label class="field">零售单价（元）<input v-model="price" type="number" min="0" max="2000" step="0.01" required :disabled="busy" /></label></div><button class="button secondary" type="submit" :disabled="busy || !selected">保存包装与价格</button></form>
   </section>
   <section><div class="field"><span>选择服务</span><AppSelect v-model="serviceId" label="选择服务" :options="services.map(s=>({value:s.id,label:s.name}))" :disabled="busy" /></div>
    <form class="booking-form inventory-form" @submit.prevent="saveRules"><h3>每次护理耗材</h3><p class="field-help">完成护理时按此规则扣减；库存不足时需先补货。</p><div v-for="(rule,index) in rules" :key="index" class="inventory-rule"><div class="field"><span>耗材</span><AppSelect v-model="rule.productId" :label="`耗材 ${index+1}`" :options="products.map(p=>({value:p.id,label:`${p.name} · ${p.unit}`}))" :disabled="busy" /></div><label class="field">每次用量<input v-model.number="rule.quantity" type="number" min="1" max="1000000" step="1" required :disabled="busy" /></label><button class="button quiet small" type="button" :disabled="busy" @click="rules.splice(index,1)">移除</button></div><button class="button secondary" type="button" :disabled="busy || rules.length>=products.length" @click="addRule">添加耗材规则</button><button class="button primary" type="submit" :disabled="busy || !serviceId">保存耗材规则</button></form>
    <h3>最近库存流水</h3><div v-if="flows.length" class="table-scroll"><table><thead><tr><th>操作</th><th>变化</th><th>结余</th><th>原因</th></tr></thead><tbody><tr v-for="flow in flows.slice(0,20)" :key="flow.id"><td>{{flow.action}}</td><td>{{flow.delta>0?'+':''}}{{flow.delta}}</td><td>{{flow.stock_after}}</td><td>{{flow.reason}}</td></tr></tbody></table></div><p v-else class="empty-note">暂无库存流水。</p>
   </section>
  </div>
 </section>
</template>
