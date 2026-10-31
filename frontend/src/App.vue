<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { Activity, ArrowRight, ArrowUpRight, Bell, CalendarDays, CheckCircle2, ChevronLeft, ChevronRight, Clock3, Heart, Images, LayoutGrid, LoaderCircle, Package, PawPrint, Plus, Search, Settings, ShieldCheck, ShoppingBag, SlidersHorizontal, Sparkles, Users, WalletCards, X } from 'lucide-vue-next'
import PetPortrait from './components/PetPortrait.vue'
import AppSelect from './components/AppSelect.vue'
import AppDatePicker from './components/AppDatePicker.vue'
import StageTimeline from './components/StageTimeline.vue'
import GanttSchedule from './components/GanttSchedule.vue'
import DialogShell from './components/DialogShell.vue'
import BookingDialog from './components/BookingDialog.vue'
import PetDialog from './components/PetDialog.vue'
import ServiceDialog from './components/ServiceDialog.vue'
import WalkInDialog from './components/WalkInDialog.vue'
import HealthScoreDialog from './components/HealthScoreDialog.vue'
import HealthTrend from './components/HealthTrend.vue'
import AccountManager from './components/AccountManager.vue'
import InventoryManager from './components/InventoryManager.vue'
import StoreSetupPanel from './components/StoreSetupPanel.vue'
import MembershipManager from './components/MembershipManager.vue'
import RetailOrderActions from './components/RetailOrderActions.vue'
import AddonRequest from './components/AddonRequest.vue'
import ScheduleManager from './components/ScheduleManager.vue'
import RescheduleDialog from './components/RescheduleDialog.vue'
import PhotoManager from './components/PhotoManager.vue'
import StoreSettingsPanel from './components/StoreSettingsPanel.vue'
import PaymentModal, { type PaymentInfo } from './components/PaymentModal.vue'
import { photoAssets, photoUrl, syncPetPhotos } from './lib/photos'
import { api, ApiError } from './lib/api'
import type { Addon, Appointment, Card, Customer, Employee, Grooming, HealthRecord, Pet, Product, Recommendation, Resource, Review, Service, SessionAccount, ShopOrder } from './lib/workspace'
import { canBook, clock, homeView, labels, localDate, money, navigationFor, scopedPets, seesInventoryAlerts, shiftedDate, sizeLabels } from './lib/workspace'
import { speciesLabel } from './lib/catalog'

