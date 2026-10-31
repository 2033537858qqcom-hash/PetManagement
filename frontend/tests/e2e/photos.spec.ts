import { test, expect, type Locator } from '@playwright/test'

const services = [
  { id: 1, name: '精致沐浴护理', category: '基础洗护', price: 98, duration: 60, species: 'ALL' },
  { id: 2, name: '蓬松造型修剪', category: '造型美容', price: 168, duration: 90, species: 'DOG' },
  { id: 3, name: '舒缓 SPA 护理', category: '深层护理', price: 148, duration: 75, species: 'ALL' },
  { id: 4, name: '猫咪静音洗护', category: '猫咪专护', price: 128, duration: 75, species: 'CAT' },
]

async function loadedImages(images: Locator, expected: number) {
  await expect(images).toHaveCount(expected)
  for (let i = 0; i < expected; i++) {
    await expect.poll(() => images.nth(i).evaluate((img: HTMLImageElement) => img.complete && img.naturalWidth > 0)).toBe(true)
  }
}

test('illustrative photos load across member, services, inventory and recommendations', async ({ page }) => {
  await page.route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname
    if (path === '/api/auth/csrf') return route.fulfill({ json: { code: 200, data: { token: 'test-token', header: 'X-CSRF-TOKEN' } } })
    if (path === '/api/auth/me') return route.fulfill({ json: { code: 200, data: { username: 'manager', role: '店长' } } })
    const data = path === '/api/pets' ? [{ id: 1, customer_id: 1, name: '糯米', species: 'DOG', breed: '比熊', size_type: 'SMALL', weight: 5, coat: '卷毛', health_note: '无' }]
      : path === '/api/customers' ? [{ id: 1, name: '林知夏', phone: '13800138001', level_name: '金卡', balance: 680, points: 1280 }, { id: 2, name: '陈思远', phone: '13800138002', level_name: '银卡', balance: 230, points: 520 }, { id: 3, name: '王小雨', phone: '13800138003', level_name: '普通会员', balance: 0, points: 120 }]
      : path === '/api/services' ? services
      : path.startsWith('/api/services/recommendations/') ? services.slice(0, 3).map(s => ({ ...s, score: 91, reason: '适合该宠物的护理' }))
      : path === '/api/members/cards' ? [{ id: 1, customer_id: 1, name: '萌宠畅洗卡', remaining: 6, total: 10, customer_name: '林知夏' }, { id: 2, customer_id: 2, name: '猫咪护理卡', remaining: 3, total: 5, customer_name: '陈思远' }]
      : path === '/api/inventory' ? [{ id: 1, name: '燕麦低敏香波', stock: 2300, threshold: 800, unit: 'ml' }, { id: 2, name: '碳酸泉 SPA 泡腾锭', stock: 18, threshold: 30, unit: '锭' }, { id: 3, name: '柔白护毛精华', stock: 450, threshold: 500, unit: 'ml' }]
      : []
    await route.fulfill({ json: { code: 200, message: '成功', data } })
  })
  await page.goto('/')
  await page.getByRole('button', { name: '会员与次卡' }).click()
  await loadedImages(page.locator('.card-art img'), 2)
  for (const wallet of await page.locator('.member-wallet').all()) {
    const url = await wallet.evaluate(el => getComputedStyle(el).backgroundImage)
    expect(url).toContain('/images/care-')
  }
  if (process.env.CAPTURE_UI) await page.screenshot({ path: 'artifacts/photos-members.png' })

  await page.getByRole('button', { name: '洗护项目' }).click()
  await loadedImages(page.locator('.catalog-art img'), 4)
  await expect(page.locator('.catalog-art img').nth(3)).toHaveAttribute('src', '/images/care-cat.jpg')
  if (process.env.CAPTURE_UI) await page.screenshot({ path: 'artifacts/photos-services.png' })

  await page.getByRole('button', { name: '库存与耗材' }).click()
  await loadedImages(page.locator('.product-icon img'), 3)
  if (process.env.CAPTURE_UI) await page.screenshot({ path: 'artifacts/photos-inventory.png' })
})

test('owner portal shows recommendation photos after login', async ({ page }) => {
  await page.route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname
    if (path === '/api/auth/csrf') return route.fulfill({ json: { code: 200, data: { token: 'test-token', header: 'X-CSRF-TOKEN' } } })
    if (path === '/api/auth/me') return route.fulfill({ json: { code: 200, data: { username: 'owner', role: '宠主', name: '林知夏', customerId: 1 } } })
    const data = path === '/api/pets' ? [{ id: 1, customer_id: 1, name: '糯米', species: 'DOG', breed: '比熊', size_type: 'SMALL', weight: 5, coat: '卷毛', health_note: '无' }]
      : path === '/api/customers' ? [{ id: 1, name: '林知夏', phone: '13800138001', level_name: '金卡', balance: 680, points: 1280 }]
      : path === '/api/services' || path.startsWith('/api/services/recommendations/') ? services.map(service => ({ ...service, score: 91, reason: '适合该宠物的护理' }))
      : []
    await route.fulfill({ json: { code: 200, message: '成功', data } })
  })
  await page.goto('/')
  await expect(page.getByRole('heading', { name: '林知夏，欢迎回来。' })).toBeVisible()
  await loadedImages(page.locator('.recommendation-photo img'), 3)
  if (process.env.CAPTURE_UI) await page.screenshot({ path: 'artifacts/photos-recommendations.png', fullPage: true })
  await page.locator('.recommendation-card').first().click()
  await expect(page.getByRole('dialog', { name: '预约一次精心护理' })).toBeVisible()
})
