<template>
  <div>
    <div class="page-head">
      <div>
        <h2>提交记录</h2>
        <p>点击一行查看该次提交的文件、问题和 Diff</p>
      </div>
      <el-button @click="load">刷新</el-button>
    </div>

    <div class="panel">
      <el-table
        :data="rows"
        v-loading="loading"
        class="clickable"
        :header-cell-style="{ fontWeight: 600 }"
        @row-click="goDetail"
      >
        <el-table-column prop="id" label="编号" width="72" />
        <el-table-column label="提交" min-width="150">
          <template #default="{ row }">
            <span class="hash">{{ shortHash(row.commitHash) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="author" label="作者" width="120" />
        <el-table-column prop="message" label="说明" min-width="180" show-overflow-tooltip />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" effect="light" round>
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="fileCount" label="文件" width="80" align="center" />
        <el-table-column label="问题" width="80" align="center">
          <template #default="{ row }">
            <span :class="{ danger: row.issueCount > 0 }">{{ row.issueCount }}</span>
          </template>
        </el-table-column>
        <el-table-column label="时间" min-width="170">
          <template #default="{ row }">
            {{ formatTime(row.createdAt) }}
          </template>
        </el-table-column>
        <template #empty>暂无审查记录，向测试仓库提交一次后再刷新</template>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { fetchCommits } from '../api/review'
import { formatTime, shortHash, statusLabel, statusType } from '../utils/format'

const router = useRouter()
const rows = ref([])
const loading = ref(false)

function goDetail(row) {
  router.push(`/commits/${row.id}`)
}

async function load() {
  loading.value = true
  try {
    rows.value = await fetchCommits()
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.clickable { cursor: pointer; }
.danger { color: #c45656; font-weight: 600; }
</style>
