// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import PaymentModal, { type PaymentInfo } from '../src/components/PaymentModal.vue'

const apiMock = vi.hoisted(() => vi.fn())
vi.mock('../src/lib/api', () => ({ api: apiMock }))

describe('PaymentModal', () => {
  const samplePayment: PaymentInfo = {
    paymentNo: 'PAY20261030001',
    groomingId: 2,
    customerId: 1,
    channel: 'ALIPAY',
    channelName: '支付宝',
    amount: 168.0,
    baseAmount: 168.0,
    addonAmount: 0,
    status: 'PENDING',
    qrCode: 'https://qr.alipay.com/baxsample123',
    expireAt: new Date(Date.now() + 15 * 60 * 1000).toISOString(),
    petName: '旺财',
    serviceName: '蓬松造型修剪',
    sandbox: true,
  }

  beforeEach(() => {
    vi.useFakeTimers()
    apiMock.mockReset()
    apiMock.mockResolvedValue({ status: 'PENDING' })
    document.body.innerHTML = '<div id="app"></div>'
  })

  afterEach(() => {
    vi.useRealTimers()
    document.body.innerHTML = ''
  })

  it('renders order summary, channel branding and dynamic QR code', () => {
    const wrapper = mount(PaymentModal, {
      props: { payment: samplePayment },
      attachTo: document.body,
    })

    expect(document.body.textContent).toContain('支付宝收银台')
    expect(document.body.textContent).toContain('#2 · 旺财 · 蓬松造型修剪')
    expect(document.body.textContent).toContain('PAY20261030001')
    expect(document.body.textContent).toContain('168.00')
    expect(document.body.querySelector('svg.qr-svg')).not.toBeNull()
    wrapper.unmount()
  })

  it('renders Alipay channel styling correctly', () => {
    const alipayPayment: PaymentInfo = {
      ...samplePayment,
      channel: 'ALIPAY',
      channelName: '支付宝',
      qrCode: 'https://qr.alipay.com/bax123',
    }
    const wrapper = mount(PaymentModal, {
      props: { payment: alipayPayment },
      attachTo: document.body,
    })

    expect(document.body.textContent).toContain('支付宝收银台')
    expect(document.body.textContent).toContain('支付宝个人开发者 · 扫码付')
    wrapper.unmount()
  })

  it('polls payment status and triggers paid event upon success', async () => {
    const wrapper = mount(PaymentModal, {
      props: { payment: samplePayment },
      attachTo: document.body,
    })

    apiMock.mockResolvedValueOnce({
      ...samplePayment,
      status: 'SUCCESS',
      tradeNo: 'WX_TX_9999',
    })

    // Advance timers for polling interval
    vi.advanceTimersByTime(1600)
    await flushPromises()

    expect(document.body.textContent).toContain('支付成功！')

    // Advance timer for modal auto-close delay
    vi.advanceTimersByTime(1300)
    await flushPromises()

    expect(wrapper.emitted('paid')).toBeTruthy()
    expect(wrapper.emitted('close')).toBeTruthy()
    wrapper.unmount()
  })

  it('simulates scan and pay via sandbox button', async () => {
    const wrapper = mount(PaymentModal, {
      props: { payment: samplePayment },
      attachTo: document.body,
    })

    apiMock.mockResolvedValueOnce({
      ...samplePayment,
      status: 'SUCCESS',
      tradeNo: 'SIM_WECHAT_123',
    })

    const simBtn = document.body.querySelector('.sandbox-button') as HTMLButtonElement
    expect(simBtn).not.toBeNull()
    simBtn.click()
    await flushPromises()

    expect(apiMock).toHaveBeenCalledWith('/payment/PAY20261030001/mock-pay', { method: 'POST' })
    expect(document.body.textContent).toContain('支付成功！')

    vi.advanceTimersByTime(1300)
    await flushPromises()

    expect(wrapper.emitted('paid')).toBeTruthy()
    expect(wrapper.emitted('close')).toBeTruthy()
    wrapper.unmount()
  })

  it('shows expired state when countdown expires or status is closed', async () => {
    const expiredPayment: PaymentInfo = {
      ...samplePayment,
      status: 'CLOSED',
      expireAt: '2026-10-30 12:00:00',
    }
    const wrapper = mount(PaymentModal, {
      props: { payment: expiredPayment },
      attachTo: document.body,
    })

    expect(document.body.textContent).toContain('二维码已过期')
    expect(document.body.querySelector('.sandbox-button')).toBeNull()
    wrapper.unmount()
  })

  it('emits close event when cancel button is clicked', async () => {
    const wrapper = mount(PaymentModal, {
      props: { payment: samplePayment },
      attachTo: document.body,
    })

    const buttons = Array.from(document.body.querySelectorAll('button'))
    const cancelBtn = buttons.find(b => b.textContent?.includes('取消支付'))
    expect(cancelBtn).toBeDefined()
    cancelBtn!.click()

    expect(wrapper.emitted('close')).toBeTruthy()
    wrapper.unmount()
  })
})
