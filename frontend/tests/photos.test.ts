// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { applyPhoto, loadPhotos, photoAssets, photoUrl, type PhotoAsset } from '../src/lib/photos'
import PhotoManager from '../src/components/PhotoManager.vue'

const apiMock = vi.hoisted(() => vi.fn())
vi.mock('../src/lib/api', () => ({ api: apiMock }))
const asset = (customized = false): PhotoAsset => ({ key: 'entry-cover', label: '登录封面', category: '品牌封面', description: '登录与注册页面', defaultUrl: '/images/entry-cover.jpg', url: customized ? '/api/media/photos/entry-cover/image?v=updated' : '/images/entry-cover.jpg', customized })
const wrappers: ReturnType<typeof mount>[] = []
beforeEach(async () => { apiMock.mockReset(); apiMock.mockResolvedValue([]); await loadPhotos() })
afterEach(() => { wrappers.splice(0).forEach(wrapper => wrapper.unmount()) })

describe('shared photo sources', () => {
  it('keeps defaults available before the catalogue loads', () => {
    expect(photoUrl('/images/entry-cover.jpg')).toBe('/images/entry-cover.jpg')
  })
  it('loads replacements and applies updates and restoration immediately', async () => {
    apiMock.mockResolvedValue([asset(true)])
    await loadPhotos()
    expect(photoUrl('/images/entry-cover.jpg')).toContain('/api/media/photos/')
    applyPhoto(asset(false))
    expect(photoUrl('/images/entry-cover.jpg')).toBe('/images/entry-cover.jpg')
    expect(photoAssets.value).toHaveLength(1)
  })
  it('does not let an older catalogue response overwrite a newly uploaded photo', async () => {
    let resolve!: (photos: PhotoAsset[]) => void
    apiMock.mockImplementationOnce(() => new Promise<PhotoAsset[]>(done => { resolve = done }))
    const pending = loadPhotos()
    applyPhoto(asset(true))
    resolve([asset(false)])
    await pending
    expect(photoUrl('/images/entry-cover.jpg')).toContain('v=updated')
  })
  it('leaves existing sources intact if the catalogue fails to load', async () => {
    applyPhoto(asset(true))
    apiMock.mockRejectedValueOnce(new Error('服务暂时不可用'))
    await expect(loadPhotos()).rejects.toThrow('服务暂时不可用')
    expect(photoUrl('/images/entry-cover.jpg')).toContain('v=updated')
  })
  it('rejects a malformed catalogue without removing existing photos', async () => {
    applyPhoto(asset(true))
    apiMock.mockResolvedValueOnce({})
    await expect(loadPhotos()).rejects.toThrow('图片列表暂时无法加载')
    expect(photoUrl('/images/entry-cover.jpg')).toContain('v=updated')
  })
})

async function setup(customized = false) {
  apiMock.mockResolvedValue([asset(customized)])
  const wrapper = mount(PhotoManager, { attachTo: document.body })
  wrappers.push(wrapper)
  await flushPromises()
  return wrapper
}
async function selectFile(wrapper: ReturnType<typeof mount>, file: File) {
  const change = wrapper.findAll('button').find(button => button.text().includes('更换图片'))!
  await change.trigger('click')
  const input = wrapper.get('input[type="file"]')
  Object.defineProperty(input.element, 'files', { configurable: true, value: [file] })
  await input.trigger('change')
  await flushPromises()
}
describe('photo management', () => {
  it('shows previews, usage and the upload format limits', async () => {
    const wrapper = await setup()
    expect(wrapper.text()).toContain('登录封面')
    expect(wrapper.text()).toContain('登录与注册页面')
    expect(wrapper.text()).toContain('5 MB')
    expect(wrapper.get('img').attributes('src')).toBe('/images/entry-cover.jpg')
  })
  it('uploads a selected image and immediately refreshes the preview', async () => {
    const wrapper = await setup()
    apiMock.mockResolvedValueOnce(asset(true))
    const file = new File(['image'], 'cover.png', { type: 'image/png' })
    await selectFile(wrapper, file)
    const [path, options] = apiMock.mock.calls.at(-1)!
    expect(path).toBe('/media/photos/entry-cover')
    expect(options.method).toBe('PUT')
    expect(options.body.get('file')).toBe(file)
    expect(wrapper.get('img').attributes('src')).toContain('v=updated')
    expect(wrapper.get('[role="status"]').text()).toContain('已更新')
  })
  it.each([
    [() => new File(['invalid'], 'image.svg', { type: 'image/svg+xml' }), 'JPG'],
    [() => new File([new Uint8Array(5 * 1024 * 1024 + 1)], 'large.png', { type: 'image/png' }), '5 MB'],
  ])('rejects unsupported or oversized local files', async (file, message) => {
    const wrapper = await setup()
    const before = apiMock.mock.calls.length
    await selectFile(wrapper, file())
    expect(wrapper.get('[role="alert"]').text()).toContain(message)
    expect(apiMock.mock.calls).toHaveLength(before)
  })
  it('keeps the previous preview and allows retry after an upload failure', async () => {
    const wrapper = await setup()
    apiMock.mockRejectedValueOnce(new Error('上传失败，请重试。'))
    await selectFile(wrapper, new File(['image'], 'cover.png', { type: 'image/png' }))
    expect(wrapper.get('[role="alert"]').text()).toContain('上传失败')
    expect(wrapper.get('img').attributes('src')).toBe('/images/entry-cover.jpg')
    expect(wrapper.findAll('button').some(button => button.attributes('disabled') === undefined)).toBe(true)
  })
  it('restores the default image', async () => {
    const wrapper = await setup(true)
    apiMock.mockResolvedValueOnce(asset(false))
    await wrapper.findAll('button').find(button => button.text().includes('恢复默认'))!.trigger('click')
    await flushPromises()
    expect(apiMock).toHaveBeenLastCalledWith('/media/photos/entry-cover', { method: 'DELETE' })
    expect(wrapper.get('img').attributes('src')).toBe('/images/entry-cover.jpg')
  })
  it('keeps the custom image if restoring the default fails', async () => {
    const wrapper = await setup(true)
    apiMock.mockRejectedValueOnce(new Error('暂时无法恢复图片'))
    await wrapper.findAll('button').find(button => button.text().includes('恢复默认'))!.trigger('click')
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('暂时无法恢复图片')
    expect(wrapper.get('img').attributes('src')).toContain('v=updated')
  })
  it('filters previews by category', async () => {
    apiMock.mockResolvedValueOnce([asset(), { ...asset(), key: 'product-shampoo', label: '低敏香波', category: '商品图片' }])
    const wrapper = mount(PhotoManager)
    wrappers.push(wrapper)
    await flushPromises()
    await wrapper.findAll('button').find(button => button.text() === '商品图片')!.trigger('click')
    expect(wrapper.findAll('article')).toHaveLength(1)
    expect(wrapper.get('article').attributes('aria-label')).toBe('低敏香波')
  })
  it('shows a useful error when the catalogue cannot load', async () => {
    apiMock.mockRejectedValueOnce(new Error('无法加载图片'))
    const wrapper = mount(PhotoManager)
    wrappers.push(wrapper)
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('无法加载图片')
  })
})
