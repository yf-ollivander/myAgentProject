package org.jeecg.modules.airag.collaboration;

import org.jeecg.modules.airag.collaboration.command.FeishuCommandParser;
import org.jeecg.modules.airag.collaboration.contract.CollaborationEnums.CommandType;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class FeishuCommandParserTest {
    private final FeishuCommandParser parser = new FeishuCommandParser();

    @Test
    void parsesNormalizedMultilinePipelineCommandAfterStructuredMentionRemoval() {
        var command = parser.parse("@_user_1 使用【交付】，第一行\n第二行", "group", List.of("@_user_1"));
        assertEquals(CommandType.PIPELINE.name(), command.type());
        assertEquals("交付", command.key());
        assertEquals("第一行\n第二行", command.task());
    }

    @Test
    void rejectsGroupTextWithoutMentionAndFuzzyAgentName() {
        assertEquals(CommandType.INVALID.name(), parser.parse("使用【交付】，任务", "group", List.of()).type());
        assertEquals(CommandType.INVALID.name(), parser.parse("请让开发帮我做", "p2p", List.of()).type());
    }

    @Test
    void parsesBindingCodeAndAsciiComma() {
        assertEquals(CommandType.BIND.name(), parser.parse("绑定【ABCDEFGHIJKLMNOPQRSTUVWXYZ】", "p2p", List.of()).type());
        assertEquals(CommandType.AGENT.name(), parser.parse("角色【developer】, task", "p2p", List.of()).type());
    }
}
