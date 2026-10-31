import { expect, test } from '@playwright/test'

test('care progress shows current, completed and unknown stages in cards and detail', async ({ page }) => {
  await page.route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname
    if (path === '/api/auth/csrf') return route.fulfill({ json: { code: 200, data: { token: 'test-token', header: 'X-CSRF-TOKEN' } } })
    if (path === '/api/auth/me') return route.fulfill({ json: { code: 200, data: { username: 'manager', role: '店长' } } })
    const data = path === '/api/grooming' ? [
      { id: 1, pet_id: 1, customer_id: 1, employee_id: 1, service_id: 1, stage: 'BATHING', status: 'GROOMING', pet_name: '糯米', employee_name: '周凯', service_name: '精致沐浴护理' },
      { id: 2, pet_id: 2, customer_id: 1, employee_id: 1, service_id: 1, stage: 'DONE', status: 'WAITING_PICKUP', pet_name: '旺财', employee_name: '周凯', service_name: '精致沐浴护理' },
      { id: 3, pet_id: 3, customer_id: 1, employee_id: 1, service_id: 1, stage: 'UNKNOWN', status: 'GROOMING', pet_name: '雪球', employee_name: '周凯', service_name: '精致沐浴护理' },
    ] : []
    await route.fulfill({ json: { code: 200, message: '成功', data } })
  })
  await page.goto('/')
  await page.getByRole('button', { name: /洗护工单/ }).click()
  const cards = page.locator('.work-order')
  const progress = cards.nth(0).getByRole('region', { name: '五阶段护理进度' })
  await expect(progress.getByRole('heading', { name: '沐浴清洁' })).toBeVisible()
  await expect(progress).toContainText('下一步吹干护理')
  await expect(progress.locator('[aria-current="step"]')).toContainText('沐浴清洁')
  await expect(cards.nth(1)).toContainText('护理已完成')
  await expect(cards.nth(2)).toContainText('阶段待更新')
  if (process.env.CAPTURE_UI) {
    await page.screenshot({ path: 'artifacts/care-progress.png', fullPage: true })
    await cards.nth(0).screenshot({ path: 'artifacts/care-progress-card.png', animations: 'disabled' })
  }
  await cards.nth(0).getByRole('button', { name: '查看护理详情' }).click()
  const detail = page.getByRole('dialog').getByRole('region', { name: '五阶段护理进度' })
  await expect(detail.getByRole('heading', { name: '沐浴清洁' })).toBeVisible()
  await expect(detail.locator('.care-progress-records li')).toHaveCount(5)
  await expect(detail.locator('.care-progress-records')).toBeVisible()
})
