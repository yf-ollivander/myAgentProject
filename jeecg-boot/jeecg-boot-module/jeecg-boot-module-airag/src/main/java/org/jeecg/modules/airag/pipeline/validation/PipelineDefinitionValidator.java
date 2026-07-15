package org.jeecg.modules.airag.pipeline.validation;

import org.jeecg.modules.airag.agent.service.AgentAccessContext;
import org.jeecg.modules.airag.agent.service.AuthorizedAgentConfigProvider;
import org.jeecg.modules.airag.pipeline.contract.*;
import org.jeecg.modules.airag.pipeline.service.AuthorizedPipelineBotResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class PipelineDefinitionValidator {
    private static final Pattern ID = Pattern.compile("^[A-Za-z][A-Za-z0-9_-]{0,63}$");
    private static final Pattern CODE = Pattern.compile("^[A-Za-z][A-Za-z0-9_-]{0,63}$");
    private static final Pattern TEMPLATE = Pattern.compile("\\{\\{([^{}]+)}}");
    private static final Pattern RUN_INPUT = Pattern.compile("run\\.input\\.([A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)*)");
    private static final Pattern NODE_OUTPUT = Pattern.compile("nodes\\.([A-Za-z][A-Za-z0-9_-]{0,63})\\.output\\.([A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)*)");
    private static final Pattern NODE_SUMMARY = Pattern.compile("nodes\\.([A-Za-z][A-Za-z0-9_-]{0,63})\\.summary");
    private final PipelineDefinitionCodec codec;
    private final PipelineNodeConfigParser parser;
    private final PipelineGraphAnalyzer graphAnalyzer;
    private final TriggerKeyNormalizer triggerNormalizer;
    private final AuthorizedAgentConfigProvider agentProvider;
    private final AuthorizedPipelineBotResolver botResolver;

    public PipelineDefinitionValidator(PipelineDefinitionCodec codec, PipelineNodeConfigParser parser,
                                       PipelineGraphAnalyzer graphAnalyzer, TriggerKeyNormalizer triggerNormalizer,
                                       AuthorizedAgentConfigProvider agentProvider,
                                       AuthorizedPipelineBotResolver botResolver) {
        this.codec = codec;
        this.parser = parser;
        this.graphAnalyzer = graphAnalyzer;
        this.triggerNormalizer = triggerNormalizer;
        this.agentProvider = agentProvider;
        this.botResolver = botResolver;
    }

    public List<ValidationIssue> validate(PipelineDefinition definition, PipelineUiModel ui,
                                          AgentAccessContext accessContext, boolean resolveDependencies) {
        List<ValidationIssue> issues = new ArrayList<>();
        if (definition == null) return List.of(issue("PIPELINE", null, null, "definition", "REQUIRED", "Definition is required"));
        if (definition.getNodes() == null) definition.setNodes(new ArrayList<>());
        if (definition.getEdges() == null) definition.setEdges(new ArrayList<>());
        if (codec.write(definition).getBytes(StandardCharsets.UTF_8).length > 1024 * 1024) {
            issues.add(issue("PIPELINE", null, null, "definition", "SIZE_LIMIT", "Definition exceeds 1 MiB"));
        }
        validateMetadata(definition.getPipeline(), issues);
        List<PipelineNode> nodes = definition.getNodes() == null ? List.of() : definition.getNodes();
        List<PipelineEdge> edges = definition.getEdges() == null ? List.of() : definition.getEdges();
        if (!"1.1".equals(definition.getSchemaVersion())) issues.add(issue("PIPELINE", null, null, "schemaVersion", "SCHEMA_VERSION", "schemaVersion must be 1.1"));
        if (nodes.size() > 100) issues.add(issue("PIPELINE", null, null, "nodes", "SIZE_LIMIT", "Node count exceeds 100"));
        if (edges.size() > 200) issues.add(issue("PIPELINE", null, null, "edges", "SIZE_LIMIT", "Edge count exceeds 200"));

        Set<String> nodeIds = duplicateCheckedIds(nodes.stream().map(PipelineNode::getId).toList(), "NODE", issues);
        Set<String> edgeIds = duplicateCheckedIds(edges.stream().map(PipelineEdge::getId).toList(), "EDGE", issues);
        for (String id : nodeIds) if (!ID.matcher(id).matches()) issues.add(issue("NODE", id, null, "id", "ID_INVALID", "Node ID is invalid"));
        for (String id : edgeIds) if (!ID.matcher(id).matches()) issues.add(issue("EDGE", null, id, "id", "ID_INVALID", "Edge ID is invalid"));

        Map<String, Object> configs = new LinkedHashMap<>();
        Map<String, AgentNodeConfig> agents = new LinkedHashMap<>();
        Map<String, Set<String>> agentOutputs = new LinkedHashMap<>();
        Set<String> stages = new HashSet<>();
        for (PipelineNode node : nodes) validateNode(node, configs, agents, agentOutputs, stages, issues);
        validateEdges(edges, nodeIds, issues);
        validateUi(ui, nodeIds, issues);

        PipelineGraphAnalyzer.Analysis graph = graphAnalyzer.analyze(definition);
        validateGraph(nodes, edges, graph, issues);
        validateReferences(definition, configs, agents, agentOutputs, graph, issues);

        if (resolveDependencies && accessContext != null) {
            for (Map.Entry<String, AgentNodeConfig> entry : agents.entrySet()) {
                try {
                    agentProvider.resolveEnabledSnapshot(entry.getValue().getAgentId(), accessContext);
                } catch (RuntimeException e) {
                    if (isAuthorizationFailure(e)) throw PipelineException.of(
                            PipelineErrorCode.PIPELINE_NOT_FOUND_OR_FORBIDDEN,
                            "Pipeline dependency is outside the authorized data scope", null);
                    issues.add(issue("NODE", entry.getKey(), null, "agentId", "AGENT_NOT_AVAILABLE", "Agent is unavailable or forbidden"));
                }
            }
            try {
                botResolver.resolveAvailable(definition.getPipeline().getNotificationBotId(), accessContext);
            } catch (PipelineException e) {
                if (e.getErrorCode() == PipelineErrorCode.PIPELINE_NOT_FOUND_OR_FORBIDDEN) throw e;
                issues.add(issue("PIPELINE", null, null, "notificationBotId", "BOT_NOT_AVAILABLE", "Notification bot is unavailable or forbidden"));
            } catch (RuntimeException e) {
                issues.add(issue("PIPELINE", null, null, "notificationBotId", "BOT_NOT_AVAILABLE", "Notification bot is unavailable or forbidden"));
            }
        }
        Collections.sort(issues);
        return issues;
    }

    public void validateOrThrow(PipelineDefinition definition, PipelineUiModel ui,
                                AgentAccessContext accessContext, boolean resolveDependencies) {
        List<ValidationIssue> issues = validate(definition, ui, accessContext, resolveDependencies);
        if (!issues.isEmpty()) throw PipelineException.invalid(issues);
    }

    private void validateMetadata(PipelineDefinition.PipelineMetadata pipeline, List<ValidationIssue> issues) {
        if (pipeline == null) {
            issues.add(issue("PIPELINE", null, null, "pipeline", "REQUIRED", "Pipeline metadata is required"));
            return;
        }
        if (!StringUtils.hasText(pipeline.getCode()) || !CODE.matcher(pipeline.getCode()).matches()) issues.add(issue("PIPELINE", null, null, "code", "CODE_INVALID", "Pipeline code is invalid"));
        if (!StringUtils.hasText(pipeline.getName()) || pipeline.getName().length() > 100) issues.add(issue("PIPELINE", null, null, "name", "NAME_INVALID", "Pipeline name is required and limited to 100 characters"));
        List<String> aliases = pipeline.getTriggerAliases() == null ? List.of() : pipeline.getTriggerAliases();
        if (aliases.size() > 10) issues.add(issue("PIPELINE", null, null, "triggerAliases", "SIZE_LIMIT", "At most 10 aliases are allowed"));
        Set<String> normalized = new HashSet<>();
        for (String alias : aliases) {
            if (!StringUtils.hasText(alias) || alias.length() > 50 || alias.matches(".*[【】,，\\r\\n\\t].*")) {
                issues.add(issue("PIPELINE", null, null, "triggerAliases", "ALIAS_INVALID", "Alias is invalid"));
            } else if (!normalized.add(triggerNormalizer.normalize(alias))) {
                issues.add(issue("PIPELINE", null, null, "triggerAliases", "ALIAS_DUPLICATE", "Aliases must be unique after normalization"));
            }
        }
        if (!StringUtils.hasText(pipeline.getNotificationBotId())) issues.add(issue("PIPELINE", null, null, "notificationBotId", "REQUIRED", "Notification bot is required"));
        if (!StringUtils.hasText(pipeline.getDefaultFeishuChatId()) || pipeline.getDefaultFeishuChatId().length() > 100) issues.add(issue("PIPELINE", null, null, "defaultFeishuChatId", "REQUIRED", "Default Feishu chat is required"));
        if (pipeline.getFinalSummaryTemplate() == null || pipeline.getFinalSummaryTemplate().length() > 4000) issues.add(issue("PIPELINE", null, null, "finalSummaryTemplate", "SIZE_LIMIT", "Final summary template is required and limited to 4000 characters"));
        PipelineDefinition.InterventionPolicy policy = pipeline.getInterventionPolicy();
        if (policy == null || policy.getOnAgentNeedsUser() != PipelineEnums.ErrorPolicy.WAIT
                || policy.getOnRetriesExhausted() == PipelineEnums.ErrorPolicy.INHERIT) {
            issues.add(issue("PIPELINE", null, null, "interventionPolicy", "POLICY_INVALID", "Intervention policy is invalid"));
        } else {
            Set<PipelineEnums.InterventionAction> actions = policy.getAllowedActions() == null
                    ? Set.of() : new HashSet<>(policy.getAllowedActions());
            if (!actions.contains(PipelineEnums.InterventionAction.SUPPLY_INPUT) || !actions.contains(PipelineEnums.InterventionAction.CANCEL)
                    || (policy.getOnRetriesExhausted() == PipelineEnums.ErrorPolicy.WAIT && !actions.contains(PipelineEnums.InterventionAction.RETRY))) {
                issues.add(issue("PIPELINE", null, null, "interventionPolicy.allowedActions", "POLICY_INVALID", "Required intervention actions are missing"));
            }
        }
    }

    private void validateNode(PipelineNode node, Map<String, Object> configs, Map<String, AgentNodeConfig> agents,
                              Map<String, Set<String>> outputs, Set<String> stages, List<ValidationIssue> issues) {
        if (node == null || !StringUtils.hasText(node.getId()) || node.getType() == null || node.getConfig() == null) return;
        try {
            Object config = parser.parse(node);
            configs.put(node.getId(), config);
            switch (node.getType()) {
                case START -> {
                    StartNodeConfig start = (StartNodeConfig) config;
                    if (start.getInputSchema() == null) start.setInputSchema(new ArrayList<>());
                    duplicateFields(start.getInputSchema(), node.getId(), "inputSchema", issues);
                }
                case AGENT -> {
                    AgentNodeConfig agent = (AgentNodeConfig) config;
                    if (agent.getOutputSchema() == null) agent.setOutputSchema(new ArrayList<>());
                    if (agent.getArtifactOutputs() == null) agent.setArtifactOutputs(new ArrayList<>());
                    if (agent.getArtifactInputs() == null) agent.setArtifactInputs(new ArrayList<>());
                    agents.put(node.getId(), agent);
                    if (!StringUtils.hasText(agent.getStageCode()) || !ID.matcher(agent.getStageCode()).matches() || !stages.add(agent.getStageCode())) issues.add(issue("NODE", node.getId(), null, "stageCode", "STAGE_INVALID", "stageCode must be valid and unique"));
                    if (!StringUtils.hasText(agent.getAgentId())) issues.add(issue("NODE", node.getId(), null, "agentId", "REQUIRED", "agentId is required"));
                    if (!"1.1".equals(agent.getResultContractVersion())) issues.add(issue("NODE", node.getId(), null, "resultContractVersion", "CONTRACT_VERSION", "Agent result contract must be 1.1"));
                    if (agent.getAgentSnapshot() != null) issues.add(issue("NODE", node.getId(), null, "agentSnapshot", "SNAPSHOT_FORBIDDEN", "Draft definition cannot contain agentSnapshot"));
                    duplicateFields(agent.getOutputSchema(), node.getId(), "outputSchema", issues);
                    List<FieldSchema> schemas = agent.getOutputSchema() == null ? List.of() : agent.getOutputSchema();
                    outputs.put(node.getId(), schemas.stream().filter(Objects::nonNull).map(FieldSchema::effectiveName).filter(Objects::nonNull).collect(Collectors.toSet()));
                }
                case CONDITION -> validateCondition(node.getId(), (ConditionNodeConfig) config, issues);
                case NOTIFY -> {
                    String template = ((NotifyNodeConfig) config).getMessageTemplate();
                    if (!StringUtils.hasText(template) || template.length() > 2000) issues.add(issue("NODE", node.getId(), null, "messageTemplate", "SIZE_LIMIT", "Notification template is required and limited to 2000 characters"));
                }
                case END -> { }
            }
        } catch (PipelineException e) {
            issues.add(issue("NODE", node.getId(), null, "config", "CONFIG_INVALID", "Node configuration is invalid"));
        }
    }

    private void validateCondition(String nodeId, ConditionNodeConfig condition, List<ValidationIssue> issues) {
        if (condition.getLeft() == null || condition.getOperator() == null) issues.add(issue("NODE", nodeId, null, "condition", "REQUIRED", "Condition left and operator are required"));
        if (condition.getOperator() != null && condition.getOperator() != PipelineEnums.ConditionOperator.EMPTY
                && condition.getOperator() != PipelineEnums.ConditionOperator.NOT_EMPTY && condition.getRight() == null) {
            issues.add(issue("NODE", nodeId, null, "right", "REQUIRED", "Condition right value is required"));
        }
        if ((condition.getOperator() == PipelineEnums.ConditionOperator.EMPTY || condition.getOperator() == PipelineEnums.ConditionOperator.NOT_EMPTY)
                && condition.getRight() != null && condition.getRight().getValue() != null) {
            issues.add(issue("NODE", nodeId, null, "right", "FORBIDDEN", "EMPTY operators forbid a right value"));
        }
    }

    private void validateEdges(List<PipelineEdge> edges, Set<String> nodeIds, List<ValidationIssue> issues) {
        Set<String> triples = new HashSet<>();
        for (PipelineEdge edge : edges) {
            if (edge == null || !StringUtils.hasText(edge.getId())) continue;
            if (!nodeIds.contains(edge.getSource()) || !nodeIds.contains(edge.getTarget())) issues.add(issue("EDGE", null, edge.getId(), "target", "ENDPOINT_INVALID", "Edge endpoint does not exist"));
            if (Objects.equals(edge.getSource(), edge.getTarget())) issues.add(issue("EDGE", null, edge.getId(), "target", "SELF_LOOP", "Self loops are forbidden"));
            if (edge.getBranch() == null) issues.add(issue("EDGE", null, edge.getId(), "branch", "REQUIRED", "Edge branch is required"));
            String triple = edge.getSource() + "\u0000" + edge.getTarget() + "\u0000" + edge.getBranch();
            if (!triples.add(triple)) issues.add(issue("EDGE", null, edge.getId(), "target", "EDGE_DUPLICATE", "Duplicate edge is forbidden"));
        }
    }

    private void validateGraph(List<PipelineNode> nodes, List<PipelineEdge> edges, PipelineGraphAnalyzer.Analysis graph,
                               List<ValidationIssue> issues) {
        long starts = nodes.stream().filter(n -> n != null && n.getType() == PipelineEnums.NodeType.START).count();
        long ends = nodes.stream().filter(n -> n != null && n.getType() == PipelineEnums.NodeType.END).count();
        if (starts != 1) issues.add(issue("PIPELINE", null, null, "nodes", "START_COUNT", "Exactly one START is required"));
        if (ends < 1) issues.add(issue("PIPELINE", null, null, "nodes", "END_COUNT", "At least one END is required"));
        if (!graph.dag()) issues.add(issue("PIPELINE", null, null, "edges", "CYCLE", "Pipeline must be a DAG"));
        for (PipelineNode node : nodes) {
            if (node == null || node.getId() == null || node.getType() == null) continue;
            List<PipelineEdge> out = graph.outgoing().getOrDefault(node.getId(), List.of());
            List<PipelineEdge> in = graph.incoming().getOrDefault(node.getId(), List.of());
            if (!graph.reachable().contains(node.getId())) issues.add(issue("NODE", node.getId(), null, "id", "UNREACHABLE", "Node is not reachable from START"));
            if (!graph.canTerminate().contains(node.getId())) issues.add(issue("NODE", node.getId(), null, "id", "NO_TERMINATION", "Node cannot reach END"));
            switch (node.getType()) {
                case START -> checkDefaultOut(node, in, out, 0, issues);
                case AGENT, NOTIFY -> checkDefaultOut(node, in, out, 1, issues);
                case CONDITION -> {
                    if (in.isEmpty() || out.size() != 2 || out.stream().filter(e -> e.getBranch() == PipelineEnums.EdgeBranch.TRUE).count() != 1
                            || out.stream().filter(e -> e.getBranch() == PipelineEnums.EdgeBranch.FALSE).count() != 1) issues.add(issue("NODE", node.getId(), null, "edges", "CONDITION_EDGES", "CONDITION requires one TRUE and one FALSE edge"));
                }
                case END -> {
                    if (in.isEmpty() || !out.isEmpty()) issues.add(issue("NODE", node.getId(), null, "edges", "END_EDGES", "END requires input and forbids output"));
                }
            }
        }
    }

    private void checkDefaultOut(PipelineNode node, List<PipelineEdge> in, List<PipelineEdge> out, int minIn,
                                 List<ValidationIssue> issues) {
        if (in.size() < minIn || out.size() != 1 || out.get(0).getBranch() != PipelineEnums.EdgeBranch.DEFAULT) {
            issues.add(issue("NODE", node.getId(), null, "edges", "DEFAULT_EDGE", node.getType() + " requires exactly one DEFAULT output"));
        }
    }

    private void validateReferences(PipelineDefinition definition, Map<String, Object> configs,
                                    Map<String, AgentNodeConfig> agents, Map<String, Set<String>> agentOutputs,
                                    PipelineGraphAnalyzer.Analysis graph, List<ValidationIssue> issues) {
        Set<String> startFields = configs.values().stream().filter(StartNodeConfig.class::isInstance)
                .map(StartNodeConfig.class::cast).flatMap(c -> c.getInputSchema().stream())
                .map(FieldSchema::effectiveName).filter(Objects::nonNull).collect(Collectors.toSet());
        validateTemplate(definition.getPipeline() == null ? null : definition.getPipeline().getFinalSummaryTemplate(),
                null, startFields, agents, agentOutputs, graph, issues, "finalSummaryTemplate");
        for (PipelineNode node : definition.getNodes()) {
            Object config = configs.get(node.getId());
            if (config instanceof AgentNodeConfig agent) {
                if (agent.getInput() != null) agent.getInput().forEach((name, value) -> {
                    if (!name.matches("[A-Za-z_][A-Za-z0-9_]*")) issues.add(issue("NODE", node.getId(), null, "input", "FIELD_NAME", "Input mapping key is invalid"));
                    validateTemplate(value != null && value.isTextual() ? value.textValue() : null, node.getId(), startFields, agents, agentOutputs, graph, issues, "input." + name);
                });
                validateArtifactInputs(node.getId(), agent.getArtifactInputs(), agents, graph, issues);
            } else if (config instanceof NotifyNodeConfig notify) {
                validateTemplate(notify.getMessageTemplate(), node.getId(), startFields, agents, agentOutputs, graph, issues, "messageTemplate");
            } else if (config instanceof EndNodeConfig end) {
                validateTemplate(end.getCompletionSummary(), node.getId(), startFields, agents, agentOutputs, graph, issues, "completionSummary");
                if (end.getOutput() != null) end.getOutput().forEach((name, value) -> {
                    if (!name.matches("[A-Za-z_][A-Za-z0-9_]*")) issues.add(issue("NODE", node.getId(), null, "output", "FIELD_NAME", "Output mapping key is invalid"));
                    validateTemplate(value != null && value.isTextual() ? value.textValue() : null, node.getId(), startFields, agents, agentOutputs, graph, issues, "output." + name);
                });
                validateArtifactInputs(node.getId(), end.getArtifactSelection(), agents, graph, issues);
            } else if (config instanceof ConditionNodeConfig condition && condition.getLeft() != null) {
                String source = condition.getLeft().getNodeId();
                String field = condition.getLeft().getField();
                AgentNodeConfig sourceAgent = agents.get(source);
                FieldSchema sourceField = sourceAgent == null || sourceAgent.getOutputSchema() == null ? null
                        : sourceAgent.getOutputSchema().stream().filter(Objects::nonNull)
                        .filter(schema -> Objects.equals(schema.effectiveName(), field)).findFirst().orElse(null);
                boolean typeMatches = sourceField != null && sourceField.getType() == condition.getLeft().getValueType()
                        && (condition.getRight() == null || condition.getRight().getValueType() == condition.getLeft().getValueType());
                if (!dominates(source, node.getId(), graph) || !agentOutputs.getOrDefault(source, Set.of()).contains(field) || !typeMatches) issues.add(issue("NODE", node.getId(), null, "left", "REFERENCE_INVALID", "Condition output must come from a dominating Agent schema with the same type"));
            }
        }
    }

    private void validateArtifactInputs(String nodeId, List<ArtifactInput> inputs, Map<String, AgentNodeConfig> agents,
                                        PipelineGraphAnalyzer.Analysis graph, List<ValidationIssue> issues) {
        if (inputs == null) return;
        Set<String> names = new HashSet<>();
        for (ArtifactInput input : inputs) {
            if (input == null || !StringUtils.hasText(input.getName()) || !names.add(input.getName())
                    || input.getTypes() == null || input.getTypes().isEmpty() || input.getSelectionMode() == null
                    || !dominates(input.getSourceNodeId(), nodeId, graph)) {
                issues.add(issue("NODE", nodeId, null, "artifactInputs", "ARTIFACT_REFERENCE", "Artifact input is invalid or source does not dominate the node"));
                continue;
            }
            AgentNodeConfig source = agents.get(input.getSourceNodeId());
            if (source == null || (Boolean.TRUE.equals(input.getRequired()) && !source.getArtifactOutputs().containsAll(input.getTypes()))) issues.add(issue("NODE", nodeId, null, "artifactInputs", "ARTIFACT_TYPE", "Required artifact type is not declared by the source"));
        }
    }

    private void validateTemplate(String template, String nodeId, Set<String> startFields,
                                  Map<String, AgentNodeConfig> agents, Map<String, Set<String>> outputs,
                                  PipelineGraphAnalyzer.Analysis graph, List<ValidationIssue> issues, String field) {
        if (template == null) return;
        Matcher matcher = TEMPLATE.matcher(template);
        StringBuilder residue = new StringBuilder(template);
        while (matcher.find()) {
            String expression = matcher.group(1).trim();
            Matcher run = RUN_INPUT.matcher(expression);
            Matcher output = NODE_OUTPUT.matcher(expression);
            Matcher summary = NODE_SUMMARY.matcher(expression);
            boolean valid = run.matches() && startFields.contains(run.group(1));
            if (output.matches()) valid = agents.containsKey(output.group(1)) && outputs.getOrDefault(output.group(1), Set.of()).contains(output.group(2)) && dominates(output.group(1), nodeId, graph);
            if (summary.matches()) valid = agents.containsKey(summary.group(1)) && dominates(summary.group(1), nodeId, graph);
            if (nodeId == null && output.matches()) valid = valid && dominatesEveryEnd(output.group(1), graph);
            if (nodeId == null && summary.matches()) valid = valid && dominatesEveryEnd(summary.group(1), graph);
            if (!valid) issues.add(issue(nodeId == null ? "PIPELINE" : "NODE", nodeId, null, field, "TEMPLATE_REFERENCE", "Template reference is invalid"));
        }
        String unmatched = TEMPLATE.matcher(residue).replaceAll("");
        if (unmatched.contains("{{") || unmatched.contains("}}")) issues.add(issue(nodeId == null ? "PIPELINE" : "NODE", nodeId, null, field, "TEMPLATE_SYNTAX", "Template syntax is invalid"));
    }

    private boolean dominates(String source, String target, PipelineGraphAnalyzer.Analysis graph) {
        return source != null && (target == null || graph.dominators().getOrDefault(target, Set.of()).contains(source));
    }

    private boolean dominatesEveryEnd(String source, PipelineGraphAnalyzer.Analysis graph) {
        return graph.nodes().values().stream().filter(node -> node.getType() == PipelineEnums.NodeType.END)
                .allMatch(end -> graph.dominators().getOrDefault(end.getId(), Set.of()).contains(source));
    }

    private void validateUi(PipelineUiModel ui, Set<String> nodeIds, List<ValidationIssue> issues) {
        if (ui == null || codec.write(ui).getBytes(StandardCharsets.UTF_8).length > 512 * 1024) {
            issues.add(issue("PIPELINE", null, null, "ui", "SIZE_LIMIT", "UI is required and limited to 512 KiB"));
            return;
        }
        if (ui.getNodes() == null) ui.setNodes(new ArrayList<>());
        Set<String> uiIds = ui.getNodes().stream().filter(Objects::nonNull).map(PipelineUiModel.UiNode::getId).collect(Collectors.toSet());
        if (!uiIds.equals(nodeIds)) issues.add(issue("PIPELINE", null, null, "ui.nodes", "UI_NODE_MISMATCH", "UI node IDs must match definition nodes"));
        for (PipelineUiModel.UiNode node : ui.getNodes()) if (node == null || !finite(node.getX()) || !finite(node.getY())) issues.add(issue("PIPELINE", null, null, "ui.nodes", "UI_COORDINATE", "UI coordinates must be finite"));
        if (ui.getViewport() == null || !finite(ui.getViewport().getX()) || !finite(ui.getViewport().getY())
                || !finite(ui.getViewport().getZoom()) || ui.getViewport().getZoom() < 0.25 || ui.getViewport().getZoom() > 2) issues.add(issue("PIPELINE", null, null, "ui.viewport.zoom", "UI_ZOOM", "UI viewport must be finite and zoom must be between 0.25 and 2"));
    }

    private boolean finite(Double value) {
        return value != null && Double.isFinite(value);
    }

    private boolean isAuthorizationFailure(RuntimeException exception) {
        String message = exception.getMessage();
        return message != null && (message.contains("not authorized") || message.contains("Authorized JEECG user"));
    }

    private Set<String> duplicateCheckedIds(List<String> ids, String scope, List<ValidationIssue> issues) {
        Set<String> unique = new LinkedHashSet<>();
        for (String id : ids) {
            if (!StringUtils.hasText(id) || !unique.add(id)) issues.add(issue(scope, "NODE".equals(scope) ? id : null, "EDGE".equals(scope) ? id : null, "id", "ID_DUPLICATE", "ID is required and unique"));
        }
        return unique;
    }

    private void duplicateFields(List<FieldSchema> fields, String nodeId, String fieldName, List<ValidationIssue> issues) {
        Set<String> names = new HashSet<>();
        for (FieldSchema field : fields == null ? List.<FieldSchema>of() : fields) {
            String name = field == null ? null : field.effectiveName();
            if (!StringUtils.hasText(name) || !name.matches("[A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)*") || !names.add(name) || field.getType() == null) issues.add(issue("NODE", nodeId, null, fieldName, "FIELD_SCHEMA", "Field schema is invalid or duplicated"));
        }
    }

    private ValidationIssue issue(String scope, String nodeId, String edgeId, String field, String code, String message) {
        return new ValidationIssue(scope, nodeId, edgeId, field, code, message);
    }
}
