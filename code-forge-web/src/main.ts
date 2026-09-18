import { createApp } from "vue";
import { createPinia } from "pinia";
import App from "./App.vue";
import '@/style.css'
import router from "./router";
import '@/plugins/axios'
import '@/access'

const app = createApp(App);
const pinia = createPinia();

app.use(router).use(pinia).mount("#app");
