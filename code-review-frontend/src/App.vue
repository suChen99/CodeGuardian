<template>
  <el-container class="shell">
    <el-aside class="sidebar" width="232px">
      <div class="brand">
        <span class="mark">CG</span>
        <div>
          <strong>CodeGuardian</strong>
          <small>智能代码审查</small>
        </div>
      </div>
      <el-menu
        class="nav"
        :router="true"
        :default-active="activeMenu"
        background-color="#1b2330"
        text-color="#c5cdd8"
        active-text-color="#ffffff"
      >
        <el-menu-item index="/commits">提交记录</el-menu-item>
        <el-menu-item index="/issues">问题看板</el-menu-item>
      </el-menu>
      <div class="sidebar-foot">本地仓库增量初审</div>
    </el-aside>

    <el-container>
      <el-header class="topbar" height="64px">
        <span class="crumb">{{ pageTitle }}</span>
        <span class="hint">规则引擎 + 大模型 · 仅审查本次 Diff</span>
      </el-header>
      <el-main class="content">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const activeMenu = computed(() =>
  route.path.startsWith('/issues') ? '/issues' : '/commits'
)
const pageTitle = computed(() => route.meta.title || 'CodeGuardian')
</script>

<style scoped>
.shell { min-height: 100vh; }

.sidebar {
  background: var(--sidebar);
  color: #fff;
  display: flex;
  flex-direction: column;
  padding: 20px 0 16px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 20px 24px;
}

.mark {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: linear-gradient(145deg, #4c8dff, #2b6cb0);
  display: grid;
  place-items: center;
  font-size: 13px;
  font-weight: 700;
}

.brand strong {
  display: block;
  font-size: 15px;
}

.brand small {
  color: #8b95a5;
  font-size: 12px;
}

.nav {
  border-right: none;
  flex: 1;
}

.nav :deep(.el-menu-item) {
  margin: 4px 12px;
  border-radius: 8px;
}

.nav :deep(.el-menu-item.is-active) {
  background: var(--sidebar-hover) !important;
}

.sidebar-foot {
  padding: 12px 20px 0;
  color: #7d8796;
  font-size: 12px;
}

.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid var(--border);
}

.crumb {
  font-weight: 600;
  font-size: 16px;
}

.hint {
  color: var(--text-secondary);
  font-size: 13px;
}

.content {
  padding: 24px 28px 32px;
}
</style>
