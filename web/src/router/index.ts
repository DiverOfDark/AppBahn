import { createRouter, createWebHistory } from 'vue-router'
import { useAuth } from '@/composables/useAuth'
import { usePageTitle } from '@/composables/usePageTitle'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { title: 'Login' },
    },
    {
      path: '/auth/complete',
      name: 'auth-complete',
      component: () => import('@/views/CallbackView.vue'),
      meta: { title: 'Authenticating' },
    },
    {
      path: '/console',
      component: () => import('@/layouts/ConsoleLayout.vue'),
      children: [
        {
          path: '',
          name: 'workspaces',
          component: () => import('@/views/console/WorkspaceListView.vue'),
          meta: { title: 'Workspaces' },
        },
        {
          path: 'admin',
          name: 'admin',
          component: () => import('@/views/console/PlaceholderView.vue'),
          meta: { title: 'Admin' },
        },
        {
          path: ':wsSlug',
          name: 'workspace',
          component: () => import('@/views/console/WorkspaceDashboardView.vue'),
        },
        {
          path: ':wsSlug/settings',
          name: 'workspace-settings',
          component: () => import('@/views/console/WorkspaceSettingsView.vue'),
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
          component: () => import('@/views/console/CreateResourceView.vue'),
          meta: { title: 'Create Resource' },
        },
        {
          path: ':wsSlug/:projSlug/:envSlug/:resSlug',
          name: 'resource',
          component: () => import('@/views/console/ResourceDetailView.vue'),
        },
      ],
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: () => import('@/views/NotFoundView.vue'),
      meta: { title: 'Not Found' },
    },
  ],
})

router.afterEach((to) => {
  const { setPageTitle } = usePageTitle()
  const title = to.meta.title as string | undefined
  if (title) {
    setPageTitle(title)
  }
})

router.beforeEach((to) => {
  const publicRoutes = ['login', 'auth-complete', 'not-found']
  if (!publicRoutes.includes(to.name as string)) {
    const { checkAuth } = useAuth()
    if (!checkAuth()) {
      return { name: 'login' }
    }
  }
})

export default router
