import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './Root.vue'
import './style.css'
createApp(App).use(createPinia()).mount('#app')
