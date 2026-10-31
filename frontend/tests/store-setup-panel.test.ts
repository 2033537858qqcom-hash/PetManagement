// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import StoreSetupPanel from '../src/components/StoreSetupPanel.vue'

const apiMock = vi.hoisted(() => vi.fn())
vi.mock('../src/lib/api', () => ({ api: apiMock }))
const wrappers: ReturnType<typeof mount>[] = []
const revoke = vi.fn()
beforeEach(() => {
  apiMock.mockReset(); apiMock.mockResolvedValue({ id: 4 }); revoke.mockReset()
  const OriginalURL = URL
  vi.stubGlobal('URL', class extends OriginalURL {
    static createObjectURL() { return 'blob:product-preview' }
    static revokeObjectURL = revoke
  })
})
afterEach(() => { wrappers.splice(0).forEach(wrapper => wrapper.unmount()); vi.unstubAllGlobals() })
async function setup() {
  const wrapper = mount(StoreSetupPanel, { props: { kind: 'product' } })
  wrappers.push(wrapper)
  await wrapper.findAll('button').find(button => button.text() === '新增耗材')!.trigger('click')
  await wrapper.get('input[maxlength="100"]').setValue('新耗材')
  await wrapper.get('input[maxlength="20"]').setValue('ml')
  await wrapper.get('input[type="number"]').setValue('0')
  return wrapper
}
async function selectFile(wrapper: ReturnType<typeof mount>, file: File) {
  const input = wrapper.get('input[type="file"]')
  Object.defineProperty(input.element, 'files', { configurable: true, value: [file] })
  await input.trigger('change')
}
describe('stock item photo upload', () => {
  it('opens the file picker from the upload button', async () => {
    const wrapper = await setup()
    const choose = vi.spyOn(wrapper.get('input[type="file"]').element, 'click')
    await wrapper.get('button[aria-label="选择耗材图片"]').trigger('click')
    expect(choose).toHaveBeenCalledOnce()
  })
  it('previews a selected image and allows removing it', async () => {
    const wrapper = await setup()
    await selectFile(wrapper, new File(['image'], 'item.png', { type: 'image/png' }))
    expect(wrapper.get('img[alt="耗材图片预览"]').attributes('src')).toBe('blob:product-preview')
    await wrapper.get('button[aria-label="移除耗材图片"]').trigger('click')
    expect(wrapper.find('img[alt="耗材图片预览"]').exists()).toBe(false)
    expect(revoke).toHaveBeenCalledWith('blob:product-preview')
  })
  it('submits the image and product details in one request', async () => {
    const wrapper = await setup()
    const image = new File(['image'], 'item.png', { type: 'image/png' })
    await selectFile(wrapper, image)
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    const [path, options] = apiMock.mock.calls[0]
    expect(path).toBe('/inventory')
    expect(options.body).toBeInstanceOf(FormData)
    expect(options.body.get('image')).toBe(image)
    const details = await new Promise<string>((resolve, reject) => {
      const reader = new FileReader(); reader.onload = () => resolve(String(reader.result)); reader.onerror = reject
      reader.readAsText(options.body.get('product'))
    })
    expect(JSON.parse(details)).toMatchObject({ name: '新耗材', unit: 'ml', threshold: 0 })
    expect(options.headers).toBeUndefined()
    expect(wrapper.emitted('saved')?.[0]).toEqual(['耗材已建立。'])
    expect(revoke).toHaveBeenCalled()
  })
  it('keeps JSON submission when no image was chosen', async () => {
    const wrapper = await setup()
    await wrapper.get('form').trigger('submit'); await flushPromises()
    expect(JSON.parse(apiMock.mock.calls[0][1].body)).toMatchObject({ name: '新耗材', unit: 'ml' })
  })
  it.each([
    [() => new File(['invalid'], 'bad.svg', { type: 'image/svg+xml' }), 'JPG'],
    [() => new File([new Uint8Array(5 * 1024 * 1024 + 1)], 'large.png', { type: 'image/png' }), '5 MB'],
  ])('rejects unsupported and oversized files before saving', async (file, message) => {
    const wrapper = await setup()
    await selectFile(wrapper, file())
    expect(wrapper.get('[role="alert"]').text()).toContain(message)
    expect(wrapper.find('img[alt="耗材图片预览"]').exists()).toBe(false)
    await wrapper.get('form').trigger('submit')
    expect(apiMock).not.toHaveBeenCalled()
  })
  it('accepts an image dropped onto the upload area', async () => {
    const wrapper = await setup()
    await wrapper.get('.product-photo-upload').trigger('drop', { dataTransfer: { files: [new File(['image'], 'drop.jpg', { type: 'image/jpeg' })] } })
    expect(wrapper.get('img').attributes('src')).toBe('blob:product-preview')
    expect(wrapper.text()).toContain('drop.jpg')
  })
  it('preserves entered details and the image after a failed save', async () => {
    const wrapper = await setup()
    await selectFile(wrapper, new File(['image'], 'item.png', { type: 'image/png' }))
    apiMock.mockRejectedValueOnce(new Error('图片保存失败，请重试'))
    await wrapper.get('form').trigger('submit'); await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('图片保存失败')
    expect(wrapper.get('input[maxlength="100"]').element.value).toBe('新耗材')
    expect(wrapper.get('img').attributes('src')).toBe('blob:product-preview')
    expect(wrapper.emitted('saved')).toBeUndefined()
  })
  it('disables repeat submission while the upload is in progress', async () => {
    const wrapper = await setup()
    await selectFile(wrapper, new File(['image'], 'item.png', { type: 'image/png' }))
    let finish!: (value: unknown) => void
    apiMock.mockImplementationOnce(() => new Promise(resolve => { finish = resolve }))
    await wrapper.get('form').trigger('submit'); await wrapper.get('form').trigger('submit')
    expect(apiMock).toHaveBeenCalledTimes(1)
    expect(wrapper.get('input[type="file"]').attributes('disabled')).toBeDefined()
    finish({ id: 4 }); await flushPromises()
  })
  it.each([
    ['customer', '宠主', '/customers'],
    ['employee', '洗护师', '/employees'],
    ['resource', '工位', '/resources'],
  ] as const)('keeps the existing %s form working without a photo control', async (kind, label, endpoint) => {
    const wrapper = mount(StoreSetupPanel, { props: { kind } })
    wrappers.push(wrapper)
    await wrapper.findAll('button').find(button => button.text() === `新增${label}`)!.trigger('click')
    expect(wrapper.find('input[type="file"]').exists()).toBe(false)
    if (kind === 'customer') {
      await wrapper.get('input[maxlength="60"]').setValue('新宠主')
      await wrapper.get('input[type="tel"]').setValue('13900139000')
    } else if (kind === 'employee') {
      const fields = wrapper.findAll('input[maxlength="60"]')
      await fields[0].setValue('小周'); await fields[1].setValue('护理师')
      await wrapper.get('input[maxlength="200"]').setValue('基础洗护')
      await wrapper.get('input[type="checkbox"][value="GIANT"]').setValue(true)
      await wrapper.getComponent({ name: 'AppSelect' }).vm.$emit('update:modelValue', 'CAT')
    } else await wrapper.get('input[maxlength="80"]').setValue('新洗护区')
    await wrapper.get('form').trigger('submit'); await flushPromises()
    expect(apiMock.mock.calls[0][0]).toBe(endpoint)
    expect(wrapper.emitted('saved')?.[0]).toEqual([`${label}已建立。`])
  })
  it('requires an employee size before saving that form', async () => {
    const wrapper = mount(StoreSetupPanel, { props: { kind: 'employee' } })
    wrappers.push(wrapper)
    await wrapper.findAll('button').find(button => button.text() === '新增洗护师')!.trigger('click')
    for (const checkbox of wrapper.findAll('input[type="checkbox"]')) await checkbox.setValue(false)
    await wrapper.get('form').trigger('submit')
    expect(wrapper.get('[role="alert"]').text()).toContain('至少选择一种适用体型')
    expect(apiMock).not.toHaveBeenCalled()
  })
})
