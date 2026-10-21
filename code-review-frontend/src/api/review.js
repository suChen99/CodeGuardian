import http from './http'

// 提交列表
export function fetchCommits(){
    return http.get('/api/commits')
}

// 提交详情
export function fetchCommit(id) {
    return http.get(`/api/commits/${id}`)
}

// 跨提交问题列表
export function fetchIssues(level){
    return http.get('/api/issues',{params:{level:level || undefined}})
}

// 手动再审
export function reanalyzeCommits(id){
    return http.post(`/api/commits/${id}/reanalyze`)
}