<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import AppSelect from './AppSelect.vue'
import { api } from '../lib/api'
import type { Employee } from '../lib/workspace'
type Shift={employee_id:number;schedule_date:string;status:string;start_time:string|null;end_time:string|null}
const props=defineProps<{employees:Employee[];date:string;canEdit:boolean}>()
const employeeId=ref<number|undefined>(props.employees[0]?.id), shifts=ref<Shift[]>([])
const status=ref('WORK'), start=ref('09:00'), end=ref('21:00'), busy=ref(false), loading=ref(false), error=ref(''), notice=ref('')
const selected=computed(()=>shifts.value.find(s=>s.employee_id===employeeId.value))
let revision=0
async function load() {
 const request=++revision;loading.value=true;error.value=''
 try {const data=await api<Shift[]>(`/schedules?date=${encodeURIComponent(props.date)}`);if(request===revision) shifts.value=data}
 catch(e){if(request===revision) error.value=(e as Error).message}finally{if(request===revision) loading.value=false}
}
watch(()=>props.date,()=>{notice.value='';void load()},{immediate:true})
watch(selected,shift=>{status.value=shift?.status || 'WORK';start.value=shift?.start_time?.slice(0,5) || '09:00';end.value=shift?.end_time?.slice(0,5) || '21:00'},{immediate:true})
watch(()=>props.employees,items=>{if(!items.some(e=>e.id===employeeId.value))employeeId.value=items[0]?.id})
async function save() {
 if(!employeeId.value || busy.value) return
 busy.value=true;error.value='';notice.value=''
 try {await api(`/schedules/${employeeId.value}/${encodeURIComponent(props.date)}`,{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify({status:status.value,startTime:status.value==='WORK'?start.value:null,endTime:status.value==='WORK'?end.value:null})});await load();notice.value='排班已保存。'}
 catch(e){error.value=(e as Error).message}finally{busy.value=false}
}
</script>
<template>
 <section class="schedule-management"><div class="section-head"><div><h3>员工排班</h3><p class="field-help">{{date}} · 未设置时按门店营业时间安排。</p></div></div>
  <div v-if="error" class="inline-error" role="alert">{{error}}</div><p v-if="notice" role="status" class="field-help">{{notice}}</p>
  <div v-if="employees.length" class="schedule-form"><div class="field"><span>选择洗护师</span><AppSelect v-model="employeeId" label="排班洗护师" :options="employees.map(e=>({value:e.id,label:e.name}))" :disabled="busy || loading" /></div><p class="field-help">{{selected?.status==='OFF'?'当日休假':selected?.status==='WORK'?`当日上班 ${selected.start_time?.slice(0,5)}–${selected.end_time?.slice(0,5)}`:'未设置，按 09:00–21:00 安排'}}</p>
   <form v-if="canEdit" class="booking-form" @submit.prevent="save"><div class="field"><span>排班状态</span><AppSelect v-model="status" label="排班状态" :options="[{value:'WORK',label:'上班'},{value:'OFF',label:'休假'}]" :disabled="busy || loading" /></div><div v-if="status==='WORK'" class="form-columns"><label class="field">上班时间<input v-model="start" type="time" min="09:00" max="21:00" required :disabled="busy" /></label><label class="field">下班时间<input v-model="end" type="time" min="09:00" max="21:00" required :disabled="busy" /></label></div><button class="button secondary" type="submit" :disabled="busy || loading || !employeeId">保存员工排班</button></form>
  </div><p v-else class="empty-note">请先建立洗护师资料。</p>
 </section>
</template>
