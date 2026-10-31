import { expect, test } from '@playwright/test'

test('manager can add customers, employees, resources and stock items', async ({ page }) => {
  const customers:unknown[] = [], employees:unknown[] = [], products:unknown[] = [], resources:unknown[] = []
  await page.route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname, method=route.request().method()
    let data:unknown=[]
    if(path==='/api/auth/csrf') data={token:'token',header:'X-CSRF-TOKEN'}
    else if(path==='/api/auth/me') data={username:'manager',role:'店长',name:'林晓晴',title:'店长'}
    else if(path==='/api/customers' && method==='POST') { const item={id:1,...route.request().postDataJSON(),level_name:'普通',balance:0,points:0}; customers.push(item); data=item }
    else if(path==='/api/customers') data=customers
    else if(path==='/api/employees' && method==='POST') { const body=route.request().postDataJSON(); const item={id:1,...body,sizes:body.sizes.join(',')}; employees.push(item); data=item }
    else if(path==='/api/employees') data=employees
    else if(path==='/api/resources' && method==='POST') { const item={id:1,...route.request().postDataJSON()}; resources.push(item); data=item }
    else if(path==='/api/resources') data=resources
    else if(path==='/api/inventory' && method==='POST') { const item={id:1,...route.request().postDataJSON(),stock:0,price:0,sale_units:1}; products.push(item); data=item }
    else if(path==='/api/inventory') data=products
    await route.fulfill({json:{code:200,message:'成功',data}})
  })
  await page.goto('/')
  await page.getByRole('button',{name:'会员与次卡'}).click()
  await page.getByRole('button',{name:'新增宠主'}).click()
  await page.getByLabel('宠主姓名').fill('新宠主')
  await page.getByLabel('联系电话').fill('13900139000')
  await page.getByRole('button',{name:'保存宠主'}).click()
  await expect(page.getByRole('status')).toContainText('宠主已建立')
  await page.getByRole('button',{name:'员工团队'}).click()
  await page.getByRole('button',{name:'新增洗护师'}).click()
  await page.getByLabel('员工姓名').fill('小周')
  await page.getByLabel('员工职位').fill('护理师')
  await page.getByLabel('护理技能').fill('基础洗护')
  await page.getByRole('button',{name:'保存洗护师'}).click()
  await expect(page.getByRole('status')).toContainText('洗护师已建立')
  await page.getByRole('button',{name:'预约与排班'}).click()
  await page.getByRole('button',{name:'新增工位'}).click()
  await page.getByLabel('工位名称').fill('一号工位')
  await page.getByRole('button',{name:'保存工位'}).click()
  await expect(page.getByRole('status')).toContainText('工位已建立')
  await page.getByRole('button',{name:'库存与耗材'}).click()
  await page.getByRole('button',{name:'新增耗材'}).click()
  await page.getByLabel('耗材名称').fill('低敏香波')
  await page.getByLabel('库存单位',{exact:true}).fill('ml')
  await page.getByLabel('预警阈值').fill('100')
  await page.getByRole('button',{name:'保存耗材',exact:true}).click()
  await expect(page.getByRole('status')).toContainText('耗材已建立')
})
