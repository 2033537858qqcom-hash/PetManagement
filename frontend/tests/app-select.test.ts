// @vitest-environment jsdom
import { afterEach, describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import AppSelect from '../src/components/AppSelect.vue'

const options = [
  { value: 1, label: '周凯' },
  { value: 2, label: '张伟' },
  { value: 3, label: '苏婉' },
]
const wrappers: ReturnType<typeof mount>[] = []
function setup(disabled = false) {
  const wrapper = mount(AppSelect, { attachTo: document.body, props: { modelValue: 1, options, label: '选择护理师', disabled } })
  wrappers.push(wrapper)
  return wrapper
}
afterEach(() => { wrappers.splice(0).forEach(wrapper => wrapper.unmount()) })

describe('accessible desktop select', () => {
  it('opens a labelled list and selects an option with the keyboard', async () => {
    const wrapper = setup()
    const trigger = wrapper.get('[role="combobox"]')
    expect(trigger.attributes('aria-expanded')).toBe('false')
    await trigger.trigger('keydown', { key: 'ArrowDown' })
    expect(trigger.attributes('aria-expanded')).toBe('true')
    expect(wrapper.get('[role="listbox"]').attributes('aria-label')).toBe('选择护理师')
    await trigger.trigger('keydown', { key: 'ArrowDown' })
    await trigger.trigger('keydown', { key: 'Enter' })
    expect(wrapper.emitted('update:modelValue')?.[0]).toEqual([2])
    expect(wrapper.emitted('change')?.[0]).toEqual([2])
    expect(trigger.attributes('aria-expanded')).toBe('false')
  })
  it('dismisses with Escape without changing the value or bubbling to a dialog', async () => {
    const wrapper = setup()
    const trigger = wrapper.get('[role="combobox"]')
    await trigger.trigger('click')
    await trigger.trigger('keydown', { key: 'ArrowDown' })
    const event = new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true })
    trigger.element.dispatchEvent(event)
    await wrapper.vm.$nextTick()
    expect(event.defaultPrevented).toBe(true)
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    expect(trigger.attributes('aria-expanded')).toBe('false')
  })
  it('selects by pointer and closes when clicking outside', async () => {
    const wrapper = setup()
    const trigger = wrapper.get('[role="combobox"]')
    await trigger.trigger('click')
    await wrapper.findAll('[role="option"]')[2].trigger('click')
    expect(wrapper.emitted('update:modelValue')?.[0]).toEqual([3])
    await trigger.trigger('click')
    document.body.dispatchEvent(new MouseEvent('pointerdown', { bubbles: true }))
    await wrapper.vm.$nextTick()
    expect(trigger.attributes('aria-expanded')).toBe('false')
  })
  it('does not open when disabled', async () => {
    const wrapper = setup(true)
    expect(wrapper.get('[role="combobox"]').attributes('disabled')).toBeDefined()
    await wrapper.get('[role="combobox"]').trigger('click')
    expect(wrapper.find('[role="listbox"]').exists()).toBe(false)
  })
  it('shows a placeholder until an optional numeric value is selected', async () => {
    const wrapper = setup()
    await wrapper.setProps({ modelValue: undefined, placeholder: '请选择洗护师' })
    const trigger = wrapper.get('[role="combobox"]')
    expect(trigger.text()).toBe('请选择洗护师')
    await trigger.trigger('click')
    await wrapper.findAll('[role="option"]')[1].trigger('click')
    expect(wrapper.emitted('update:modelValue')?.[0]).toEqual([2])
  })
  it('disables an empty menu and dismisses it if its options disappear', async () => {
    const wrapper = setup()
    const trigger = wrapper.get('[role="combobox"]')
    await trigger.trigger('click')
    await wrapper.setProps({ options: [] })
    expect(trigger.attributes('disabled')).toBeDefined()
    expect(wrapper.find('[role="listbox"]').exists()).toBe(false)
  })
  it('keeps the menu available during list and containing page scrolling', async () => {
    const wrapper = setup()
    const trigger = wrapper.get('[role="combobox"]')
    await trigger.trigger('click')
    await wrapper.get('[role="listbox"]').trigger('scroll')
    expect(trigger.attributes('aria-expanded')).toBe('true')
    document.body.dispatchEvent(new Event('scroll'))
    await wrapper.vm.$nextTick()
    expect(trigger.attributes('aria-expanded')).toBe('true')
  })
})