const props = defineProps<{ account: SessionAccount }>()
defineEmits<{ exit: [] }>()
const role = computed(() => props.account.role)
const view = ref(homeView(role.value))
const ownerId = computed(() => props.account.customerId ?? 1)
const staffId = computed(() => props.account.employeeId ?? 1)
const navIcons = { 总览: LayoutGrid, 排班: CalendarDays, 工单: Activity, 宠物: PawPrint, 会员: WalletCards, 服务: Sparkles, 员工: Users, 库存: Package, 订单: ShoppingBag, 图片: Images, 设置: Settings }
const pets = ref<Pet[]>([]), services = ref<Service[]>([]), employees = ref<Employee[]>([]), appointments = ref<Appointment[]>([]), grooming = ref<Grooming[]>([]), addons = ref<Addon[]>([]), customers = ref<Customer[]>([]), cards = ref<Card[]>([]), inventory = ref<Product[]>([]), orders = ref<ShopOrder[]>([]), healthRecords = ref<HealthRecord[]>([]), resources = ref<Resource[]>([]), reviews = ref<Review[]>([])
const reviewRating = ref(5), reviewContent = ref(''), reviewBusy = ref(false)
const precheckNote = ref(''), reassignGroomerId = ref<number>()
const passwordDialogOpen = ref(false), oldPassword = ref(''), selfNewPassword = ref(''), passwordBusy = ref(false), passwordError = ref('')
const loading = ref(true), loadError = ref(''), busy = ref(false), toast = ref(''), toastError = ref(false)
const query = ref(''), searchOpen = ref(false), taskTray = ref(false), bookingOpen = ref(false)
const taskPanel = ref<HTMLElement>(), taskTrigger = ref<HTMLElement>(), searchPanel = ref<HTMLElement>()
const bookingPet = ref<number>(), bookingDate = ref<string>()
const bookingService = ref<number>()
const selectedDate = ref(localDate()), serviceFilter = ref('全部'), stockFilter = ref('全部'), petFilter = ref('全部'), statusFilter = ref('全部')
const recommendations = ref<Recommendation[]>([]), recommendedPetId = ref(1)
const drawer = ref<{ kind: 'pet' | 'appointment' | 'grooming'; id: number } | null>(null)
const petDialog = ref(false), editingPet = ref<Pet | null>(null), serviceEditor = ref<Service | null | undefined>(undefined), walkInOpen = ref(false), scoring = ref<Grooming | null>(null), cancelArmed = ref(false), serviceBusy = ref(0)
const rescheduling=ref<Appointment|null>(null)
const orderQty = ref<Record<number, number>>({})
const finance = ref({ todayRevenue: 0, todayServiceRevenue: 0, todayRetailRevenue: 0, todayRefundAmount: 0, unpaidCount: 0, paymentMethods: [] as { method: string; count: number; amount: number }[] })
const report = ref({ totalRevenue: 0, serviceRevenue: 0, retailRevenue: 0, refundAmount: 0, netRevenue: 0, completedGroomingCount: 0, totalRetailOrders: 0, paymentMethods: [] as { method: string; count: number; amount: number }[] })
type StoreNotice = { id: number; category: string; title: string; content: string; related_no?: string; unread: number | boolean; urgent?: number | boolean; created_at: string }
const notifications = ref<StoreNotice[]>([])
const storeInfo = ref({ storeName: '爪印宠护 · 旗舰店', businessHours: '09:00 - 21:00', phone: '', address: '' })
function payMethodLabel(method?: string) {
  return ({ ALIPAY: '支付宝', CASH: '现金', BALANCE: '会员余额', CARD: '次卡' } as Record<string, string>)[method || ''] || method || '其他'
}
let toastTimer: ReturnType<typeof setTimeout> | undefined
const today = localDate()
// These are illustrative stock photographs, not photos of an actual treatment or product.
function servicePhoto(service: Pick<Service, 'name' | 'category' | 'species'>) {
 if (service.species === 'CAT' || /猫/.test(service.name + service.category)) return photoUrl('/images/care-cat.jpg')
 if (/造型|修剪/.test(service.name + service.category)) return photoUrl('/images/care-styling.jpg')
 if (/SPA|深层/i.test(service.name + service.category)) return photoUrl('/images/care-spa.jpg')
 return photoUrl('/images/care-bath.jpg')
}
function productPhoto(name: string, productId?: number) {
 const imageKey=inventory.value.find(product=>product.id===productId)?.image_key
 if(imageKey) return `/api/media/products/${encodeURIComponent(imageKey)}/image`
 if (/泡腾|碳酸/.test(name)) return photoUrl('/images/product-bath.jpg')
 if (/精华|护毛/.test(name)) return photoUrl('/images/product-serum.jpg')
 return photoUrl('/images/product-shampoo.jpg')
}
function walletPhoto(id: number) {
 return photoUrl(['/images/care-member.jpg', '/images/care-spa.jpg', '/images/care-cat.jpg'][(id - 1 + 3) % 3])
}
const isOwner = computed(() => role.value === '宠主')
const isGroomer = computed(() => role.value === '洗护师')
const isManager = computed(() => role.value === '店长')
const isReception = computed(() => role.value === '前台')
const isAdmin = computed(() => role.value === '管理员')
const bookable = computed(() => canBook(role.value))
const canManageServices = computed(() => isManager.value || isAdmin.value)
const canOpenWalkIn = computed(() => isManager.value || isReception.value || isAdmin.value)
const canManagePets = computed(() => isOwner.value || isManager.value || isAdmin.value || isReception.value)
const pickupInput = ref('')
type SettlementView = { grooming_id:number; customer_id:number; base_amount:number; addon_amount:number; amount:number; status:string; method?:string; trade_no?:string; paid_at?:string }
type GroomingEvent = { id:number; grooming_id:number; event_type:string; operator_name:string; created_at:string }
const settlement = ref<SettlementView | null>(null)
const activePayment = ref<PaymentInfo | null>(null)
const groomingEvents = ref<GroomingEvent[]>([])
const settlementLoading = ref(false)
const paymentMethod = ref('CASH')
const paymentCardId = ref<number>()
const paymentAddonMethod = ref('CASH')
const refundReason = ref('')
const applicableCards = computed(() => {
 if (!selectedGrooming.value) return []
 const cid = selectedGrooming.value.customer_id
 const sid = selectedGrooming.value.service_id
 return cards.value.filter(c => c.customer_id === cid && c.remaining > 0 && Array.isArray(c.service_ids) && c.service_ids.includes(sid))
})
const reassignableEmployees = computed(() => {
 if (!selectedGrooming.value) return []
 const pet = petById(selectedGrooming.value.pet_id)
 if (!pet) return []
 return employees.value.filter(e => e.id !== selectedGrooming.value?.employee_id && (e.enabled !== false && e.enabled !== 0) && e.species === pet.species && String(e.sizes || '').includes(pet.size_type))
})
const retailCustomerId = ref<number>(1)
const stockAlerts = computed(() => seesInventoryAlerts(role.value))
const owner = computed(() => customers.value.find(c => c.id === ownerId.value))
const visiblePets = computed(() => scopedPets(pets.value, role.value, ownerId.value))
function canEditPet(pet: Pet) {
 return (isGroomer.value && visiblePets.value.some(item => item.id === pet.id)) || (canManagePets.value && (!isOwner.value || pet.customer_id === ownerId.value))
}
const visibleAppointments = computed(() => appointments.value.filter(a => (!isOwner.value || a.customer_id === ownerId.value) && (!isGroomer.value || a.employee_id === staffId.value)))
const visibleGrooming = computed(() => grooming.value.filter(g => (!isOwner.value || g.customer_id === ownerId.value) && (!isGroomer.value || g.employee_id === staffId.value)))
const visibleAddons = computed(() => addons.value.filter(a => visibleGrooming.value.some(g => g.id === a.grooming_id)))
const pendingAddons = computed(() => visibleAddons.value.filter(a => a.status === 'PENDING'))
const lowStock = computed(() => inventory.value.filter(p => p.stock <= p.threshold))
const unreadNotice = (item: StoreNotice) => item.unread === true || Number(item.unread) === 1
const unreadNotifications = computed(() => notifications.value.filter(unreadNotice))
const taskCount = computed(() => pendingAddons.value.length + (stockAlerts.value ? lowStock.value.length : 0) + unreadNotifications.value.length)
const todayAppointments = computed(() => visibleAppointments.value.filter(a => a.appointment_date === today))
const dateAppointments = computed(() => visibleAppointments.value.filter(a => a.appointment_date === selectedDate.value))
const scheduled = computed(() => visibleAppointments.value.filter(a => a.appointment_date === selectedDate.value && (statusFilter.value === '全部' || a.status === statusFilter.value)))
const activeGrooming = computed(() => visibleGrooming.value.filter(g => g.status === 'GROOMING'))
const waitingPickup = computed(() => visibleGrooming.value.filter(g => g.status === 'WAITING_PICKUP'))
const filteredPets = computed(() => visiblePets.value.filter(p => petFilter.value === '全部' || (petFilter.value === 'OTHER' ? p.species !== 'DOG' && p.species !== 'CAT' : p.species === petFilter.value)))
const filteredServices = computed(() => services.value.filter(s => (canManageServices.value || s.enabled !== false) && (serviceFilter.value === '全部' || (serviceFilter.value === 'OTHER' ? s.species !== 'DOG' && s.species !== 'CAT' : s.species === 'ALL' || s.species === serviceFilter.value))))
const shopProducts = computed(() => inventory.value.filter(p => Number(p.price) > 0))
const visibleCards = computed(() => cards.value.filter(c => !isOwner.value || c.customer_id === ownerId.value))
const visibleCustomers = computed(() => customers.value.filter(c => !isOwner.value || c.id === ownerId.value))
const filteredInventory = computed(() => inventory.value.filter(p => stockFilter.value === '全部' || p.stock <= p.threshold))
const selectedPet = computed(() => drawer.value?.kind === 'pet' ? visiblePets.value.find(p => p.id === drawer.value?.id) : undefined)
const selectedAppointment = computed(() => drawer.value?.kind === 'appointment' ? visibleAppointments.value.find(a => a.id === drawer.value?.id) : undefined)
const selectedGrooming = computed(() => drawer.value?.kind === 'grooming' ? visibleGrooming.value.find(g => g.id === drawer.value?.id) : undefined)
const drawerTitle = computed(() => selectedPet.value ? `${selectedPet.value.name}的档案` : selectedAppointment.value ? '预约详情' : '护理详情')
const dateLabel = computed(() => new Intl.DateTimeFormat('zh-CN', { month: 'long', day: 'numeric', weekday: 'long' }).format(new Date((selectedDate.value || today) + 'T12:00:00')))
const navigation = computed(() => navigationFor(role.value).map(item => ({ ...item, icon: navIcons[item.label as keyof typeof navIcons] })))
const title = computed(() => navigation.value.find(item => item.label === view.value)?.text || '门店总览')
const navCaption = computed(() => isGroomer.value ? '我的工作' : isReception.value ? '前台接待' : isAdmin.value ? '系统管理' : '门店工作空间')
const intro = computed(() => {
 if (view.value === '总览') return isOwner.value ? '爱宠的每一次变化，都值得用心记录。' : isReception.value ? '核对到店、护理进度和接宠安排。' : isAdmin.value ? '员工、会员与耗材，是门店每天的底座。' : isGroomer.value ? '只看分配给你的预约、工单和经手档案。' : '预约、护理与交接，今天的安排一目了然。'
 if (view.value === '排班') return isOwner.value ? '查看已预约的护理，提前安排下一次见面。' : isGroomer.value ? '只显示分配给你的服务时段。' : isReception.value ? '为到店的小客人安排洗护师与工位。' : '按洗护师查看服务时段，清洁缓冲独立标记。'
 if (view.value === '工单') return isGroomer.value ? '推进分配给你的护理，加项需要宠主确认。' : isReception.value ? '查看谁正在护理、谁可以接回家。' : '关注当前护理阶段，及时处理需要确认的事项。'
 if (view.value === '宠物') return '体型、毛发与健康提醒，照顾从了解开始。'
 if (view.value === '库存') return '先关注需要补充的耗材，让每次护理准备充分。'
 if (view.value === '员工') return isAdmin.value ? '查看在岗洗护师与今日工作量。' : '专业的人，做用心的护理。'
 if (view.value === '会员') return isOwner.value ? '储值、积分和次卡，都在这个账户里。' : '查看会员储值与洗护次卡。'
 if (view.value === '服务') return isOwner.value ? '按爱宠的种类挑选一次护理。' : canManageServices.value ? '维护项目、价格和是否可预约。' : '门店在售的洗护与造型项目。'
 if (view.value === '订单') return isOwner.value ? '挑选洗护用品，订单会记在这个账户下。' : '处理商品订单的收款、取消和退货。'
 if (view.value === '图片') return '维护登录封面、宠物、洗护项目和商品展示图片。'
 if (view.value === '设置') return '更新门店名称、营业时间，并查看关键操作审计。'
 return '查看门店已建立的资料与账户信息。'
})
function petById(id?: number) { return pets.value.find(p => p.id === id) }
function canCancel(appointment: Appointment) { return appointment.status === 'CONFIRMED' && (isOwner.value ? appointment.customer_id === ownerId.value : isManager.value || isAdmin.value || isReception.value) }
function editPet(pet: Pet) { drawer.value = null; editingPet.value = pet; petDialog.value = true }
function customerName(groomingId: number) {
 const customerId = grooming.value.find(item => item.id === groomingId)?.customer_id
 return customers.value.find(item => item.id === customerId)?.name || '宠主'
}
const searchResults = computed(() => {
 const q=query.value.trim().toLowerCase(); if(!q) return []
 return [...visiblePets.value.filter(p => `${p.name} ${p.breed}`.toLowerCase().includes(q)).map(p => ({kind:'pet' as const,id:p.id,title:p.name,detail:p.breed})),...visibleAppointments.value.filter(a=>`${a.pet_name} ${a.employee_name} ${a.service_name}`.toLowerCase().includes(q)).map(a=>({kind:'appointment' as const,id:a.id,title:a.pet_name,detail:`${a.appointment_date} · ${a.service_name}`}))].slice(0,7)
})
function pauseToast() { clearTimeout(toastTimer) }
function resumeToast() { clearTimeout(toastTimer); if(toast.value && !toastError.value) toastTimer=setTimeout(()=>{toast.value=''},6500) }
function notify(message: string, error = false) {toast.value=message;toastError.value=error;resumeToast()}
function onOnlinePay(info: Record<string, unknown>) {
 activePayment.value = info as unknown as PaymentInfo
}
function go(page: string) {view.value=page;taskTray.value=false;drawer.value=null;statusFilter.value='全部';window.scrollTo(0,0)}
function noticeUnread(item: StoreNotice) { return item.unread === true || Number(item.unread) === 1 }
async function loadNotifications() {
 try { notifications.value = await api<StoreNotice[]>('/notifications') } catch { /* keep current */ }
}
function openNotice(item: StoreNotice) {
 void markNoticeRead(item)
 const related = Number(item.related_no)
 if (['ADDON_CONFIRM','PICKUP_READY','PAYMENT_SUCCESS'].includes(item.category) && Number.isFinite(related) && related > 0) { openGrooming(related); return }
 if (item.category === 'APPOINTMENT') { go('排班'); return }
 if (item.category === 'RECHARGE_SUCCESS' || item.category === 'CARD_PURCHASE') { go('会员'); return }
 if (item.category === 'INVENTORY_ALERT') { go('库存'); return }
 taskTray.value = false
}
async function markNoticeRead(item: StoreNotice) {
 if (!noticeUnread(item)) return
 try {
  await api(`/notifications/${item.id}/read`, { method: 'POST' })
  item.unread = 0
 } catch { /* ignore */ }
}
async function markAllNoticesRead() {
 try {
  await api('/notifications/read-all', { method: 'POST' })
  notifications.value = notifications.value.map(item => ({ ...item, unread: 0 }))
 } catch (e) { notify((e as Error).message, true) }
}
function openBooking(petId?: number, date?: string, serviceId?: number) {
 const service=services.value.find(s=>s.id===serviceId)
 const pet=visiblePets.value.find(p=>p.id===petId) || visiblePets.value.find(p=>!service || service.species==='ALL' || service.species===p.species)
 if(service && !pet){notify('当前家庭暂无适用于该项目的宠物。',true);return}
 drawer.value=null;bookingPet.value=pet?.id;bookingDate.value=date;bookingService.value=serviceId;bookingOpen.value=true
}
let recommendationRequest = 0
async function openPet(petId: number) {
 drawer.value={kind:'pet',id:petId}; healthRecords.value=[]
 try { healthRecords.value = await api<HealthRecord[]>(`/pets/${petId}/records`) } catch { healthRecords.value = [] }
}
function openGrooming(id: number) {drawer.value={kind:'grooming',id};taskTray.value=false}
async function recommend() {
 const request=++recommendationRequest
 const id=visiblePets.value.find(p=>p.id===recommendedPetId.value)?.id || visiblePets.value[0]?.id
 if(!id){recommendations.value=[];return}recommendedPetId.value=id
 try{const results=await api<Recommendation[]>(`/services/recommendations/${id}`);if(request===recommendationRequest)recommendations.value=results}catch{if(request===recommendationRequest)recommendations.value=[]}
}
async function load() {
 loading.value=true;loadError.value=''
 try{
  const fetchResources = (isManager.value || isAdmin.value || isReception.value) ? api<Resource[]>('/resources').catch(() => []) : Promise.resolve([])
  const fetchReviews = api<Review[]>('/reviews').catch(() => [])
  const fetchStore = api<{ storeName: string; businessHours: string; phone?: string; address?: string }>('/store/settings').catch(() => storeInfo.value)
  const fetchNotices = api<StoreNotice[]>('/notifications').catch(() => [] as StoreNotice[])
  const d=await Promise.all([api<Pet[]>('/pets'),api<Service[]>('/services'),api<Employee[]>('/employees'),api<Appointment[]>('/appointments'),api<Grooming[]>('/grooming'),api<Addon[]>('/grooming/addons'),api<Customer[]>('/customers'),api<Card[]>('/members/cards'),api<Product[]>('/inventory'),api<ShopOrder[]>('/orders'),fetchResources,fetchReviews,fetchStore,fetchNotices])
  ;[pets.value,services.value,employees.value,appointments.value,grooming.value,addons.value,customers.value,cards.value,inventory.value,orders.value,resources.value,reviews.value]=d
  storeInfo.value = { storeName: (d[12] as { storeName?: string }).storeName || storeInfo.value.storeName, businessHours: (d[12] as { businessHours?: string }).businessHours || storeInfo.value.businessHours, phone: (d[12] as { phone?: string }).phone || '', address: (d[12] as { address?: string }).address || '' }
  notifications.value = d[13] as StoreNotice[]
  syncPetPhotos(pets.value)
  if (isManager.value || isAdmin.value) {
    try {
      finance.value = await api('/analytics/dashboard') as typeof finance.value
      report.value = await api('/analytics/reports') as typeof report.value
    } catch { /* 金额统计失败不阻断工作台 */ }
  }
  if (customers.value.length && !customers.value.some(c => c.id === retailCustomerId.value)) retailCustomerId.value = customers.value[0].id
  if(isOwner.value) for(const item of inventory.value) {
   const saved=sessionStorage.getItem(`petcare.pending.order.${props.account.username}.${item.id}`)
   if(saved) try { const body=JSON.parse(JSON.parse(saved).body); if(body.productId===item.id && Number.isInteger(body.quantity)) orderQty.value[item.id]=body.quantity } catch { notify('有一笔订单待核对，请刷新后联系门店。',true) }
  }
  await recommend()
 }catch(e){loadError.value=(e as Error).message}finally{loading.value=false}
}
async function onBooked(message:string){
 bookingOpen.value=false
 notify(message)
 try{appointments.value=await api<Appointment[]>('/appointments')}catch{notify('预约已提交成功，列表暂未同步，请刷新后核对。',true)}
}
async function onRescheduled() { rescheduling.value=null; notify('预约已改期。'); try { appointments.value=await api<Appointment[]>('/appointments') } catch { notify('预约已改期，列表暂未同步，请刷新后核对。',true) } }
async function onPetSaved(message: string) { petDialog.value = false; try { pets.value = await api<Pet[]>('/pets'); syncPetPhotos(pets.value); notify(message) } catch { notify('档案已保存，列表暂未同步。', true) } }
async function onServiceSaved(message: string) { serviceEditor.value = undefined; try { services.value = await api<Service[]>('/services'); notify(message) } catch { notify('项目已保存，列表暂未同步。', true) } }
async function onWalkIn(message: string) { walkInOpen.value = false; try { [grooming.value, appointments.value] = await Promise.all([api<Grooming[]>('/grooming'), api<Appointment[]>('/appointments')]); notify(message) } catch { notify('工单已建立，列表暂未同步。', true) } }
async function refreshInventory() { try { inventory.value=await api<Product[]>('/inventory') } catch(e) { notify((e as Error).message,true) } }
async function onAddonRequested() { notify('加项申请已发送，等待宠主确认。'); try { addons.value=await api<Addon[]>('/grooming/addons') } catch { notify('加项申请已发送，列表暂未同步。',true) } }
async function refreshMembership() { try { [customers.value,cards.value]=await Promise.all([api<Customer[]>('/customers'),api<Card[]>('/members/cards')]) } catch(e) { notify((e as Error).message,true) } }
async function onSetupSaved(message:string) { await load(); if (!loadError.value) notify(message); else notify('资料已保存，但列表暂未同步。',true) }
async function checkInAppointment(appointment: Appointment) {
 if (busy.value) return
 busy.value = true
 try {
  const order = await api<Grooming>(`/appointments/${appointment.id}/check-in`, { method: 'POST' })
  ;[appointments.value, grooming.value] = await Promise.all([api<Appointment[]>('/appointments'), api<Grooming[]>('/grooming')])
  drawer.value = { kind: 'grooming', id: order.id }
  notify('已签到并建立洗护工单。')
 } catch (error) { notify((error as Error).message, true) } finally { busy.value = false }
}
async function advanceGrooming(order: Grooming) {
 if (busy.value) return
 busy.value = true
 try {
  const body = order.stage === 'CHECK_IN' && precheckNote.value.trim() ? JSON.stringify({ precheckNote: precheckNote.value.trim() }) : undefined
  await api(`/grooming/${order.id}/advance`, { method: 'POST', headers: body ? { 'Content-Type': 'application/json' } : undefined, body })
  grooming.value = await api<Grooming[]>('/grooming')
  void loadGroomingEvents(order.id)
  precheckNote.value = ''
  notify('护理阶段已更新。')
 } catch (error) { notify((error as Error).message, true) } finally { busy.value = false }
}
async function rollbackGrooming(order: Grooming) {
 if (busy.value) return
 busy.value = true
 try {
  await api(`/grooming/${order.id}/rollback`, { method: 'POST' })
  grooming.value = await api<Grooming[]>('/grooming')
  void loadGroomingEvents(order.id)
  notify('护理阶段已回退。')
 } catch (error) { notify((error as Error).message, true) } finally { busy.value = false }
}
async function reassignGrooming(order: Grooming) {
 if (busy.value || !reassignGroomerId.value) return
 busy.value = true
 try {
  await api(`/grooming/${order.id}/reassign`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ employeeId: reassignGroomerId.value }) })
  ;[appointments.value, grooming.value] = await Promise.all([api<Appointment[]>('/appointments'), api<Grooming[]>('/grooming')])
  void loadGroomingEvents(order.id)
  reassignGroomerId.value = undefined
  notify('洗护师已成功改派。')
 } catch (error) { notify((error as Error).message, true) } finally { busy.value = false }
}
async function voidGrooming(order: Grooming) {
 if (busy.value) return
 if (!window.confirm(`确定要终止并作废 ${order.pet_name} 的本次护理任务吗？`)) return
 busy.value = true
 try {
  await api(`/grooming/${order.id}/void`, { method: 'POST' })
  ;[appointments.value, grooming.value] = await Promise.all([api<Appointment[]>('/appointments'), api<Grooming[]>('/grooming')])
  void loadGroomingEvents(order.id)
  drawer.value = null
  notify('护理工单已作废终止。')
 } catch (error) { notify((error as Error).message, true) } finally { busy.value = false }
}
async function handoverGrooming(order: Grooming) {
 if (busy.value || !pickupInput.value.trim()) return
 busy.value = true
 try {
  await api(`/grooming/${order.id}/handover`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ pickupCode: pickupInput.value.trim() }) })
  ;[appointments.value, grooming.value] = await Promise.all([api<Appointment[]>('/appointments'), api<Grooming[]>('/grooming')])
  void loadGroomingEvents(order.id)
  pickupInput.value = ''
  notify('凭证核验成功，已完成交宠。')
 } catch (error) { notify((error as Error).message, true) } finally { busy.value = false }
}
async function loadGroomingEvents(id: number) {
 try {
  const events = await api<GroomingEvent[]>(`/grooming/${id}/events`)
  if (selectedGrooming.value?.id === id) groomingEvents.value = events
 } catch {
  if (selectedGrooming.value?.id === id) groomingEvents.value = []
 }
}
async function loadSettlement(id: number) {
 if (!(isOwner.value || isManager.value || isAdmin.value || isReception.value)) return
 settlementLoading.value = true
 try {
  const result = await api<SettlementView>(`/grooming/${id}/settlement`)
  if (selectedGrooming.value?.id === id) settlement.value = result
 } catch (error) { if (selectedGrooming.value?.id === id) notify((error as Error).message, true) }
 finally { if (selectedGrooming.value?.id === id) settlementLoading.value = false }
}
async function settleGrooming(order: Grooming) {
 if (busy.value || (paymentMethod.value === 'CARD' && !paymentCardId.value)) return
 if (paymentMethod.value === 'ALIPAY') {
  busy.value = true
  try {
   const res = await api<PaymentInfo>('/payment/create', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ groomingId: order.id, channel: paymentMethod.value }) })
   activePayment.value = res
  } catch (error) { notify((error as Error).message, true) } finally { busy.value = false }
  return
 }
 busy.value = true
 try {
  await api(`/grooming/${order.id}/settle`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ method: paymentMethod.value, cardId: paymentMethod.value === 'CARD' ? paymentCardId.value : null, addonMethod: paymentMethod.value === 'CARD' ? paymentAddonMethod.value : undefined }) })
  ;[cards.value, customers.value] = await Promise.all([api<Card[]>('/members/cards'), api<Customer[]>('/customers')])
  await loadSettlement(order.id)
  notify('结算已记录。')
 } catch (error) { notify((error as Error).message, true) } finally { busy.value = false }
}
async function onPaymentCompleted(paidInfo: PaymentInfo) {
 activePayment.value = null
 const kind = paidInfo.bizType === 'RECHARGE' ? '充值已到账。' : paidInfo.bizType === 'RETAIL' ? '商品订单已用支付宝结清。' : `${paidInfo.channelName || '支付宝'}支付成功，工单已完成线上结清！`
 notify(kind)
 try {
  ;[grooming.value, customers.value, orders.value, cards.value] = await Promise.all([api<Grooming[]>('/grooming'), api<Customer[]>('/customers'), api<ShopOrder[]>('/orders'), api<Card[]>('/members/cards')])
  void loadNotifications()
  if (selectedGrooming.value?.id) {
   await Promise.all([loadSettlement(selectedGrooming.value.id), loadGroomingEvents(selectedGrooming.value.id)])
  }
 } catch { /* ignore */ }
}
async function refundGrooming(order: Grooming) {
 if (busy.value || !refundReason.value.trim()) return
 busy.value = true
 try {
  await api(`/grooming/${order.id}/refund`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ reason: refundReason.value.trim() }) })
  ;[cards.value, customers.value] = await Promise.all([api<Card[]>('/members/cards'), api<Customer[]>('/customers')])
  await loadSettlement(order.id)
  refundReason.value = ''
  notify('退款已记录。')
 } catch (error) { notify((error as Error).message, true) } finally { busy.value = false }
}
async function onScored() {
 const current = scoring.value; scoring.value = null
 notify('健康评分已记录，工单已完成。')
 try { [grooming.value, pets.value] = await Promise.all([api<Grooming[]>('/grooming'), api<Pet[]>('/pets')]); if (current) drawer.value = { kind: 'grooming', id: current.id } } catch { notify('评分已保存，列表暂未同步。', true) }
}
async function toggleService(service: Service) {
 serviceBusy.value = service.id
 const turningOn = service.enabled === false
 try {
  await api(`/services/${service.id}`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ price: service.price, enabled: turningOn }) })
  services.value = await api<Service[]>('/services')
  notify(turningOn ? '项目已启用。' : '项目已停用，新的预约将不能选择它。')
 } catch (e) { notify((e as Error).message, true) } finally { serviceBusy.value = 0 }
}
async function cancelAppointment(appointment: Appointment) {
 if (!cancelArmed.value) { cancelArmed.value = true; return }
 busy.value = true
 try { await api(`/appointments/${appointment.id}/cancel`, { method: 'POST' }); appointments.value = await api<Appointment[]>('/appointments'); cancelArmed.value = false; notify('预约已取消。') }
 catch (e) { cancelArmed.value = false; notify((e as Error).message, true) } finally { busy.value = false }
}
async function placeOrder(product: Product) {
 const quantity = orderQty.value[product.id] || 1
 const payload = isOwner.value ? { productId: product.id, quantity } : { customerId: retailCustomerId.value, productId: product.id, quantity }
 const key=`petcare.pending.order.${props.account.username}.${product.id}`, body=JSON.stringify(payload)
 busy.value = true
 try {
  const existing=sessionStorage.getItem(key); let requestId:string
  if(existing){const saved=JSON.parse(existing) as {body:string;requestId:string};if(saved.body!==body) throw new Error('上次订单结果尚未确认，请按原数量重试。');requestId=saved.requestId}
  else {requestId=crypto.randomUUID();sessionStorage.setItem(key,JSON.stringify({body,requestId}))}
  await api('/orders', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({...payload,requestId}) })
  sessionStorage.removeItem(key);notify('订单已提交。')
  try { [orders.value, inventory.value] = await Promise.all([api<ShopOrder[]>('/orders'), api<Product[]>('/inventory')]) } catch { notify('订单已提交，列表暂未同步，请刷新后核对。',true) }
 }
 catch (e) { if(e instanceof ApiError && e.status && e.status>=400 && e.status<500) sessionStorage.removeItem(key);notify((e as Error).message, true) } finally { busy.value = false }
}
async function refreshRetail(message:string) {
 notify(message)
 try { [orders.value,inventory.value,customers.value]=await Promise.all([api<ShopOrder[]>('/orders'),api<Product[]>('/inventory'),api<Customer[]>('/customers')]) } catch { notify('操作已记录，列表暂未同步，请刷新后核对。',true) }
}
async function decide(addon:Addon,decision:'APPROVED'|'REJECTED'){
 if(busy.value)return
 busy.value=true
 try{
  await api(`/grooming/addons/${addon.id}/decision`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({decision})})
  addons.value=await api<Addon[]>('/grooming/addons')
  if (isOwner.value) {
   notify(decision==='APPROVED'?'已同意本次加项，门店将收到确认。':'已拒绝本次加项。')
  } else if (isGroomer.value) {
   notify('已撤回加项申请。')
  } else {
   notify(decision==='APPROVED'?'已代客同意本次加项。':'已代客拒绝本次加项。')
  }
 }catch(e){notify((e as Error).message,true)}finally{busy.value=false}
}
async function submitReview(groomingId: number) {
 if (reviewBusy.value || !reviewContent.value.trim()) return
 reviewBusy.value = true
 try {
  const created = await api<Review>('/reviews', {
   method: 'POST',
   headers: { 'Content-Type': 'application/json' },
   body: JSON.stringify({ groomingId, rating: reviewRating.value, content: reviewContent.value.trim() })
  })
  reviews.value = [created, ...reviews.value]
  reviewContent.value = ''
  reviewRating.value = 5
  notify('评价已提交，感谢您的反馈！')
 } catch (e) {
  notify((e as Error).message, true)
 } finally {
  reviewBusy.value = false
 }
}
async function changeSelfPassword() {
 if (passwordBusy.value || !oldPassword.value || !selfNewPassword.value) return
 passwordBusy.value = true; passwordError.value = ''
 try {
  await api('/auth/password', { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ oldPassword: oldPassword.value, newPassword: selfNewPassword.value }) })
  passwordDialogOpen.value = false
  oldPassword.value = ''; selfNewPassword.value = ''
  notify('密码已成功修改，请妥善保存。')
 } catch (e) { passwordError.value = (e as Error).message } finally { passwordBusy.value = false }
}
function shortcut(e:KeyboardEvent){
 if((e.metaKey||e.ctrlKey)&&e.key.toLowerCase()==='k'){
  e.preventDefault()
  if(!drawer.value&&!bookingOpen.value&&!searchOpen.value){taskTray.value=false;searchOpen.value=true}
 }
 if(e.key==='Escape'&&taskTray.value&&!drawer.value&&!bookingOpen.value&&!searchOpen.value){taskTray.value=false;taskTrigger.value?.focus()}
}
function outsideTask(e:PointerEvent){if(taskTray.value&&!taskPanel.value?.contains(e.target as Node)&&!taskTrigger.value?.contains(e.target as Node))taskTray.value=false}
function selectSearchResult(result:{kind:'pet'|'appointment';id:number}){searchOpen.value=false; if(result.kind==='pet'){openPet(result.id);return} drawer.value={kind:result.kind,id:result.id}}
watch(() => selectedAppointment.value?.id, () => { cancelArmed.value = false })
watch(() => selectedGrooming.value?.id, id => { settlement.value = null; groomingEvents.value = []; settlementLoading.value = false; pickupInput.value = ''; precheckNote.value = ''; reassignGroomerId.value = undefined; paymentMethod.value = isOwner.value ? 'BALANCE' : 'CASH'; paymentCardId.value = undefined; paymentAddonMethod.value = 'CASH'; refundReason.value = ''; reviewRating.value = 5; reviewContent.value = ''; if (id) { void loadSettlement(id); void loadGroomingEvents(id) } })
function navigateSearch(e:KeyboardEvent){
 const controls=Array.from(searchPanel.value?.querySelectorAll<HTMLElement>('input,button')||[])
 const index=controls.indexOf(document.activeElement as HTMLElement)
 if(e.key==='ArrowDown'){e.preventDefault();controls[Math.min(index+1,controls.length-1)]?.focus()}
 if(e.key==='ArrowUp'){e.preventDefault();controls[Math.max(index-1,0)]?.focus()}
}
watch(selectedDate,date=>{if(!date)selectedDate.value=today})
watch(searchOpen,open=>{if(open)taskTray.value=false})
watch(taskTray,open=>{if(open) void loadNotifications()})
onMounted(()=>{load();document.addEventListener('keydown',shortcut);document.addEventListener('pointerdown',outsideTask)})
onUnmounted(()=>{document.removeEventListener('keydown',shortcut);document.removeEventListener('pointerdown',outsideTask);clearTimeout(toastTimer)})
</script>

