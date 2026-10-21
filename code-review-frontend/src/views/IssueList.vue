<template>
  <div>
    <div class="page-head">
      <div>
        <h2>问题看板</h2>
        <p>跨提交查看审查意见，可按严重级别筛选</p>
      </div>
      <el-radio-group v-model="level" @change="load">
        <el-radio-button value="">全部</el-radio-button>
        <el-radio-button value="ERROR">错误</el-radio-button>
        <el-radio-button value="WARN">警告</el-radio-button>
        <el-radio-button value="INFO">提示</el-radio-button>
      </el-radio-group>
    </div>

    <div class="panel">
      <el-table :data="rows" v-loading="loading" :header-cell-style="{ fontWeight: 600 }">
        <el-table-column label="提交" width="140">
          <template #default="{ row }">
            <el-link type="primary" @click="router.push(`/commits/${row.commitId}`)">
              <span class="hash">{{ shortHash(row.commitHash) }}</span>
            </el-link>
          </template>
        </el-table-column>
        <el-table-column prop="fileName" label="文件" min-width="220" show-overflow-tooltip />
        <el-table-column prop="lineNo" label="行" width="70" />
        <el-table-column label="级别" width="90">
          <template #default="{ row }">
            <el-tag :type="levelType(row.level)" size="small">{{ row.level }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="来源" width="90">
          <template #default="{ row }">
            {{ sourceLabel(row.source) }}
          </template>
        </el-table-column>
        <el-table-column prop="suggestion" label="建议" min-width="260" show-overflow-tooltip />
        <template #empty>没有符合条件的问题</template>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { fetchIssues } from '../api/review'
import { levelType, shortHash, sourceLabel } from '../utils/format'

const router = useRouter()
const level = ref('')
const rows = ref([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    rows.value = await fetchIssues(level.value)
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>
