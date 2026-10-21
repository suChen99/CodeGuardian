/** 把后端时间收成 2026-09-11 15:32:00，字符串和 Jackson 数组都能吃 */
export function formatTime(value) {
  if (!value) return '-'
  if (Array.isArray(value)) {
    const [y, m, d, h = 0, min = 0, s = 0] = value
    const pad = (n) => String(n).padStart(2, '0')
    return `${y}-${pad(m)}-${pad(d)} ${pad(h)}:${pad(min)}:${pad(s)}`
  }
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return String(value)
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

export function shortHash(hash) {
  return hash ? hash.slice(0, 10) : '-'
}

export function statusType(status) {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'PENDING') return 'warning'
  return 'info'
}

export function statusLabel(status) {
  if (status === 'SUCCESS') return '审查完成'
  if (status === 'FAILED') return '审查失败'
  if (status === 'PENDING') return '审查中'
  return status || '-'
}

export function sourceLabel(source) {
  if (source === 'RULE') return '规则'
  if (source === 'LLM') return '模型'
  return source || '-'
}

export function levelType(level) {
  if (level === 'ERROR') return 'danger'
  if (level === 'WARN') return 'warning'
  return 'info'
}
