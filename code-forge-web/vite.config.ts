import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import tailwindcss from '@tailwindcss/vite'
import { resolve } from 'path'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue(), tailwindcss()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
      '@generated': resolve(__dirname, 'generated'),
      '@/lib/utils': resolve(__dirname, 'src/lib/utils.ts')
    },
    // 强制单 vue 副本（装 reka-ui 等传递依赖后易多份 vue → slot/ref 丢 owner context）
    dedupe: ['vue', '@vue/runtime-core']
  },
  server: {
    fs: {
      allow: ['..']
    },
    // 开发期 SDK 的 baseURL 是相对 /api（见 .env.development），请求经此代理转发到网关 8101。
    // 走代理 = 浏览器视角同源 → cookie 自动携带，不受 SameSite / CORS 约束。
    // ⚠️ 别把 VITE_API_BASE_URL 改回绝对地址，那样会绕开代理变成跨源请求。
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8101',
        changeOrigin: true,
        // 网关 CorsConfig 的白名单固定 5173：dev server 换端口（5174…）时浏览器仍会带
        // `Origin: http://localhost:<port>`，经代理转给网关会被挡成 403 —— 且登录接口同样
        // 被挡，cookie 种不上，表现为整站"有骨架没数据"。经代理本就是同源语义，Origin
        // 是浏览器的多余附带，删掉后网关按非 CORS 请求放行，换任何端口都不受白名单约束。
        configure: (proxy) => {
          proxy.on('proxyReq', (proxyReq) => {
            proxyReq.removeHeader('origin')
          })
        }
      }
    }
  },
  optimizeDeps: {
    include: ['monaco-editor']
  },
  build: {
    // 分包：monaco 全量 ≥5MB，单独拆出避免首屏硬拉；其余按厂商归类
    // Vite 8(rolldown)：manualChunks 迁移到 rolldownOptions.output（rollupOptions 已废弃）
    rolldownOptions: {
      output: {
        manualChunks(id: string) {
          if (id.includes('node_modules')) {
            if (id.includes('monaco-editor')) return 'monaco-editor'
            if (id.includes('vue') || id.includes('pinia') || id.includes('vue-router')) return 'vue-vendor'
            if (id.includes('reka-ui') || id.includes('lucide-vue-next')) return 'ui-vendor'
            if (id.includes('markdown-it') || id.includes('highlight.js') || id.includes('katex')) return 'markdown'
          }
        }
      }
    }
  }
})
