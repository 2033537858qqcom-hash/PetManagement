import { expect, test } from '@playwright/test'

test('login handles invalid credentials, restores a session and logs out', async ({ page }) => {
  let authenticated = false
  await page.route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname
    if (path === '/api/auth/csrf') return route.fulfill({ json: { code: 200, data: { token: 'token', header: 'X-CSRF-TOKEN' } } })
    if (path === '/api/auth/me') return authenticated ? route.fulfill({ json: { code: 200, data: { username: 'manager', role: '店长' } } }) : route.fulfill({ status: 401, json: { message: '请先登录' } })
    if (path === '/api/auth/login') {
      const credentials = route.request().postDataJSON()
      if (credentials.password !== 'petcare123') return route.fulfill({ status: 401, json: { message: '账号或密码错误' } })
      expect(route.request().headers()['x-csrf-token']).toBe('token')
      authenticated = true
      return route.fulfill({ json: { code: 200, data: { username: 'manager', role: '店长' } } })
    }
    if (path === '/api/auth/logout') { authenticated = false; return route.fulfill({ status: 204 }) }
    return route.fulfill({ json: { code: 200, data: [] } })
  })
  await page.goto('/')
  await page.getByLabel('账号').fill('manager')
  await page.getByLabel('密码').fill('wrong')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page.getByRole('alert')).toHaveText('账号或密码错误')
  await page.getByLabel('密码').fill('petcare123')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page.getByRole('heading', { name: '门店总览' })).toBeVisible()
  await page.reload()
  await expect(page.getByRole('heading', { name: '门店总览' })).toBeVisible()
  await page.getByRole('button', { name: '退出登录' }).click()
  await expect(page.getByRole('heading', { name: '欢迎回来' })).toBeVisible()
})

test('chosen identity opens that role workspace', async ({ page }) => {
  type DemoAccount = { username: string; role: string; name: string; title: string; customerId: number | null; employeeId: number | null }
  const accounts: Record<string, DemoAccount> = {
    reception: { username: 'reception', role: '前台', name: '陈雨桐', title: '前台接待', customerId: null, employeeId: null },
    groomer: { username: 'groomer', role: '洗护师', name: '周凯', title: '首席美容师', customerId: null, employeeId: 1 },
    owner: { username: 'owner', role: '宠主', name: '林知夏', title: '金卡会员', customerId: 1, employeeId: null },
    admin: { username: 'admin', role: '管理员', name: '系统管理员', title: '系统管理', customerId: null, employeeId: null },
  }
  let current: DemoAccount | null = null
  await page.route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname
    if (path === '/api/auth/csrf') return route.fulfill({ json: { code: 200, data: { token: 'token', header: 'X-CSRF-TOKEN' } } })
    if (path === '/api/auth/me') return current ? route.fulfill({ json: { code: 200, data: current } }) : route.fulfill({ status: 401, json: { message: '请先登录' } })
    if (path === '/api/auth/login') {
      const credentials = route.request().postDataJSON()
      const next = accounts[credentials.username]
      if (!next || credentials.password !== 'petcare123') return route.fulfill({ status: 401, json: { message: '账号或密码错误' } })
      current = next
      return route.fulfill({ json: { code: 200, data: next } })
    }
    if (path === '/api/auth/logout') { current = null; return route.fulfill({ status: 204 }) }
    const data = path === '/api/customers' ? [{ id: 1, name: '林知夏', phone: '13800138001', level_name: '金卡', balance: 680, points: 1280 }]
      : path === '/api/pets' ? [{ id: 1, customer_id: 1, name: '糯米', species: 'DOG', breed: '比熊', size_type: 'SMALL', weight: 5, coat: '卷毛', health_note: '无' }]
      : path === '/api/employees' ? [{ id: 1, name: '周凯', title: '首席美容师', skills: '精致护理', species: 'DOG', sizes: 'SMALL' }]
      : []
    return route.fulfill({ json: { code: 200, data } })
  })

  async function enter(role: string, username: string) {
    await page.getByRole('button', { name: `${role}: ${username}` }).click()
    await expect(page.getByLabel('账号')).toHaveValue(username)
    await page.getByLabel('密码').fill('petcare123')
    await page.getByRole('button', { name: '登录', exact: true }).click()
  }

  await page.goto('/')
  await enter('前台', 'reception')
  await expect(page.getByRole('heading', { name: '今日接待' })).toBeVisible()
  await expect(page.locator('.workspace-user')).toContainText('陈雨桐')
  await expect(page.getByText('当前身份')).toHaveCount(0)
  await expect(page.getByRole('button', { name: '库存与耗材' })).toHaveCount(0)
  await expect(page.getByRole('button', { name: '新建预约' })).toBeVisible()
  await page.getByRole('button', { name: '退出登录' }).click()

  await enter('洗护师', 'groomer')
  await expect(page.getByRole('heading', { name: '我的护理工单' })).toBeVisible()
  await expect(page.getByRole('button', { name: '新建预约' })).toHaveCount(0)
  await expect(page.getByRole('button', { name: '我的预约安排' })).toBeVisible()
  await page.getByRole('button', { name: '退出登录' }).click()

  await enter('宠主', 'owner')
  await expect(page.getByRole('heading', { name: '林知夏，欢迎回来。' })).toBeVisible()
  await expect(page.getByRole('navigation', { name: '宠主导航' })).toBeVisible()
  await expect(page.getByText('家庭账户')).toBeVisible()
  await page.getByRole('button', { name: '退出登录' }).click()

  await enter('管理员', 'admin')
  await expect(page.getByRole('heading', { name: '系统总览' })).toBeVisible()
  await expect(page.getByRole('button', { name: '员工与权限' })).toBeVisible()
  await expect(page.getByRole('button', { name: '耗材库存' })).toBeVisible()
  await expect(page.getByRole('button', { name: '洗护工单' })).toBeVisible()
  await expect(page.getByRole('button', { name: '新建预约' })).toBeVisible()
  await expect(page.getByRole('button', { name: '门店设置' })).toBeVisible()
