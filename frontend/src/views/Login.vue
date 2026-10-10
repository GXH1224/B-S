<template>
  <div class="login-page">
    <!-- 左侧深色品牌区 -->
    <section class="brand-panel">
      <div class="brand-glow" aria-hidden="true"></div>
      <div class="brand-inner">
        <div class="brand-head">
          <div class="brand-logo">EES</div>
          <div>
            <h1 class="brand-name">效能评估系统</h1>
            <p class="brand-en">Efficiency Evaluation System</p>
          </div>
        </div>

        <p class="brand-slogan">
          面向试验数据的效能评估一体化平台，指标体系、数据接入、评估任务、结果分析与报告导出全流程在线完成。
        </p>
      </div>
    </section>

    <!-- 右侧登录表单区 -->
    <section class="form-panel">
      <div class="form-wrap">
        <div class="login-card">
          <h2 class="card-title">用户登录</h2>
          <p class="card-sub">请使用系统分配的账号登录，登录后仅可查看授权范围内的数据</p>

          <el-form
            ref="formRef"
            :model="form"
            :rules="rules"
            label-position="top"
            @keyup.enter="submit"
          >
            <el-form-item label="用户名" prop="username">
              <el-input
                v-model="form.username"
                size="large"
                clearable
                placeholder="请输入用户名"
                autocomplete="username"
              >
                <template #prefix>
                  <el-icon><User /></el-icon>
                </template>
              </el-input>
            </el-form-item>

            <el-form-item label="密码" prop="password">
              <el-input
                v-model="form.password"
                size="large"
                type="password"
                show-password
                placeholder="请输入密码"
                autocomplete="current-password"
              >
                <template #prefix>
                  <el-icon><Lock /></el-icon>
                </template>
              </el-input>
            </el-form-item>

            <div class="form-row">
              <el-checkbox v-model="form.remember">记住用户名</el-checkbox>
              <span class="ees-hint">忘记密码请联系系统管理员重置</span>
            </div>

            <el-button
              type="primary"
              size="large"
              class="submit-btn"
              :loading="loading"
              @click="submit"
            >
              登 录
            </el-button>
          </el-form>

          <!-- 默认账号：点击账号标题自动填充表单 -->
          <el-collapse v-model="activeAccount" accordion class="account-collapse" @change="onAccountChange">
            <el-collapse-item name="__help" class="account-intro">
              <template #title>
                <span class="acct-title acct-title--hint">
                  <el-icon><InfoFilled /></el-icon>
                  <span>默认账号说明</span>
                </span>
              </template>
              <div class="ees-hint">
                系统预置两类角色的演示账号，展开任一账号即会自动填充登录表单，便于快速切换角色验证权限。
              </div>
            </el-collapse-item>

            <el-collapse-item
              v-for="item in accounts"
              :key="item.username"
              :name="item.username"
            >
              <template #title>
                <span class="acct-title">
                  <span class="ees-mono acct-cred">{{ item.username }} / {{ item.password }}</span>
                  <span class="acct-role">{{ item.role }}</span>
                </span>
              </template>
              <div class="acct-body">
                <div class="ees-hint">{{ item.desc }}</div>
                <el-button size="small" type="primary" plain @click="fillAccount(item)">
                  使用该账号
                </el-button>
              </div>
            </el-collapse-item>
          </el-collapse>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useUserStore } from '@/stores/user'

interface AccountItem {
  username: string
  password: string
  role: string
  desc: string
}

const REMEMBER_KEY = 'ees_remember_user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const accounts: AccountItem[] = [
  {
    username: 'admin',
    password: 'admin123',
    role: '系统管理员',
    desc: '拥有全部菜单与配置权限，可管理用户、角色、审计日志与系统参数'
  },
  {
    username: 'evaluator',
    password: 'eval123',
    role: '评估人员',
    desc: '创建并执行评估任务，查看评估结果、图形分析与导出报告'
  }
]

const loading = ref(false)
const activeAccount = ref<string>('')
const formRef = ref<FormInstance>()

