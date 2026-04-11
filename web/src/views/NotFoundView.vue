<template>
  <div class="not-found">
    <h1 class="not-found-title">404</h1>
    <p class="not-found-message">Page not found</p>
    <router-link to="/console" class="btn-primary">Go to Console</router-link>
  </div>
</template>

<style scoped>
.not-found {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 60vh;
  gap: 12px;
}

.not-found-title {
  font-family: var(--font-heading);
  font-size: 48px;
  font-weight: 600;
  color: var(--color-text-primary);
}

.not-found-message {
  font-size: 16px;
  color: var(--color-text-secondary);
  margin-bottom: 12px;
}
</style>
