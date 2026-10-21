import { createRouter, createWebHistory } from 'vue-router'
import CommitList from '../views/CommitList.vue'
import CommitDetail from '../views/CommitDetail.vue'
import IssueList from '../views/IssueList.vue'

const router = createRouter({
    history: createWebHistory(),
    routes: [
        {
            path: '/',
            redirect:'/commits'
        },
        {
            path: '/commits',
            component: CommitList,
            meta:{title:'提交列表'}
        },
        {
            path: '/commits/:id',
            component: CommitDetail,
            meta:{title:'提交详情'}
        },
        {
            path: '/issues',
            component: IssueList,
            meta:{title:'问题看板'}
        }
    ]
})
export default router
