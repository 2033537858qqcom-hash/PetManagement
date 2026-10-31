import { expect, test } from '@playwright/test'

test('staff records cash payment and product return', async ({page}) => {
  const order={id:9,customer_id:1,product_id:1,product_name:'燕麦低敏香波',quantity:1,unit_price:68,amount:68,status:'SUBMITTED',created_at:'2026-10-29 12:00'}
  await page.route('**/api/**',async route=>{
    const path=new URL(route.request().url()).pathname
    let data:unknown=[]
    if(path==='/api/auth/csrf') data={token:'token',header:'X-CSRF-TOKEN'}
    else if(path==='/api/auth/me') data={username:'manager',role:'店长',name:'林晓晴',title:'店长'}
    else if(path==='/api/customers') data=[{id:1,name:'林知夏',phone:'13800138001',level_name:'金卡',balance:680,points:0}]
    else if(path==='/api/orders/9/settle') {order.status='PAID';data=order}
    else if(path==='/api/orders/9/refund') {expect(route.request().postDataJSON().reason).toBe('退货');order.status='REFUNDED';data=order}
    else if(path==='/api/orders') data=[order]
    await route.fulfill({json:{code:200,message:'成功',data}})
  })
  await page.goto('/')
  await page.getByRole('button',{name:'商品订单'}).click()
  await page.getByRole('button',{name:'确认已收现金'}).click()
  await expect(page.getByText('已收款')).toBeVisible()
  await page.getByLabel('商品退款原因').fill('退货')
  await page.getByRole('button',{name:'确认已退款并退货'}).click()
  await expect(page.getByText('已退款')).toBeVisible()
})
