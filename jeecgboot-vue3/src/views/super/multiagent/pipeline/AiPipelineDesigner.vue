<template>
  <div class="designer" :class="{ 'designer--readonly': readonlyMode }">
    <header class="designer__toolbar">
      <a-tooltip title="返回"><a-button type="text" @click="router.back()"><Icon icon="ant-design:arrow-left-outlined" /></a-button></a-tooltip>
      <div class="designer__title"><span>{{ metadata?.name || '流程设计器' }}</span><a-tag v-if="readonlyMode" color="default">V{{ versionNumber }} 只读</a-tag><a-badge v-else-if="state.dirty" status="warning" text="未保存" /></div>
      <div class="designer__actions">
        <a-tooltip title="保存"><a-button v-auth="'ai:pipeline:edit'" :disabled="readonlyMode" :loading="saving" @click="save"><Icon icon="ant-design:save-outlined" /></a-button></a-tooltip>
        <a-button v-auth="'ai:pipeline:validate'" :disabled="readonlyMode" :loading="validating" @click="validate">校验</a-button>
        <a-button v-auth="'ai:pipeline:publish'" type="primary" :disabled="readonlyMode" :loading="publishing" @click="publish">发布</a-button>
        <a-tooltip title="撤销"><a-button :disabled="readonlyMode" @click="logicFlow?.undo()"><Icon icon="ant-design:undo-outlined" /></a-button></a-tooltip>
        <a-tooltip title="重做"><a-button :disabled="readonlyMode" @click="logicFlow?.redo()"><Icon icon="ant-design:redo-outlined" /></a-button></a-tooltip>
        <a-tooltip title="适应画布"><a-button @click="logicFlow?.fitView(30, 30)"><Icon icon="ant-design:fullscreen-outlined" /></a-button></a-tooltip>
        <a-tooltip title="版本"><a-button @click="versionOpen = true"><Icon icon="ant-design:history-outlined" /></a-button></a-tooltip>
      </div>
    </header>
    <main class="designer__main">
      <NodePalette v-if="!readonlyMode" @drag="startDrag" />
      <section class="designer__canvas-wrap">
        <div ref="canvas" class="designer__canvas"></div>
        <ValidationPanel :issues="state.issues" @locate="locateIssue" />
      </section>
      <aside class="designer__properties">
        <a-tabs v-model:active-key="propertyTab" size="small">
          <a-tab-pane key="pipeline" tab="流程">
            <PipelineSettings v-if="metadata" v-model="metadata" :bot-options="botOptions" :readonly="readonlyMode" @update:model-value="changed" />
          </a-tab-pane>
          <a-tab-pane key="node" tab="节点/边">
            <NodePropertyPanel v-if="selectedNode" :node="selectedNode" :all-nodes="definitionNodes" :agent-options="agentOptions" :readonly="readonlyMode" @update:node="updateNode" />
            <div v-else-if="selectedEdge" class="edge-panel">
              <a-form layout="vertical"><a-form-item label="分支"><a-select :value="selectedEdge.properties?.branch || 'DEFAULT'" :disabled="readonlyMode" :options="branchOptions" @change="updateEdgeBranch" /></a-form-item></a-form>
            </div>
            <div v-else class="property-empty">选择节点或边后编辑属性</div>
          </a-tab-pane>
        </a-tabs>
      </aside>
    </main>
    <VersionDrawer v-model:open="versionOpen" :pipeline-id="pipelineId" @open-version="openVersion" />
  </div>
</template>

