// @vitest-environment jsdom
import { afterEach, describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import AppDatePicker from '../src/components/AppDatePicker.vue'

const wrappers: ReturnType<typeof mount>[] = []
function setup(min?: string) {
  const wrapper = mount(AppDatePicker, { attachTo: document.body, props: { modelValue: '2026-10-29', label: '预约日期', min } })
  wrappers.push(wrapper)
  return wrapper
}
afterEach(() => wrappers.splice(0).forEach(w => w.unmount()))

describe('date picker', () => {
  it('shows a labelled calendar and selects an allowed day', async () => {
    const wrapper = setup('2026-10-29')
    await wrapper.get('button[aria-label="预约日期"]').trigger('click')
    expect(wrapper.get('[role="dialog"]').attributes('aria-label')).toBe('预约日期日历')
    expect(wrapper.get('[data-date="2026-10-28"]').attributes('disabled')).toBeDefined()
    await wrapper.get('[data-date="2026-10-30"]').trigger('click')
    expect(wrapper.emitted('update:modelValue')?.[0]).toEqual(['2026-10-30'])
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
  })
  it('moves through dates with the keyboard and Escape closes only the calendar', async () => {
    const wrapper = setup()
    await wrapper.get('button[aria-label="预约日期"]').trigger('click')
    const day = wrapper.get('[data-date="2026-10-29"]')
    await day.trigger('keydown', { key: 'ArrowRight' })
    expect(wrapper.get('[data-date="2026-10-30"]').attributes('tabindex')).toBe('0')
    const event = new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true })
    wrapper.get('[data-date="2026-10-30"]').element.dispatchEvent(event)
    await wrapper.vm.$nextTick()
    expect(event.defaultPrevented).toBe(true)
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
  })
  it('updates the visible month without changing the selected date', async () => {
    const wrapper = setup()
    await wrapper.get('button[aria-label="预约日期"]').trigger('click')
    await wrapper.get('button[aria-label="下个月"]').trigger('click')
    expect(wrapper.text()).toContain('2026年11月')
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  })
})
