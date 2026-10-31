import { expect, test } from '@playwright/test'

test('manager records a day off in the selected schedule',async({page})=>{
  let state='UNSET'
  await page.route('**/api/**',async route=>{
    const url=new URL(route.request().url()), path=url.pathname
    let data:unknown=[]
    if(path==='/api/auth/csrf') data={token:'token',header:'X-CSRF-TOKEN'}
    else if(path==='/api/auth/me') data={username:'manager',role:'店长',name:'林晓晴',title:'店长'}
    else if(path==='/api/employees') data=[{id:1,name:'周凯',title:'洗护师',skills:'基础',species:'DOG',sizes:'SMALL'}]
    else if(path==='/api/schedules' && state==='OFF') data=[{employee_id:1,schedule_date:url.searchParams.get('date'),status:'OFF',start_time:null,end_time:null}]
    else if(path.startsWith('/api/schedules/1/')) {expect(route.request().postDataJSON().status).toBe('OFF');state='OFF';data={status:'OFF'}}
    await route.fulfill({json:{code:200,message:'成功',data}})
  })
  await page.goto('/')
  await page.getByRole('button',{name:'预约与排班'}).click()
  await page.getByRole('combobox',{name:'排班状态'}).click()
  await page.getByRole('option',{name:'休假'}).click()
  await page.getByRole('button',{name:'保存员工排班'}).click()
  await expect(page.getByRole('status')).toContainText('排班已保存')
  await expect(page.getByText('当日休假',{exact:true})).toBeVisible()
})
