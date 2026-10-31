import { expect, test, type Page, type Route } from '@playwright/test'

const pet = {
  id: 2,
  customer_id: 1,
  name: '旺财',
  species: 'DOG',
  breed: '金毛',
  size_type: 'LARGE',
  weight: 28,
  coat: '双层毛',
  health_note: '香波香精过敏',
  age_band: 'ADULT',
  age_label: '成年（3-7岁）',
  allergy: 'FRAGRANCE',
  allergy_label: '香波香精过敏',
  skin_history: 'DERMATITIS',
  skin_label: '过敏性皮炎史',
  grooming_history: 'SPA',
  grooming_label: '曾做深层SPA',
}

const customer = {
  id: 1,
  name: '林知夏',
  phone: '13800138001',
  level_name: '金卡',
  balance: 50.0,
  points: 1200,
}

async function fulfill(route: Route, data: unknown, status = 200) {
  await route.fulfill({ status, json: { code: status, message: status === 200 ? '操作成功' : '无法完成', data } })
}

async function mock(page: Page, handle: (route: Route) => Promise<void>) {
  await page.route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname
    if (path === '/api/auth/csrf') return fulfill(route, { token: 'token', header: 'X-CSRF-TOKEN' })
    await handle(route)
  })
}

test('pet owner settles waiting-pickup order with alipay payment via cashier modal', async ({ page }) => {
  let settlementStatus = 'UNPAID'
  let settlementMethod: string | undefined = undefined
  let tradeNo: string | undefined = undefined

  const groomingOrder = {
    id: 2,
    pet_id: 2,
    customer_id: 1,
    employee_id: 1,
    service_id: 1,
    stage: 'DONE',
    status: 'WAITING_PICKUP',
    pickup_code: '882103',
    pet_name: '旺财',
    employee_name: '周凯',
    service_name: '精致沐浴护理',
    base_price: 168.0,
  }

  const paymentOrder = {
    paymentNo: 'PAY20261030999901',
    groomingId: 2,
    customerId: 1,
    channel: 'ALIPAY',
    channelName: '支付宝',
    amount: 168.0,
    baseAmount: 168.0,
    addonAmount: 0,
    status: 'PENDING',
    qrCode: 'https://qr.alipay.com/baxmockpayload',
    expireAt: new Date(Date.now() + 15 * 60 * 1000).toISOString(),
    petName: '旺财',
    serviceName: '精致沐浴护理',
    pickupCode: '882103',
    sandbox: true,
  }

  await mock(page, async route => {
    const path = new URL(route.request().url()).pathname
    const method = route.request().method()

    if (path === '/api/auth/me') {
      return fulfill(route, { username: 'owner', role: '宠主', name: '林知夏', title: '金卡会员', customerId: 1, employeeId: null })
    }
    if (path === '/api/pets') return fulfill(route, [pet])
    if (path === '/api/customers') return fulfill(route, [customer])
    if (path === '/api/grooming') return fulfill(route, [groomingOrder])
    if (path === '/api/members/cards') return fulfill(route, [])
    if (path === '/api/addons') return fulfill(route, [])
    if (path === '/api/reviews') return fulfill(route, [])
    if (path === '/api/appointments') return fulfill(route, [])
    if (path === '/api/services') return fulfill(route, [{ id: 1, name: '精致沐浴护理', category: '基础洗护', price: 98, duration: 60, species: 'ALL', enabled: true }])
    if (path === '/api/employees') return fulfill(route, [{ id: 1, name: '周凯', title: '首席美容师', species: 'DOG', sizes: 'SMALL,MEDIUM,LARGE' }])
    if (path === '/api/health-records') return fulfill(route, [])
    if (path === '/api/grooming/2/events') return fulfill(route, [{ id: 1, grooming_id: 2, event_type: 'CHECK_IN', operator_name: '前台', created_at: '2026-10-30 10:00:00' }])

    if (path === '/api/grooming/2/settlement') {
      return fulfill(route, {
        grooming_id: 2,
        customer_id: 1,
        base_amount: 168.0,
        addon_amount: 0,
        amount: 168.0,
        status: settlementStatus,
        method: settlementMethod,
        trade_no: tradeNo,
      })
    }

    if (path === '/api/payment/create' && method === 'POST') {
      const body = route.request().postDataJSON()
      expect(body.groomingId).toBe(2)
      expect(body.channel).toBe('ALIPAY')
      return fulfill(route, paymentOrder)
    }

    if (path === `/api/payment/${paymentOrder.paymentNo}` && method === 'GET') {
      return fulfill(route, {
        ...paymentOrder,
        status: settlementStatus === 'PAID' ? 'SUCCESS' : 'PENDING',
        tradeNo,
      })
    }

    if (path === `/api/payment/${paymentOrder.paymentNo}/mock-pay` && method === 'POST') {
      settlementStatus = 'PAID'
      settlementMethod = 'ALIPAY'
      tradeNo = 'ALI_TX_MOCK_888888'
      return fulfill(route, {
        ...paymentOrder,
        status: 'SUCCESS',
        tradeNo,
      })
    }

    return fulfill(route, [])
  })

  await page.goto('/')
  // Switch to grooming work order drawer for pet owner
  await page.getByRole('button', { name: '护理进度' }).click()

  const orderCard = page.locator('.work-order').filter({ hasText: '旺财' })
  await expect(orderCard).toBeVisible()
  await orderCard.getByRole('button', { name: /查看护理详情/ }).click()

  const drawer = page.getByRole('dialog', { name: '护理详情' })
  await expect(drawer).toBeVisible()

  // Verify settlement section displays and shows amount
  await expect(drawer.getByText('服务费用')).toBeVisible()
  await expect(drawer.locator('.detail-price')).toHaveText('¥168.00')

  // Owner chooses WeChat Pay
  const selectTrigger = drawer.getByRole('combobox', { name: '结算方式' })
  await selectTrigger.click()
  await page.getByRole('option', { name: '支付宝支付' }).click()
  const payBtn = drawer.getByRole('button', { name: /调起支付宝收银台/ })
  await expect(payBtn).toBeVisible()
  await payBtn.click()
  const modal = page.getByRole('dialog', { name: '支付宝收银台' })
  await expect(modal).toBeVisible()
  await expect(modal.getByText('PAY20261030999901')).toBeVisible()
  await expect(modal.locator('.qr-svg')).toBeVisible()
  await expect(modal.getByText(/支付倒计时/)).toBeVisible()

  // Simulate mock payment click
  const mockBtn = modal.getByRole('button', { name: /模拟扫码支付成功/ })
  await expect(mockBtn).toBeVisible()
  await mockBtn.click()

  // Verify success state in modal and auto close
  await expect(modal.getByText('支付成功！')).toBeVisible()
  await expect(modal).not.toBeVisible({ timeout: 5000 })

  // Drawer now displays completed settlement and ready for pickup badge
  await expect(drawer.getByText('已结算')).toBeVisible()
  await expect(drawer.getByText('支付宝')).toBeVisible()
  await expect(drawer.getByText('ALI_TX_MOCK_888888')).toBeVisible()
  await expect(drawer.getByText(/费用已结清 · 交宠就绪/)).toBeVisible()
  await expect(drawer.getByText('882103')).toBeVisible()
})
