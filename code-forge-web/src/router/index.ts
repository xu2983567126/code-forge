import { createRouter, createWebHistory } from 'vue-router'
import { routes } from './routes';

// 对应你选的 history mode (createWebHistory)
const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
});

export default router;