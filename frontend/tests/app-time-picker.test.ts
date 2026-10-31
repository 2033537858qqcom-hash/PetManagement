// @vitest-environment jsdom
import { afterEach, describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import AppTimePicker from '../src/components/AppTimePicker.vue'

const wrappers: ReturnType<typeof mount>[] = []
function setup(disabled = false) {
  const wrapper = mount(AppTimePicker, { attachTo: document.body, props: { modelValue: '11:30', label: '开始时间', min: '09:00', max: '12:00', disabled } })
  wrappers.push(wrapper)
  return wrapper
}
afterEach(() => wrappers.splice(0).forEach(w => w.unmount()))

describe('time picker', () => {
  it('offers times within the allowed range and supports keyboard selection', async () => {
    const wrapper = setup()
    const trigger = wrapper.get('[aria-label="选择开始时间时段"]')
    await trigger.trigger('click')
    expect(wrapper.findAll('[role="option"]').length).toBe(13)
    await trigger.trigger('keydown', { key: 'ArrowRight' })
    await trigger.trigger('keydown', { key: 'Enter' })
    expect(wrapper.emitted('update:modelValue')?.[0]).toEqual(['11:45'])
    expect(trigger.attributes('aria-expanded')).toBe('false')
  })
  it('still permits precise manual time input', async () => {
    const wrapper = setup()
    await wrapper.get('input[aria-label="开始时间"]').setValue('10:07')
    expect(wrapper.emitted('update:modelValue')?.[0]).toEqual(['10:07'])
  })
  it('does not open while disabled', async () => {
    const wrapper = setup(true)
    expect(wrapper.get('[aria-label="选择开始时间时段"]').attributes('disabled')).toBeDefined()
    await wrapper.get('[aria-label="选择开始时间时段"]').trigger('click')
    expect(wrapper.find('[role="listbox"]').exists()).toBe(false)
  })
})
