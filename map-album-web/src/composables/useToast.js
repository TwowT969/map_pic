import { reactive } from 'vue'

const state = reactive({
  show: false,
  message: '',
  type: 'info' // 'info' | 'success' | 'error'
})

let _timer = null

export function useToast() {
  function showToast(message, type = 'info') {
    clearTimeout(_timer)
    state.show = true
    state.message = message
    state.type = type
    _timer = setTimeout(() => { state.show = false }, 2500)
  }

  return { toastState: state, showToast }
}
