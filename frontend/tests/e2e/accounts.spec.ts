import { expect, test } from '@playwright/test'

test('administrator creates and disables a bound owner account', async ({ page }) => {
  const accounts = [{ username: 'admin', role_code: 'ADMIN', display_name: '系统管理员', title: '系统管理', enabled: true }]
  await page.route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname
    const method = route.request().method()
    let data: unknown = []
    if (path === '/api/auth/csrf') data = { token: 'token', header: 'X-CSRF-TOKEN' }
    else if (path === '/api/auth/me') data = { username: 'admin', role: '管理员', name: '系统管理员', title: '系统管理', customerId: null, employeeId: null }
    else if (path === '/api/customers') data = [{ id: 1, name: '林知夏', phone: '13800138001' }, { id: 2, name: '陈思远', phone: '13800138002' }]
    else if (path === '/api/accounts' && method === 'POST') {
      const form = route.request().postDataJSON()
      expect(form).toMatchObject({ username: 'owner2', role: 'OWNER', customerId: 2 })
      accounts.push({ username: 'owner2', role_code: 'OWNER', display_name: '陈思远', title: '银卡会员', enabled: true })
      data = accounts[1]
    } else if (path === '/api/accounts/owner2/password') { expect(route.request().postDataJSON().password).toBe('NewStrong456!'); data = { username:'owner2' } }
    else if (path === '/api/accounts/owner2/status') { accounts[1].enabled = false; data = accounts[1] }
    else if (path === '/api/accounts') data = accounts
    await route.fulfill({ json: { code: 200, message: '成功', data } })
  })
  await page.goto('/')
  await page.getByRole('button', { name: '员工与权限' }).click()
  await page.getByRole('button', { name: '新增账号' }).click()
  await page.getByLabel('登录账号').fill('owner2')
  await page.getByLabel('初始密码').fill('StrongPass123!')
  await page.getByRole('combobox', { name: '绑定宠主' }).click()
  await page.getByRole('option', { name: /陈思远/ }).click()
  await page.getByRole('button', { name: '创建账号' }).click()
  const row = page.getByRole('row').filter({ hasText: 'owner2' })
  await expect(row).toContainText('陈思远')
  await row.getByRole('button', { name: '重置密码' }).click()
  await page.getByLabel('新密码').fill('NewStrong456!')
  await page.getByRole('button', { name: '确认重置密码' }).click()
  await expect(page.getByRole('status')).toContainText('密码已重置')
  await row.getByRole('button', { name: '停用账号' }).click()
  await expect(row).toContainText('已停用')
})
