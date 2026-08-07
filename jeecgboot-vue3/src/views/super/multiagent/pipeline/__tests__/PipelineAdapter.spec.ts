import { fromLogicFlow, semanticDefinition, toLogicFlow } from '../pipeline.adapter';
import type { PipelineDefinition, PipelineUi } from '../pipeline.types';

const definition: PipelineDefinition = {
  schemaVersion: '1.1',
  pipeline: { code: 'sample', name: 'Sample', triggerAliases: ['b', 'a'], notificationBotId: 'bot', defaultFeishuChatId: 'chat', interventionPolicy: { onAgentNeedsUser: 'WAIT', onRetriesExhausted: 'FAIL', allowedActions: ['SUPPLY_INPUT', 'RETRY', 'CANCEL'] }, finalSummaryTemplate: '{{nodes.agent.summary}}' },
  nodes: [
    { id: 'start', type: 'START', name: 'Start', config: { inputSchema: [{ name: 'task', type: 'string', required: true }] } },
    { id: 'agent', type: 'AGENT', name: 'Agent', config: { stageCode: 'agent', agentId: 'a1', resultContractVersion: '1.1', input: { task: '{{run.input.task}}' }, outputSchema: [], artifactOutputs: ['JSON'], artifactInputs: [], onError: 'INHERIT' } },
    { id: 'end', type: 'END', name: 'End', config: { output: {}, artifactSelection: [{ name: 'json', sourceNodeId: 'agent', types: ['JSON'], required: true, selectionMode: 'LATEST' }], completionSummary: '{{nodes.agent.summary}}' } },
  ],
  edges: [{ id: 'e1', source: 'start', target: 'agent', branch: 'DEFAULT' }, { id: 'e2', source: 'agent', target: 'end', branch: 'DEFAULT' }],
};
const ui: PipelineUi = { nodes: [{ id: 'start', x: 100, y: 200 }, { id: 'agent', x: 300, y: 200 }, { id: 'end', x: 500, y: 200 }], viewport: { x: 15, y: 20, zoom: 1.2 } };

describe('Pipeline Adapter', () => {
  it('round trips definition semantics and UI coordinates', () => {
    const graph = toLogicFlow(definition, ui);
    const restored = fromLogicFlow(graph, definition.pipeline, ui.viewport);
    expect(semanticDefinition(restored.definition)).toBe(semanticDefinition(definition));
    expect(restored.ui).toEqual(ui);
  });

  it('preserves condition branch labels', () => {
    const source = structuredClone(definition);
    source.edges[0].branch = 'TRUE';
    expect(fromLogicFlow(toLogicFlow(source, ui), source.pipeline).definition.edges[0].branch).toBe('TRUE');
  });
});
