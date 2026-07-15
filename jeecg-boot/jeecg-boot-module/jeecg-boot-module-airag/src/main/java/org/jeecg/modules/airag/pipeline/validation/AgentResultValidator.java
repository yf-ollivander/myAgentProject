package org.jeecg.modules.airag.pipeline.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jeecg.modules.airag.pipeline.contract.AgentResultContract;
import org.jeecg.modules.airag.pipeline.contract.ArtifactDescriptor;
import org.jeecg.modules.airag.pipeline.contract.PipelineEnums;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component
public class AgentResultValidator {
    private final ObjectMapper mapper = new ObjectMapper();

    public List<String> validate(AgentResultContract result) {
        List<String> errors = new ArrayList<>();
        if (result == null || !"1.1".equals(result.getContractVersion()) || result.getStatus() == null) {
            return List.of("contractVersion 1.1 and status are required");
        }
        boolean needsInput = result.getStatus() == PipelineEnums.AgentResultStatus.NEEDS_INPUT;
        if (!Boolean.valueOf(needsInput).equals(result.getNeedsUser())) errors.add("needsUser must match status");
        if (needsInput && !StringUtils.hasText(result.getUserPrompt())) errors.add("NEEDS_INPUT requires userPrompt");
        if (!needsInput && StringUtils.hasText(result.getUserPrompt())) errors.add("userPrompt is allowed only for NEEDS_INPUT");
        if (result.getStatus() != PipelineEnums.AgentResultStatus.FAILED && Boolean.TRUE.equals(result.getRetryable())) {
            errors.add("retryable is allowed only for FAILED");
        }
        if (result.getStatus() != PipelineEnums.AgentResultStatus.FAILED
                && (StringUtils.hasText(result.getErrorCode()) || StringUtils.hasText(result.getErrorMessage()))) {
            errors.add("error fields are allowed only for FAILED");
        }
        if (result.getStatus() == PipelineEnums.AgentResultStatus.FAILED
                && (!StringUtils.hasText(result.getErrorCode()) || !StringUtils.hasText(result.getErrorMessage()))) {
            errors.add("FAILED requires errorCode and errorMessage");
        }
        if (result.getSummary() != null && result.getSummary().length() > 1000) errors.add("summary exceeds 1000 characters");
        if (result.getUserPrompt() != null && result.getUserPrompt().length() > 1000) errors.add("userPrompt exceeds 1000 characters");
        if (result.getArtifacts() != null && result.getArtifacts().size() > 20) errors.add("artifacts exceeds 20 items");
        if (result.getArtifacts() != null) result.getArtifacts().forEach(item -> validateArtifact(item, errors));
        if (mapper.valueToTree(result).toString().getBytes(StandardCharsets.UTF_8).length > 1024 * 1024) {
            errors.add("complete Agent result exceeds 1 MiB");
        }
        return errors;
    }

    private void validateArtifact(ArtifactDescriptor artifact, List<String> errors) {
        if (artifact == null || artifact.getType() == null || !StringUtils.hasText(artifact.getName())) {
            errors.add("artifact type and name are required");
            return;
        }
        if (artifact.getName().length() > 200) errors.add("artifact name exceeds 200 characters");
        if (artifact.getUri() != null && artifact.getUri().length() > 2000) errors.add("artifact URI exceeds 2000 characters");
        boolean inline = artifact.getType() == PipelineEnums.ArtifactType.TEXT
                || artifact.getType() == PipelineEnums.ArtifactType.JSON;
        if (inline && (artifact.getContent() == null || StringUtils.hasText(artifact.getUri()))) {
            errors.add("TEXT/JSON artifacts require content and forbid uri");
        }
        if (inline && artifact.getContent() != null
                && artifact.getContent().toString().getBytes(StandardCharsets.UTF_8).length > 64 * 1024) {
            errors.add("inline artifact content exceeds 64 KiB");
        }
        if (!inline) {
            try {
                URI uri = URI.create(artifact.getUri());
                if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null || artifact.getContent() != null) {
                    errors.add("external artifacts require a safe https URI and forbid content");
                }
            } catch (Exception e) {
                errors.add("external artifact URI is invalid");
            }
        }
        if (StringUtils.hasText(artifact.getChecksum()) && !artifact.getChecksum().matches("(?i)[0-9a-f]{64}")) {
            errors.add("artifact checksum must be SHA-256");
        }
        if (artifact.getMetadata() != null
                && mapper.valueToTree(artifact.getMetadata()).toString().getBytes(StandardCharsets.UTF_8).length > 16 * 1024) {
            errors.add("artifact metadata exceeds 16 KiB");
        }
    }
}
