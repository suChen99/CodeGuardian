<template>
  <div v-loading="loading">
    <div class="page-head">
      <div>
        <el-button text class="back" @click="router.push('/commits')">返回列表</el-button>
        <h2>提交详情</h2>
        <p v-if="detail">{{ detail.message || '本次提交的审查结果' }}</p>
      </div>
      <el-button type="primary" :loading="reanalyzing" @click="onReanalyze">
        再审一次
      </el-button>
    </div>

    <div class="panel meta" v-if="detail">
      <div class="meta-item">
        <span>哈希</span>
        <strong class="hash">
          {{ detail.commitHash }}
          <el-button link type="primary" @click="copyHash">复制</el-button>
        </strong>
      </div>
      <div class="meta-item">
        <span>作者</span>
        <strong>{{ detail.author }}</strong>
      </div>
      <div class="meta-item">
        <span>状态</span>
        <el-tag :type="statusType(detail.status)" effect="light" round>
          {{ statusLabel(detail.status) }}
        </el-tag>
      </div>
      <div class="meta-item">
        <span>时间</span>
        <strong>{{ formatTime(detail.createdAt) }}</strong>
      </div>
      <div class="meta-item wide" v-if="detail.errorMessage">
        <span>失败原因</span>
        <strong class="err">{{ detail.errorMessage }}</strong>
      </div>
    </div>

    <el-empty v-if="detail && !detail.files?.length" description="没有文件（首次提交或尚未审完）" />

    <div class="file-card panel" v-for="file in detail?.files || []" :key="file.id">
      <div class="file-head">
        <strong>{{ file.fileName }}</strong>
        <el-tag size="small" type="info" effect="plain">{{ file.issues?.length || 0 }} 个问题</el-tag>
      </div>
      <el-table :data="file.issues" size="small">
        <el-table-column prop="lineNo" label="行" width="70" />
        <el-table-column label="级别" width="100">
          <template #default="{ row }">
            <el-tag :type="levelType(row.level)" size="small">{{ row.level }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="来源" width="90">
          <template #default="{ row }">
            {{ sourceLabel(row.source) }}
          </template>
        </el-table-column>
        <el-table-column prop="suggestion" label="建议" />
      </el-table>
      <pre class="diff" v-if="file.diffText">{{ file.diffText }}</pre>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { fetchCommit, reanalyzeCommits } from '../api/review'
import { formatTime, levelType, sourceLabel, statusLabel, statusType } from '../utils/format'
import { ElMessage, ElMessageBox } from 'element-plus'

const route = useRoute()
const router = useRouter()
const detail = ref(null)
const loading = ref(false)
const reanalyzing = ref(false)

async function load() {
  loading.value = true
  try {
    detail.value = await fetchCommit(route.params.id)
  } finally {
    loading.value = false
  }
}

async function copyHash() {
  await navigator.clipboard.writeText(detail.value.commitHash)
  ElMessage.success('已复制')
}

async function onReanalyze() {
  try {
    await ElMessageBox.confirm('将用已保存的 diff 重新调用 AI，大约需要十几秒。', '再审一次', {
      type: 'warning',
      confirmButtonText: '开始',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  reanalyzing.value = true
  try {
    detail.value = await reanalyzeCommits(route.params.id)
    ElMessage.success('再审完成')
  } finally {
    reanalyzing.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.back { padding-left: 0; margin-bottom: 4px; }
.meta {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px 24px;
  padding: 20px 22px;
  margin-bottom: 16px;
}
.meta-item span {
  display: block;
  color: var(--text-secondary);
  font-size: 12px;
  margin-bottom: 6px;
}
.meta-item.wide { grid-column: 1 / -1; }
.err { color: #c45656; font-weight: 500; }
.file-card { margin-bottom: 16px; }
.file-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 18px;
  border-bottom: 1px solid var(--border);
}
.diff {
  margin: 0;
  padding: 14px 18px;
  background: #0f1724;
  color: #d7e3ff;
  overflow: auto;
  font-size: 12px;
  line-height: 1.55;
}
</style>
