import { expect, test, type Page, type Route } from '@playwright/test'

const pet = { id: 2, customer_id: 1, name: '旺财', species: 'DOG', breed: '金毛', size_type: 'LARGE', weight: 28, coat: '双层毛', health_note: '香波香精过敏', age_band: 'ADULT', age_label: '成年（3-7岁）', allergy: 'FRAGRANCE', allergy_label: '香波香精过敏', skin_history: 'DERMATITIS', skin_label: '过敏性皮炎史', grooming_history: 'SPA', grooming_label: '曾做深层SPA', risks: ['洗护禁忌：香波香精过敏。请避开对应成分，优先使用低敏洗护。', '皮肤病史：过敏性皮炎史。洗护前检查患处，避免刺激和长时间吹风。'] }
const records = [
  { id: 1, pet_id: 2, recorded_on: '2026-09-01', weight: 27.5, score: 72 },
  { id: 2, pet_id: 2, recorded_on: '2026-10-01', weight: 28, score: 93 },
]
const appointment = { id: 4, customer_id: 1, pet_id: 2, service_id: 1, employee_id: 2, resource_id: 2, appointment_date: '2026-11-01', start_time: '11:00:00', end_time: '12:00:00', price: 148, status: 'CONFIRMED', pet_name: '旺财', service_name: '精致沐浴护理', employee_name: '张伟', resource_name: '2 号洗护区' }
const service = { id: 1, name: '精致沐浴护理', category: '基础洗护', price: 98, duration: 60, species: 'ALL', enabled: true }
const product = { id: 1, name: '燕麦低敏香波', stock: 20, threshold: 5, unit: 'ml', price: 68 }
const employee = { id: 2, name: '张伟', title: '资深洗护师', skills: '大型犬护理', species: 'DOG', sizes: 'SMALL,MEDIUM,LARGE,GIANT' }

async function fulfill(route: Route, data: unknown, status = 200) {
  await route.fulfill({ status, json: { code: status, message: status === 200 ? '操作成功' : '无法完成', data } })
}

