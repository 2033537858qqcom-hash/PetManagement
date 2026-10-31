import { defineConfig } from '@playwright/test'
const database = process.env.PET_TEST_MYSQL_DB || ''
if (!/^codex_pet_browser_[a-z0-9]+$/.test(database)) throw new Error('Set PET_TEST_MYSQL_DB to an isolated browser-test database name')
export default defineConfig({
  testDir:'./tests/real-e2e', timeout:90_000,
  use:{baseURL:'http://127.0.0.1:5194',channel:'chrome',viewport:{width:1440,height:900}},
  webServer:[
    {command:'mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8181',cwd:'../backend',url:'http://127.0.0.1:8181/api/auth/csrf',reuseExistingServer:false,timeout:120_000,env:{SPRING_PROFILES_ACTIVE:'mysql,mysql-demo',PET_DB_URL:`jdbc:mysql://127.0.0.1:13318/${database}?serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false`,PET_DB_USER:'root',PET_DB_PASSWORD:'',PET_GROOMING_ALLOWED_ORIGINS:'http://127.0.0.1:5194'}},
    {command:'npm run dev -- --port 5194 --strictPort',url:'http://127.0.0.1:5194',env:{VITE_API_PROXY_TARGET:'http://127.0.0.1:8181'},reuseExistingServer:false,timeout:60_000},
  ],
})