<script lang="ts" setup name="multiagent-pipeline-designer">
  import LogicFlow from '@logicflow/core';
  import '@logicflow/core/lib/index.css';
  import { register } from '@logicflow/vue-node-registry';
  import { Modal } from 'ant-design-vue';
  import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue';
  import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router';
  import Icon from '/@/components/Icon';
  import { useMessage } from '/@/hooks/web/useMessage';
  import { listAgentOptions } from '../agent/agent.api';
  import NodePalette from './components/NodePalette.vue';
  import NodePropertyPanel from './components/NodePropertyPanel.vue';
  import PipelineNodeView from './components/PipelineNode.vue';
  import PipelineSettings from './components/PipelineSettings.vue';
  import ValidationPanel from './components/ValidationPanel.vue';
  import VersionDrawer from './components/VersionDrawer.vue';
  import { fromLogicFlow, toLogicFlow } from './pipeline.adapter';
  import { getPipelineDraft, getPipelineVersion, listNotificationBotOptions, PipelineApiError, publishPipeline, savePipelineDraft, validatePipeline } from './pipeline.api';
  import { PipelineDesignerState } from './pipeline.designer-state';
  import type { LogicFlowData, PipelineMetadata, PipelineNode, ValidationIssue } from './pipeline.types';

  const route = useRoute();
  const router = useRouter();
  const { createMessage } = useMessage();
  const pipelineId = String(route.query.id || '');
  const versionNumber = route.query.version ? Number(route.query.version) : undefined;
  const readonlyMode = versionNumber !== undefined;
  const state = reactive(new PipelineDesignerState(readonlyMode));
  const canvas = ref<HTMLElement>();
  const metadata = ref<PipelineMetadata>();
  const definitionNodes = ref<PipelineNode[]>([]);
  const selectedNode = ref<PipelineNode>();
  const selectedEdge = ref<any>();
  const agentOptions = ref<any[]>([]);
  const botOptions = ref<any[]>([]);
  const propertyTab = ref('pipeline');
  const versionOpen = ref(false);
  const saving = ref(false), validating = ref(false), publishing = ref(false);
  let logicFlow: LogicFlow | undefined;
  let uiViewport = { x: 0, y: 0, zoom: 1 };
  const branchOptions = ['DEFAULT', 'TRUE', 'FALSE'].map((value) => ({ label: value, value }));

  onMounted(async () => {
    if (!pipelineId) { createMessage.error('缺少流程 ID'); router.back(); return; }
    if (!readonlyMode) {
      [agentOptions.value, botOptions.value] = await Promise.all([listAgentOptions({ limit: 200 }), listNotificationBotOptions({ limit: 200 })]);
    }
    const source: any = readonlyMode ? await getPipelineVersion(pipelineId, versionNumber!) : await getPipelineDraft(pipelineId);
    state.draftRevision = source.draftRevision || 0;
    metadata.value = structuredClone(source.definition.pipeline);
    definitionNodes.value = structuredClone(source.definition.nodes);
    uiViewport = source.ui.viewport || uiViewport;
    await nextTick();
    initialize(toLogicFlow(source.definition, source.ui));
  });

  function initialize(data: LogicFlowData) {
    logicFlow = new LogicFlow({ container: canvas.value!, grid: { size: 16, visible: true }, keyboard: { enabled: !readonlyMode }, history: true, edgeType: 'polyline' });
    for (const type of ['start', 'agent', 'condition', 'notify', 'end']) register({ type: `pipeline-${type}`, component: PipelineNodeView }, logicFlow);
    logicFlow.render(data as any);
    if (readonlyMode) logicFlow.updateEditConfig({ isSilentMode: true, adjustNodePosition: false, adjustEdge: false, hideAnchors: true, nodeTextEdit: false, edgeTextEdit: false });
    if (uiViewport.zoom !== 1) logicFlow.zoom(uiViewport.zoom);
    if (uiViewport.x || uiViewport.y) logicFlow.translate(uiViewport.x, uiViewport.y);
    logicFlow.on('node:click', ({ data }) => selectNode(data));
    logicFlow.on('edge:click', ({ data }) => { selectedEdge.value = data; selectedNode.value = undefined; propertyTab.value = 'node'; });
    logicFlow.on('blank:click', () => { selectedNode.value = undefined; selectedEdge.value = undefined; });
    logicFlow.on('graph:change', () => { syncNodes(); state.changed(); });
  }

  function syncNodes() {
    if (!logicFlow || !metadata.value) return;
    const graph = logicFlow.getGraphData() as LogicFlowData;
    definitionNodes.value = fromLogicFlow(graph, metadata.value).definition.nodes;
    if (selectedNode.value) selectedNode.value = definitionNodes.value.find((node) => node.id === selectedNode.value?.id);
  }
  function selectNode(data) { syncNodes(); selectedNode.value = definitionNodes.value.find((node) => node.id === data.id); selectedEdge.value = undefined; propertyTab.value = 'node'; }
  function changed() { state.changed(); }
  function updateNode(node: PipelineNode) {
    state.assertEditable();
    logicFlow?.setProperties(node.id, { nodeType: node.type, name: node.name, config: structuredClone(node.config) });
    logicFlow?.updateText(node.id, node.name);
    selectedNode.value = node;
    syncNodes();
    state.changed();
  }
  function updateEdgeBranch(branch) { if (!selectedEdge.value || readonlyMode) return; logicFlow?.setProperties(selectedEdge.value.id, { branch }); logicFlow?.updateText(selectedEdge.value.id, branch === 'DEFAULT' ? '' : branch); selectedEdge.value.properties = { ...selectedEdge.value.properties, branch }; state.changed(); }
  function startDrag(item) {
    state.assertEditable();
    const id = `${item.nodeType.toLowerCase()}_${Date.now().toString(36)}`;
    logicFlow?.dnd.startDrag({ id, type: item.type, text: item.label, properties: { nodeType: item.nodeType, name: item.label, config: defaultConfig(item.nodeType) } });
  }
  function defaultConfig(type) {
    if (type === 'START') return { inputSchema: [] };
    if (type === 'AGENT') return { stageCode: '', agentId: '', resultContractVersion: '1.1', input: {}, outputSchema: [], artifactOutputs: [], artifactInputs: [], onError: 'INHERIT' };
    if (type === 'CONDITION') return { left: { source: 'NODE_OUTPUT', nodeId: '', field: '', valueType: 'string' }, operator: 'EQ', right: { valueType: 'string', value: '' } };
    if (type === 'NOTIFY') return { messageTemplate: '' };
    return { output: {}, artifactSelection: [], completionSummary: '' };
  }
  function snapshot() {
    if (!logicFlow || !metadata.value) throw new Error('设计器尚未加载');
    const transform = logicFlow.getTransform();
    return fromLogicFlow(logicFlow.getGraphData() as LogicFlowData, metadata.value, { x: transform.TRANSLATE_X, y: transform.TRANSLATE_Y, zoom: transform.SCALE_X });
  }
  async function save() {
    state.assertEditable(); saving.value = true;
    try { const data = snapshot(); const revision = await savePipelineDraft(pipelineId, { draftRevision: state.draftRevision, ...data }); state.saved(revision); createMessage.success('草稿已保存'); return revision; }
    catch (error) { if (error instanceof PipelineApiError && error.code === 409) Modal.confirm({ title: '草稿已被其他会话更新', content: '当前内容不会自动合并。重新加载后将显示数据库中的最新草稿。', okText: '重新加载', cancelText: '留在当前页', onOk: () => window.location.reload() }); throw error; }
    finally { saving.value = false; }
  }
  async function validate() {
    validating.value = true;
    try { if (state.dirty) await save(); const result = await validatePipeline(pipelineId, state.draftRevision); state.setIssues(result.errors || []); result.valid ? createMessage.success('流程校验通过') : createMessage.error(`发现 ${result.errors.length} 个问题`); }
    finally { validating.value = false; }
  }
  async function publish() {
    publishing.value = true;
    try { if (state.dirty) await save(); const validation = await validatePipeline(pipelineId, state.draftRevision); state.setIssues(validation.errors || []); if (!validation.valid) { createMessage.error('请先修复校验问题'); return; }
      const requestId = globalThis.crypto?.randomUUID?.() || `${Date.now()}-${Math.random().toString(16).slice(2)}`;
      const result: any = await publishPipeline(pipelineId, { draftRevision: state.draftRevision, requestId }); createMessage.success(result.reused ? `复用版本 V${result.version}` : `已发布 V${result.version}`);
    } finally { publishing.value = false; }
  }
  function locateIssue(issue: ValidationIssue) { const target = state.locate(issue); if (!target || !logicFlow) return; logicFlow.selectElementById(target.id); logicFlow.focusOn({ id: target.id }); if (target.kind === 'node') { const data: any = (logicFlow.getGraphData() as any).nodes.find((node) => node.id === target.id); if (data) selectNode(data); } }
  function openVersion(version) { versionOpen.value = false; router.push({ path: '/multi-agent/pipelines/design', query: { id: pipelineId, version } }); }

  onBeforeRouteLeave((_to, _from, next) => {
    if (!state.dirty || readonlyMode) { next(); return; }
    Modal.confirm({ title: '存在未保存修改', content: '离开后当前修改将丢失。', okText: '离开', cancelText: '继续编辑', onOk: () => next(), onCancel: () => next(false) });
  });
  onBeforeUnmount(() => { logicFlow?.destroy(); logicFlow = undefined; });
