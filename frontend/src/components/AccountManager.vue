<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import AppSelect from './AppSelect.vue'
import { api } from '../lib/api'
import { normalizeUsername } from '../lib/workspace'
import type { Customer, Employee } from '../lib/workspace'

type Account = { username:string; role_code:string; display_name:string; title:string; enabled:boolean | number; customer_id?:number; employee_id?:number }
const props = defineProps<{ customers:Customer[]; employees:Employee[]; currentUsername:string; isAdmin?:boolean }>()
const accounts = ref<Account[]>([])
const showForm = ref(false), busy = ref(false), error = ref(''), notice = ref('')
const username = ref(''), password = ref(''), role = ref('OWNER'), name = ref(''), title = ref('')
const customerId = ref<number>(), employeeId = ref<number>()
const resetAccount = ref(''), newPassword = ref('')
const allRoles = [{value:'OWNER',label:'宠主'},{value:'GROOMER',label:'洗护师'},{value:'RECEPTION',label:'前台'},{value:'MANAGER',label:'店长'},{value:'ADMIN',label:'管理员'}]
const roles = computed(() => props.isAdmin === false ? allRoles.filter(r => ['OWNER', 'GROOMER', 'RECEPTION'].includes(r.value)) : allRoles)
const roleName = (code:string) => allRoles.find(item => item.value === code)?.label || code
const canManageAccount = (account:Account) => props.isAdmin !== false || !['ADMIN', 'MANAGER'].includes(account.role_code)
const bound = computed(() => role.value === 'OWNER' ? !!customerId.value : role.value === 'GROOMER' ? !!employeeId.value : !!name.value.trim() && !!title.value.trim())
async function load() { accounts.value = await api<Account[]>('/accounts') }
async function create() {
 if (busy.value || !bound.value) return
 busy.value = true; error.value = ''; notice.value = ''
 try {
  await api('/accounts', { method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({username:normalizeUsername(username.value),password:password.value,role:role.value,customerId:role.value==='OWNER'?customerId.value:null,employeeId:role.value==='GROOMER'?employeeId.value:null,displayName:name.value.trim(),title:title.value.trim()}) })
  await load(); username.value=''; password.value=''; name.value=''; title.value=''; customerId.value=undefined; employeeId.value=undefined; showForm.value=false; notice.value='账号已创建。'
 } catch (reason) { error.value=(reason as Error).message } finally { busy.value=false }
}
async function toggle(account:Account) {
 if (busy.value) return
 busy.value=true; error.value=''; notice.value=''
 try { await api(`/accounts/${encodeURIComponent(account.username)}/status`, { method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify({enabled:!account.enabled}) }); await load(); notice.value=account.enabled?'账号已停用。':'账号已启用。' }
 catch (reason) { error.value=(reason as Error).message } finally { busy.value=false }
}
async function resetPassword() {
 if (busy.value || !resetAccount.value) return
 busy.value=true; error.value=''; notice.value=''
 try {
  await api(`/accounts/${encodeURIComponent(resetAccount.value)}/password`, { method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify({password:newPassword.value}) })
  resetAccount.value=''; newPassword.value=''; notice.value='密码已重置，旧登录已失效。'
 } catch (reason) { error.value=(reason as Error).message } finally { busy.value=false }
}
async function remove(account:Account) {
 if (busy.value || account.username===props.currentUsername) return
 if (!window.confirm(`确定要注销并删除账号 ${account.username} 吗？删除后该人员可重新绑定新账号。`)) return
 busy.value=true; error.value=''; notice.value=''
 try {
  await api(`/accounts/${encodeURIComponent(account.username)}`, { method:'DELETE' })
  await load()
  notice.value='账号已删除，原绑定资料已解除锁定。'
 } catch (reason) { error.value=(reason as Error).message } finally { busy.value=false }
}
onMounted(async () => { try { await load() } catch (reason) { error.value=(reason as Error).message } })
</script>
<template>
 <section class="surface account-management">
  <div class="section-head"><div><h2>账号与权限</h2><p class="field-help">为每位员工或宠主建立独立账号，并绑定相应资料。</p></div><button class="button secondary" type="button" @click="showForm=!showForm">{{showForm?'收起':'新增账号'}}</button></div>
  <div v-if="error" class="inline-error" role="alert">{{error}}</div><p v-if="notice" role="status" class="field-help">{{notice}}</p>
  <form v-if="showForm" class="booking-form account-form" @submit.prevent="create">
   <div class="form-columns"><label class="field">登录账号<input v-model="username" required pattern="([A-Za-z0-9_]{3,32}|[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,})" maxlength="64" autocomplete="off" placeholder="字母、数字、下划线，或邮箱" :disabled="busy" /></label><label class="field">初始密码<input v-model="password" type="password" required minlength="12" maxlength="72" autocomplete="new-password" placeholder="至少 12 位" :disabled="busy" /></label></div>
   <div class="field"><span>角色</span><AppSelect v-model="role" label="角色" :options="roles" :disabled="busy" /></div>
   <div v-if="role==='OWNER'" class="field"><span>绑定宠主</span><AppSelect v-model="customerId" label="绑定宠主" :options="customers.map(c=>({value:c.id,label:`${c.name} · ${c.phone}`}))" placeholder="请选择宠主" :disabled="busy" /></div>
   <div v-else-if="role==='GROOMER'" class="field"><span>绑定洗护师</span><AppSelect v-model="employeeId" label="绑定洗护师" :options="employees.map(e=>({value:e.id,label:`${e.name} · ${e.title}`}))" placeholder="请选择洗护师" :disabled="busy" /></div>
   <div v-else class="form-columns"><label class="field">姓名<input v-model="name" required maxlength="60" :disabled="busy" /></label><label class="field">职位<input v-model="title" required maxlength="60" :disabled="busy" /></label></div>
   <button class="button primary" type="submit" :disabled="busy || !bound">创建账号</button>
  </form>
  <div class="table-scroll"><table><thead><tr><th>账号</th><th>姓名</th><th>角色</th><th>状态</th><th>操作</th></tr></thead><tbody><tr v-for="account in accounts" :key="account.username"><td>{{account.username}}</td><td>{{account.display_name}}</td><td>{{roleName(account.role_code)}}</td><td>{{account.enabled?'已启用':'已停用'}}</td><td><div v-if="canManageAccount(account)" class="account-actions"><button class="text-link" type="button" :disabled="busy || account.username===currentUsername" @click="toggle(account)">{{account.enabled?'停用账号':'启用账号'}}</button><button class="text-link" type="button" :disabled="busy" @click="resetAccount=account.username;newPassword=''">重置密码</button><button class="text-link" type="button" :disabled="busy || account.username===currentUsername" style="color:var(--rose,#e11d48)" @click="remove(account)">删除</button></div><span v-else class="field-help">受限</span></td></tr></tbody></table></div>
  <form v-if="resetAccount" class="booking-form account-form" @submit.prevent="resetPassword"><p>为 {{resetAccount}} 重置密码。重置后该账号需要重新登录。</p><label class="field">新密码<input v-model="newPassword" type="password" minlength="12" maxlength="72" autocomplete="new-password" required :disabled="busy" /></label><div class="account-actions"><button class="button primary" type="submit" :disabled="busy">确认重置密码</button><button class="button secondary" type="button" :disabled="busy" @click="resetAccount='';newPassword=''">取消</button></div></form>
 </section>
</template>
