<script setup lang="ts">
import { ref, onUnmounted } from 'vue'

withDefaults(
  defineProps<{
    label?: string
    confirmLabel?: string
    btnClass?: string
    timeout?: number
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

function handleConfirm() {
  clearTimer()
  confirming.value = false
  emit('confirm')
}

onUnmounted(clearTimer)

defineExpose({ reset: cancel })
</script>

<template>
  <button v-if="!confirming" :class="btnClass" @click="startConfirm(timeout)">
    {{ label }}
  </button>
  <span v-else class="confirm-group">
    <button :class="btnClass" @click="handleConfirm">
      {{ confirmLabel }}
    </button>
    <button class="btn-secondary btn-sm" @click="cancel">Cancel</button>
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
