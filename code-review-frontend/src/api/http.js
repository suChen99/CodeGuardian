import axios from 'axios'
import { ElMessage } from 'element-plus'

const http = axios.create({
    baseURL: '',
    timeout: 180000
})

http.interceptors.response.use(
    (res)=>{
        const body = res.data
        // 和后端约定好，code===0,才是业务成功
        if (body && body.code === 0){
            return body.data
        }
        ElMessage.error(body?.message || '接口返回异常')
        return Promise.reject(body)
    },
    (err)=>{
        const status = err.response?.status
        const msg =
            err.response?.data?.detail ||
            err.response?.data?.message ||
            err.message
        ElMessage.error(status ? `HTTP ${status}: ${msg}` : msg)
        return Promise.reject(err)

    }
)


export default http