test('owner edits fixed pet terms, sees the trend, cancels and orders', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 720 })
  const pets = [structuredClone(pet)]
  const appointments = [structuredClone(appointment)]
  const orders = [{ id: 1, customer_id: 1, product_id: 1, product_name: '燕麦低敏香波', quantity: 1, unit_price: 68, amount: 68, status: 'SUBMITTED', created_at: '2026-10-27 10:00' }]
  await mock(page, async route => {
    const path = new URL(route.request().url()).pathname
    const method = route.request().method()
    if (path === '/api/auth/me') return fulfill(route, { username: 'owner', role: '宠主', name: '林知夏', title: '金卡会员', customerId: 1, employeeId: null })
    if (method === 'PUT' && path.startsWith('/api/pets/')) {
      const body = route.request().postDataJSON()
      pets[0].allergy = body.allergy
      pets[0].allergy_label = body.allergy === 'NONE' ? '无已知过敏' : '香波香精过敏'
      pets[0].risks = body.allergy === 'NONE' ? ['皮肤病史：过敏性皮炎史。洗护前检查患处，避免刺激和长时间吹风。'] : pets[0].risks
      return fulfill(route, pets[0])
    }
    if (path === '/api/pets/2/records') return fulfill(route, records)
    if (path === '/api/pets') return fulfill(route, pets)
    if (path === '/api/appointments/4/cancel') { appointments[0].status = 'CANCELLED'; return fulfill(route, appointments[0]) }
    if (path === '/api/appointments') return fulfill(route, appointments)
    if (path === '/api/orders' && method === 'POST') {
      orders.unshift({ id: 2, customer_id: 1, product_id: 1, product_name: product.name, quantity: 1, unit_price: 68, amount: 68, status: 'SUBMITTED', created_at: '2026-10-29 12:00' })
      return fulfill(route, orders[0])
    }
    if (path === '/api/orders') return fulfill(route, orders)
    if (path === '/api/inventory') return fulfill(route, [product])
    if (path === '/api/services') return fulfill(route, [service])
    if (path === '/api/customers') return fulfill(route, [{ id: 1, name: '林知夏', phone: '13800138001', level_name: '金卡', balance: 680, points: 1280 }])
    return fulfill(route, [])
  })
  await page.goto('/')
  await page.getByRole('button', { name: '我的爱宠' }).click()
  await expect(page.getByText('香波香精过敏').first()).toBeVisible()
  await page.getByRole('button', { name: '编辑档案' }).click()
  const editor = page.getByRole('dialog', { name: '编辑爱宠档案' })
  await expect(editor.locator('textarea')).toHaveCount(0)
  await editor.getByLabel('宠物昵称').fill('旺财 ')
  await expect(editor.getByLabel('宠物昵称')).toHaveValue('旺财 ')
  await expect(editor.getByRole('combobox', { name: '品种', exact: true })).toHaveText('金毛')
  await editor.getByRole('combobox', { name: '品种', exact: true }).click()
  if (process.env.CAPTURE_UI) {
    await expect(editor.getByRole('listbox', { name: '品种', exact: true })).toHaveCSS('opacity', '1')
    await page.screenshot({ path: 'artifacts/unified-form-controls.png' })
  }
  await editor.getByRole('combobox', { name: '品种', exact: true }).press('Escape')
  await expect(editor).toBeVisible()
  await editor.getByRole('combobox', { name: '过敏史', exact: true }).click()
  await editor.getByRole('option', { name: '无已知过敏', exact: true }).click()
  const history = editor.getByRole('combobox', { name: '既往洗护记录', exact: true })
  await history.click()
  const historyMenu = editor.getByRole('listbox', { name: '既往洗护记录', exact: true })
  await expect(historyMenu).toBeVisible()
  await expect(historyMenu).toHaveCSS('opacity', '1')
  const panelBounds = await editor.boundingBox(), menuBounds = await historyMenu.boundingBox()
  expect(menuBounds!.y).toBeGreaterThanOrEqual(panelBounds!.y)
  expect(menuBounds!.y + menuBounds!.height).toBeLessThanOrEqual(panelBounds!.y + panelBounds!.height)
  expect(menuBounds!.x + menuBounds!.width).toBeLessThanOrEqual(panelBounds!.x + panelBounds!.width)
  await history.press('Escape')
  const saveRequest = page.waitForRequest(request => new URL(request.url()).pathname === '/api/pets/2' && request.method() === 'PUT')
  await editor.getByRole('button', { name: '保存档案' }).click()
  expect((await saveRequest).postDataJSON()).toMatchObject({ name: '旺财', weight: 28, allergy: 'NONE' })
  await expect(page.getByRole('status')).toContainText('档案已更新')

  await page.getByRole('button', { name: '打开完整档案' }).click()
  const archive = page.getByRole('dialog', { name: '旺财的档案' })
  await expect(archive.getByRole('img', { name: /健康趋势/ })).toBeVisible()
  await expect(archive.locator('polyline')).toHaveCount(2)
  await expect(archive.getByText('皮肤病史：过敏性皮炎史')).toBeVisible()
  await page.keyboard.press('Escape')

  await page.getByRole('button', { name: '我的预约' }).click()
  await page.getByRole('button', { name: '查看旺财预约详情' }).click()
  await page.getByRole('button', { name: '取消预约' }).click()
  await page.getByRole('button', { name: '确认取消预约' }).click()
  await expect(page.getByRole('status')).toContainText('预约已取消')
  await expect(page.getByText('已取消').first()).toBeVisible()
  await page.keyboard.press('Escape')

  await page.getByRole('button', { name: '我的订单' }).click()
  await expect(page.getByRole('heading', { name: '我的订单', level: 1 })).toBeVisible()
  await page.getByRole('combobox', { name: '燕麦低敏香波数量' }).click()
  const quantityMenu = page.getByRole('listbox', { name: '燕麦低敏香波数量' })
  const selectedQuantity = quantityMenu.getByRole('option', { name: '1 件' })
  await expect(selectedQuantity).toHaveAttribute('aria-selected', 'true')
  const textBounds = await selectedQuantity.locator('span').boundingBox()
  const quantityBounds = await quantityMenu.boundingBox()
  expect(textBounds!.height).toBeLessThanOrEqual(24)
  expect(quantityBounds!.x).toBeGreaterThanOrEqual(0)
  expect(quantityBounds!.x + quantityBounds!.width).toBeLessThanOrEqual(1280)
  if (process.env.CAPTURE_UI) {
    await expect(quantityMenu).toHaveCSS('opacity', '1')
    await page.screenshot({ path: 'artifacts/quantity-select.png' })
  }
  await page.getByRole('option', { name: '2 件' }).click()
  const orderRequest = page.waitForRequest(request => new URL(request.url()).pathname === '/api/orders' && request.method() === 'POST')
  await page.getByRole('button', { name: '提交订单' }).click()
  expect((await orderRequest).postDataJSON().quantity).toBe(2)
  await expect(page.getByText('2026-10-29 12:00')).toBeVisible()
})

