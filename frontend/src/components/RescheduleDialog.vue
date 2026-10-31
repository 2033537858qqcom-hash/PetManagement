<script setup lang="ts">
import { computed, ref } from 'vue'
import DialogShell from './DialogShell.vue'
import AppDatePicker from './AppDatePicker.vue'
import AppTimePicker from './AppTimePicker.vue'
import { api } from '../lib/api'
import { bookingTiming, localDate, suggestedTime, type Appointment } from '../lib/workspace'
const props=defineProps<{appointment:Appointment}>()
const emit=defineEmits<{close:[];saved:[]}>()
const date=ref(props.appointment.appointment_date>=localDate()?props.appointment.appointment_date:localDate())
const time=ref(props.appointment.start_time.slice(0,5) || suggestedTime(date.value))
const busy=ref(false), error=ref('')
const minutes=(value:string)=>{const [hour,minute]=value.split(':').map(Number);return hour*60+minute}
const duration=computed(()=>minutes(props.appointment.end_time)-minutes(props.appointment.start_time))
const timing=computed(()=>bookingTiming(date.value,time.value,duration.value))
async function submit() {
 if(busy.value || timing.value.error) return
 busy.value=true;error.value=''
 try {await api(`/appointments/${props.appointment.id}/reschedule`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({date:date.value,startTime:time.value})});emit('saved')}
 catch(e){error.value=(e as Error).message}finally{busy.value=false}
}
</script>
<template>
 <DialogShell title="改期预约" :subtitle="`${appointment.pet_name} · ${appointment.service_name}。原预约价格保持不变。`" :busy="busy" @close="emit('close')">
  <form class="booking-form" @submit.prevent="submit"><div v-if="error" class="inline-error" role="alert">{{error}}</div><div class="form-columns"><div class="field"><span>新的预约日期</span><AppDatePicker v-model="date" label="新的预约日期" :min="localDate()" :disabled="busy" /></div><div class="field"><span>新的开始时间</span><AppTimePicker v-model="time" label="新的开始时间" min="09:00" :max="timing.latestStart" :disabled="busy" /></div></div><p v-if="timing.error" class="field-error" role="status">{{timing.error}}</p><p v-else class="field-help">护理至 {{timing.endTime}}，清洁缓冲至 {{timing.cleanupEndTime}}。系统会重新检查洗护师和工位。</p><button class="button primary full" type="submit" :disabled="busy || !!timing.error">{{busy?'正在调整…':'确认改期'}}</button></form>
 </DialogShell>
</template>
