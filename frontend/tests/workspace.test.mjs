import test from 'node:test'
import assert from 'node:assert/strict'
import { scheduleBlock, scopedPets, estimatePrice, serviceDuration, careStages, navigationFor, homeView, canBook, sessionAccount } from '../src/lib/workspace.ts'

test('schedule places service and 15-minute cleanup independently', () => {
  const block = scheduleBlock('10:00:00', '11:30:00')
  assert.equal(block.left, 100 / 12)
  assert.equal(block.width, 12.5)
  assert.equal(block.bufferWidth, 100 * 15 / 720)
})
test('schedule clips at store closing and rejects reversed time', () => {
  assert.equal(scheduleBlock('20:00', '21:00').bufferWidth, 0)
  assert.equal(scheduleBlock('12:00', '11:00'), null)
})
test('owner view scopes pets to the selected family', () => {
  const pets = [{id:1,customer_id:1}, {id:2,customer_id:2}]
  assert.deepEqual(scopedPets(pets, '宠主', 1).map(p=>p.id),[1])
  assert.equal(scopedPets(pets,'店长',1).length,2)
})
test('price estimate follows current backend size surcharge', () => {
  assert.equal(estimatePrice(98, 'LARGE'),148)
  assert.equal(estimatePrice(98, 'SMALL'),98)
})
test('configured size pricing controls displayed price and duration', () => {
  const service={price:98,duration:60,size_rules:[{size_type:'LARGE',price:210,duration:90}]}
  assert.equal(estimatePrice(service.price,'LARGE',service.size_rules),210)
  assert.equal(serviceDuration(service,'LARGE'),90)
  assert.equal(serviceDuration(service,'SMALL'),60)
})
test('five-stage view marks the current stage without inventing timestamps', () => {
  assert.deepEqual(careStages('DRYING').map(s=>s.state),['done','done','current','next','next'])
  assert.equal(careStages('DONE').filter(s=>s.state==='done').length,5)
})
test('login identity chooses a distinct workspace', () => {
  assert.deepEqual(navigationFor('店长').map(item => item.label), ['总览', '排班', '工单', '宠物', '会员', '服务', '员工', '库存', '订单', '设置'])
  assert.deepEqual(navigationFor('前台').map(item => item.text), ['今日接待', '预约排班', '到店与接宠', '宠物档案', '会员账户', '洗护项目', '商品订单'])
  assert.deepEqual(navigationFor('洗护师').map(item => item.label), ['总览', '工单', '排班', '宠物', '服务'])
  assert.deepEqual(navigationFor('管理员').map(item => item.label), ['总览', '排班', '工单', '宠物', '会员', '服务', '员工', '库存', '订单', '图片', '设置'])
  assert.equal(navigationFor('宠主')[0].text, '我的主页')
  assert.equal(homeView('洗护师'), '工单')
  assert.equal(homeView('前台'), '总览')
  assert.equal(canBook('管理员'), true)
  assert.equal(canBook('前台'), true)
  const owner = sessionAccount({ username: 'owner', role: '宠主' })
  assert.equal(owner.customerId, 1)
  assert.equal(owner.name, '林知夏')
  assert.equal(sessionAccount({ username: 'groomer', role: '洗护师' }).employeeId, 1)
})