test('manager disables a service and opens a walk-in order with the risk', async ({ page }) => {
  const services = [structuredClone(service)]
  await mock(page, async route => {
    const path = new URL(route.request().url()).pathname
    const method = route.request().method()
    if (path === '/api/auth/me') return fulfill(route, { username: 'manager', role: '店长', name: '林晓晴', title: '旗舰店店长', customerId: null, employeeId: null })
    if (path === '/api/services/1' && method === 'PUT') { services[0].enabled = false; return fulfill(route, services[0]) }
    if (path === '/api/services') return fulfill(route, services)
    if (path === '/api/pets') return fulfill(route, [pet])
    if (path === '/api/employees') return fulfill(route, [employee])
    if (path === '/api/grooming' && method === 'POST') return fulfill(route, { id: 9, pet_name: '旺财', employee_name: '张伟', stage: 'CHECK_IN', status: 'GROOMING' })
    if (path === '/api/grooming') return fulfill(route, [])
    return fulfill(route, [])
  })
  await page.goto('/')
  await page.getByRole('button', { name: '洗护工单' }).click()
  await page.getByRole('button', { name: '到店开单' }).click()
  const dialog = page.getByRole('dialog', { name: '到店开单' })
  await expect(dialog.getByText(/香波香精过敏/)).toBeVisible()
  await expect(dialog.getByRole('combobox', { name: '洗护项目', exact: true })).toHaveText('请选择项目')
  await expect(dialog.getByRole('button', { name: '生成到店工单' })).toBeDisabled()
  await dialog.getByRole('combobox', { name: '洗护项目', exact: true }).click()
  await dialog.getByRole('option', { name: '精致沐浴护理 · 60 分钟' }).click()
  await dialog.getByRole('combobox', { name: '负责洗护师' }).click()
  await dialog.getByRole('option', { name: '张伟 · 资深洗护师' }).click()
  await dialog.getByRole('button', { name: '生成到店工单' }).click()
  await expect(page.getByRole('status')).toContainText('到店工单已交给张伟')
  await page.getByRole('button', { name: '洗护项目' }).click()
  await page.getByRole('button', { name: '停用' }).click()
  await expect(page.getByText('已停用', { exact: true })).toBeVisible()
})

