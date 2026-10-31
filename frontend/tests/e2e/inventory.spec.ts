import { expect, test } from '@playwright/test'

test('manager records stock receipt and configures service consumption', async ({ page }) => {
  const product = { id:1, name:'燕麦低敏香波', stock:2300, threshold:800, unit:'ml', price:68, sale_units:500 }
  let flows: { id:number; delta:number; action:string; reason:string; stock_after:number }[] = []
  let rules: { product_id:number; quantity:number; name:string; unit:string }[] = []
  await page.route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname, method = route.request().method()
    let data: unknown = []
    if (path === '/api/auth/csrf') data = { token:'token',header:'X-CSRF-TOKEN' }
    else if (path === '/api/auth/me') data = { username:'manager',role:'店长',name:'林晓晴',title:'店长' }
    else if (path === '/api/inventory/1/adjust') { const form=route.request().postDataJSON(); expect(form).toMatchObject({kind:'IN',quantity:20,reason:'补货'}); product.stock+=20; flows=[{id:1,delta:20,action:'IN',reason:'补货',stock_after:product.stock}]; data=product }
    else if (path === '/api/inventory/1/flows') data=flows
    else if (path === '/api/inventory') data=[product]
    else if (path === '/api/services/1/consumption' && method === 'PUT') { const form=route.request().postDataJSON(); expect(form.items).toEqual([{productId:1,quantity:100}]); rules=[{product_id:1,quantity:100,name:product.name,unit:product.unit}]; data=rules }
    else if (path === '/api/services/1/consumption') data=rules
    else if (path === '/api/services') data=[{id:1,name:'基础洗护',category:'基础洗护',price:98,duration:60,species:'ALL',enabled:true}]
    await route.fulfill({json:{code:200,message:'成功',data}})
  })
  await page.goto('/')
  await page.getByRole('button',{name:'库存与耗材'}).click()
  await page.getByLabel('库存数量').fill('20')
  await page.getByLabel('调整原因').fill('补货')
  await page.getByRole('button',{name:'确认调整库存'}).click()
  await expect(page.getByRole('status')).toContainText('库存已更新')
  await expect(page.getByText('补货',{exact:true})).toBeVisible()
  await page.getByRole('button',{name:'添加耗材规则'}).click()
  await page.getByLabel('每次用量').fill('100')
  await page.getByRole('button',{name:'保存耗材规则'}).click()
  await expect(page.getByRole('status')).toContainText('耗材规则已保存')
})
