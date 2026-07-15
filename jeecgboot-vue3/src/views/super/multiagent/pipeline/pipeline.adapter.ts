import type { LogicFlowData, PipelineDefinition, PipelineEdge, PipelineMetadata, PipelineNode, PipelineUi } from './pipeline.types';

export function toLogicFlow(definition: PipelineDefinition, ui: PipelineUi): LogicFlowData {
  const positions = new Map(ui.nodes.map((node) => [node.id, node]));
  return {
    nodes: definition.nodes.map((node, index) => {
      const position = positions.get(node.id) || { x: 240 + index * 220, y: 300 };
      return {
        id: node.id,
        type: `pipeline-${node.type.toLowerCase()}`,
        x: position.x,
        y: position.y,
        text: node.name,
        properties: { nodeType: node.type, name: node.name, config: structuredClone(node.config) },
      };
    }),
    edges: definition.edges.map((edge) => ({
      id: edge.id,
      type: 'polyline',
      sourceNodeId: edge.source,
      targetNodeId: edge.target,
      text: edge.branch === 'DEFAULT' ? '' : edge.branch,
      properties: { branch: edge.branch },
    })),
  };
}

export function fromLogicFlow(graph: LogicFlowData, pipeline: PipelineMetadata, viewport = { x: 0, y: 0, zoom: 1 }): { definition: PipelineDefinition; ui: PipelineUi } {
  const nodes: PipelineNode[] = graph.nodes.map((node) => ({
    id: node.id,
    type: node.properties.nodeType,
    name: node.properties.name || String(node.text || node.properties.nodeType),
    config: structuredClone(node.properties.config || {}),
  }));
  const edges: PipelineEdge[] = graph.edges.map((edge) => ({
    id: edge.id,
    source: edge.sourceNodeId,
    target: edge.targetNodeId,
    branch: edge.properties?.branch || 'DEFAULT',
  }));
  return {
    definition: { schemaVersion: '1.1', pipeline: structuredClone(pipeline), nodes, edges },
    ui: { nodes: graph.nodes.map((node) => ({ id: node.id, x: node.x, y: node.y })), viewport },
  };
}

export function semanticDefinition(definition: PipelineDefinition): string {
  const copy = structuredClone(definition);
  copy.nodes.sort((a, b) => a.id.localeCompare(b.id));
  copy.edges.sort((a, b) => `${a.source}|${a.branch}|${a.target}|${a.id}`.localeCompare(`${b.source}|${b.branch}|${b.target}|${b.id}`));
  copy.pipeline.triggerAliases = [...new Set(copy.pipeline.triggerAliases)].sort();
  return JSON.stringify(copy);
}