test('groomer reads the risk and previews a fixed-term health score', async ({ page }) => {
  let stage = 'BATHING'
  await mock(page, async route => {
    const path = new URL(route.request().url()).pathname
    if (path === '/api/auth/me') return fulfill(route, { username: 'groomer', role: '洗护师', name: '周凯', title: '首席美容师', customerId: null, employeeId: 1 })
    if (path === '/api/pets') return fulfill(route, [pet])
    if (path === '/api/grooming/4/advance') { stage = stage === 'BATHING' ? 'DRYING' : 'TRIMMING'; return fulfill(route, { stage }) }
    if (path === '/api/grooming') return fulfill(route, [{ id: 4, pet_id: 2, customer_id: 1, employee_id: 1, service_id: 1, stage, status: 'GROOMING', pickup_code: '661204', pet_name: '旺财', employee_name: '周凯', service_name: '精致沐浴护理' }])
    if (path === '/api/grooming/4/assessment') return fulfill(route, { score: 93, status: 'WAITING_PICKUP' })
    return fulfill(route, [])
  })
  await page.goto('/')
  await expect(page.getByText('洗护前有禁忌或皮肤病史')).toBeVisible()
  await page.getByRole('button', { name: '查看护理详情' }).click()
  await expect(page.getByRole('heading', { name: '洗护前风险提示' })).toBeVisible()
  await expect(page.getByText(/香波香精过敏/)).toBeVisible()
  await expect(page.getByRole('button', { name: '记录健康评分' })).toHaveCount(0)
  await page.getByRole('button', { name: '推进到吹干护理' }).click()
  await page.getByRole('button', { name: '推进到精修造型' }).click()
  await page.getByRole('button', { name: '记录健康评分' }).click()
  const dialog = page.getByRole('dialog', { name: '记录健康评分' })
  await expect(dialog.locator('textarea')).toHaveCount(0)
  await expect(dialog.getByRole('button', { name: '完成洗护并保存评分' })).toBeDisabled()
  await dialog.getByLabel('本次体重（kg）').fill('28.3')
  await dialog.getByRole('combobox', { name: '毛发', exact: true }).click()
  await dialog.getByRole('option', { name: '轻微打结' }).click()
  await expect(dialog.locator('.score-preview strong')).toHaveText('93')
  await dialog.getByRole('button', { name: '完成洗护并保存评分' }).click()
  await expect(page.getByRole('status')).toContainText('健康评分已记录')
})

test('reception records settlement before verifying pickup', async ({ page }) => {
  let status = 'UNPAID'
  let groomingStatus = 'WAITING_PICKUP'
  await mock(page, async route => {
    const path = new URL(route.request().url()).pathname
    if (path === '/api/auth/me') return fulfill(route, { username: 'reception', role: '前台', name: '陈雨桐', customerId: null, employeeId: null })
    if (path === '/api/pets') return fulfill(route, [pet])
    if (path === '/api/grooming/4/settlement') return fulfill(route, { grooming_id: 4, customer_id: 1, base_amount: 148, addon_amount: 0, amount: 148, status, method: status === 'PAID' ? 'CASH' : undefined })
    if (path === '/api/grooming/4/settle') { expect(route.request().postDataJSON().method).toBe('CASH'); status = 'PAID'; return fulfill(route, { status }) }
    if (path === '/api/grooming/4/handover') { expect(route.request().postDataJSON().pickupCode).toBe('661204'); groomingStatus = 'COMPLETED'; return fulfill(route, { status: groomingStatus }) }
    if (path === '/api/grooming') return fulfill(route, [{ id: 4, pet_id: 2, customer_id: 1, employee_id: 1, service_id: 1, stage: 'DONE', status: groomingStatus, pickup_code: '661204', pet_name: '旺财', employee_name: '周凯', service_name: '精致沐浴护理' }])
    return fulfill(route, [])
  })
  await page.goto('/')
  await page.getByRole('button', { name: '到店与接宠' }).click()
  await page.getByRole('button', { name: '查看护理详情' }).click()
  const drawer = page.getByRole('dialog', { name: '护理详情' })
  await expect(drawer.locator('.detail-price')).toHaveText('¥148.00')
  await expect(drawer.getByRole('button', { name: '核验凭证并完成交宠' })).toHaveCount(0)
  await drawer.getByRole('button', { name: '确认已收取现金' }).click()
  await expect(drawer.getByText('已结算')).toBeVisible()
  await drawer.getByLabel('接宠凭证').fill('661204')
  await drawer.getByRole('button', { name: '核验凭证并完成交宠' }).click()
  await expect(page.getByRole('status')).toContainText('已完成交宠')
})

