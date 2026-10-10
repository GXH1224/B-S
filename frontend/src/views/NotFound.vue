<template>
  <div class="notfound-page">
    <div class="ees-card notfound-card">
      <div class="code">404</div>
      <h1 class="title">页面不存在或已被移除</h1>
      <p class="ees-hint desc">
        您访问的地址可能已失效、已被归档，或当前账号没有访问该页面的权限。<br />
        请检查地址是否正确，或返回综合驾驶舱继续操作。
      </p>

      <div class="actions">
        <el-button type="primary" @click="goDashboard">
          <el-icon><Odometer /></el-icon>
          返回驾驶舱
        </el-button>
        <el-button plain @click="goBack">
          <el-icon><Back /></el-icon>
          返回上一页
        </el-button>
      </div>

      <div class="ees-hint foot">效能评估系统 · 内网离线部署</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()

function goDashboard(): void {
  router.push('/dashboard')
}

function goBack(): void {
  router.back()
}
</script>

<style scoped>
.notfound-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px 20px;
  background: var(--ees-workbench-bg);
}

.notfound-card {
  width: 100%;
  max-width: 520px;
  margin-bottom: 0;
  padding: 38px 32px 26px;
  text-align: center;
}

.code {
  font-size: 84px;
  font-weight: 700;
  line-height: 1;
  letter-spacing: 4px;
  background: linear-gradient(135deg, #1f6feb, #4f92f7);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  font-variant-numeric: tabular-nums;
}

.title {
  margin: 14px 0 10px;
  font-size: 18px;
  font-weight: 600;
  color: var(--ees-text);
}

.desc {
  margin: 0 0 22px;
}

.actions {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  flex-wrap: wrap;
}

.foot {
  margin-top: 24px;
  padding-top: 14px;
  border-top: 1px solid var(--ees-border);
}
</style>
