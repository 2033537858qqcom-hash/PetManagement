import test from 'node:test'
import assert from 'node:assert/strict'
import { acquireDialogLock } from '../src/lib/dialogLock.ts'
function page() {
  const root = {inert:false}
  const focused = {isConnected:true,focus(){focused.restored++},restored:0}
  const document = {body:{style:{overflow:'auto'}},activeElement:focused,getElementById:()=>root}
  return {root, focused, document}
}
test('replacing search with a detail dialog keeps the page locked until both mounts settle', () => {
  const p=page()
  const closeSearch=acquireDialogLock(p.document)
  const closeDetail=acquireDialogLock(p.document)
  closeSearch()
  assert.equal(p.root.inert,true)
  assert.equal(p.document.body.style.overflow,'hidden')
  assert.equal(p.focused.restored,0)
  closeDetail()
  assert.equal(p.root.inert,false)
  assert.equal(p.document.body.style.overflow,'auto')
  assert.equal(p.focused.restored,1)
  closeDetail()
  assert.equal(p.focused.restored,1)
})
test('a dialog preserves an existing inert state and scroll lock', () => {
  const p=page();p.root.inert=true;p.document.body.style.overflow='hidden'
  const close=acquireDialogLock(p.document);close()
  assert.equal(p.root.inert,true)
  assert.equal(p.document.body.style.overflow,'hidden')
})
