import { test, expect } from '@playwright/test'

const pets = [
  { id: 1, customer_id: 1, name: '糯米', species: 'DOG', breed: '比熊', size_type: 'SMALL', weight: 5.2, coat: '卷毛', health_note: '耳后轻微打结' },
  { id: 2, customer_id: 2, name: '雪球', species: 'CAT', breed: '布偶猫', size_type: 'SMALL', weight: 4.8, coat: '长毛', health_note: '对噪音敏感' },
]
const customers = [
  { id: 1, name: '林知夏', phone: '13800138001', level_name: '金卡', balance: 680, points: 1280 },
  { id: 2, name: '陈思远', phone: '13800138002', level_name: '银卡', balance: 230, points: 520 },
]
const employees = [
  { id: 1, name: '周凯', title: '首席美容师', skills: '精致护理', species: 'DOG', sizes: 'SMALL' },
  { id: 2, name: '张伟', title: '资深洗护师', skills: '犬类护理', species: 'DOG', sizes: 'SMALL' },
  { id: 3, name: '苏婉', title: '猫咪护理师', skills: '猫咪护理', species: 'CAT', sizes: 'SMALL' },
]
const services = [{ id: 1, name: '精致沐浴护理', category: '基础洗护', price: 98, duration: 60, species: 'ALL' }]

test.beforeEach(async ({ page }) => {
  await page.route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname
    if (path === '/api/auth/csrf') return route.fulfill({ json: { code: 200, data: { token: 'test-token', header: 'X-CSRF-TOKEN' } } })
    if (path === '/api/auth/me') return route.fulfill({ json: { code: 200, data: { username: 'manager', role: '店长' } } })
    const data = path === '/api/pets' ? pets : path === '/api/customers' ? customers : path === '/api/employees' ? employees : path === '/api/services' ? services : path.startsWith('/api/services/recommendations/') ? [] : []
    await route.fulfill({ json: { code: 200, message: '成功', data } })
  })
  await page.goto('/')
  await expect(page.getByRole('heading', { name: '门店总览' })).toBeVisible()
})

test('schedule status filter uses a custom accessible menu and keyboard selection', async ({ page }) => {
  await page.getByRole('button', { name: '预约与排班' }).click()
  const trigger = page.getByRole('combobox', { name: '筛选预约状态' })
  await trigger.click()
  const list = page.getByRole('listbox', { name: '筛选预约状态' })
  await expect(list).toBeVisible()
  await expect(list).toHaveCSS('opacity', '1')
  await expect(list.getByRole('option', { name: '全部状态' })).toHaveAttribute('aria-selected', 'true')
  if (process.env.CAPTURE_UI) await page.screenshot({ path: 'artifacts/polished-select.png' })
  await trigger.press('ArrowDown')
  await trigger.press('Enter')
  await expect(trigger).toContainText('待到店')
  await expect(list).toHaveCount(0)
})

test('schedule calendar changes date and is fully visible', async ({ page }) => {
  await page.getByRole('button', { name: '预约与排班' }).click()
  const trigger = page.getByRole('button', { name: '选择排班日期' })
  await trigger.click()
  const calendar = page.getByRole('dialog', { name: '选择排班日期日历' })
  await expect(calendar).toBeVisible()
  await expect(calendar).toHaveCSS('opacity', '1')
  await expect(calendar.getByRole('gridcell', { selected: true })).toHaveCount(1)
  const rect = await calendar.boundingBox()
  expect(rect).not.toBeNull()
  expect(rect!.x).toBeGreaterThanOrEqual(0)
  expect(rect!.y).toBeGreaterThanOrEqual(0)
  expect(rect!.x + rect!.width).toBeLessThanOrEqual(1440)
  expect(rect!.y + rect!.height).toBeLessThanOrEqual(900)
  if (process.env.CAPTURE_UI) await page.screenshot({ path: 'artifacts/polished-calendar.png' })
  await calendar.getByRole('button', { name: '下个月' }).click()
  const nextMonthDay = calendar.getByRole('gridcell').filter({ hasText: /^15$/ }).first()
  await nextMonthDay.click()
  await expect(calendar).toHaveCount(0)
})

test('booking picker keeps dialog open on Escape and preserves exact time entry', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 720 })
  await page.getByRole('button', { name: '新建预约' }).click()
  const booking = page.getByRole('dialog', { name: '预约一次精心护理' })
  const customer = booking.getByRole('combobox', { name: '选择宠主' })
  await customer.click()
  await page.getByRole('option', { name: /陈思远/ }).click()
  await expect(booking.getByRole('button', { name: /雪球/ })).toBeVisible()
  await booking.getByRole('radio', { name: /精致沐浴护理/ }).check()
  const time = booking.getByLabel('开始时间', { exact: true })
  await time.fill('10:07')
  await expect(time).toHaveValue('10:07')
  const preset = booking.getByRole('combobox', { name: '选择开始时间时段' })
  await preset.click()
  const timeList = booking.getByRole('listbox', { name: '开始时间可选时段' })
  await expect(timeList).toBeVisible()
  await expect(timeList.getByRole('option', { name: '10:15' })).toBeVisible()
  await timeList.getByRole('option', { name: '10:15' }).click()
  await expect(time).toHaveValue('10:15')
  await preset.click()
  await preset.press('Escape')
  await expect(booking.getByRole('listbox', { name: '开始时间可选时段' })).toHaveCount(0)
  await expect(booking).toBeVisible()
  const dateButton = booking.getByRole('button', { name: '预约日期', exact: true })
  await dateButton.click()
  const calendar = booking.getByRole('dialog', { name: '预约日期日历' })
  await expect(calendar).toBeVisible()
  const dialogRect = await booking.boundingBox(), calendarRect = await calendar.boundingBox()
  expect(calendarRect!.y).toBeGreaterThanOrEqual(dialogRect!.y)
  expect(calendarRect!.y + calendarRect!.height).toBeLessThanOrEqual(dialogRect!.y + dialogRect!.height)
  if (process.env.CAPTURE_UI) { await expect(calendar).toHaveCSS('opacity', '1'); await page.screenshot({ path: 'artifacts/polished-booking-calendar.png' }) }
  await page.keyboard.press('Escape')
  await expect(calendar).toHaveCount(0)
  await expect(booking).toBeVisible()
})
