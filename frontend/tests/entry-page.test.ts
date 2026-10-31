// @vitest-environment jsdom
import { afterEach, describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import EntryPage from '../src/components/EntryPage.vue'

const wrappers: ReturnType<typeof mount>[] = []
function setup() {
  const wrapper = mount(EntryPage, { attachTo: document.body, props: { error: '', busy: false } })
  wrappers.push(wrapper)
  return wrapper
}
async function registerForm() {
  const wrapper = setup()
  await wrapper.get('button.entry-switch-action').trigger('click')
  await wrapper.get('input[autocomplete="username"]').setValue('new_owner')
  await wrapper.get('input[autocomplete="name"]').setValue('夏小满')
  await wrapper.get('input[autocomplete="tel"]').setValue('13900139001')
  await wrapper.get('#register-password').setValue('StrongPass123!')
  await wrapper.get('#register-confirm').setValue('StrongPass123!')
  return wrapper
}
afterEach(() => { wrappers.splice(0).forEach(wrapper => wrapper.unmount()) })

describe('owner registration', () => {
  it('opens registration without privileged role choices and returns to login', async () => {
    const wrapper = await registerForm()
    expect(wrapper.get('#entry-title').text()).toBe('创建宠主账号')
    expect(wrapper.find('input[type="radio"]').exists()).toBe(false)
    await wrapper.get('button.entry-switch-action').trigger('click')
    expect(wrapper.get('#entry-title').text()).toBe('欢迎回来')
    expect(wrapper.get('input[autocomplete="current-password"]').element.value).toBe('')
    expect(wrapper.emitted('modeChange')).toHaveLength(2)
  })
  it('accepts an email as the owner account', async () => {
    const wrapper = await registerForm()
    await wrapper.get('input[autocomplete="username"]').setValue('2033537858qq.com@Gmail.com')
    await wrapper.get('form').trigger('submit')
    expect(wrapper.emitted('register')?.[0]).toEqual([{ username: '2033537858qq.com@gmail.com', password: 'StrongPass123!', name: '夏小满', phone: '13900139001' }])
  })
  it('submits normalized account and contact fields without changing the password', async () => {
    const wrapper = await registerForm()
    await wrapper.get('input[autocomplete="username"]').setValue(' new_owner ')
    await wrapper.get('input[autocomplete="name"]').setValue(' 夏小满 ')
    await wrapper.get('input[autocomplete="tel"]').setValue(' 13900139001 ')
    await wrapper.get('form').trigger('submit')
    expect(wrapper.emitted('register')?.[0]).toEqual([{ username: 'new_owner', password: 'StrongPass123!', name: '夏小满', phone: '13900139001' }])
  })
  it.each([
    ['input[autocomplete="username"]', 'ab', '账号'],
    ['input[autocomplete="name"]', '   ', '姓名'],
    ['input[autocomplete="name"]', '夏'.repeat(61), '姓名'],
    ['input[autocomplete="tel"]', 'bad-phone', '联系电话'],
    ['#register-password', 'short', '密码'],
    ['#register-password', '宠'.repeat(25), '密码'],
    ['#register-confirm', 'DifferentPass123!', '两次输入的密码不一致'],
  ])('rejects an invalid value in %s', async (selector, value, message) => {
    const wrapper = await registerForm()
    await wrapper.get(selector).setValue(value)
    await wrapper.get('form').trigger('submit')
    expect(wrapper.get('[role="alert"]').text()).toContain(message)
    expect(wrapper.emitted('register')).toBeUndefined()
  })
  it('keeps entered fields visible when the server rejects registration', async () => {
    const wrapper = await registerForm()
    await wrapper.setProps({ error: '账号已存在，请更换账号。' })
    expect(wrapper.get('[role="alert"]').text()).toContain('账号已存在')
    expect(wrapper.get('input[autocomplete="username"]').element.value).toBe('new_owner')
    expect(wrapper.get('input[autocomplete="name"]').element.value).toBe('夏小满')
  })
  it('blocks repeat submissions and switching modes while busy', async () => {
    const wrapper = await registerForm()
    await wrapper.setProps({ busy: true })
    await wrapper.get('form').trigger('submit')
    await wrapper.get('button.entry-switch-action').trigger('click')
    expect(wrapper.emitted('register')).toBeUndefined()
    expect(wrapper.get('#entry-title').text()).toBe('创建宠主账号')
    expect(wrapper.get('.entry-submit').attributes('disabled')).toBeDefined()
    expect(wrapper.findAll('input').every(input => input.attributes('disabled') !== undefined)).toBe(true)
  })
  it('preserves normal account login', async () => {
    const wrapper = setup()
    await wrapper.get('input[autocomplete="username"]').setValue(' manager ')
    await wrapper.get('input[autocomplete="current-password"]').setValue('petcare123')
    await wrapper.get('form').trigger('submit')
    expect(wrapper.emitted('login')?.[0]).toEqual(['manager', 'petcare123'])
  })
})