</script>

<style scoped>
  .designer { position: fixed; inset: 0; z-index: 600; display: flex; flex-direction: column; background: #eef1f5; color: #1f2937; }
  .designer__toolbar { height: 54px; flex: 0 0 54px; display: flex; align-items: center; gap: 10px; padding: 0 12px; border-bottom: 1px solid #dce1e8; background: #fff; }
  .designer__title { min-width: 180px; max-width: 420px; display: flex; align-items: center; gap: 10px; font-weight: 600; overflow: hidden; }
  .designer__title > span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .designer__actions { margin-left: auto; display: flex; gap: 7px; }
  .designer__main { min-height: 0; flex: 1; display: flex; }
  .designer__canvas-wrap { min-width: 0; flex: 1; display: flex; flex-direction: column; background: #fff; }
  .designer__canvas { min-height: 0; flex: 1; }
  .designer__properties { width: min(390px, 34vw); flex: 0 0 min(390px, 34vw); overflow: auto; border-left: 1px solid #e1e5eb; background: #fff; }
  .designer__properties :deep(.ant-tabs-nav) { margin: 0; padding: 0 14px; }
  .edge-panel { padding: 18px; }
  .property-empty { padding: 40px 18px; text-align: center; color: #8b95a5; }
  @media (max-width: 900px) { .designer__properties { width: 320px; flex-basis: 320px; } .designer__title { min-width: 100px; } .designer__actions .ant-btn:nth-child(4), .designer__actions .ant-btn:nth-child(5) { display: none; } }
</style>
