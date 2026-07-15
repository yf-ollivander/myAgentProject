export type NodeType = 'START' | 'AGENT' | 'CONDITION' | 'NOTIFY' | 'END';
export type EdgeBranch = 'DEFAULT' | 'TRUE' | 'FALSE';
export type ValueType = 'string' | 'number' | 'boolean' | 'object' | 'array';
export type ErrorPolicy = 'INHERIT' | 'WAIT' | 'FAIL';
export type ArtifactType = 'TEXT' | 'JSON' | 'FEISHU_DOC' | 'GIT_REPO' | 'COMMIT' | 'BUILD' | 'TEST_REPORT' | 'DEPLOYMENT_URL' | 'OTHER';
export type SelectionMode = 'ALL' | 'LATEST';

export interface FieldSchema { name?: string; field?: string; type: ValueType; required: boolean }
export interface ArtifactInput { name: string; sourceNodeId: string; types: ArtifactType[]; required: boolean; selectionMode: SelectionMode }
export interface InterventionPolicy { onAgentNeedsUser: 'WAIT'; onRetriesExhausted: 'WAIT' | 'FAIL'; allowedActions: Array<'SUPPLY_INPUT' | 'RETRY' | 'CANCEL'> }
export interface PipelineMetadata {
  code: string;
  name: string;
  triggerAliases: string[];
  notificationBotId: string | null;
  defaultFeishuChatId: string | null;
  interventionPolicy: InterventionPolicy;
  finalSummaryTemplate: string;
}
export interface PipelineNode { id: string; type: NodeType; name: string; config: Record<string, any> }
export interface PipelineEdge { id: string; source: string; target: string; branch: EdgeBranch }
export interface PipelineDefinition { schemaVersion: '1.1'; pipeline: PipelineMetadata; nodes: PipelineNode[]; edges: PipelineEdge[] }
export interface PipelineUiNode { id: string; x: number; y: number; width?: number; height?: number }
export interface PipelineUi { nodes: PipelineUiNode[]; viewport: { x: number; y: number; zoom: number } }
export interface PipelineDraft { draftRevision: number; definition: PipelineDefinition; ui: PipelineUi }
export interface ValidationIssue { scope: 'PIPELINE' | 'NODE' | 'EDGE'; nodeId?: string; edgeId?: string; field: string; code: string; message: string }
export interface ValidationResult { valid: boolean; errors: ValidationIssue[]; warnings: ValidationIssue[] }
export interface PipelineVersionSummary { id: string; version: number; sourceDraftRevision: number; definitionHash: string; publishedBy: string; publishedAt: string }
export interface PipelineVersionView { id: string; version: number; schemaVersion: string; definition: PipelineDefinition; ui: PipelineUi; definitionHash: string; publishedBy: string; publishedAt: string }
export interface LogicFlowData {
  nodes: Array<{ id: string; type: string; x: number; y: number; text?: string; properties: Record<string, any> }>;
  edges: Array<{ id: string; type?: string; sourceNodeId: string; targetNodeId: string; text?: string; properties: Record<string, any> }>;
}

export const ARTIFACT_TYPES: ArtifactType[] = ['TEXT', 'JSON', 'FEISHU_DOC', 'GIT_REPO', 'COMMIT', 'BUILD', 'TEST_REPORT', 'DEPLOYMENT_URL', 'OTHER'];
export const VALUE_TYPES: ValueType[] = ['string', 'number', 'boolean', 'object', 'array'];
