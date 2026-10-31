import { expect, test } from '@playwright/test'

test('staff records recharge and creates a service card', async ({ page }) => {
  const customer={id:1,name:'林知夏',phone:'13800138001',level_name:'金卡',balance:680,points:1280}
  const cards:unknown[]=[]
  let rechargeRequests=0
  await page.route('**/api/**',async route=>{
    const path=new URL(route.request().url()).pathname, method=route.request().method()
    let data:unknown=[]
    if(path==='/api/auth/csrf') data={token:'token',header:'X-CSRF-TOKEN'}
    else if(path==='/api/auth/me') data={username:'manager',role:'店长',name:'林晓晴',title:'店长'}
    else if(path==='/api/customers') data=[customer]
    else if(path==='/api/services') data=[{id:1,name:'基础洗护',category:'基础洗护',price:98,duration:60,species:'ALL',enabled:true}]
    else if(path==='/api/customers/1/history') data={balance:[],cardSales:[],cardUsage:[]}
    else if(path==='/api/customers/1/recharge' && method==='POST') {const form=route.request().postDataJSON();expect(form.amount).toBe(100);expect(form.requestId).toMatch(/^[0-9a-f-]{36}$/);customer.balance+=100;rechargeRequests++;data={request_id:form.requestId}}
    else if(path==='/api/customers/1/cards' && method==='POST') {const form=route.request().postDataJSON();expect(form).toMatchObject({name:'基础卡',total:5,price:400,serviceIds:[1]});cards.push({id:1,customer_id:1,customer_name:'林知夏',name:'基础卡',remaining:5,total:5});data=cards[0]}
    else if(path==='/api/members/cards') data=cards
    await route.fulfill({json:{code:200,message:'成功',data}})
  })
  await page.goto('/')
  await page.getByRole('button',{name:'会员与次卡'}).click()
  await page.getByLabel('充值金额').fill('100')
  await page.getByRole('button',{name:'确认已收现金并充值'}).click()
  await expect(page.getByRole('status')).toContainText('充值已记录')
  expect(rechargeRequests).toBe(1)
  await page.getByLabel('次卡名称').fill('基础卡')
  await page.getByLabel('护理次数').fill('5')
  await page.getByLabel('办卡金额').fill('400')
  await page.getByRole('checkbox',{name:'基础洗护'}).check()
  await page.getByRole('button',{name:'确认已收现金并办卡'}).click()
  await expect(page.getByRole('status')).toContainText('次卡已办理')
  await expect(page.getByRole('heading',{name:'基础卡'})).toBeVisible()
})

test('a rejected recharge can be corrected and retried', async ({page}) => {
  let attempts=0
  await page.route('**/api/**',async route=>{
    const path=new URL(route.request().url()).pathname
    let data:unknown=[]
    if(path==='/api/auth/csrf') data={token:'token',header:'X-CSRF-TOKEN'}
    else if(path==='/api/auth/me') data={username:'manager',role:'店长',name:'林晓晴',title:'店长'}
    else if(path==='/api/customers') data=[{id:1,name:'林知夏',phone:'13800138001',level_name:'金卡',balance:0,points:0}]
    else if(path==='/api/customers/1/history') data={balance:[],cardSales:[],cardUsage:[]}
    else if(path==='/api/customers/1/recharge') {
      attempts++
      if(attempts===1) return route.fulfill({status:409,json:{message:'余额超出范围'}})
      expect(route.request().postDataJSON().amount).toBe(200)
      data={}
    }
    await route.fulfill({json:{code:200,message:'成功',data}})
  })
  await page.goto('/')
  await page.getByRole('button',{name:'会员与次卡'}).click()
  await page.getByLabel('充值金额').fill('100')
  await page.getByRole('button',{name:'确认已收现金并充值'}).click()
  await expect(page.getByRole('alert')).toContainText('余额超出范围')
  await page.getByLabel('充值金额').fill('200')
  await page.getByRole('button',{name:'确认已收现金并充值'}).click()
  await expect(page.getByRole('status')).toContainText('充值已记录')
  expect(attempts).toBe(2)
})

test('an interrupted recharge retains its request id across reload', async ({page}) => {
  let firstId='', balance=0, calls=0
  await page.route('**/api/**',async route=>{
    const path=new URL(route.request().url()).pathname
    let data:unknown=[]
    if(path==='/api/auth/csrf') data={token:'token',header:'X-CSRF-TOKEN'}
    else if(path==='/api/auth/me') data={username:'manager',role:'店长',name:'林晓晴',title:'店长'}
    else if(path==='/api/customers') data=[{id:1,name:'林知夏',phone:'13800138001',level_name:'金卡',balance,points:0}]
    else if(path==='/api/customers/1/history') data={balance:[],cardSales:[],cardUsage:[]}
    else if(path==='/api/customers/1/recharge') {
      const form=route.request().postDataJSON();calls++
      if(calls===1) {firstId=form.requestId;balance+=form.amount;return route.abort('failed')}
      expect(form.requestId).toBe(firstId);expect(form.amount).toBe(100);data={request_id:firstId}
    }
    await route.fulfill({json:{code:200,message:'成功',data}})
  })
  await page.goto('/')
  await page.getByRole('button',{name:'会员与次卡'}).click()
  await page.getByLabel('充值金额').fill('100')
  await page.getByRole('button',{name:'确认已收现金并充值'}).click()
  await expect(page.getByRole('alert')).toContainText('无法连接')
  await page.reload()
  await page.getByRole('button',{name:'会员与次卡'}).click()
  await expect(page.getByLabel('充值金额')).toHaveValue('100')
  await page.getByRole('button',{name:'确认已收现金并充值'}).click()
  await expect(page.getByRole('status')).toContainText('充值已记录')
  expect(balance).toBe(100)
  expect(calls).toBe(2)
})
