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
      <NodePalette v-if="!readonlyMode" @drag="startDrag" @add="addNode" />
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
            <div v-if="selectedNode" class="element-panel">
              <!-- update-begin---author:Codex ---date:2026-08-10  for：【REQ-HTTP-MODEL-20260810】为选中节点提供可发现的类型转换与删除入口----------- -->
              <div class="element-panel__actions">
                <a-form layout="vertical" class="element-panel__type-form">
                  <a-form-item label="节点类型">
                    <a-select :value="selectedNode.type" :options="nodeTypeOptions" :disabled="readonlyMode" @change="confirmNodeTypeChange" />
                  </a-form-item>
                </a-form>
                <a-tooltip title="删除节点">
                  <a-button v-if="!readonlyMode" danger type="text" aria-label="删除节点" @click="deleteSelectedElement">
                    <Icon icon="ant-design:delete-outlined" />
                  </a-button>
                </a-tooltip>
              </div>
              <!-- update-end---author:Codex ---date:2026-08-10  for：【REQ-HTTP-MODEL-20260810】为选中节点提供可发现的类型转换与删除入口----------- -->
              <!-- update-begin---author:Codex ---date:2026-08-10  for：【REQ-HTTP-MODEL-20260810】提供可见的新增连线入口，作为画布锚点拖拽的易用补充----------- -->
              <a-form v-if="!readonlyMode && selectedNode.type !== 'END'" layout="vertical" size="small" class="connection-panel">
                <a-form-item label="新增连线到">
                  <a-select v-model:value="newEdgeTargetId" show-search option-filter-prop="label" :options="newEdgeTargetOptions" placeholder="选择目标节点" />
                </a-form-item>
                <a-button block type="dashed" :disabled="!newEdgeTargetId" @click="addConnection"><Icon icon="ant-design:node-index-outlined" /> 新增连线</a-button>
              </a-form>
              <!-- update-end---author:Codex ---date:2026-08-10  for：【REQ-HTTP-MODEL-20260810】提供可见的新增连线入口，作为画布锚点拖拽的易用补充----------- -->
              <NodePropertyPanel :node="selectedNode" :all-nodes="definitionNodes" :agent-options="agentOptions" :readonly="readonlyMode" @update:node="updateNode" />
            </div>
            <div v-else-if="selectedEdge" class="edge-panel">
              <!-- update-begin---author:Codex ---date:2026-08-10  for：【REQ-HTTP-MODEL-20260810】边属性面板补充显式删除操作----------- -->
              <div class="element-panel__actions">
                <a-form layout="vertical" class="element-panel__type-form">
                  <a-form-item label="起点"><a-select :value="selectedEdge.sourceNodeId" show-search option-filter-prop="label" :disabled="readonlyMode" :options="edgeSourceOptions" @change="(value) => updateEdgeEndpoint('source', value)" /></a-form-item>
                  <a-form-item label="终点"><a-select :value="selectedEdge.targetNodeId" show-search option-filter-prop="label" :disabled="readonlyMode" :options="edgeTargetOptions" @change="(value) => updateEdgeEndpoint('target', value)" /></a-form-item>
                  <a-form-item label="分支"><a-select :value="selectedEdge.properties?.branch || 'DEFAULT'" :disabled="readonlyMode" :options="branchOptions" @change="updateEdgeBranch" /></a-form-item>
                </a-form>
                <a-tooltip title="删除边">
                  <a-button v-if="!readonlyMode" danger type="text" aria-label="删除边" @click="deleteSelectedElement"><Icon icon="ant-design:delete-outlined" /></a-button>
                </a-tooltip>
              </div>
              <!-- update-end---author:Codex ---date:2026-08-10  for：【REQ-HTTP-MODEL-20260810】边属性面板补充显式删除操作----------- -->
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
  import { clonePipelineJson, fromLogicFlow, toLogicFlow } from './pipeline.adapter';
  import { getPipelineDraft, getPipelineVersion, listNotificationBotOptions, PipelineApiError, publishPipeline, savePipelineDraft, validatePipeline } from './pipeline.api';
  import { PipelineDesignerState } from './pipeline.designer-state';
  import { connectionError, convertPipelineNode, defaultNodeConfig, NODE_TYPE_OPTIONS, shouldHandleDesignerDelete } from './pipeline.node-actions';
  import type { EdgeBranch, LogicFlowData, NodeType, PipelineEdge, PipelineMetadata, PipelineNode, ValidationIssue } from './pipeline.types';

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
  const newEdgeTargetId = ref<string>();
  const agentOptions = ref<any[]>([]);
  const botOptions = ref<any[]>([]);
  const propertyTab = ref('pipeline');
  const versionOpen = ref(false);
  const saving = ref(false), validating = ref(false), publishing = ref(false);
  let logicFlow: LogicFlow | undefined;
  let uiViewport = { x: 0, y: 0, zoom: 1 };
  const branchOptions = ['DEFAULT', 'TRUE', 'FALSE'].map((value) => ({ label: value, value }));
  const nodeTypeOptions = NODE_TYPE_OPTIONS;
  const allNodeOptions = computed(() => definitionNodes.value.map((node) => ({ label: `${node.name} (${node.id})`, value: node.id, type: node.type })));
  const newEdgeTargetOptions = computed(() => allNodeOptions.value.filter((option) => option.value !== selectedNode.value?.id && option.type !== 'START'));
  const edgeSourceOptions = computed(() => allNodeOptions.value.filter((option) => option.value !== selectedEdge.value?.targetNodeId && option.type !== 'END'));
  const edgeTargetOptions = computed(() => allNodeOptions.value.filter((option) => option.value !== selectedEdge.value?.sourceNodeId && option.type !== 'START'));

  onMounted(async () => {
    if (!pipelineId) { createMessage.error('缺少流程 ID'); router.back(); return; }
    if (!readonlyMode) {
      [agentOptions.value, botOptions.value] = await Promise.all([listAgentOptions({ limit: 200 }), listNotificationBotOptions({ limit: 200 })]);
    }
    const source: any = readonlyMode ? await getPipelineVersion(pipelineId, versionNumber!) : await getPipelineDraft(pipelineId);
    state.draftRevision = source.draftRevision || 0;
    metadata.value = clonePipelineJson(source.definition.pipeline);
    definitionNodes.value = clonePipelineJson(source.definition.nodes);
    uiViewport = source.ui.viewport || uiViewport;
    await nextTick();
    initialize(toLogicFlow(source.definition, source.ui));
    window.addEventListener('keydown', handleDesignerKeydown);
  });

  function initialize(data: LogicFlowData) {
    logicFlow = new LogicFlow({ container: canvas.value!, grid: { size: 16, visible: true }, keyboard: { enabled: !readonlyMode }, history: true, edgeType: 'polyline' });
    for (const type of ['start', 'agent', 'condition', 'notify', 'end']) register({ type: `pipeline-${type}`, component: PipelineNodeView }, logicFlow);
    logicFlow.render(data as any);
    if (readonlyMode) logicFlow.updateEditConfig({ isSilentMode: true, adjustNodePosition: false, adjustEdge: false, hideAnchors: true, nodeTextEdit: false, edgeTextEdit: false });
    else logicFlow.updateEditConfig({ adjustEdgeStartAndEnd: true, hideAnchors: false });
    if (uiViewport.zoom !== 1) logicFlow.zoom(uiViewport.zoom);
    if (uiViewport.x || uiViewport.y) logicFlow.translate(uiViewport.x, uiViewport.y);
    logicFlow.on('node:click', ({ data }) => selectNode(data));
    logicFlow.on('edge:click', ({ data }) => { selectedEdge.value = data; selectedNode.value = undefined; newEdgeTargetId.value = undefined; propertyTab.value = 'node'; });
    logicFlow.on('edge:adjust', ({ data }) => { selectedEdge.value = data; selectedNode.value = undefined; newEdgeTargetId.value = undefined; propertyTab.value = 'node'; state.changed(); });
    logicFlow.on('blank:click', () => { selectedNode.value = undefined; selectedEdge.value = undefined; newEdgeTargetId.value = undefined; });
    logicFlow.on('graph:change', () => { syncNodes(); state.changed(); });
  }

  function syncNodes() {
    if (!logicFlow || !metadata.value) return;
    const graph = logicFlow.getGraphData() as LogicFlowData;
    definitionNodes.value = fromLogicFlow(graph, metadata.value).definition.nodes;
    if (selectedNode.value) selectedNode.value = definitionNodes.value.find((node) => node.id === selectedNode.value?.id);
  }
  function selectNode(data) { syncNodes(); selectedNode.value = definitionNodes.value.find((node) => node.id === data.id); selectedEdge.value = undefined; newEdgeTargetId.value = undefined; propertyTab.value = 'node'; }
  function changed() { state.changed(); }
  function updateNode(node: PipelineNode) {
    state.assertEditable();
    logicFlow?.setProperties(node.id, { nodeType: node.type, name: node.name, config: clonePipelineJson(node.config) });
    logicFlow?.updateText(node.id, node.name);
    selectedNode.value = node;
    syncNodes();
    state.changed();
  }
  function updateEdgeBranch(branch) { if (!selectedEdge.value || readonlyMode) return; logicFlow?.setProperties(selectedEdge.value.id, { branch }); logicFlow?.updateText(selectedEdge.value.id, branch === 'DEFAULT' ? '' : branch); selectedEdge.value.properties = { ...selectedEdge.value.properties, branch }; state.changed(); }
  function graphEdges(): PipelineEdge[] {
    if (!logicFlow) return [];
    return (logicFlow.getGraphData() as LogicFlowData).edges.map((edge) => ({ id: edge.id, source: edge.sourceNodeId, target: edge.targetNodeId, branch: edge.properties?.branch || 'DEFAULT' }));
  }
  function addConnection() {
    state.assertEditable();
    if (!logicFlow || !selectedNode.value || !newEdgeTargetId.value) return;
    const branch: EdgeBranch = 'DEFAULT';
    const error = connectionError(graphEdges(), selectedNode.value.id, newEdgeTargetId.value, branch);
    if (error) { createMessage.warning(error); return; }
    let id = `edge_${Date.now().toString(36)}`;
    let suffix = 1;
    while (logicFlow.getEdgeDataById(id)) id = `edge_${Date.now().toString(36)}_${suffix++}`;
    const edge = logicFlow.addEdge({ id, type: 'polyline', sourceNodeId: selectedNode.value.id, targetNodeId: newEdgeTargetId.value, properties: { branch } });
    selectedEdge.value = edge.getData();
    selectedNode.value = undefined;
    newEdgeTargetId.value = undefined;
    logicFlow.selectElementById(id);
    state.changed();
  }
  function updateEdgeEndpoint(endpoint: 'source' | 'target', nodeId: string) {
    state.assertEditable();
    if (!logicFlow || !selectedEdge.value) return;
    const current = selectedEdge.value;
    const sourceNodeId = endpoint === 'source' ? nodeId : current.sourceNodeId;
    const targetNodeId = endpoint === 'target' ? nodeId : current.targetNodeId;
    const branch: EdgeBranch = current.properties?.branch || 'DEFAULT';
    const error = connectionError(graphEdges(), sourceNodeId, targetNodeId, branch, current.id);
    if (error) { createMessage.warning(error); return; }
    const properties = clonePipelineJson(current.properties || { branch });
    logicFlow.deleteEdge(current.id);
    const edge = logicFlow.addEdge({ id: current.id, type: current.type || 'polyline', sourceNodeId, targetNodeId, text: branch === 'DEFAULT' ? '' : branch, properties });
    selectedEdge.value = edge.getData();
    logicFlow.selectElementById(current.id);
    state.changed();
  }
  function confirmNodeTypeChange(nextType: NodeType) {
    if (!selectedNode.value || nextType === selectedNode.value.type || readonlyMode) return;
    const currentNode = selectedNode.value;
    Modal.confirm({
      title: '转换节点类型？',
      content: `将“${currentNode.name}”转换为 ${nodeTypeOptions.find((option) => option.value === nextType)?.label}，原类型的配置会被清空，连线保留。`,
      okText: '确认转换',
      cancelText: '取消',
      onOk: () => changeNodeType(currentNode, nextType),
    });
  }
  function changeNodeType(node: PipelineNode, nextType: NodeType) {
    state.assertEditable();
    if (!logicFlow || node.type === nextType) return;
    const nextNode = convertPipelineNode(node, nextType);
    logicFlow.changeNodeType(node.id, `pipeline-${nextType.toLowerCase()}`);
    logicFlow.setProperties(node.id, { nodeType: nextType, name: nextNode.name, config: clonePipelineJson(nextNode.config) });
    logicFlow.updateText(node.id, nextNode.name);
    syncNodes();
    selectedNode.value = definitionNodes.value.find((item) => item.id === node.id) || nextNode;
    logicFlow.selectElementById(node.id);
    state.changed();
  }
  function deleteSelectedElement() {
    state.assertEditable();
    if (!logicFlow) return;
    if (selectedNode.value) logicFlow.deleteNode(selectedNode.value.id);
    else if (selectedEdge.value) logicFlow.deleteEdge(selectedEdge.value.id);
    else return;
    selectedNode.value = undefined;
    selectedEdge.value = undefined;
    syncNodes();
    state.changed();
  }
  function handleDesignerKeydown(event: KeyboardEvent) {
    if (!shouldHandleDesignerDelete(event, readonlyMode, Boolean(selectedNode.value || selectedEdge.value))) return;
    event.preventDefault();
    deleteSelectedElement();
  }
  function startDrag(item) {
    state.assertEditable();
    const id = `${item.nodeType.toLowerCase()}_${Date.now().toString(36)}`;
    logicFlow?.dnd.startDrag({ id, type: item.type, text: item.label, properties: { nodeType: item.nodeType, name: item.label, config: defaultNodeConfig(item.nodeType) } });
  }
  function addNode(item) {
    state.assertEditable();
    if (!logicFlow || !canvas.value) return;
    const id = `${item.nodeType.toLowerCase()}_${Date.now().toString(36)}`;
    const bounds = canvas.value.getBoundingClientRect();
    const point = logicFlow.getPointByClient({ x: bounds.left + bounds.width / 2, y: bounds.top + bounds.height / 2 }).canvasOverlayPosition;
    logicFlow.addNode({ id, type: item.type, x: point.x + (definitionNodes.value.length % 4) * 24, y: point.y + (definitionNodes.value.length % 4) * 24, text: item.label, properties: { nodeType: item.nodeType, name: item.label, config: defaultNodeConfig(item.nodeType) } });
    const data: any = (logicFlow.getGraphData() as LogicFlowData).nodes.find((node) => node.id === id);
    if (data) { logicFlow.selectElementById(id); selectNode(data); }
    state.changed();
  }
  function snapshot() {
    if (!logicFlow || !metadata.value) throw new Error('设计器尚未加载');
    const transform = logicFlow.getTransform();
    return fromLogicFlow(logicFlow.getGraphData() as LogicFlowData, metadata.value, { x: transform.TRANSLATE_X, y: transform.TRANSLATE_Y, zoom: transform.SCALE_X });
  }
  async function save() {
    state.assertEditable(); saving.value = true;
    try { const data = snapshot(); const revision = await savePipelineDraft(pipelineId, { draftRevision: state.draftRevision, ...data }); state.saved(revision); createMessage.success('草稿已保存'); return revision; }
    catch (error) {
      if (error instanceof PipelineApiError && error.code === 409) Modal.confirm({ title: '草稿已被其他会话更新', content: '当前内容不会自动合并。重新加载后将显示数据库中的最新草稿。', okText: '重新加载', cancelText: '留在当前页', onOk: () => window.location.reload() });
      else {
        // update-begin---author:Codex ---date:2026-08-10  for：【REQ-HTTP-MODEL-20260810】保存 API 关闭了全局错误提示，设计器必须就地回显失败原因-----------
        const message = error instanceof Error && error.message ? error.message : '草稿保存失败';
        createMessage.error(message);
        // update-end---author:Codex ---date:2026-08-10  for：【REQ-HTTP-MODEL-20260810】保存 API 关闭了全局错误提示，设计器必须就地回显失败原因-----------
      }
      throw error;
    }
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
  onBeforeUnmount(() => { window.removeEventListener('keydown', handleDesignerKeydown); logicFlow?.destroy(); logicFlow = undefined; });
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
  .element-panel__actions { display: flex; align-items: flex-start; gap: 8px; padding: 14px 16px 0; border-bottom: 1px solid #edf0f3; }
  .element-panel__type-form { min-width: 0; flex: 1; }
  .element-panel__actions > .ant-btn { flex: 0 0 32px; margin-top: 27px; }
  .connection-panel { padding: 14px 16px; border-bottom: 1px solid #edf0f3; }
  .connection-panel :deep(.ant-form-item) { margin-bottom: 10px; }
  .edge-panel { padding-bottom: 18px; }
  .property-empty { padding: 40px 18px; text-align: center; color: #8b95a5; }
  @media (max-width: 900px) { .designer__properties { width: 320px; flex-basis: 320px; } .designer__title { min-width: 100px; } .designer__actions .ant-btn:nth-child(4), .designer__actions .ant-btn:nth-child(5) { display: none; } }
</style>
