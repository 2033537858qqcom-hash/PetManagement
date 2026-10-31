export type Role = '店长' | '前台' | '洗护师' | '宠主' | '管理员'
export interface SessionAccount { username: string; role: Role; name: string; title: string; customerId: number | null; employeeId: number | null }
export interface RegistrationInput { username: string; password: string; name: string; phone: string }
export function normalizeUsername(username: string) {
  const value = username.trim()
  return value.includes('@') ? value.toLowerCase() : value
}
export function validUsername(username: string) {
  return /^[A-Za-z0-9_]{3,32}$/.test(username) || (username.length <= 64 && /^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/.test(username))
}
export const demoAccounts: SessionAccount[] = [
  { username: 'manager', role: '店长', name: '林晓晴', title: '旗舰店店长', customerId: null, employeeId: null },
  { username: 'reception', role: '前台', name: '陈雨桐', title: '前台接待', customerId: null, employeeId: null },
  { username: 'groomer', role: '洗护师', name: '周凯', title: '首席美容师', customerId: null, employeeId: 1 },
  { username: 'owner', role: '宠主', name: '林知夏', title: '金卡会员', customerId: 1, employeeId: null },
  { username: 'admin', role: '管理员', name: '系统管理员', title: '系统管理', customerId: null, employeeId: null },
]
export function sessionAccount(raw?: Partial<SessionAccount> | null): SessionAccount {
  const preset = demoAccounts.find(account => account.username === raw?.username) || demoAccounts.find(account => account.role === raw?.role) || demoAccounts[0]
  const role = demoAccounts.some(account => account.role === raw?.role) ? raw!.role as Role : preset.role
  return {
    username: raw?.username || preset.username,
    role,
    name: raw?.name || preset.name,
    title: raw?.title || preset.title,
    customerId: raw?.customerId === undefined ? preset.customerId : raw.customerId,
    employeeId: raw?.employeeId === undefined ? preset.employeeId : raw.employeeId,
  }
}
export function navigationFor(role: Role) {
  if (role === '宠主') return [{ label: '总览', text: '我的主页' }, { label: '宠物', text: '我的爱宠' }, { label: '排班', text: '我的预约' }, { label: '工单', text: '护理进度' }, { label: '订单', text: '我的订单' }, { label: '会员', text: '会员账户' }, { label: '服务', text: '探索护理' }]
  if (role === '洗护师') return [{ label: '总览', text: '我的工作台' }, { label: '工单', text: '我的护理工单' }, { label: '排班', text: '我的预约安排' }, { label: '宠物', text: '宠物档案' }, { label: '服务', text: '护理项目' }]
  if (role === '前台') return [{ label: '总览', text: '今日接待' }, { label: '排班', text: '预约排班' }, { label: '工单', text: '到店与接宠' }, { label: '宠物', text: '宠物档案' }, { label: '会员', text: '会员账户' }, { label: '服务', text: '洗护项目' }, { label: '订单', text: '商品订单' }]
  if (role === '管理员') return [{ label: '总览', text: '系统总览' }, { label: '排班', text: '预约与排班' }, { label: '工单', text: '洗护工单' }, { label: '宠物', text: '宠物档案' }, { label: '会员', text: '会员档案' }, { label: '服务', text: '服务项目' }, { label: '员工', text: '员工与权限' }, { label: '库存', text: '耗材库存' }, { label: '订单', text: '商品订单' }, { label: '图片', text: '图片管理' }, { label: '设置', text: '门店设置' }]
  return [{ label: '总览', text: '门店总览' }, { label: '排班', text: '预约与排班' }, { label: '工单', text: '洗护工单' }, { label: '宠物', text: '宠物档案' }, { label: '会员', text: '会员与次卡' }, { label: '服务', text: '洗护项目' }, { label: '员工', text: '员工团队' }, { label: '库存', text: '库存与耗材' }, { label: '订单', text: '商品订单' }, { label: '设置', text: '门店设置' }]
}
export const homeView = (role: Role) => role === '洗护师' ? '工单' : '总览'
export const canBook = (role: Role) => role === '店长' || role === '前台' || role === '宠主' || role === '管理员'
export const seesInventoryAlerts = (role: Role) => role === '店长' || role === '管理员'
export interface Pet { id:number; customer_id:number; name:string; species:string; breed:string; size_type:string; weight:number; coat:string; health_note:string; age_band?:string; age_label?:string; allergy?:string; allergy_label?:string; skin_history?:string; skin_label?:string; grooming_history?:string; grooming_label?:string; risks?:string[]; photo_url?:string }
export interface SizeRule { size_type: string; price: number; duration: number }
export interface Service { id:number; name:string; category:string; price:number; duration:number; species:string; enabled?:boolean; size_rules?:SizeRule[] }
export interface Employee { id:number; name:string; title:string; skills:string; species:string; sizes:string; enabled?:boolean | number }
export interface Resource { id:number; name:string; species:string; enabled?:boolean | number }
export interface Appointment { id:number; customer_id:number; pet_id:number; service_id:number; employee_id:number; resource_id:number; appointment_date:string; start_time:string; end_time:string; price:number; status:string; pet_name:string; service_name:string; employee_name:string; resource_name:string }
export interface Grooming { id:number; pet_id:number; customer_id:number; employee_id:number; service_id:number; stage:string; status:string; pickup_code:string; pet_name:string; employee_name:string; service_name:string; appointment_id?:number|null; resource_id?:number|null; precheck_note?:string|null }
export interface Addon { id:number; grooming_id:number; name:string; reason:string; amount:number; status:string; pet_name:string }
export interface Customer { id:number; name:string; phone:string; level_name:string; balance:number; points:number }
export interface Card { id:number; customer_id:number; name:string; remaining:number; total:number; customer_name:string; service_ids?:number[] }
export interface Product { id:number; name:string; stock:number; threshold:number; unit:string; price?:number; sale_units?:number; image_key?:string|null }
export interface HealthRecord { id:number; pet_id:number; recorded_on:string; weight:number; score:number; skin_label?:string; coat_label?:string; ear_label?:string; groomer_note?:string|null }
export interface ShopOrder { id:number; customer_id:number; product_id:number; product_name:string; quantity:number; unit_price:number; amount:number; status:string; created_at:string; customer_name?:string; phone?:string; pay_method?:string }
export interface Review { id:number; rating:number; content:string; grooming_id?:number|null; customer_name?:string; pet_name?:string }
export interface Recommendation extends Service { score:number; reason:string }
export const labels:Record<string,string> = { CONFIRMED:'待到店', IN_SERVICE:'服务中', COMPLETED:'已完成', GROOMING:'护理中', WAITING_PICKUP:'待接宠', PENDING:'待确认', APPROVED:'已同意', REJECTED:'已拒绝', BATHING:'沐浴清洁', DRYING:'吹干护理', DONE:'护理完成', TRIMMING:'精修造型', CANCELLED:'已取消', NO_SHOW:'未到店', SUBMITTED:'待收款', PAID:'已收款', REFUNDED:'已退款', CHECK_IN:'接宠检查', HANDOVER:'已交宠', ADDON_APPROVED:'已同意加项', ADDON_REJECTED:'已拒绝加项', REASSIGN:'改派洗护师', ROLLBACK_CHECK_IN:'回退至接宠检查', ROLLBACK_BATHING:'回退至沐浴清洁', ROLLBACK_DRYING:'回退至吹干护理' }
export const sizeLabels:Record<string,string> = { SMALL:'小型',MEDIUM:'中型',LARGE:'大型',GIANT:'巨型' }
export const portraits:Record<number,string> = {1:'/images/nuomi.png',2:'/images/wangcai.jpg',3:'/images/xueqiu.jpg',4:'/images/mianhua.jpg'}
export const money = (value:number) => new Intl.NumberFormat('zh-CN',{minimumFractionDigits:2,maximumFractionDigits:2}).format(value)
export function localDate(date=new Date()) { return `${date.getFullYear()}-${String(date.getMonth()+1).padStart(2,'0')}-${String(date.getDate()).padStart(2,'0')}` }
export function shiftedDate(date:string,days:number) { const d=new Date((date || localDate())+'T12:00:00'); d.setDate(d.getDate()+days); return localDate(d) }
export const clock = (time:string) => time.slice(0,5)
export function scopedPets<T extends { customer_id:number }>(pets:T[],role:string,customerId:number):T[] { return role==='宠主'?pets.filter(p=>p.customer_id===customerId):pets }
export function estimatePrice(price:number,size:string,sizeRules?:{size_type:string;price:number}[]) {
 const matched=sizeRules?.find(r=>r.size_type===size)
 if(matched) return Number(matched.price)
 return price + (['LARGE','GIANT'].includes(size)?50:0)
}
export function serviceDuration(service:{duration:number;size_rules?:{size_type:string;duration:number}[]},size?:string) {
 const matched=service.size_rules?.find(r=>r.size_type===size)
 return matched ? Number(matched.duration) : service.duration
}
const minuteLabel = (minutes: number) => `${String(Math.floor(minutes / 60)).padStart(2, '0')}:${String(minutes % 60).padStart(2, '0')}`
export function suggestedTime(date: string, now = new Date()) {
 if (date !== localDate(now)) return '11:30'
 const next = Math.max(540, Math.ceil((now.getHours() * 60 + now.getMinutes() + 1) / 15) * 15)
 return minuteLabel(Math.min(next, 1260))
}
export function bookingTiming(date: string, time: string, duration: number, now = new Date()) {
 const latestStart = minuteLabel(Math.max(540, 1260 - duration - 15))
 const result = { error: '', field: null as 'date' | 'time' | 'service' | null, endTime: '', cleanupEndTime: '', latestStart }
 const invalid = (field: 'date' | 'time' | 'service', error: string) => ({ ...result, field, error })
 if (!/^\d{4}-\d{2}-\d{2}$/.test(date)) return invalid('date', '请选择有效的预约日期。')
 const day = new Date(date + 'T12:00:00')
 if (Number.isNaN(day.getTime()) || localDate(day) !== date) return invalid('date', '预约日期无效，请重新选择。')
 if (!/^([01]\d|2[0-3]):[0-5]\d$/.test(time)) return invalid('time', '请选择有效的开始时间。')
 if (!Number.isInteger(duration) || duration <= 0 || duration > 705) return invalid('service', '该项目的护理时长异常，请选择其他项目。')
 const [h, m] = time.split(':').map(Number)
 const start = h * 60 + m
 if (start < 540) return invalid('time', '门店 09:00 开始营业，请选择稍晚的时间。')
 if (start + duration + 15 > 1260) return invalid('time', `含 15 分钟清洁缓冲，本项目最晚可于 ${latestStart} 开始。`)
 if (new Date(`${date}T${time}:00`).getTime() <= now.getTime()) return invalid(date < localDate(now) ? 'date' : 'time', '这个时间已过去，请选择稍晚的时段或其他日期。')
 return { ...result, endTime: minuteLabel(start + duration), cleanupEndTime: minuteLabel(start + duration + 15) }
}
export function scheduleBlock(start:string,end:string) {
 const minutes=(value:string)=>{const [h,m]=value.split(':').map(Number);return h*60+m}
 const a=minutes(start),b=minutes(end),range=720
 if(!Number.isFinite(a)||!Number.isFinite(b)||b<=a||b<=540||a>=1260) return null
 const left=Math.max(a,540),right=Math.min(b,1260)
 return {left:(left-540)*100/range,width:(right-left)*100/range,bufferWidth:Math.max(0,Math.min(b+15,1260)-Math.max(b,540))*100/range}
}
export function careStages(stage:string) {
 const stages=[{code:'CHECK_IN',label:'接宠检查'},{code:'BATHING',label:'沐浴清洁'},{code:'DRYING',label:'吹干护理'},{code:'TRIMMING',label:'精修造型'},{code:'DONE',label:'完成待接'}]
 const current=stages.findIndex(s=>s.code===stage)
 return stages.map((s,i)=>({...s,state:stage==='DONE'||(current>=0&&i<current)?'done':i===current?'current':'next'}))
}
