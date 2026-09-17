import { createApp } from 'vue'
import App from './App.vue'
import { installVueErrorHandler } from './utils/applog.js'

const app = createApp(App)
installVueErrorHandler(app)
app.mount('#app')
