interface LockState {
  count: number
  overflow: string
  root: HTMLElement | null
  inert: boolean
  focus: HTMLElement | null
}
const locks = new WeakMap<Document, LockState>()

/** Transfer the same page lock when one dialog is replaced by another. */
export function acquireDialogLock(page: Document = document) {
  let state = locks.get(page)
  if (!state) {
    const root = page.getElementById('app')
    state = { count: 0, overflow: page.body.style.overflow, root, inert: root?.inert || false, focus: page.activeElement as HTMLElement | null }
    locks.set(page, state)
  }
  state.count++
  page.body.style.overflow = 'hidden'
  if (state.root) state.root.inert = true
  let released = false
  return () => {
    if (released) return
    released = true
    state.count--
    if (state.count > 0) return
    page.body.style.overflow = state.overflow
    if (state.root) state.root.inert = state.inert
    locks.delete(page)
    if (state.focus?.isConnected) state.focus.focus({ preventScroll: true })
  }
}