<template>
<div class="workspace" :class="{'owner-mode':isOwner}">
 <aside v-if="!isOwner" class="sidebar">
  <a class="brand" href="#" @click.prevent="go(navigation[0].label)"><span class="brand-symbol"><PawPrint :size="23" :stroke-width="2.2"/></span><span>paw & pure<small>宠护，让关爱有迹可循</small></span></a>
  <div class="store-switch"><span class="store-monogram">P.</span><div><strong>{{storeInfo.storeName}}</strong><small><i class="live-dot"></i>营业时间 · {{storeInfo.businessHours}}</small></div></div>
  <p class="nav-caption">{{navCaption}}</p>
  <nav aria-label="主导航"><button v-for="item in navigation" :key="item.label" :class="{active:view===item.label}" :aria-current="view===item.label?'page':undefined" @click="go(item.label)"><component :is="item.icon" :size="19" :stroke-width="1.8"/><span>{{item.text}}</span><span v-if="item.label==='工单' && activeGrooming.length" class="nav-count">{{activeGrooming.length}}</span></button></nav>
  <div class="sidebar-bottom"><div class="sidebar-pet"><PetPortrait :id="2" name="旺财"/><div><strong>每一位，都是家人。</strong><small>PAW & PURE PET CARE</small></div></div><div class="workspace-user"><span class="employee-avatar tone-0">{{account.name.slice(0,1)}}</span><div><strong>{{account.name}}</strong><small>{{account.title}}</small></div><ShieldCheck :size="17"/></div></div>
 </aside>
 <div class="workspace-main">
  <header class="topbar">
   <a v-if="isOwner" href="#" class="brand owner-brand" @click.prevent="go('总览')"><span class="brand-symbol"><PawPrint :size="21"/></span><span>paw & pure</span></a>
   <div v-else class="breadcrumb">{{storeInfo.storeName}}<ChevronRight :size="14"/><strong>{{title}}</strong></div>
   <div class="topbar-tools"><button class="icon-button" aria-label="搜索宠物与预约" @click="searchOpen=true"><Search :size="20"/></button><button ref="taskTrigger" class="icon-button notification-button" aria-controls="task-notifications" aria-label="查看待办提醒" :aria-expanded="taskTray" @click="taskTray=!taskTray"><Bell :size="20"/><i v-if="taskCount"/></button><button class="button quiet small" @click="passwordDialogOpen=true;passwordError='';oldPassword='';selfNewPassword=''">修改密码</button><button class="exit-demo" @click="$emit('exit')">退出登录</button><span class="profile-dot">{{account.name.slice(-1)}}</span></div>
  </header>
  <nav v-if="isOwner" class="owner-nav" aria-label="宠主导航"><button v-for="item in navigation" :key="item.label" :class="{active:view===item.label}" :aria-current="view===item.label?'page':undefined" @click="go(item.label)">{{item.text}}</button><div class="account-picker"><span>家庭账户</span><strong>{{owner?.name || account.name}}</strong></div></nav>
  <div v-if="taskTray" id="task-notifications" ref="taskPanel" class="task-popover" role="region" aria-label="通知与待办"><div class="section-head"><h3>通知与待办 <span>{{taskCount}}</span></h3><div class="notice-head-actions"><button v-if="unreadNotifications.length" class="text-link" type="button" @click="markAllNoticesRead">全部已读</button><button class="icon-button" aria-label="关闭提醒" @click="taskTray=false"><X :size="17"/></button></div></div><button v-for="n in notifications" :key="'n'+n.id" class="task-popover-row" :class="{unread:noticeUnread(n)}" @click="openNotice(n)"><span class="task-icon" :class="n.urgent ? 'rose' : 'blue'"><Bell :size="17"/></span><span><strong>{{n.title}}</strong><small>{{n.content}}</small></span><ChevronRight :size="16"/></button><button v-for="a in pendingAddons" :key="'a'+a.id" class="task-popover-row" @click="openGrooming(a.grooming_id)"><span class="task-icon amber"><Bell :size="17"/></span><span><strong>{{a.pet_name}} · 加项待确认</strong><small>{{a.name}} · ¥{{money(a.amount)}}</small></span><ChevronRight :size="16"/></button><template v-if="stockAlerts"><button v-for="p in lowStock" :key="'s'+p.id" class="task-popover-row" @click="go('库存')"><span class="task-icon rose"><Package :size="17"/></span><span><strong>{{p.name}}</strong><small>库存 {{p.stock}}{{p.unit}}，低于补货阈值</small></span><ChevronRight :size="16"/></button></template><div v-if="!taskCount && !notifications.length" class="empty-state"><CheckCircle2 :size="30"/><p>当前没有待处理事项</p></div></div>
  <main :key="`${role}-${view}`" class="page-content">
   <div v-if="loading" class="page-loading"><div class="loading-skeleton wide"></div><div class="loading-skeleton medium"></div><div class="loading-skeleton block"></div><p><LoaderCircle class="spin" :size="17"/>正在同步门店数据</p></div>
   <div v-else-if="loadError" class="empty-state connection-error"><Activity :size="35"/><h2>暂时无法加载工作台</h2><p>{{loadError}}</p><button class="button primary" @click="load">重新连接</button></div>
   <template v-else>
    <div class="page-heading"><div><div class="eyebrow">{{isOwner?'YOUR PET, OUR PRIORITY':new Intl.DateTimeFormat('zh-CN',{month:'long',day:'numeric',weekday:'long'}).format(new Date())}}</div><h1>{{isOwner && view==='总览'?`${owner?.name || account.name}，欢迎回来。`:title}}</h1><p>{{intro}}</p></div><div class="heading-actions"><button v-if="canOpenWalkIn && view==='工单'" class="button secondary" @click="walkInOpen=true"><Plus :size="17"/>到店开单</button><button v-if="bookable" class="button primary" @click="openBooking(undefined,view==='排班'&&!isOwner?selectedDate:undefined)"><Plus :size="17"/>{{isOwner?'预约护理':'新建预约'}}</button></div></div>

    <template v-if="view==='总览' && isManager">
     <section class="overview-hero"><div class="hero-copy"><span class="label-with-dot"><i class="live-dot"></i>今日门店概况</span><h2>好好照顾，<br/>每一位小客人。</h2><div class="hero-people"><div class="avatar-stack"><PetPortrait v-for="g in visibleGrooming.slice(0,3)" :key="g.id" :id="g.pet_id" :name="g.pet_name"/></div><p><strong>{{activeGrooming.length}} 位小客人</strong>正在接受护理<button aria-label="查看护理工作台" @click="go('工单')"><ArrowUpRight :size="16"/></button></p></div></div><div class="hero-photo"><PetPortrait :id="1" name="糯米"/><span class="photo-caption"><Heart :size="14"/>CARE WITH LOVE</span></div><div class="hero-note"><span class="note-mark"><ShieldCheck :size="22"/></span><strong>从接宠，到安心回家</strong><p>服务进度与加项确认，<br/>每一步都清楚。</p><button class="text-link" @click="go('工单')">查看护理工作台<ArrowRight :size="16"/></button></div></section>
     <section class="metric-strip" aria-label="今日门店概况"><div><span>今日预约</span><strong>{{todayAppointments.length}}<small> 单</small></strong><p>{{todayAppointments.filter(a=>a.status==='CONFIRMED').length}} 单待到店</p></div><div><span>正在护理</span><strong>{{activeGrooming.length}}<small> 只</small></strong><p>{{visibleGrooming.filter(g=>g.status==='WAITING_PICKUP').length}} 只待接宠</p></div><div><span>今日营收</span><strong>¥{{money(finance.todayRevenue)}}</strong><p>洗护 ¥{{money(finance.todayServiceRevenue)}} · 零售 ¥{{money(finance.todayRetailRevenue)}}</p></div><div><span>待收款</span><strong :class="{'amber-text':finance.unpaidCount}">{{finance.unpaidCount}}<small> 单</small></strong><p>今日退款 ¥{{money(finance.todayRefundAmount)}}</p></div></section>
     <section class="surface finance-panel" aria-label="金额构成"><div class="section-head"><div><span class="eyebrow">TODAY'S CASH</span><h2>今日金额</h2></div><span>净营收累计 ¥{{money(report.netRevenue)}}</span></div><div class="finance-methods"><div v-for="item in (finance.paymentMethods.length ? finance.paymentMethods : [{method:'—',count:0,amount:0}])" :key="item.method"><span>{{payMethodLabel(item.method)}}</span><strong>¥{{money(item.amount)}}</strong><p>{{item.count}} 笔</p></div></div></section>
     <div class="overview-columns"><section class="surface agenda-panel"><div class="section-head"><div><span class="eyebrow">TODAY'S AGENDA</span><h2>今日到店安排 <span class="counter">{{todayAppointments.length}}</span></h2></div><button class="text-link" @click="go('排班')">排班日历<ArrowUpRight :size="16"/></button></div><div v-for="a in todayAppointments" :key="a.id" class="agenda-row"><div class="agenda-time"><strong>{{clock(a.start_time)}}</strong><span>{{clock(a.end_time)}}</span></div><PetPortrait :id="a.pet_id" :name="a.pet_name"/><div class="agenda-info"><strong>{{a.pet_name}}<span>{{a.service_name}}</span></strong><p>{{a.employee_name}}<i>·</i>{{a.resource_name}}</p></div><span class="status-pill" :class="a.status">{{labels[a.status]}}</span><button class="icon-button row-open" :aria-label="`查看${a.pet_name}预约`" @click="drawer={kind:'appointment',id:a.id}"><ChevronRight :size="18"/></button></div><div v-if="!todayAppointments.length" class="empty-state"><CalendarDays :size="28"/><p>今天暂无预约</p><button class="text-link" @click="openBooking()">安排第一笔预约<Plus :size="15"/></button></div></section>
     <section class="surface priority-panel"><div class="section-head"><div><span class="eyebrow">PRIORITIES</span><h2>先处理这些</h2></div><span class="counter">{{taskCount}}</span></div><button v-for="a in pendingAddons" :key="a.id" class="priority-task" @click="openGrooming(a.grooming_id)"><span class="task-icon amber"><Bell :size="19"/></span><span><strong>{{a.pet_name}}的加项待确认</strong><small>{{a.name}} · ¥{{money(a.amount)}}</small><b>查看申请<ArrowRight :size="13"/></b></span></button><button v-for="p in lowStock" :key="p.id" class="priority-task" @click="go('库存')"><span class="task-icon rose"><Package :size="19"/></span><span><strong>{{p.name}}</strong><small>剩余 {{p.stock}}{{p.unit}} / 阈值 {{p.threshold}}{{p.unit}}</small><b>查看库存<ArrowRight :size="13"/></b></span></button><p v-if="!taskCount" class="empty-note">所有事项均已处理。</p></section></div>
     <section class="care-section"><div class="section-head"><div><span class="eyebrow">CARE IN PROGRESS</span><h2>正在被温柔照顾</h2></div><button class="text-link" @click="go('工单')">全部工单<ArrowRight :size="16"/></button></div><div class="care-preview-grid"><button v-for="g in visibleGrooming" :key="g.id" class="care-preview" @click="openGrooming(g.id)"><PetPortrait :id="g.pet_id" :name="g.pet_name"/><div><span class="status-pill" :class="g.status">{{labels[g.status]}}</span><h3>{{g.pet_name}}</h3><p>{{labels[g.stage]}} · {{g.employee_name}}</p></div><ArrowUpRight :size="20"/></button></div></section>
    </template>
    <template v-else-if="view==='总览' && isReception">
     <section class="overview-hero"><div class="hero-copy"><span class="label-with-dot"><i class="live-dot"></i>前台接待</span><h2>今天的到店，<br/>从一声招呼开始。</h2><div class="hero-people"><div class="avatar-stack"><PetPortrait v-for="a in todayAppointments.slice(0,3)" :key="a.id" :id="a.pet_id" :name="a.pet_name"/></div><p><strong>{{todayAppointments.length}} 单今日到店</strong> · {{waitingPickup.length}} 只可以接回家</p></div></div><div class="hero-photo"><PetPortrait :id="2" name="旺财"/><span class="photo-caption"><Heart :size="14"/>WELCOME IN</span></div><div class="hero-note"><span class="note-mark"><CalendarDays :size="22"/></span><strong>先看排班，再安排工位</strong><p>洗护师与清洁缓冲，<br/>在排班里分得清楚。</p><button class="text-link" @click="go('排班')">打开排班表<ArrowRight :size="16"/></button></div></section>
     <section class="metric-strip" aria-label="今日接待概况"><div><span>今日预约</span><strong>{{todayAppointments.length}}<small> 单</small></strong><p>全天到店安排</p></div><div><span>待到店</span><strong>{{todayAppointments.filter(a=>a.status==='CONFIRMED').length}}<small> 单</small></strong><p>还未开始护理</p></div><div><span>等待接宠</span><strong>{{waitingPickup.length}}<small> 只</small></strong><p>护理完成，可以交接</p></div><div><span>待确认加项</span><strong :class="{'amber-text':pendingAddons.length}">{{pendingAddons.length}}<small> 项</small></strong><p>需对应宠主登录后授权</p></div></section>
     <div class="overview-columns"><section class="surface agenda-panel"><div class="section-head"><div><span class="eyebrow">ARRIVALS</span><h2>今日到店 <span class="counter">{{todayAppointments.length}}</span></h2></div><button class="text-link" @click="go('排班')">排班日历<ArrowUpRight :size="16"/></button></div><div v-for="a in todayAppointments" :key="a.id" class="agenda-row"><div class="agenda-time"><strong>{{clock(a.start_time)}}</strong><span>{{clock(a.end_time)}}</span></div><PetPortrait :id="a.pet_id" :name="a.pet_name"/><div class="agenda-info"><strong>{{a.pet_name}}<span>{{a.service_name}}</span></strong><p>{{a.employee_name}}<i>·</i>{{a.resource_name}}</p></div><span class="status-pill" :class="a.status">{{labels[a.status]}}</span><button class="icon-button row-open" :aria-label="`查看${a.pet_name}预约`" @click="drawer={kind:'appointment',id:a.id}"><ChevronRight :size="18"/></button></div><div v-if="!todayAppointments.length" class="empty-state"><CalendarDays :size="28"/><p>今天还没有预约</p><button class="text-link" @click="openBooking()">安排一笔预约<Plus :size="15"/></button></div></section>
     <section class="surface priority-panel"><div class="section-head"><div><span class="eyebrow">FRONT DESK</span><h2>接待要处理</h2></div><span class="counter">{{pendingAddons.length + waitingPickup.length}}</span></div><button v-for="g in waitingPickup" :key="g.id" class="priority-task" @click="openGrooming(g.id)"><span class="task-icon blue"><PawPrint :size="19"/></span><span><strong>{{g.pet_name}}可以接回家</strong><small>{{g.service_name}} · {{g.employee_name}}</small><b>查看接宠信息<ArrowRight :size="13"/></b></span></button><button v-for="a in pendingAddons" :key="a.id" class="priority-task" @click="openGrooming(a.grooming_id)"><span class="task-icon amber"><Bell :size="19"/></span><span><strong>{{a.pet_name}}的加项待确认</strong><small>等待{{customerName(a.grooming_id)}}授权 · ¥{{money(a.amount)}}</small><b>查看申请<ArrowRight :size="13"/></b></span></button><p v-if="!pendingAddons.length && !waitingPickup.length" class="empty-note">当前没有待交接或待授权的事项。</p></section></div>
    </template>
    <template v-else-if="view==='总览' && isAdmin">
     <section class="metric-strip" aria-label="系统概况"><div><span>累计净营收</span><strong>¥{{money(report.netRevenue)}}</strong><p>洗护 ¥{{money(report.serviceRevenue)}} · 零售 ¥{{money(report.retailRevenue)}}</p></div><div><span>退款合计</span><strong>¥{{money(report.refundAmount)}}</strong><p>今日营收 ¥{{money(finance.todayRevenue)}}</p></div><div><span>耗材待补充</span><strong :class="{'rose-text':lowStock.length}">{{lowStock.length}}<small> 件</small></strong><p>低于库存预警阈值</p></div><div><span>在册会员</span><strong>{{customers.length}}<small> 位</small></strong><p>{{report.completedGroomingCount}} 单已完成护理</p></div></section>
     <div class="overview-columns"><section class="surface agenda-panel"><div class="section-head"><div><span class="eyebrow">TEAM</span><h2>洗护团队 <span class="counter">{{employees.length}}</span></h2></div><button class="text-link" @click="go('员工')">员工资料<ArrowUpRight :size="16"/></button></div><div v-for="(e,index) in employees" :key="e.id" class="agenda-row"><span class="employee-avatar" :class="`tone-${index%3}`">{{e.name.slice(0,1)}}</span><div class="agenda-info"><strong>{{e.name}}<span>{{e.title}}</span></strong><p>{{speciesLabel(e.species)}}<i>·</i>今日 {{appointments.filter(a=>a.employee_id===e.id&&a.appointment_date===today).length}} 单</p></div><span class="status-pill" :class="grooming.some(g=>g.employee_id===e.id&&g.status==='GROOMING')?'GROOMING':'COMPLETED'">{{grooming.filter(g=>g.employee_id===e.id&&g.status==='GROOMING').length}} 只护理中</span></div><div v-if="!employees.length" class="empty-state"><Users :size="28"/><p>还没有洗护师档案</p></div></section>
     <section class="surface priority-panel"><div class="section-head"><div><span class="eyebrow">SUPPLIES</span><h2>耗材预警</h2></div><span class="counter">{{lowStock.length}}</span></div><button v-for="p in lowStock" :key="p.id" class="priority-task" @click="go('库存')"><span class="task-icon rose"><Package :size="19"/></span><span><strong>{{p.name}}</strong><small>剩余 {{p.stock}}{{p.unit}} / 阈值 {{p.threshold}}{{p.unit}}</small><b>查看库存<ArrowRight :size="13"/></b></span></button><p v-if="!lowStock.length" class="empty-note">耗材储备都在阈值以上。</p></section></div>
    </template>
    <template v-else-if="view==='总览' && isOwner">
     <section class="owner-hero"><div><span class="eyebrow">A LITTLE CARE. A LOT OF LOVE.</span><h2>把爱宠交给我们，<br/>把安心带回家。</h2><p>为{{visiblePets.map(p=>p.name).join('和')}}挑选一次专属护理。</p><button class="button primary" @click="openBooking(visiblePets[0]?.id)">开始预约<ArrowRight :size="17"/></button></div><PetPortrait :id="visiblePets[0]?.id || 1" :name="visiblePets[0]?.name"/><span class="owner-hero-stamp"><PawPrint :size="18"/>MADE FOR YOUR LITTLE ONE</span></section>
     <div class="owner-home-columns"><section><div class="section-head"><h2>我的小家人</h2><button class="text-link" @click="go('宠物')">查看档案<ArrowRight :size="16"/></button></div><div class="family-grid"><button v-for="p in visiblePets" :key="p.id" class="family-card" @click="openPet(p.id)"><PetPortrait :id="p.id" :name="p.name"/><div><h3>{{p.name}}</h3><p>{{p.breed}} · {{p.weight}} kg</p></div><ArrowUpRight :size="19"/></button></div></section><section class="surface owner-account"><WalletCards :size="23"/><div><small>{{owner?.level_name}}账户</small><strong>¥{{money(owner?.balance || 0)}}</strong><p>储值余额 · {{owner?.points || 0}} 积分</p></div><button class="icon-button" aria-label="查看会员账户" @click="go('会员')"><ArrowUpRight :size="22"/></button></section></div>
     <section v-if="pendingAddons.length" class="owner-attention"><span class="task-icon amber"><Bell :size="21"/></span><div><h3>有 {{pendingAddons.length}} 项护理申请需要你确认</h3><p>门店将在你同意后处理本次加项。</p></div><button class="button secondary" @click="go('工单')">查看申请<ArrowRight :size="16"/></button></section>
     <section class="surface recommendation-panel"><div class="section-head"><div><span class="eyebrow">PICKED FOR YOUR PET</span><h2>适合爱宠的护理</h2></div><AppSelect v-model="recommendedPetId" label="选择推荐宠物" :options="visiblePets.map(p=>({value:p.id,label:p.name}))" compact @change="recommend" /></div><div class="recommendation-grid"><button v-for="s in recommendations.slice(0,3)" :key="s.id" class="recommendation-card" @click="openBooking(recommendedPetId,undefined,s.id)"><span class="recommendation-photo"><img :src="servicePhoto(s)" alt="" loading="lazy"/></span><span class="match-tag">匹配 {{s.score}} 分</span><h3>{{s.name}}</h3><p>{{s.reason}}</p><span class="text-link">挑选护理<ArrowRight :size="15"/></span></button></div><p v-if="!recommendations.length" class="empty-note">暂时没有适用的推荐项目。</p></section>
    </template>
    <template v-else-if="view==='总览' && isGroomer">
     <section class="metric-strip" aria-label="我的工作概况"><div><span>今日预约</span><strong>{{todayAppointments.length}}<small> 单</small></strong><p>分配给你的到店安排</p></div><div><span>正在护理</span><strong>{{activeGrooming.length}}<small> 只</small></strong><p>{{waitingPickup.length}} 只待接宠</p></div><div><span>待确认加项</span><strong :class="{'amber-text':pendingAddons.length}">{{pendingAddons.length}}<small> 项</small></strong><p>等待宠主或前台确认</p></div><div><span>负责爱宠</span><strong>{{visiblePets.length}}<small> 只</small></strong><p>近期经手的档案</p></div></section>
     <div class="overview-columns"><section class="surface agenda-panel"><div class="section-head"><div><span class="eyebrow">MY SHIFT</span><h2>今日安排 <span class="counter">{{todayAppointments.length}}</span></h2></div><button class="text-link" @click="go('排班')">我的排班<ArrowUpRight :size="16"/></button></div><div v-for="a in todayAppointments" :key="a.id" class="agenda-row"><div class="agenda-time"><strong>{{clock(a.start_time)}}</strong><span>{{clock(a.end_time)}}</span></div><PetPortrait :id="a.pet_id" :name="a.pet_name"/><div class="agenda-info"><strong>{{a.pet_name}}<span>{{a.service_name}}</span></strong><p>{{a.resource_name}}</p></div><span class="status-pill" :class="a.status">{{labels[a.status]}}</span><button class="icon-button row-open" :aria-label="`查看${a.pet_name}预约`" @click="drawer={kind:'appointment',id:a.id}"><ChevronRight :size="18"/></button></div><div v-if="!todayAppointments.length" class="empty-state"><CalendarDays :size="28"/><p>今天还没有分配给你的预约</p></div></section>
     <section class="surface priority-panel"><div class="section-head"><div><span class="eyebrow">ON THE TABLE</span><h2>进行中的护理</h2></div><span class="counter">{{activeGrooming.length}}</span></div><button v-for="g in activeGrooming" :key="g.id" class="priority-task" @click="openGrooming(g.id)"><span class="task-icon blue"><PawPrint :size="19"/></span><span><strong>{{g.pet_name}}</strong><small>{{labels[g.stage]}} · {{g.service_name}}</small><b>打开工单<ArrowRight :size="13"/></b></span></button><p v-if="!activeGrooming.length" class="empty-note">当前没有进行中的护理。</p></section></div>
    </template>
    <template v-else-if="view==='排班'">
     <StoreSetupPanel v-if="isManager || isAdmin" kind="resource" :items="resources" @saved="onSetupSaved"/><section v-if="!isOwner" class="surface schedule-panel"><div class="schedule-toolbar"><div class="date-controls"><button class="icon-button" aria-label="前一天" @click="selectedDate=shiftedDate(selectedDate,-1)"><ChevronLeft :size="18"/></button><AppDatePicker v-model="selectedDate" label="选择排班日期" :display-label="dateLabel" inline /><button class="icon-button" aria-label="后一天" @click="selectedDate=shiftedDate(selectedDate,1)"><ChevronRight :size="18"/></button><button class="button quiet small" @click="selectedDate=today">今天</button></div><span class="schedule-summary">{{scheduled.length}} / {{dateAppointments.length}} 笔预约 · {{isGroomer?'个人安排':'全店安排'}}</span></div><ScheduleManager :employees="isGroomer?employees.filter(e=>e.id===staffId):employees" :date="selectedDate" :can-edit="isManager || isAdmin"/><GanttSchedule :appointments="scheduled" :employees="isGroomer?employees.filter(e=>e.id===staffId):employees" :date="selectedDate" @select="a=>drawer={kind:'appointment',id:a.id}"/></section>
     <section class="surface appointment-table"><div class="section-head"><h2>{{isOwner?'我的预约':'当日预约明细'}}</h2><div class="filter-label"><SlidersHorizontal :size="15"/><AppSelect v-model="statusFilter" label="筛选预约状态" :options="[{value:'全部',label:'全部状态'},{value:'CONFIRMED',label:'待到店'},{value:'IN_SERVICE',label:'服务中'},{value:'COMPLETED',label:'已完成'}]" compact /></div></div><div class="table-scroll"><table><thead><tr><th>小客人</th><th>日期与时段</th><th>护理项目</th><th>洗护师 / 工位</th><th>预估费用</th><th>状态</th><th><span class="sr-only">查看详情</span></th></tr></thead><tbody><tr v-for="a in isOwner?visibleAppointments.filter(a=>statusFilter==='全部'||a.status===statusFilter):scheduled" :key="a.id"><td><div class="table-pet"><PetPortrait :id="a.pet_id" :name="a.pet_name"/><strong>{{a.pet_name}}</strong></div></td><td>{{a.appointment_date}}<small>{{clock(a.start_time)}}–{{clock(a.end_time)}}</small></td><td>{{a.service_name}}</td><td>{{a.employee_name}}<small>{{a.resource_name}}</small></td><td class="numeric">¥{{money(a.price)}}</td><td><span class="status-pill" :class="a.status">{{labels[a.status]||a.status}}</span></td><td><button class="icon-button" :aria-label="`查看${a.pet_name}预约详情`" @click="drawer={kind:'appointment',id:a.id}"><ChevronRight :size="17"/></button></td></tr></tbody></table></div><div v-if="!(isOwner?visibleAppointments.filter(a=>statusFilter==='全部'||a.status===statusFilter):scheduled).length" class="empty-state"><CalendarDays :size="32"/><h3>这里还没有预约</h3><p>可以切换日期或状态，也可以安排一次新的护理。</p><button v-if="bookable" class="text-link" @click="openBooking(undefined,selectedDate)">新建预约<Plus :size="16"/></button></div></section>
    </template>
    <template v-else-if="view==='工单'">
     <div class="work-order-summary"><span><i class="live-dot"></i>{{activeGrooming.length}} 只正在护理</span><span>{{visibleGrooming.filter(g=>g.status==='WAITING_PICKUP').length}} 只等待接宠</span><span v-if="pendingAddons.length" class="amber-text">{{pendingAddons.length}} 项待确认</span></div>
     <div class="work-order-grid"><article v-for="g in visibleGrooming" :key="g.id" class="surface work-order"><div class="work-order-top"><PetPortrait :id="g.pet_id" :name="g.pet_name"/><div><h2>{{g.pet_name}}</h2><p>{{g.service_name}}</p></div><span class="status-pill" :class="g.status">{{labels[g.status]}}</span></div><StageTimeline :stage="g.stage"/><div class="work-order-bottom"><span><Users :size="15"/>{{g.employee_name}}</span><button class="text-link" @click="openGrooming(g.id)">查看护理详情<ArrowRight :size="16"/></button></div><div v-if="petById(g.pet_id)?.risks?.length" class="work-order-alert"><ShieldCheck :size="15"/>洗护前有禁忌或皮肤病史，请先查看</div><div v-if="addons.some(a=>a.grooming_id===g.id&&a.status==='PENDING')" class="work-order-alert"><Bell :size="15"/>有一项加项申请等待宠主确认</div></article></div>
     <div v-if="!visibleGrooming.length" class="surface empty-state"><PawPrint :size="34"/><h3>当前没有护理工单</h3><p>{{isGroomer?'今天还没有分配给你的护理。':isReception?'到店之后，护理进度会显示在这里。':'爱宠到店后，可以在这里查看护理进度。'}}</p></div>
     <section v-if="visibleAddons.length" class="surface addon-panel"><div class="section-head"><div><span class="eyebrow">CONSENT BEFORE CARE</span><h2>护理加项申请</h2></div><ShieldCheck :size="21"/></div><div v-for="a in visibleAddons" :key="a.id" class="addon-row"><div class="addon-description"><span class="status-pill" :class="a.status">{{labels[a.status]}}</span><h3>{{a.pet_name}} · {{a.name}}</h3><p>{{a.reason}}</p><small>申请费用 <strong>¥{{money(a.amount)}}</strong></small></div><div v-if="a.status==='PENDING' && isOwner" class="addon-actions"><button class="button secondary" :disabled="busy" @click="decide(a,'REJECTED')">拒绝加项</button><button class="button primary" :disabled="busy" @click="decide(a,'APPROVED')">同意 ¥{{money(a.amount)}}</button></div><div v-else-if="a.status==='PENDING' && isGroomer" class="addon-actions"><button class="button secondary" :disabled="busy" @click="decide(a,'REJECTED')">撤回加项申请</button></div><div v-else-if="a.status==='PENDING' && (isManager || isAdmin || isReception)" class="addon-actions"><button class="button secondary" :disabled="busy" @click="decide(a,'REJECTED')">代客拒绝</button><button class="button primary" :disabled="busy" @click="decide(a,'APPROVED')">代客同意</button></div><div v-else-if="a.status==='PENDING'" class="addon-store-note"><Clock3 :size="17"/>等待{{customerName(a.grooming_id)}}授权</div></div></section>
    </template>
    <template v-else-if="view==='订单'">
     <div class="order-layout">
      <section v-if="isOwner || canManagePets" class="surface order-shop"><div class="section-head"><h2>洗护好物</h2><span>{{shopProducts.length}} 件</span></div><div v-if="!isOwner && customers.length" class="field"><span>代客下单会员</span><AppSelect v-model="retailCustomerId" label="代客下单会员" :options="customers.map(c=>({value:c.id,label:`${c.name} · ¥${money(c.balance)}`}))" :disabled="busy" compact /></div><article v-for="p in shopProducts" :key="p.id" class="shop-row"><span class="product-icon"><img :src="productPhoto(p.name,p.id)" alt="" loading="lazy"/></span><div><h3>{{p.name}}</h3><p>¥{{money(Number(p.price))}} · 库存 {{p.stock}}{{p.unit}} · 每件 {{p.sale_units || 1}}{{p.unit}}</p></div><div class="field"><AppSelect :model-value="orderQty[p.id] || 1" :label="`${p.name}数量`" :options="[1,2,3].map(value => ({ value, label: `${value} 件` }))" :disabled="busy" compact @update:model-value="orderQty[p.id]=Number($event)" /></div><button class="button primary" :disabled="busy || p.stock<(p.sale_units || 1)*(orderQty[p.id] || 1)" @click="placeOrder(p)">提交订单</button></article><p v-if="!shopProducts.length" class="empty-note">暂时没有可购买的洗护用品。</p></section>
      <section class="surface order-history">
       <div class="section-head"><h2>{{isOwner?'我的订单':'商品订单'}}</h2><span class="order-count">{{orders.length}} 笔</span></div>
       <article v-for="o in orders" :key="o.id" class="order-row">
        <div class="order-summary">
         <div class="order-item"><span class="product-icon"><img :src="productPhoto(o.product_name,o.product_id)" alt="" loading="lazy"/></span><div class="order-description"><h3>{{o.product_name}}</h3><p class="order-meta"><span v-if="!isOwner && o.customer_name" class="order-customer">客户：<strong>{{o.customer_name}}</strong><span v-if="o.phone">（{{o.phone}}）</span> · </span><time :datetime="o.created_at.replace(' ','T')">{{o.created_at}}</time><span aria-hidden="true">·</span><span>{{o.quantity}} 件</span><span v-if="o.pay_method" class="order-pay-method"> · {{o.pay_method==='BALANCE'?'余额支付':'现金支付'}}</span></p></div></div>
         <div class="order-total"><strong class="order-amount"><span>¥</span>{{money(Number(o.amount))}}</strong><span class="status-pill" :class="o.status">{{labels[o.status] || o.status}}</span></div>
        </div>
        <RetailOrderActions :order="o" :owner="isOwner" :can-refund="isManager || isAdmin" @changed="refreshRetail" @pay="onOnlinePay"/>
       </article>
       <p v-if="!orders.length" class="empty-note">还没有订单。选一件洗护用品，就会出现在这里。</p>
      </section>
     </div>
    </template>
    <template v-else-if="view==='宠物'">
     <div class="collection-toolbar"><div class="segmented" role="group" aria-label="宠物种类筛选"><button v-for="f in [{code:'全部',text:'全部爱宠'},{code:'DOG',text:'狗狗'},{code:'CAT',text:'猫咪'},{code:'OTHER',text:'其他'}]" :key="f.code" :class="{selected:petFilter===f.code}" :aria-pressed="petFilter===f.code" @click="petFilter=f.code">{{f.text}}</button></div><button v-if="canManagePets" class="button secondary" @click="editingPet=null;petDialog=true"><Plus :size="16"/>添加爱宠</button><span>{{filteredPets.length}} 份爱宠档案</span></div><div class="pet-profile-grid"><article v-for="p in filteredPets" :key="p.id" class="surface pet-profile"><button class="pet-profile-photo" :aria-label="`查看${p.name}档案`" @click="openPet(p.id)"><PetPortrait :id="p.id" :name="p.name"/><span class="photo-overlay-label">{{speciesLabel(p.species)}}<ArrowUpRight :size="17"/></span></button><div class="pet-profile-body"><div class="section-head"><div><h2>{{p.name}}</h2><p>{{p.breed}}<template v-if="p.age_label"> · {{p.age_label}}</template></p></div><span class="pet-id">PET {{String(p.id).padStart(3,'0')}}</span></div><div class="pet-facts"><span><small>体重</small><strong>{{p.weight}} <em>kg</em></strong></span><span><small>体型</small><strong>{{sizeLabels[p.size_type]}}</strong></span><span><small>毛发</small><strong>{{p.coat}}</strong></span></div><div class="health-note" :class="{risk:!!p.risks?.length}"><ShieldCheck :size="16"/><span>{{p.risks?.length?p.risks[0]:p.health_note}}</span></div><div class="pet-card-links"><button v-if="canEditPet(p)" class="text-link" @click="editPet(p)">编辑档案</button><button class="text-link" @click="openPet(p.id)">打开完整档案<ArrowRight :size="16"/></button></div></div></article></div><div v-if="!filteredPets.length" class="surface empty-state"><PawPrint :size="30"/><p>当前筛选下暂无宠物档案。</p><button v-if="canManagePets" class="button secondary" @click="editingPet=null;petDialog=true">添加爱宠</button></div>
    </template>
    <template v-else-if="view==='会员'">
     <StoreSetupPanel v-if="!isOwner" kind="customer" :items="customers" @saved="onSetupSaved"/><div class="member-account-grid"><section v-for="c in visibleCustomers" :key="c.id" class="member-wallet" :style="{'--wallet-image': `url(${walletPhoto(c.id)})`}"><div class="wallet-top"><span>PAW & PURE MEMBER</span><WalletCards :size="25"/></div><h2>{{c.name}}<small>{{c.level_name}}</small></h2><div class="wallet-balance"><span>储值余额</span><strong>¥{{money(c.balance)}}</strong></div><div class="wallet-bottom"><span>{{c.points}} 可用积分</span><span>{{c.phone.slice(0,3)}} **** {{c.phone.slice(-4)}}</span></div></section></div><section class="surface membership-panel"><div class="section-head"><div><span class="eyebrow">A LITTLE MORE CARE</span><h2>{{isOwner?'我的洗护次卡':'会员洗护次卡'}}</h2></div><span>{{visibleCards.length}} 张次卡</span></div><div v-for="card in visibleCards" :key="card.id" class="service-card-row"><span class="card-art"><img :src="photoUrl(card.name.includes('猫') ? '/images/care-cat.jpg' : '/images/care-member.jpg')" alt="" loading="lazy"/></span><div class="service-card-name"><h3>{{card.name}}</h3><p>{{card.customer_name}} · 共 {{card.total}} 次护理</p></div><div class="card-remaining"><strong>{{card.remaining}}</strong><span>次可用</span></div><div class="card-progress"><i :style="{width:`${card.remaining/card.total*100}%`}"></i></div></div><div v-if="!visibleCards.length" class="empty-state"><WalletCards :size="30"/><p>当前账户暂无洗护次卡。</p></div></section><MembershipManager :customers="visibleCustomers" :services="services" :owner="isOwner" :owner-id="ownerId" :current-username="account.username" @changed="refreshMembership" @pay="onOnlinePay"/>
    </template>
    <template v-else-if="view==='服务'">
     <div class="collection-toolbar"><div class="segmented" role="group" aria-label="服务适用种类筛选"><button v-for="f in [{code:'全部',text:'全部项目'},{code:'DOG',text:'狗狗'},{code:'CAT',text:'猫咪'},{code:'OTHER',text:'其他'}]" :key="f.code" :class="{selected:serviceFilter===f.code}" :aria-pressed="serviceFilter===f.code" @click="serviceFilter=f.code">{{f.text}}</button></div><button v-if="canManageServices" class="button secondary" @click="serviceEditor=null"><Plus :size="16"/>新增项目</button><span>{{filteredServices.length}} 项护理服务</span></div><div class="service-catalog"><article v-for="s in filteredServices" :key="s.id" class="surface catalog-card" :class="{paused:s.enabled===false}"><div class="catalog-art"><img :src="servicePhoto(s)" alt="" loading="lazy"/><span>{{s.category}}</span></div><div class="catalog-body"><span class="eyebrow">{{speciesLabel(s.species)}}</span><h2>{{s.name}}</h2><p><Clock3 :size="15"/>约 {{s.duration}} 分钟</p><div class="catalog-bottom"><strong>¥{{money(s.price)}}<small> 起</small></strong><span v-if="s.enabled===false" class="status-pill">已停用</span><button v-if="bookable && s.enabled!==false" class="round-button" :aria-label="`预约${s.name}`" @click="openBooking(undefined,undefined,s.id)"><ArrowUpRight :size="20"/></button></div><div v-if="canManageServices" class="catalog-actions"><button class="button quiet small" @click="serviceEditor=s">调整价格</button><button class="button quiet small" :disabled="serviceBusy===s.id" @click="toggleService(s)">{{s.enabled===false?'启用':'停用'}}</button></div></div></article></div>
    </template>
    <template v-else-if="view==='员工'">
     <section class="team-intro"><Users :size="24"/><div><h2>专业的人，做用心的护理。</h2><p>{{employees.length}} 位洗护师 · 按种类分区 · 查看技能与当日预约</p></div><button class="text-link" @click="go('排班')">查看预约安排<ArrowUpRight :size="16"/></button></section><StoreSetupPanel v-if="isManager || isAdmin" kind="employee" :items="employees" @saved="onSetupSaved"/><AccountManager v-if="isAdmin || isManager" :customers="customers" :employees="employees" :current-username="account.username" :is-admin="isAdmin"/><div class="team-grid"><article v-for="(e,index) in employees" :key="e.id" class="surface team-card"><span class="employee-avatar" :class="`tone-${index%3}`">{{e.name}}</span><span class="expertise-label">{{speciesLabel(e.species)}}</span><h2>{{e.name}}</h2><p>{{e.title}}</p><div class="skill-tags"><span v-for="skill in e.skills.split('、')" :key="skill">{{skill}}</span></div><div class="team-stat"><span>今日已安排<strong>{{appointments.filter(a=>a.employee_id===e.id&&a.appointment_date===today).length}} <small>单</small></strong></span><span>正在护理<strong>{{grooming.filter(g=>g.employee_id===e.id&&g.status==='GROOMING').length}} <small>只</small></strong></span></div><button class="text-link" @click="go('排班')">查看工作安排<ArrowRight :size="16"/></button></article></div>
    </template>
    <template v-else-if="view==='图片' && isAdmin"><PhotoManager/></template>
    <template v-else-if="view==='设置' && (isManager || isAdmin)"><StoreSettingsPanel @saved="onSetupSaved"/></template>
    <template v-else-if="view==='库存'">
     <section class="stock-alert-summary"><span class="task-icon rose"><Package :size="24"/></span><div><h2>{{lowStock.length}} 件耗材需要关注</h2><p>低于预警阈值的耗材已优先标记。</p></div><button class="button secondary" @click="stockFilter=stockFilter==='全部'?'待补充':'全部'">{{stockFilter==='全部'?'只看待补充':'查看全部'}}</button></section><section class="surface inventory-panel"><div class="section-head"><h2>耗材库存</h2><div class="segmented" role="group" aria-label="库存状态筛选"><button v-for="f in ['全部','待补充']" :key="f" :class="{selected:stockFilter===f}" :aria-pressed="stockFilter===f" @click="stockFilter=f">{{f}}</button></div></div><div class="table-scroll"><table><thead><tr><th>耗材名称</th><th>当前库存</th><th>预警阈值</th><th>储备情况</th><th>库存状态</th></tr></thead><tbody><tr v-for="p in filteredInventory" :key="p.id"><td><div class="product-name"><span class="product-icon"><img :src="productPhoto(p.name,p.id)" alt="" loading="lazy"/></span><strong>{{p.name}}</strong></div></td><td class="numeric"><strong>{{p.stock}}</strong> {{p.unit}}</td><td class="numeric">{{p.threshold}} {{p.unit}}</td><td><div class="stock-meter" :class="{low:p.stock<=p.threshold}"><i :style="{width:`${Math.min(p.stock/p.threshold*100,100)}%`}"></i></div></td><td><span class="status-pill" :class="p.stock<=p.threshold?'PENDING':'APPROVED'">{{p.stock<=p.threshold?'需要补充':'库存充足'}}</span></td></tr></tbody></table></div><p v-if="!filteredInventory.length" class="empty-note">当前没有需要补充的耗材。</p></section><StoreSetupPanel kind="product" :items="inventory" @saved="onSetupSaved"/><InventoryManager :products="inventory" :services="services" :current-username="account.username" @changed="refreshInventory"/>
    </template>
    <footer class="page-footer"><span>PAW & PURE <i>·</i> 每一份关爱，都有迹可循</span><span>{{photoAssets.some(photo=>photo.customized) || inventory.some(product=>product.image_key) ? '图片由门店维护' : '照片均为演示配图'}}</span></footer>
   </template>
  </main>
 </div>
 <Transition name="toast"><div v-if="toast" class="toast-message" :class="{error:toastError}" :role="toastError?'alert':'status'" @mouseenter="pauseToast" @mouseleave="resumeToast" @focusin="pauseToast" @focusout="resumeToast"><CheckCircle2 v-if="!toastError" :size="19"/><Bell v-else :size="19"/><span>{{toast}}</span><button class="icon-button" aria-label="关闭提示" @click="toast=''"><X :size="16"/></button></div></Transition>
 <BookingDialog v-if="bookingOpen" :pets="visiblePets" :services="services" :employees="employees" :customers="customers" :customer-id="ownerId" :owner="isOwner" :initial-pet-id="bookingPet" :initial-date="bookingDate" :initial-service-id="bookingService" @close="bookingOpen=false" @booked="onBooked"/>
 <PetDialog v-if="petDialog" :pet="editingPet" :customers="customers" :owner="isOwner || isGroomer" :customer-id="ownerId" @close="petDialog=false" @saved="onPetSaved"/>
 <ServiceDialog v-if="serviceEditor!==undefined" :service="serviceEditor ?? null" @close="serviceEditor=undefined" @saved="onServiceSaved"/>
 <WalkInDialog v-if="walkInOpen" :pets="pets" :services="services" :employees="employees" @close="walkInOpen=false" @saved="onWalkIn"/>
 <HealthScoreDialog v-if="scoring" :grooming="scoring" @close="scoring=null" @saved="onScored"/>
 <DialogShell v-if="drawer" :title="drawerTitle" subtitle="PAW & PURE · 用心记录每一次照顾" drawer @close="drawer=null">
  <template v-if="selectedPet"><div class="drawer-pet-hero"><PetPortrait :id="selectedPet.id" :name="selectedPet.name"/><div><span>{{selectedPet.breed}}</span><h2>{{selectedPet.name}}</h2><p>{{sizeLabels[selectedPet.size_type]}} · {{selectedPet.weight}} kg · {{selectedPet.coat}}</p></div></div><section class="drawer-section"><h3>档案词条</h3><dl class="detail-list"><div><dt>年龄</dt><dd>{{selectedPet.age_label || '未记录'}}</dd></div><div><dt>过敏史</dt><dd>{{selectedPet.allergy_label || '未记录'}}</dd></div><div><dt>皮肤病史</dt><dd>{{selectedPet.skin_label || '未记录'}}</dd></div><div><dt>既往洗护</dt><dd>{{selectedPet.grooming_label || '未记录'}}</dd></div></dl></section><section class="drawer-section"><h3><ShieldCheck :size="18"/>洗护前风险提示</h3><div v-if="selectedPet.risks?.length" class="risk-panel"><p v-for="risk in selectedPet.risks" :key="risk">{{risk}}</p></div><div v-else class="health-note">未发现过敏或皮肤病史，可按常规流程洗护。</div></section><HealthTrend :records="healthRecords"/><section class="drawer-section"><h3>家庭与档案</h3><dl class="detail-list"><div><dt>所属宠主</dt><dd>{{customers.find(c=>c.id===selectedPet?.customer_id)?.name}}</dd></div><div><dt>宠物种类</dt><dd>{{speciesLabel(selectedPet.species)}}</dd></div><div><dt>档案编号</dt><dd>PET {{String(selectedPet.id).padStart(3,'0')}}</dd></div></dl></section><section class="drawer-section"><h3>关联护理</h3><button v-for="g in visibleGrooming.filter(g=>g.pet_id===selectedPet?.id)" :key="g.id" class="drawer-related" @click="openGrooming(g.id)"><span><strong>{{g.service_name}}</strong><small>{{labels[g.stage]}} · {{g.employee_name}}</small></span><ChevronRight :size="18"/></button><p v-if="!visibleGrooming.some(g=>g.pet_id===selectedPet?.id)" class="empty-note">暂无关联护理工单。</p></section><button v-if="canEditPet(selectedPet)" class="button secondary full" @click="editPet(selectedPet)">编辑档案</button><button v-if="bookable" class="button primary full" @click="openBooking(selectedPet.id)">为{{selectedPet.name}}预约护理<ArrowRight :size="17"/></button></template>
  <template v-else-if="selectedAppointment"><div class="drawer-subject"><PetPortrait :id="selectedAppointment.pet_id" :name="selectedAppointment.pet_name"/><div><h2>{{selectedAppointment.pet_name}}</h2><p>{{selectedAppointment.service_name}}</p></div><span class="status-pill" :class="selectedAppointment.status">{{labels[selectedAppointment.status]}}</span></div><section class="drawer-section"><h3>到店安排</h3><dl class="detail-list"><div><dt>预约日期</dt><dd>{{selectedAppointment.appointment_date}}</dd></div><div><dt>服务时段</dt><dd>{{clock(selectedAppointment.start_time)}}–{{clock(selectedAppointment.end_time)}}</dd></div><div><dt>洗护师</dt><dd>{{selectedAppointment.employee_name}}</dd></div><div><dt>护理工位</dt><dd>{{selectedAppointment.resource_name}}</dd></div><div><dt>预估费用</dt><dd class="detail-price">¥{{money(selectedAppointment.price)}}</dd></div></dl><p class="field-help"><ShieldCheck :size="16"/>服务后预留 15 分钟清洁缓冲</p></section><div v-if="canCancel(selectedAppointment)"><p v-if="cancelArmed" class="cancel-note">确认取消 {{selectedAppointment.pet_name}} 的这场预约？取消后该时段会释放。</p><button class="button secondary full" :disabled="busy" @click="cancelAppointment(selectedAppointment)">{{cancelArmed?'确认取消预约':'取消预约'}}</button></div><button class="button secondary full" @click="openPet(selectedAppointment.pet_id)">查看爱宠档案<ArrowRight :size="16"/></button></template>
  <template v-else-if="selectedGrooming"><div class="drawer-subject"><PetPortrait :id="selectedGrooming.pet_id" :name="selectedGrooming.pet_name"/><div><h2>{{selectedGrooming.pet_name}}</h2><p>{{selectedGrooming.service_name}}</p></div><span class="status-pill" :class="selectedGrooming.status">{{labels[selectedGrooming.status]}}</span></div><section class="drawer-section"><h3>当前护理阶段</h3><StageTimeline :stage="selectedGrooming.stage" detailed/><p class="drawer-helper">由 {{selectedGrooming.employee_name}} 负责本次护理</p></section><section v-if="selectedGrooming.status==='WAITING_PICKUP' && (isOwner || isManager || isAdmin || isReception)" class="pickup-pass"><div v-if="settlement?.status==='PAID'" style="display:inline-block;padding:3px 10px;margin-bottom:8px;border-radius:12px;background:#dcfce7;color:#166534;font-size:12px;font-weight:600">✓ 费用已结清 · 交宠就绪</div><span>{{ isOwner ? '到店接宠凭证' : '接宠凭证（前台/管理应急查看）' }}</span><strong>{{selectedGrooming.pickup_code}}</strong><p v-if="isOwner">{{ settlement?.status==='PAID' ? '您已结清洗护费用，到店出示此凭证即可快速提宠。' : '请先完成下方自付结算，到店出示此凭证即可提宠。' }}</p><div v-else style="display:flex;justify-content:space-between;align-items:center;margin-top:0.35rem"><small style="opacity:0.85">如顾客手机没电或未带，核实身份后可代填</small><button type="button" class="button quiet small" style="color:#fff;border-color:rgba(255,255,255,0.4)" @click="pickupInput = selectedGrooming.pickup_code">填入凭证码</button></div></section><section class="drawer-section"><h3><ShieldCheck :size="18"/>洗护前风险提示</h3><div v-if="petById(selectedGrooming.pet_id)?.risks?.length" class="risk-panel"><p v-for="risk in petById(selectedGrooming.pet_id)?.risks" :key="risk">{{risk}}</p></div><div v-else-if="petById(selectedGrooming.pet_id)" class="health-note">未发现过敏或皮肤病史，可按常规流程洗护。</div><div v-else class="health-note">还没有读到这份档案，请先核对宠物资料。</div></section><section v-if="selectedGrooming.precheck_note" class="drawer-section"><h3><ShieldCheck :size="18"/>接宠检查备注</h3><div class="health-note"><span>{{selectedGrooming.precheck_note}}</span></div></section><button v-if="((isGroomer && selectedGrooming.employee_id===staffId) || isManager || isAdmin) && selectedGrooming.status==='GROOMING' && selectedGrooming.stage==='TRIMMING'" class="button primary full" @click="scoring=selectedGrooming;drawer=null">记录健康评分<ArrowRight :size="17"/></button><section class="drawer-section"><h3>加项确认</h3><div v-for="a in visibleAddons.filter(a=>a.grooming_id===selectedGrooming?.id)" :key="a.id" class="drawer-addon"><span class="status-pill" :class="a.status">{{labels[a.status]}}</span><h3>{{a.name}}</h3><p>{{a.reason}}</p><strong>¥{{money(a.amount)}}</strong><div v-if="a.status==='PENDING' && isOwner" class="addon-actions"><button class="button secondary" :disabled="busy" @click="decide(a,'REJECTED')">拒绝加项</button><button class="button primary" :disabled="busy" @click="decide(a,'APPROVED')">同意本次加项</button></div><div v-else-if="a.status==='PENDING' && isGroomer && selectedGrooming.employee_id===staffId" class="addon-actions"><button class="button secondary" :disabled="busy" @click="decide(a,'REJECTED')">撤回加项申请</button></div><div v-else-if="a.status==='PENDING' && (isManager || isAdmin || isReception)" class="addon-actions"><button class="button secondary" :disabled="busy" @click="decide(a,'REJECTED')">代客拒绝</button><button class="button primary" :disabled="busy" @click="decide(a,'APPROVED')">代客同意</button></div><p v-else-if="a.status==='PENDING'" class="field-help">等待{{customerName(a.grooming_id)}}登录后同意</p></div><p v-if="!visibleAddons.some(a=>a.grooming_id===selectedGrooming?.id)" class="empty-note">本次护理暂无加项申请。</p></section><section v-if="groomingEvents.length" class="drawer-section"><h3>操作履历</h3><dl class="detail-list"><div v-for="ev in groomingEvents" :key="ev.id"><dt>{{labels[ev.event_type] || ev.event_type}}</dt><dd>{{ev.operator_name}} · {{ev.created_at.slice(5,16)}}</dd></div></dl></section><section v-if="selectedGrooming.status==='COMPLETED'" class="drawer-section"><h3>服务评价</h3><div v-if="reviews.find(r=>r.grooming_id===selectedGrooming?.id)" class="review-box" style="padding:0.75rem;background:var(--bg-subtle, rgba(0,0,0,0.03));border-radius:8px"><div style="display:flex;align-items:center;gap:0.35rem;margin-bottom:0.35rem"><span style="color:#f59e0b;font-size:1.1rem">{{'★'.repeat(reviews.find(r=>r.grooming_id===selectedGrooming?.id)!.rating)}}{{'☆'.repeat(5 - reviews.find(r=>r.grooming_id===selectedGrooming?.id)!.rating)}}</span><strong style="font-size:0.875rem">{{reviews.find(r=>r.grooming_id===selectedGrooming?.id)!.rating}} 星好评</strong><span v-if="reviews.find(r=>r.grooming_id===selectedGrooming?.id)!.customer_name" style="font-size:0.8rem;color:var(--text-muted, #888);margin-left:auto">{{reviews.find(r=>r.grooming_id===selectedGrooming?.id)!.customer_name}}</span></div><p style="margin:0;font-size:0.875rem;line-height:1.4">{{reviews.find(r=>r.grooming_id===selectedGrooming?.id)!.content}}</p></div><form v-else-if="isOwner && selectedGrooming.customer_id===ownerId" class="booking-form" @submit.prevent="submitReview(selectedGrooming.id)"><div class="field"><span>服务评分</span><div style="display:flex;gap:0.5rem;align-items:center"><button v-for="star in 5" :key="star" type="button" class="icon-button" :aria-label="`${star}星`" style="color:#f59e0b;font-size:1.3rem;background:none;border:none;cursor:pointer" @click="reviewRating=star">{{ star <= reviewRating ? '★' : '☆' }}</button><span style="font-size:0.875rem;font-weight:600">{{ reviewRating }} 星</span></div></div><label class="field">评价内容<textarea v-model="reviewContent" rows="3" maxlength="500" required placeholder="分享本次洗护体验与建议…" :disabled="reviewBusy" style="width:100%;border-radius:6px;padding:0.5rem;border:1px solid var(--border-color,#ddd)"></textarea></label><button class="button primary full" type="submit" :disabled="reviewBusy || !reviewContent.trim()">{{ reviewBusy ? '正在提交…' : '提交洗护评价' }}</button></form><p v-else class="empty-note">宠主暂未提交本次服务评价。</p></section></template>
  <section v-if="selectedAppointment && canCancel(selectedAppointment)" class="drawer-section"><button class="button secondary full" @click="rescheduling=selectedAppointment;drawer=null">改期预约</button></section>
  <section v-if="selectedAppointment && selectedAppointment.status==='CONFIRMED' && selectedAppointment.appointment_date===today && (isManager || isAdmin || isReception)" class="drawer-section"><button class="button primary full" :disabled="busy" @click="checkInAppointment(selectedAppointment)">确认到店并建立工单</button></section>
  <section v-if="selectedGrooming && ((isGroomer && selectedGrooming.employee_id===staffId) || isManager || isAdmin) && selectedGrooming.status==='GROOMING' && ['CHECK_IN','BATHING','DRYING'].includes(selectedGrooming.stage)" class="drawer-section">
    <div v-if="selectedGrooming.stage==='CHECK_IN'" class="field" style="margin-bottom:0.75rem"><span>接宠检查备注（可选）</span><input v-model="precheckNote" maxlength="300" placeholder="可记录进店已有打结、皮肤异常或情绪状况…" :disabled="busy" /></div>
    <div style="display:flex;gap:0.5rem"><button v-if="['BATHING','DRYING'].includes(selectedGrooming.stage)" class="button secondary" style="flex:1" :disabled="busy" @click="rollbackGrooming(selectedGrooming)">回退至{{ selectedGrooming.stage==='BATHING' ? '接宠检查' : '沐浴清洁' }}</button><button class="button primary" style="flex:2" :disabled="busy" @click="advanceGrooming(selectedGrooming)">推进到{{ selectedGrooming.stage==='CHECK_IN' ? '沐浴清洁' : selectedGrooming.stage==='BATHING' ? '吹干护理' : '精修造型' }}</button></div>
  </section>
  <section v-else-if="selectedGrooming && ((isGroomer && selectedGrooming.employee_id===staffId) || isManager || isAdmin) && selectedGrooming.status==='GROOMING' && selectedGrooming.stage==='TRIMMING'" class="drawer-section">
    <button class="button secondary full" :disabled="busy" @click="rollbackGrooming(selectedGrooming)">回退至吹干护理</button>
  </section>
  <section v-if="selectedGrooming && (isManager || isAdmin) && selectedGrooming.status==='GROOMING' && reassignableEmployees.length" class="drawer-section">
    <h3>改派洗护师</h3>
    <div style="display:flex;gap:0.5rem;align-items:center"><div style="flex:1"><AppSelect v-model="reassignGroomerId" label="选择接手洗护师" :options="reassignableEmployees.map(e=>({value:e.id,label:`${e.name} · ${e.title}`}))" placeholder="选择接手洗护师" :disabled="busy" compact /></div><button class="button secondary" :disabled="busy || !reassignGroomerId" @click="reassignGrooming(selectedGrooming)">确认改派</button></div>
  </section>
  <AddonRequest v-if="selectedGrooming && ((isGroomer && selectedGrooming.employee_id===staffId) || isManager || isAdmin) && selectedGrooming.status==='GROOMING'" :grooming-id="selectedGrooming.id" :current-username="account.username" @saved="onAddonRequested"/>
  <section v-if="selectedGrooming && (isManager || isAdmin || isReception) && (selectedGrooming.status==='GROOMING' || (selectedGrooming.status==='WAITING_PICKUP' && settlement?.status!=='PAID'))" class="drawer-section">
    <button class="button quiet small" style="color:var(--rose,#e11d48);border-color:rgba(225,29,72,0.3);width:100%" :disabled="busy" @click="voidGrooming(selectedGrooming)">作废 / 终止本次护理</button>
  </section>
  <section v-if="selectedGrooming && (isOwner || isManager || isAdmin || isReception)" class="drawer-section">
    <h3>服务结算</h3><p v-if="settlementLoading" class="field-help">正在核对金额…</p>
    <template v-else-if="settlement"><dl class="detail-list"><div><dt>服务费用</dt><dd>¥{{money(Number(settlement.base_amount))}}</dd></div><div><dt>已同意加项</dt><dd>¥{{money(Number(settlement.addon_amount))}}</dd></div><div><dt>应结金额</dt><dd class="detail-price">¥{{money(Number(settlement.amount))}}</dd></div><div><dt>状态</dt><dd>{{settlement.status==='PAID'?'已结算':settlement.status==='REFUNDED'?'已退款':'待结算'}}</dd></div><div v-if="settlement.status==='PAID' && settlement.method"><dt>支付方式</dt><dd>{{settlement.method==='ALIPAY'?'支付宝':settlement.method==='BALANCE'?'会员余额':settlement.method==='CARD'?'次卡核销':'现金'}}</dd></div><div v-if="settlement.status==='PAID' && settlement.trade_no"><dt>交易流水号</dt><dd style="font-family:monospace;font-size:12px">{{settlement.trade_no}}</dd></div></dl>
      <form v-if="settlement.status==='UNPAID' && selectedGrooming.status==='WAITING_PICKUP' && (isOwner || isManager || isAdmin || isReception)" class="booking-form" @submit.prevent="settleGrooming(selectedGrooming)">
        <div v-if="!isOwner" class="field"><span>结算方式</span><AppSelect v-model="paymentMethod" label="结算方式" :options="[{value:'CASH',label:'现金收款'},{value:'BALANCE',label:'会员余额'},{value:'CARD',label:'洗护次卡'},{value:'ALIPAY',label:'支付宝扫码收银'}]" :disabled="busy" /></div>
        <div v-else class="field"><span>结算方式</span><AppSelect v-model="paymentMethod" label="结算方式" :options="[{value:'BALANCE',label:'会员余额支付'},{value:'ALIPAY',label:'支付宝支付'}]" :disabled="busy" /><p v-if="paymentMethod==='BALANCE'" class="field-help" style="margin-top:0.25rem">当前账户可用余额：¥{{money(owner?.balance || 0)}}</p><p v-if="paymentMethod==='ALIPAY'" class="field-help" style="margin-top:0.25rem">使用支付宝个人开发者扫码付，打开支付宝扫一扫完成自付</p><p v-if="isOwner && paymentMethod==='BALANCE' && Number(owner?.balance || 0) < Number(settlement.amount)" class="inline-error" style="margin-top:0.25rem">当前账户余额不足以完成支付（需 ¥{{money(Number(settlement.amount))}}），建议切换支付宝线上自付</p></div>
        <div v-if="paymentMethod==='CARD'" class="field"><span>选择次卡</span><AppSelect v-if="applicableCards.length" v-model="paymentCardId" label="选择次卡" :options="applicableCards.map(c=>({value:c.id,label:`${c.name} · 剩余 ${c.remaining} 次`}))" :disabled="busy" placeholder="请选择适用次卡" /><p v-else class="inline-error" style="margin-top:0.25rem">该客户名下无适用于此洗护服务的有效次卡</p></div>
        <div v-if="paymentMethod==='CARD' && Number(settlement.addon_amount) > 0" class="field"><span>加项支付方式</span><AppSelect v-model="paymentAddonMethod" label="加项支付方式" :options="[{value:'CASH',label:`现金补差 ¥${money(Number(settlement.addon_amount))}`},{value:'BALANCE',label:`余额补差 ¥${money(Number(settlement.addon_amount))}`}]" :disabled="busy" /></div>
        <button class="button primary full" type="submit" :disabled="busy || (isOwner && paymentMethod==='BALANCE' && Number(owner?.balance || 0) < Number(settlement.amount)) || (paymentMethod==='CARD' && (!paymentCardId || !applicableCards.length))">{{isOwner ? (paymentMethod==='ALIPAY'?'调起支付宝收银台':'确认使用余额支付') : paymentMethod==='ALIPAY'?'调起支付宝扫码收银':paymentMethod==='CASH'?'确认已收取现金':paymentMethod==='BALANCE'?'确认余额结算':'确认核销次卡'}}</button>
      </form>
      <form v-if="settlement.status==='PAID' && (isManager || isAdmin)" class="booking-form" @submit.prevent="refundGrooming(selectedGrooming)"><label class="field">退款原因<input v-model="refundReason" required maxlength="255" placeholder="填写退款原因" :disabled="busy" /></label><p v-if="settlement.method==='CASH'" class="field-help">请先将现金退还给宠主，再登记退款。</p><p v-else-if="settlement.method==='ALIPAY'" class="field-help">退款申请将通过支付宝原路退回宠主账户。</p><button class="button secondary full" type="submit" :disabled="busy">确认退款并登记</button></form>
    </template>
  </section>
  <form v-if="selectedGrooming && selectedGrooming.status==='WAITING_PICKUP' && (settlement?.status==='PAID' || settlement?.status==='REFUNDED') && (isManager || isAdmin || isReception)" class="drawer-section booking-form" @submit.prevent="handoverGrooming(selectedGrooming)"><label class="field">接宠凭证<input v-model="pickupInput" inputmode="numeric" maxlength="6" pattern="[0-9]{6}" required placeholder="请输入宠主出示的 6 位凭证" :disabled="busy" /></label><button class="button primary full" type="submit" :disabled="busy">核验凭证并完成交宠</button></form>
 </DialogShell>
 <PaymentModal v-if="activePayment" :payment="activePayment" @close="activePayment=null" @paid="onPaymentCompleted"/>
 <DialogShell v-if="passwordDialogOpen" title="修改登录密码" subtitle="为了账号安全，新密码至少 12 个字符。" :busy="passwordBusy" @close="passwordDialogOpen=false">
  <form class="booking-form" @submit.prevent="changeSelfPassword">
   <div v-if="passwordError" class="inline-error" role="alert">{{ passwordError }}</div>
   <label class="field">当前原密码<input v-model="oldPassword" type="password" required autocomplete="current-password" placeholder="请输入当前密码" :disabled="passwordBusy" /></label>
   <label class="field">新密码<input v-model="selfNewPassword" type="password" required minlength="12" maxlength="72" autocomplete="new-password" placeholder="至少 12 位新密码" :disabled="passwordBusy" /></label>
   <button class="button primary full" type="submit" :disabled="passwordBusy || !oldPassword || !selfNewPassword">{{ passwordBusy ? '正在保存…' : '确认修改密码' }}</button>
  </form>
 </DialogShell>
 <DialogShell v-if="searchOpen" title="寻找一位小客人" subtitle="搜索宠物昵称、品种、护理项目或洗护师。" @close="searchOpen=false"><div ref="searchPanel" class="search-panel" @keydown="navigateSearch"><div class="search-input"><Search :size="20"/><input v-model="query" placeholder="例如：糯米、比熊、SPA…" aria-label="搜索内容" autofocus/></div><div class="search-results"><button v-for="r in searchResults" :key="`${r.kind}-${r.id}`" @click="selectSearchResult(r)"><span class="task-icon blue"><component :is="r.kind==='pet'?PawPrint:CalendarDays" :size="19"/></span><span><strong>{{r.title}}</strong><small>{{r.detail}}</small></span><ChevronRight :size="17"/></button><div v-if="!searchResults.length" class="empty-state"><Search :size="28"/><p>{{query?'没有找到匹配的宠物或预约。':'输入关键词开始搜索。'}}</p></div></div><p class="search-keyboard-help"><span>↑ ↓ 选择结果</span><span>Enter 打开</span><span>Esc 关闭</span></p></div></DialogShell>
</div>
</template>
