<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { ArrowRight, Eye, EyeOff, PawPrint } from 'lucide-vue-next'
import { demoAccounts, normalizeUsername, validUsername } from '../lib/workspace'
import type { RegistrationInput } from '../lib/workspace'
import { photoUrl } from '../lib/photos'

const props = defineProps<{ error: string; busy: boolean }>()
const emit = defineEmits<{ login: [username: string, password: string]; register: [form: RegistrationInput]; modeChange: [] }>()
const registering = ref(false)
const showPassword = ref(false)
const username = ref('')
const password = ref('')
const name = ref(''), phone = ref(''), confirmation = ref(''), localError = ref('')
const errorMessage = computed(() => localError.value || props.error)
const errorElement = ref<HTMLElement>(), heading = ref<HTMLElement>()
const chosen = computed(() => demoAccounts.find(account => account.username === username.value.trim()))

function pick(account: typeof demoAccounts[number]) {
  if (props.busy) return
  username.value = account.username
}

function switchMode() {
  if (props.busy) return
  registering.value = !registering.value
  if (registering.value && chosen.value) username.value = ''
  password.value = ''; confirmation.value = ''; localError.value = ''
  emit('modeChange')
  nextTick(() => heading.value?.focus())
}
function invalid(message: string) {
  localError.value = message
  nextTick(() => errorElement.value?.focus())
}
function submit() {
  if (props.busy) return
  localError.value = ''
  if (!registering.value) { emit('login', normalizeUsername(username.value), password.value); return }
  const form = { username: normalizeUsername(username.value), password: password.value, name: name.value.trim(), phone: phone.value.trim() }
  if (!validUsername(form.username)) return invalid('账号需为 3 至 32 位字母、数字或下划线，或有效的邮箱地址。')
  if (!form.name || form.name.length > 60) return invalid('请填写 1 至 60 个字符的姓名。')
  if (!/^\+?[0-9]{6,20}$/.test(form.phone)) return invalid('请输入有效的联系电话（6 至 20 位数字，可带国际区号）。')
  if (!form.password.trim() || Array.from(form.password).length < 12) return invalid('密码至少需要 12 个字符。')
  if (new TextEncoder().encode(form.password).length > 72) return invalid('密码过长，请减少字符数量。')
  if (form.password !== confirmation.value) return invalid('两次输入的密码不一致，请重新确认。')
  emit('register', form)
}
watch(() => props.error, message => { if (message) nextTick(() => errorElement.value?.focus()) })
</script>

<template>
  <main class="entry-shell">
    <section class="entry-cover" aria-label="品牌封面">
      <img :src="photoUrl('/images/entry-cover.jpg')" alt="PAW & PURE 宠物护理封面" />
      <div class="entry-cover-shade"></div>
      <div class="entry-cover-brand"><span class="entry-brand-mark"><PawPrint :size="22"/></span><span>paw & pure</span></div>
      <div class="entry-cover-copy"><span>用心照顾每一个小家人</span><h1>从预约到接回家，<br/>每一步都安心。</h1><p>让每一次洗护，都有迹可循。</p></div>
      <div class="entry-cover-foot"><span>PAW & PURE · 宠物洗护</span><span>{{photoUrl('/images/entry-cover.jpg') === '/images/entry-cover.jpg' ? '演示图片' : '门店图片'}}</span></div>
    </section>
    <section class="entry-login" :class="{'is-register': registering}" aria-labelledby="entry-title">
      <div class="entry-login-inner">
        <div class="entry-heading">
          <span class="entry-overline">{{ registering ? 'Join Paw & Pure' : 'Your pets, cared for' }}</span>
          <div class="entry-title-row">
            <h2 id="entry-title" ref="heading" tabindex="-1">{{ registering ? '创建宠主账号' : '欢迎回来' }}</h2>
            <span class="entry-title-icon" aria-hidden="true"><PawPrint :size="26"/></span>
          </div>
          <p>{{ registering ? '从这里开始，照顾好你的每一位小家人。' : '使用你的账号和密码进入工作台。' }}</p>
        </div>
        <p v-if="errorMessage" ref="errorElement" class="entry-banner" role="alert" tabindex="-1">{{ errorMessage }}</p>
        <form :novalidate="registering" :aria-busy="busy" @submit.prevent="submit">
         <template v-if="!registering">
          <div class="entry-auth-note">
            <div class="entry-auth-fields">
              <label>账号<input v-model="username" type="text" placeholder="请输入账号" autocomplete="username" required :disabled="busy" /></label>
              <label>密码
                <span class="entry-password-wrap">
                  <input v-model="password" :type="showPassword ? 'text' : 'password'" placeholder="请输入密码" autocomplete="current-password" required :disabled="busy" />
                  <button class="entry-password-toggle" type="button" :aria-label="showPassword ? '隐藏密码' : '显示密码'" :disabled="busy" @click="showPassword = !showPassword">
                    <EyeOff v-if="showPassword" :size="18"/><Eye v-else :size="18"/>
                  </button>
                </span>
              </label>
            </div>
          </div>
         </template>
         <div v-else class="entry-registration">
          <div class="entry-auth-fields">
           <label>账号<input v-model="username" type="text" placeholder="字母、数字、下划线，或邮箱" autocomplete="username" maxlength="64" required :disabled="busy" autocapitalize="none" :spellcheck="false" /></label>
           <div class="entry-contact-fields">
            <label>姓名<input v-model="name" type="text" placeholder="怎么称呼你" autocomplete="name" maxlength="60" required :disabled="busy" /></label>
            <label>联系电话<input v-model="phone" type="tel" placeholder="便于门店联系你" autocomplete="tel" maxlength="21" required :disabled="busy" /></label>
           </div>
           <div><label for="register-password">设置密码<input id="register-password" v-model="password" type="password" placeholder="请输入至少 12 个字符" autocomplete="new-password" maxlength="72" required :disabled="busy" aria-describedby="register-password-hint" /></label><p id="register-password-hint" class="entry-field-hint">建议组合使用字母、数字和符号。</p></div>
           <label for="register-confirm">确认密码<input id="register-confirm" v-model="confirmation" type="password" placeholder="请再次输入密码" autocomplete="new-password" maxlength="72" required :disabled="busy" /></label>
          </div>
         </div>
          <button class="entry-submit" type="submit" :disabled="busy">{{ registering ? (busy ? '正在创建账号…' : '注册并进入') : (busy ? '登录中…' : '登录') }} <ArrowRight :size="18"/></button>
        </form>
        <p class="entry-disclaimer">{{registering ? '自助注册将创建宠主账号，可管理爱宠、预约和护理记录。' : '门店员工请使用管理员创建的账号登录。'}}</p>
        <p class="entry-switch">{{registering ? '已有账号？' : '还没有账号？'}}<button type="button" class="entry-switch-action" :disabled="busy" @click="switchMode">{{registering ? '返回登录' : '立即注册'}}</button></p>
        <details v-if="!registering" class="entry-demo" open>
          <summary>本地演示账号</summary>
          <div class="entry-demo-list">
            <button v-for="account in demoAccounts" :key="account.username" class="entry-demo-account" type="button" :disabled="busy" @click="pick(account)">{{ account.role }}: {{ account.username }}</button>
          </div>
          <p>演示密码: petcare123</p>
          <p>账号与记录均为演示数据。</p>
        </details>
      </div>
      <div class="entry-footer"><span>PAW & PURE</span><span>智慧宠物洗护预约与门店管理</span></div>
    </section>
  </main>
</template>
