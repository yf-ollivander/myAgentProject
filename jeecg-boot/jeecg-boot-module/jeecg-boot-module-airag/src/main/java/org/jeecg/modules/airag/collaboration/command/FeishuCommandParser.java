package org.jeecg.modules.airag.collaboration.command;

import org.jeecg.modules.airag.collaboration.contract.CollaborationEnums.CommandType;
import org.jeecg.modules.airag.collaboration.dto.CollaborationDtos.Command;
import org.springframework.stereotype.Component;
import java.text.Normalizer;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class FeishuCommandParser {
    private static final Pattern PIPELINE = Pattern.compile("^使用【([^】]{1,50})】[，,]\\s*(.{1,10000})$", Pattern.DOTALL);
    private static final Pattern AGENT = Pattern.compile("^角色【([A-Za-z][A-Za-z0-9_-]{0,63})】[，,]\\s*(.{1,10000})$", Pattern.DOTALL);
    private static final Pattern BIND = Pattern.compile("^绑定【([A-Z2-7]{26})】$");

    public Command parse(String rawText, String chatType, List<String> mentionKeys) {
        String text = rawText == null ? "" : rawText;
        if (mentionKeys != null) for (String key : mentionKeys) text = text.replace(key, "");
        text = Normalizer.normalize(text.trim(), Normalizer.Form.NFKC);
        if (isGroup(chatType) && (mentionKeys == null || mentionKeys.isEmpty())) return invalid();
        if ("帮助".equals(text)) return new Command(CommandType.HELP.name(), null, null);
        Matcher bind = BIND.matcher(text); if (bind.matches()) return new Command(CommandType.BIND.name(), bind.group(1), null);
        Matcher pipeline = PIPELINE.matcher(text); if (pipeline.matches() && codePoints(pipeline.group(2)) <= 10000)
            return new Command(CommandType.PIPELINE.name(), pipeline.group(1).trim(), pipeline.group(2).trim());
        Matcher agent = AGENT.matcher(text); if (agent.matches() && codePoints(agent.group(2)) <= 10000)
            return new Command(CommandType.AGENT.name(), agent.group(1), agent.group(2).trim());
        return invalid();
    }

    public String extractText(String content) {
        if (content == null) return "";
        try {
            com.fasterxml.jackson.databind.JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(content);
            return node.has("text") ? node.get("text").asText() : content;
        } catch (Exception ignored) { return content; }
    }

    private boolean isGroup(String chatType) { return chatType != null && !"p2p".equalsIgnoreCase(chatType) && !"private".equalsIgnoreCase(chatType); }
    private int codePoints(String value) { return value.codePointCount(0, value.length()); }
    private Command invalid() { return new Command(CommandType.INVALID.name(), null, null); }
}
