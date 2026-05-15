<script setup lang="ts">
import { ref, onUnmounted } from 'vue'

const props = withDefaults(
  defineProps<{
    label?: string
    confirmLabel?: string
    btnClass?: string
    timeout?: number
    /**
     * Async handler invoked on confirm. When provided, the button awaits
     * the returned Promise; if it rejects (or the sync handler throws), the
     * confirming state is restored so the user can try again. Use this
     * instead of `@confirm` when the action can fail and the parent can't
     * easily reset the button itself (e.g. two ConfirmButtons sharing one
     * handler in the parent — only one would otherwise get reset).
     */
    handler?: () => void | Promise<void>
  }>(),
  {
    label: 'Delete',
    confirmLabel: 'Confirm Delete',
    btnClass: 'btn-danger',
    timeout: 5000,
  },
)

const emit = defineEmits<{
  confirm: []
}>()

const confirming = ref(false)
const pending = ref(false)
let resetTimer: ReturnType<typeof setTimeout> | null = null

function clearTimer() {
  if (resetTimer) {
    clearTimeout(resetTimer)
    resetTimer = null
  }
}

function startConfirm(timeout: number) {
  confirming.value = true
  clearTimer()
  resetTimer = setTimeout(() => {
    confirming.value = false
  }, timeout)
}

function cancel() {
  clearTimer()
  confirming.value = false
}

async function handleConfirm() {
  // Race-guard: a slow handler must not let the user click "Confirm" twice.
  // We keep `confirming` true (so the second click hits *this* function, not
  // the initial `Delete` button) and gate the body on `pending`. The 5s
  // auto-cancel timer is stopped for the same reason — it must not collapse
  // the UI back to "Delete" while the handler is in flight.
  if (pending.value) return
  clearTimer()
  if (props.handler) {
    pending.value = true
    try {
      await props.handler()
      // Success: collapse back to the un-armed "Delete" state.
      confirming.value = false
    } catch {
      // Failure: re-arm the 5s timer so the parent's error UI is visible
      // alongside an actionable retry button. The parent surfaces the
      // failure reason separately.
      startConfirm(props.timeout)
    } finally {
      pending.value = false
    }
    return
  }
  // Sync-emit path: no handler to await, so the caller is responsible for
  // any subsequent state. Collapse immediately.
  confirming.value = false
  emit('confirm')
}

onUnmounted(clearTimer)

defineExpose({ reset: cancel })
</script>

<template>
  <button v-if="!confirming" :class="btnClass" :disabled="pending" @click="startConfirm(timeout)">
    {{ label }}
  </button>
  <span v-else class="confirm-group">
    <button :class="btnClass" :disabled="pending" @click="handleConfirm">
      {{ pending ? 'Working…' : confirmLabel }}
    </button>
    <button class="btn-secondary btn-sm" :disabled="pending" @click="cancel">Cancel</button>
  </span>
</template>

<style scoped>
.confirm-group {
  display: inline-flex;
  gap: 4px;
  align-items: center;
}

.btn-sm {
  padding: 4px 10px;
  font-size: 12px;
}
</style>
