<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { ImagePlus, LoaderCircle, Upload, X } from 'lucide-vue-next'
import AppSelect from './AppSelect.vue'
import { api } from '../lib/api'
import type { Customer, Employee, Product, Resource } from '../lib/workspace'
import { setupSpeciesOptions } from '../lib/catalog'
const CUSTOM = '__custom__'
const props=defineProps<{kind:'customer'|'employee'|'resource'|'product'; items?: (Customer | Employee | Resource | Product)[]}>()
const emit=defineEmits<{saved:[message:string]}>()
const names={customer:'宠主',employee:'洗护师',resource:'工位',product:'耗材'}
const label=computed(()=>names[props.kind])
const open=ref(false), editingId=ref<number | null>(null), busy=ref(false), error=ref('')
const name=ref(''), phone=ref(''), title=ref(''), skills=ref(''), species=ref('DOG'), customSpecies=ref(''), sizes=ref<string[]>(['SMALL','MEDIUM']), unit=ref(''), threshold=ref('0'), enabled=ref<number>(1)
const speciesChoices=computed(() => {
  const options=[...setupSpeciesOptions]
  if (species.value && species.value !== CUSTOM && !options.some(item => item.value === species.value)) options.push({ value: species.value, label: species.value })
  options.push({ value: CUSTOM, label: '其他（自行填写）' })
  return options
})
function resolvedSpecies() { return (species.value === CUSTOM ? customSpecies.value : species.value).trim() }
const photoFile=ref<File>(), photoPreview=ref(''), photoError=ref(''), photoPicker=ref<HTMLInputElement>()
function revokePreview() { if(photoPreview.value) URL.revokeObjectURL(photoPreview.value) }
function clearPhoto() {
 revokePreview(); photoPreview.value=''; photoFile.value=undefined; photoError.value=''; error.value=''
 if(photoPicker.value) photoPicker.value.value=''
}
function selectPhoto(file?:File) {
 if(!file || busy.value) return
 if(!['image/jpeg','image/png'].includes(file.type)) { photoError.value='请选择 JPG 或 PNG 图片。'; error.value=photoError.value; return }
 if(!file.size || file.size>5*1024*1024) { photoError.value='请选择大小不超过 5 MB 的非空图片。'; error.value=photoError.value; return }
 revokePreview(); photoFile.value=file; photoPreview.value=URL.createObjectURL(file); photoError.value=''; error.value=''
}
function photoChanged(event:Event) { const input=event.target as HTMLInputElement; selectPhoto(input.files?.[0]); input.value='' }
function photoDropped(event:DragEvent) { selectPhoto(event.dataTransfer?.files[0]) }
onBeforeUnmount(revokePreview)
function startCreate() {
 if (open.value && editingId.value === null) { open.value = false; return }
 editingId.value = null; error.value = ''
 name.value = ''; phone.value = ''; title.value = ''; skills.value = ''; species.value = 'DOG'; customSpecies.value = ''; sizes.value = ['SMALL','MEDIUM']; unit.value = ''; threshold.value = '0'; enabled.value = 1
 clearPhoto()
 open.value = true
}
function startEdit(item: Record<string, unknown>) {
 editingId.value = Number(item.id); error.value = ''
 name.value = String(item.name || '')
 phone.value = String(item.phone || '')
 title.value = String(item.title || '')
 skills.value = String(item.skills || '')
 const raw = String(item.species || 'DOG')
 const known = setupSpeciesOptions.some(item => item.value === raw) || raw === CUSTOM
 species.value = known ? raw : CUSTOM
 customSpecies.value = known ? '' : raw
 sizes.value = typeof item.sizes === 'string' ? item.sizes.split(',').filter(Boolean) : ['SMALL','MEDIUM']
 unit.value = String(item.unit || '')
 threshold.value = String(item.threshold ?? '0')
 enabled.value = item.enabled !== false && item.enabled !== 0 ? 1 : 0
 clearPhoto()
 open.value = true
}
async function submit() {
 if(busy.value) return
 if(props.kind==='product' && photoError.value) { error.value=photoError.value; return }
 if(props.kind==='employee' && !sizes.value.length) { error.value='请至少选择一种适用体型。'; return }
 if((props.kind==='employee' || props.kind==='resource') && !resolvedSpecies()) { error.value='请选择或填写适用种类。'; return }
 busy.value=true; error.value=''
 const kindSpecies=resolvedSpecies()
 const payload=props.kind==='customer'?{name:name.value.trim(),phone:phone.value.trim()}:props.kind==='employee'?{name:name.value.trim(),title:title.value.trim(),skills:skills.value.trim(),species:kindSpecies,sizes:sizes.value,enabled:enabled.value===1}:props.kind==='resource'?{name:name.value.trim(),species:kindSpecies,enabled:enabled.value===1}:{name:name.value.trim(),unit:unit.value.trim(),threshold:Number(threshold.value)}
 const baseEndpoint={customer:'/customers',employee:'/employees',resource:'/resources',product:'/inventory'}[props.kind]
 const isEdit = editingId.value !== null
 const endpoint = isEdit ? `${baseEndpoint}/${editingId.value}` : baseEndpoint
 try {
  if(!isEdit && props.kind==='product' && photoFile.value) {
   const body=new FormData(); body.append('product',new Blob([JSON.stringify(payload)],{type:'application/json'})); body.append('image',photoFile.value)
   await api(endpoint,{method:'POST',body})
  } else await api(endpoint,{method:isEdit?'PUT':'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(payload)})
  open.value=false; editingId.value=null
  if(props.kind==='product') { clearPhoto(); name.value=''; unit.value=''; threshold.value='0' }
  emit('saved', isEdit ? `${label.value}资料已更新。` : `${label.value}已建立。`)
 }
 catch(e) { error.value=(e as Error).message } finally {busy.value=false}
}
</script>
<template>
 <section class="surface setup-panel"><div class="section-head"><div><h2>{{label}}管理</h2><p class="field-help">建立或维护门店使用的{{label}}资料。</p></div><div style="display:flex;gap:0.5rem;align-items:center"><AppSelect v-if="items?.length" :model-value="editingId || 0" :label="`编辑已有${label}`" :options="[{value:0,label:`选择编辑${label}…`}, ...items.map((item: any) => ({value:item.id,label:`${item.name}${item.enabled===false||item.enabled===0?'（已停用）':''}`}))]" compact :disabled="busy" @update:model-value="(val: any) => { const found = items?.find((i: any) => i.id === Number(val)); if (found) startEdit(found as any); else { editingId = null; open = false } }" /><button class="button secondary" type="button" :disabled="busy" @click="startCreate">{{open && editingId===null?'收起':`新增${label}`}}</button></div></div>
  <form v-if="open" class="booking-form setup-form" @submit.prevent="submit"><div v-if="error" class="inline-error" role="alert">{{error}}</div>
   <template v-if="kind==='customer'"><div class="form-columns"><label class="field">宠主姓名<input v-model="name" maxlength="60" required :disabled="busy" /></label><label class="field">联系电话<input v-model="phone" type="tel" pattern="\+?[0-9]{6,20}" required :disabled="busy" /></label></div></template>
   <template v-else-if="kind==='employee'"><div class="form-columns"><label class="field">员工姓名<input v-model="name" maxlength="60" required :disabled="busy" /></label><label class="field">员工职位<input v-model="title" maxlength="60" required :disabled="busy" /></label></div><label class="field">护理技能<input v-model="skills" maxlength="200" required :disabled="busy" placeholder="例如基础洗护、造型修剪" /></label><div class="form-columns"><div class="field"><span>适用种类</span><AppSelect v-model="species" label="适用种类" :options="speciesChoices" :disabled="busy" /></div><div v-if="editingId!==null" class="field"><span>在岗状态</span><AppSelect v-model="enabled" label="在岗状态" :options="[{value:1,label:'在职接单'},{value:0,label:'停用休整'}]" :disabled="busy" /></div></div><label v-if="species===CUSTOM" class="field">自定义种类<input v-model="customSpecies" maxlength="20" required :disabled="busy" placeholder="例如 兔子、龙猫" /></label><fieldset :disabled="busy"><legend>适用体型</legend><div class="setup-checks"><label v-for="item in [{value:'SMALL',label:'小型'},{value:'MEDIUM',label:'中型'},{value:'LARGE',label:'大型'},{value:'GIANT',label:'巨型'}]" :key="item.value"><input v-model="sizes" type="checkbox" :value="item.value" /> {{item.label}}</label></div></fieldset></template>
   <template v-else-if="kind==='resource'"><label class="field">工位名称<input v-model="name" maxlength="80" required :disabled="busy" /></label><div class="form-columns"><div class="field"><span>适用种类</span><AppSelect v-model="species" label="适用种类" :options="speciesChoices" :disabled="busy" /></div><div v-if="editingId!==null" class="field"><span>工位状态</span><AppSelect v-model="enabled" label="工位状态" :options="[{value:1,label:'正常启用'},{value:0,label:'暂停使用'}]" :disabled="busy" /></div></div><label v-if="species===CUSTOM" class="field">自定义种类<input v-model="customSpecies" maxlength="20" required :disabled="busy" placeholder="例如 兔子、龙猫" /></label></template>
   <template v-else>
    <label class="field">耗材名称<input v-model="name" maxlength="100" required :disabled="busy" /></label>
    <div v-if="editingId===null" class="field"><span>耗材图片 <small class="optional-field">选填</small></span>
     <div class="product-photo-upload" :class="{'has-image':!!photoPreview,'is-busy':busy}" @dragover.prevent @drop.prevent="photoDropped">
      <div class="product-photo-preview"><img v-if="photoPreview" :src="photoPreview" alt="耗材图片预览"/><ImagePlus v-else :size="28" :stroke-width="1.4"/></div>
      <div class="product-photo-details"><strong>{{photoFile ? photoFile.name : '为这件耗材添加图片'}}</strong><p>支持 JPG、PNG，最大 5 MB。也可将图片拖到这里。</p><div class="product-photo-actions"><button type="button" class="button quiet small" :disabled="busy" aria-label="选择耗材图片" @click="photoPicker?.click()"><Upload :size="14"/>{{photoFile?'重新选择':'选择图片'}}</button><button v-if="photoFile || photoError" type="button" class="product-photo-remove" :disabled="busy" aria-label="移除耗材图片" @click="clearPhoto"><X :size="13"/>移除</button></div></div>
      <input ref="photoPicker" type="file" class="sr-only" tabindex="-1" accept="image/jpeg,image/png" aria-label="耗材图片" :disabled="busy" @change="photoChanged"/>
     </div>
    </div>
    <div class="form-columns"><label class="field">库存单位<input v-model="unit" maxlength="20" required :disabled="busy" placeholder="例如 ml、件" /></label><label class="field">预警阈值<input v-model="threshold" type="number" min="0" max="1000000" step="1" required :disabled="busy" /></label></div>
   </template>
   <button class="button primary" type="submit" :disabled="busy"><LoaderCircle v-if="busy" class="spin" :size="15"/>{{busy?'正在保存…':`保存${label}`}}</button>
  </form>
 </section>
</template>