test('assigned groomer requests an addon for owner confirmation', async ({page}) => {
  const addons:{id:number;grooming_id:number;name:string;reason:string;amount:number;status:string;pet_name:string}[]=[]
  await mock(page,async route=>{
    const path=new URL(route.request().url()).pathname
    if(path==='/api/auth/me') return fulfill(route,{username:'groomer',role:'洗护师',name:'周凯',title:'首席美容师',customerId:null,employeeId:1})
    if(path==='/api/pets') return fulfill(route,[pet])
    if(path==='/api/grooming/4/addons') {const form=route.request().postDataJSON();expect(form).toMatchObject({name:'低敏护理',reason:'皮肤敏感',amount:30});expect(form.requestId).toMatch(/^[0-9a-f-]{36}$/);addons.push({id:7,grooming_id:4,name:form.name,reason:form.reason,amount:form.amount,status:'PENDING',pet_name:'旺财'});return fulfill(route,addons[0])}
    if(path==='/api/grooming/addons') return fulfill(route,addons)
    if(path==='/api/grooming') return fulfill(route,[{id:4,pet_id:2,customer_id:1,employee_id:1,service_id:1,stage:'BATHING',status:'GROOMING',pickup_code:'661204',pet_name:'旺财',employee_name:'周凯',service_name:'精致沐浴护理'}])
    return fulfill(route,[])
  })
  await page.goto('/')
  await page.getByRole('button',{name:'查看护理详情'}).click()
  await page.getByRole('button',{name:'申请加项'}).click()
  await page.getByLabel('加项名称').fill('低敏护理')
  await page.getByLabel('申请原因').fill('皮肤敏感')
  await page.getByLabel('加项费用（元）').fill('30')
  await page.getByRole('button',{name:'提交加项申请'}).click()
  await expect(page.getByRole('status')).toContainText('加项申请已发送')
  await expect(page.getByText('低敏护理',{exact:true})).toBeVisible()
})

test('owner reschedules an appointment without cancelling it', async ({page}) => {
  const nextDate=new Date();nextDate.setDate(nextDate.getDate()+20)
  const date=`${nextDate.getFullYear()}-${String(nextDate.getMonth()+1).padStart(2,'0')}-${String(nextDate.getDate()).padStart(2,'0')}`
  const item={...appointment,appointment_date:date}
  await mock(page,async route=>{
    const path=new URL(route.request().url()).pathname
    if(path==='/api/auth/me') return fulfill(route,{username:'owner',role:'宠主',name:'林知夏',customerId:1,employeeId:null})
    if(path==='/api/pets') return fulfill(route,[pet])
    if(path==='/api/appointments/4/reschedule') {const form=route.request().postDataJSON();expect(form).toMatchObject({date,startTime:'12:15'});item.start_time='12:15:00';item.end_time='13:15:00';return fulfill(route,item)}
    if(path==='/api/appointments') return fulfill(route,[item])
    return fulfill(route,[])
  })
  await page.goto('/')
  await page.getByRole('button',{name:'我的预约'}).click()
  await page.getByRole('button',{name:'查看旺财预约详情'}).click()
  await page.getByRole('button',{name:'改期预约'}).click()
  const dialog=page.getByRole('dialog',{name:'改期预约'})
  await dialog.getByLabel('新的开始时间',{exact:true}).fill('12:15')
  await dialog.getByRole('button',{name:'确认改期'}).click()
  await expect(page.getByRole('status')).toContainText('预约已改期')
})

async function mock(page: Page, handle: (route: Route) => Promise<void>) {
  await page.route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname
    if (path === '/api/auth/csrf') return fulfill(route, { token: 'token', header: 'X-CSRF-TOKEN' })
    await handle(route)
  })
}
