import { expect, test } from '@playwright/test'
import { fileURLToPath } from 'node:url'

test('administrator replaces project photos and the replacement is visible after logout and reload', async ({ page }) => {
  async function login(username: string) {
    await page.getByLabel('账号', { exact: true }).fill(username)
    await page.getByLabel('密码', { exact: true }).fill('petcare123')
    await page.getByRole('button', { name: '登录', exact: true }).click()
    await expect(page.getByRole('button', { name: '退出登录', exact: true })).toBeVisible()
  }
  const path = fileURLToPath(new URL('../../public/images/nuomi.png', import.meta.url))
  await page.goto('/')
  await login('admin')
  await page.getByRole('button', { name: '图片管理', exact: true }).click()
  if (process.env.CAPTURE_UI) {
    await expect(page.locator('.page-content')).toHaveCSS('opacity', '1')
    await expect.poll(() => page.locator('.photo-preview img').evaluateAll(images => images.slice(0, 3).every(image => (image as HTMLImageElement).complete && (image as HTMLImageElement).naturalWidth > 0))).toBe(true)
    await page.screenshot({ path: 'artifacts/photo-management.png' })
  }
  for (const [label, key, section, selector] of [
    ['低敏香波', 'product-shampoo', '商品订单', '.shop-row .product-icon img'],
    ['基础洗护', 'care-bath', '服务项目', '.catalog-art img'],
  ]) {
    const item = page.getByRole('article', { name: label, exact: true })
    const fileDialog = page.waitForEvent('filechooser')
    await item.getByRole('button', { name: `更换${label}`, exact: true }).click()
    await (await fileDialog).setFiles(path)
    await expect(page.getByRole('status')).toContainText(`${label}已更新`)
    await page.getByRole('navigation', { name: '主导航' }).getByRole('button', { name: section, exact: true }).click()
    await expect(page.locator(selector).first()).toHaveAttribute('src', new RegExp(`/api/media/photos/${key}/image`))
    await page.getByRole('button', { name: '图片管理', exact: true }).click()
    await item.getByRole('button', { name: `恢复${label}默认图片`, exact: true }).click()
    await expect(page.getByRole('status')).toContainText('已恢复默认')
  }
  const cover = page.getByRole('article', { name: '登录封面', exact: true })
  const chooser = page.waitForEvent('filechooser')
  await cover.getByRole('button', { name: '更换登录封面', exact: true }).click()
  await (await chooser).setFiles(path)
  await expect(page.getByRole('status')).toContainText('登录封面已更新')
  await expect(cover.locator('img')).toHaveAttribute('src', /\/api\/media\/photos\/entry-cover\/image\?v=/)
  await page.getByRole('button', { name: '退出登录', exact: true }).click()
  await expect(page.locator('.entry-cover > img')).toHaveAttribute('src', /\/api\/media\/photos\/entry-cover\/image\?v=/)
  await page.reload()
  await expect(page.locator('.entry-cover > img')).toHaveAttribute('src', /\/api\/media\/photos\/entry-cover\/image\?v=/)
  await expect.poll(() => page.locator('.entry-cover > img').evaluate((img: HTMLImageElement) => img.complete && img.naturalWidth > 0)).toBe(true)
  await login('manager')
  await expect(page.getByRole('button', { name: '图片管理', exact: true })).toHaveCount(0)
  await page.getByRole('button', { name: '退出登录', exact: true }).click()
  await login('admin')
  await page.getByRole('button', { name: '图片管理', exact: true }).click()
  await cover.getByRole('button', { name: '恢复登录封面默认图片', exact: true }).click()
  await expect(page.getByRole('status')).toContainText('已恢复默认')
  await page.getByRole('button', { name: '退出登录', exact: true }).click()
  await expect(page.locator('.entry-cover > img')).toHaveAttribute('src', '/images/entry-cover.jpg')
})
