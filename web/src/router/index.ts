import { createRouter, createWebHistory } from 'vue-router'
import { useAuth } from '@/composables/useAuth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
    },
    {
      path: '/auth/complete',
      name: 'auth-complete',
      component: () => import('@/views/CallbackView.vue'),
    },
    {
      path: '/console',
      component: () => import('@/layouts/ConsoleLayout.vue'),
      children: [
        {
          path: '',
          name: 'workspaces',
          component: () => import('@/views/console/WorkspaceListView.vue'),
        },
        {
          path: ':wsSlug',
          name: 'workspace',
          component: () => import('@/views/console/WorkspaceDashboardView.vue'),
        },
        {
          path: ':wsSlug/settings',
          name: 'workspace-settings',
          component: () => import('@/views/console/PlaceholderView.vue'),
        },
        {
          path: ':wsSlug/:projSlug',
          name: 'project',
          component: () => import('@/views/console/ProjectView.vue'),
        },
        {
          path: ':wsSlug/:projSlug/:envSlug',
          name: 'environment',
          component: () => import('@/views/console/EnvironmentView.vue'),
        },
        {
          path: ':wsSlug/:projSlug/:envSlug/create',
          name: 'create-resource',
          component: () => import('@/views/console/PlaceholderView.vue'),
        },
        {
          path: ':wsSlug/:projSlug/:envSlug/:resSlug',
          name: 'resource',
          component: () => import('@/views/console/PlaceholderView.vue'),
        },
        {
          path: 'admin',
          name: 'admin',
          component: () => import('@/views/console/PlaceholderView.vue'),
        },
      ],
    },
  ],
})

router.beforeEach((to) => {
  const publicRoutes = ['login', 'auth-complete']
  if (!publicRoutes.includes(to.name as string)) {
    const { checkAuth } = useAuth()
    if (!checkAuth()) {
      return { name: 'login' }
    }
  }
})

export default router
