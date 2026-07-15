<template>
  <section v-if="issues.length" class="validation-panel">
    <button v-for="issue in issues" :key="`${issue.scope}-${issue.nodeId}-${issue.edgeId}-${issue.field}-${issue.code}`" type="button" @click="emit('locate', issue)">
      <span class="validation-panel__code">{{ issue.code }}</span>
      <span>{{ issue.message }}</span>
      <span class="validation-panel__target">{{ issue.nodeId || issue.edgeId || '流程' }}</span>
    </button>
  </section>
</template>

<script lang="ts" setup>
  import type { ValidationIssue } from '../pipeline.types';
  defineProps<{ issues: ValidationIssue[] }>();
  const emit = defineEmits(['locate']);
</script>

<style scoped>
  .validation-panel { max-height: 150px; overflow: auto; border-top: 1px solid #f1c5c2; background: #fff8f7; padding: 8px 12px; }
  .validation-panel button { width: 100%; display: grid; grid-template-columns: 180px 1fr 150px; gap: 12px; padding: 5px 8px; border: 0; background: transparent; text-align: left; color: #7f1d1d; cursor: pointer; }
  .validation-panel button:hover { background: #feeceb; }
  .validation-panel__code { font-family: monospace; }
  .validation-panel__target { color: #9f5550; text-align: right; }
</style>