const form = reactive({
  username: '',
  password: '',
  remember: false
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

function fillAccount(item: AccountItem): void {
  form.username = item.username
  form.password = item.password
  formRef.value?.clearValidate()
}

function onAccountChange(name: string | number | (string | number)[]): void {
  const key = Array.isArray(name) ? name[0] : name
  if (!key) return
  const hit = accounts.find((item) => item.username === key)
  if (hit) fillAccount(hit)
}

async function submit(): Promise<void> {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    const username = form.username.trim()
    await userStore.login(username, form.password)
    if (form.remember) {
      localStorage.setItem(REMEMBER_KEY, username)
    } else {
      localStorage.removeItem(REMEMBER_KEY)
    }
    ElMessage.success('登录成功')
    const redirect = route.query.redirect
    await router.push(typeof redirect === 'string' && redirect ? redirect : '/dashboard')
  } catch {
    // 失败提示由请求拦截器统一给出，此处仅结束加载状态
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  const remembered = localStorage.getItem(REMEMBER_KEY)
  if (remembered) {
    form.username = remembered
    form.remember = true
  }
})
</script>

<style scoped>
.login-page {
  display: flex;
  min-height: 100vh;
  background: var(--ees-workbench-bg);
}

/* ---------------- 左侧品牌区 ---------------- */

.brand-panel {
  position: relative;
  flex: 1 1 auto;
  min-width: 0;
  display: flex;
  align-items: center;
  padding: 56px 64px;
  overflow: hidden;
  background: linear-gradient(160deg, #101a2c 0%, #16233a 100%);
}

.brand-glow {
  position: absolute;
  right: -140px;
  top: -120px;
  width: 460px;
  height: 460px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(31, 111, 235, 0.34) 0%, rgba(31, 111, 235, 0) 68%);
  pointer-events: none;
}

.brand-panel::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 1px;
  background: linear-gradient(90deg, rgba(111, 177, 255, 0) 0%, rgba(111, 177, 255, 0.45) 50%, rgba(111, 177, 255, 0) 100%);
}

.brand-inner {
  position: relative;
  z-index: 1;
  max-width: 620px;
}

.brand-head {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 22px;
}

.brand-logo {
  width: 50px;
  height: 50px;
  flex-shrink: 0;
  border-radius: 12px;
  background: linear-gradient(135deg, #1f6feb, #4f92f7);
  color: #fff;
  font-size: 17px;
  font-weight: 700;
  letter-spacing: 1px;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 8px 22px rgba(31, 111, 235, 0.42);
}

.brand-name {
  margin: 0;
  font-size: 26px;
  font-weight: 700;
  letter-spacing: 2px;
  color: #ffffff;
}

.brand-en {
  margin: 3px 0 0;
  font-size: 12px;
  letter-spacing: 0.6px;
  color: #7d8ba3;
}

.brand-slogan {
  margin: 0 0 28px;
  font-size: 14px;
  line-height: 1.9;
  color: #b6c2d6;
}

/* ---------------- 右侧表单区 ---------------- */

.form-panel {
  flex: 0 0 490px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 44px 36px;
  background: #f7faff;
}

.form-wrap {
  width: 100%;
  max-width: 386px;
}

.login-card {
  background: #ffffff;
  border: 1px solid var(--ees-border);
  border-radius: 12px;
  box-shadow: 0 10px 32px rgba(21, 44, 84, 0.1);
  padding: 26px 26px 18px;
}

.card-title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: var(--ees-text);
}

.card-sub {
  margin: 6px 0 20px;
  font-size: 12.5px;
  line-height: 1.7;
  color: var(--ees-text-muted);
}

.form-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.submit-btn {
  width: 100%;
  letter-spacing: 3px;
  font-weight: 600;
}

.account-collapse {
  margin-top: 18px;
  border-top: 1px solid var(--ees-border);
  border-bottom: none;
}

.account-collapse :deep(.el-collapse-item__header) {
  height: 40px;
  line-height: 40px;
  font-size: 12.5px;
  color: var(--ees-text-secondary);
  border-bottom: 1px solid var(--ees-border);
}

.account-collapse :deep(.el-collapse-item__wrap) {
  border-bottom: 1px solid var(--ees-border);
}

.account-collapse :deep(.el-collapse-item__content) {
  padding-bottom: 12px;
  font-size: 12.5px;
}

.acct-title {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.acct-title--hint {
  color: var(--ees-primary);
}

.acct-cred {
  color: var(--ees-text);
  font-weight: 600;
}

.acct-role {
  font-size: 11.5px;
  color: var(--ees-text-muted);
}

.acct-body {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.acct-body .ees-hint {
  flex: 1 1 200px;
}

/* ---------------- 响应式：900px 以下单列 ---------------- */

@media (max-width: 900px) {
  .login-page {
    flex-direction: column;
  }

  .brand-panel {
    padding: 34px 24px 30px;
  }

  .brand-name {
    font-size: 22px;
  }

  .brand-slogan {
    margin-bottom: 20px;
  }

  .form-panel {
    flex: 1 1 auto;
    padding: 26px 20px 36px;
  }

  .form-wrap {
    max-width: 460px;
  }
}
</style>
