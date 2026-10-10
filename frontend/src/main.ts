import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import 'element-plus/dist/index.css'

import App from './App.vue'
import router from './router'
import { dropLegacyPersistentToken } from './api/request'
import './styles/index.scss'

// 登录态已改为会话级（sessionStorage），启动时清掉历史版本遗留在 localStorage 里的令牌
dropLegacyPersistentToken()

const app = createApp(App)

for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn, size: 'default' })

app.config.errorHandler = (err, _instance, info) => {
  // 生产环境不向用户暴露堆栈，仅记录到控制台便于运维排查
  console.error('[EES] 未捕获异常:', err, info)
}

app.mount('#app')
