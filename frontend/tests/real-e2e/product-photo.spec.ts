import { expect, test } from '@playwright/test'
import { fileURLToPath } from 'node:url'
import { randomUUID } from 'node:crypto'

test('manager creates a stock item with a photo that is shown in inventory, retail and its order', async ({ page }) => {
  await page.goto('/')
  await page.getByLabel('账号', { exact: true }).fill('manager')
  await page.getByLabel('密码', { exact: true }).fill('petcare123')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await page.getByRole('button', { name: '库存与耗材', exact: true }).click()
  await page.getByRole('button', { name: '新增耗材', exact: true }).click()
  const form = page.locator('.setup-panel').filter({ has: page.getByRole('heading', { name: '耗材管理' }) })
  await form.getByLabel('耗材名称', { exact: true }).fill('独立图片耗材')
  await form.getByLabel('库存单位', { exact: true }).fill('件')
  const chooser = page.waitForEvent('filechooser')
  await form.getByRole('button', { name: '选择耗材图片', exact: true }).click()
  await (await chooser).setFiles(fileURLToPath(new URL('../../public/images/product-serum.jpg', import.meta.url)))
  await expect(form.getByRole('img', { name: '耗材图片预览' })).toHaveAttribute('src', /^blob:/)
  if (process.env.CAPTURE_UI) {
    await expect(page.locator('.page-content')).toHaveCSS('opacity', '1')
    await form.screenshot({ path: 'artifacts/product-photo-upload.png' })
  }
  const creation = page.waitForResponse(response => new URL(response.url()).pathname === '/api/inventory' && response.request().method() === 'POST')
  await form.getByRole('button', { name: '保存耗材', exact: true }).click()
  const response = await creation
  expect(response.status()).toBe(200)
  const { data: product } = await response.json()
  const imageUrl = `/api/media/products/${product.image_key}/image`
  const row = page.getByRole('row').filter({ hasText: '独立图片耗材' })
  await expect(row.locator('img')).toHaveAttribute('src', imageUrl)
  await page.reload()
  await page.getByRole('button', { name: '库存与耗材', exact: true }).click()
  await expect(row.locator('img')).toHaveAttribute('src', imageUrl)
  await expect.poll(() => row.locator('img').evaluate((img: HTMLImageElement) => img.complete && img.naturalWidth > 0)).toBe(true)
  const { data: csrf } = await (await page.request.get('/api/auth/csrf')).json()
  const headers = { [csrf.header]: csrf.token }
  expect((await page.request.put(`/api/inventory/${product.id}/retail`, { headers, data: { saleUnits: 1, price: 88 } })).ok()).toBe(true)
  expect((await page.request.post(`/api/inventory/${product.id}/adjust`, { headers, data: { kind: 'IN', quantity: 2, reason: '测试入库', requestId: randomUUID() } })).ok()).toBe(true)
  await page.reload()
  await page.getByRole('button', { name: '商品订单', exact: true }).click()
  const item = page.locator('.shop-row').filter({ hasText: '独立图片耗材' })
  await expect(item.locator('img')).toHaveAttribute('src', imageUrl)
  await item.getByRole('button', { name: '提交订单', exact: true }).click()
  await expect(page.locator('.order-row').filter({ hasText: '独立图片耗材' }).locator('img')).toHaveAttribute('src', imageUrl)
})
