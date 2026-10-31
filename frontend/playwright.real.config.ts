import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: './tests/real-e2e',
  timeout: 90_000,
  use: { baseURL: 'http://127.0.0.1:5194', channel: 'chrome', viewport: { width:1440, height:900 } },
  webServer: [
    { command: 'mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8181', cwd:'../backend', url:'http://127.0.0.1:8181/api/auth/csrf', env: { PET_GROOMING_ALLOWED_ORIGINS:'http://127.0.0.1:5194', PET_MEDIA_DIRECTORY:'./target/browser-photos' }, reuseExistingServer:false, timeout:120_000 },
    { command: 'npm run dev -- --port 5194 --strictPort', url:'http://127.0.0.1:5194', env: { VITE_API_PROXY_TARGET:'http://127.0.0.1:8181' }, reuseExistingServer:false, timeout:60_000 },
  ],
})
