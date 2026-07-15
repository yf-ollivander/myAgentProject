package org.jeecg.modules.airag.pipeline.validation;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.jeecg.modules.airag.pipeline.contract.AgentNodeConfig;
import org.jeecg.modules.airag.pipeline.contract.ArtifactInput;
import org.jeecg.modules.airag.pipeline.contract.EndNodeConfig;
import org.jeecg.modules.airag.pipeline.contract.PipelineDefinition;
import org.jeecg.modules.airag.pipeline.contract.PipelineDefinitionCodec;
import org.jeecg.modules.airag.pipeline.contract.PipelineNode;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.LinkedHashSet;

@Component
public class PipelineDefinitionNormalizer {
    private final PipelineDefinitionCodec codec;
    private final PipelineNodeConfigParser parser;
    private final ObjectMapper canonicalMapper;

    public PipelineDefinitionNormalizer(PipelineDefinitionCodec codec, PipelineNodeConfigParser parser) {
        this.codec = codec;
        this.parser = parser;
        this.canonicalMapper = codec.mapper().copy()
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    public NormalizedDefinition normalize(PipelineDefinition source) {
        PipelineDefinition copy = codec.readDefinition(codec.write(source));
        copy.getNodes().sort(Comparator.comparing(PipelineNode::getId));
        copy.getEdges().sort(Comparator.comparing(org.jeecg.modules.airag.pipeline.contract.PipelineEdge::getSource)
                .thenComparing(edge -> edge.getBranch().name()).thenComparing(org.jeecg.modules.airag.pipeline.contract.PipelineEdge::getTarget)
                .thenComparing(org.jeecg.modules.airag.pipeline.contract.PipelineEdge::getId));
        if (copy.getPipeline().getTriggerAliases() != null) {
            copy.getPipeline().setTriggerAliases(copy.getPipeline().getTriggerAliases().stream().distinct().sorted().toList());
        }
        if (copy.getPipeline().getInterventionPolicy() != null
                && copy.getPipeline().getInterventionPolicy().getAllowedActions() != null) {
            copy.getPipeline().getInterventionPolicy().setAllowedActions(
                    copy.getPipeline().getInterventionPolicy().getAllowedActions().stream().distinct().sorted().toList());
        }
        for (PipelineNode node : copy.getNodes()) {
            if (node.getType() == org.jeecg.modules.airag.pipeline.contract.PipelineEnums.NodeType.AGENT) {
                AgentNodeConfig config = parser.parse(node, AgentNodeConfig.class);
                config.setArtifactOutputs(new LinkedHashSet<>(config.getArtifactOutputs() == null
                        ? java.util.List.of() : config.getArtifactOutputs()).stream()
                        .sorted().toList());
                sortArtifactTypes(config.getArtifactInputs());
                node.setConfig(codec.valueToTree(config));
            } else if (node.getType() == org.jeecg.modules.airag.pipeline.contract.PipelineEnums.NodeType.END) {
                EndNodeConfig config = parser.parse(node, EndNodeConfig.class);
                sortArtifactTypes(config.getArtifactSelection());
                node.setConfig(codec.valueToTree(config));
            }
        }
        try {
            String json = canonicalMapper.writeValueAsString(copy);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(json.getBytes(StandardCharsets.UTF_8));
            StringBuilder hash = new StringBuilder(64);
            for (byte value : digest) hash.append(String.format("%02x", value));
            return new NormalizedDefinition(copy, json, hash.toString());
        } catch (Exception e) {
            throw PipelineException.of(PipelineErrorCode.PIPELINE_DEFINITION_INVALID,
                    "Pipeline normalization failed", e.getMessage());
        }
    }

    private void sortArtifactTypes(java.util.List<ArtifactInput> inputs) {
        if (inputs == null) return;
        for (ArtifactInput input : inputs) {
            if (input != null && input.getTypes() != null) {
                input.setTypes(input.getTypes().stream().distinct().sorted().toList());
            }
        }
    }

    public record NormalizedDefinition(PipelineDefinition definition, String json, String hash) {
    }
}
