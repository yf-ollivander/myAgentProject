<template>
  <a-drawer :open="open" title="发布版本" width="520" @close="emit('update:open', false)">
    <a-list :data-source="versions" :loading="loading">
      <template #renderItem="{ item }">
        <a-list-item>
          <a-list-item-meta :title="`V${item.version}`" :description="`${item.publishedBy} · ${item.publishedAt}`" />
          <template #actions><a-button type="link" @click="emit('openVersion', item.version)">只读查看</a-button></template>
        </a-list-item>
      </template>
    </a-list>
  </a-drawer>
</template>

<script lang="ts" setup>
  import { ref, watch } from 'vue';
  import { listPipelineVersions } from '../pipeline.api';
  import type { PipelineVersionSummary } from '../pipeline.types';
  const props = defineProps<{ open: boolean; pipelineId: string }>();
  const emit = defineEmits(['update:open', 'openVersion']);
  const versions = ref<PipelineVersionSummary[]>([]);
  const loading = ref(false);
  watch(() => props.open, async (value) => {
    if (!value) return;
    loading.value = true;
    try { const page: any = await listPipelineVersions(props.pipelineId, { pageNo: 1, pageSize: 100 }); versions.value = page.records || []; }
    finally { loading.value = false; }
  });
</script>
