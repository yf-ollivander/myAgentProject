package org.jeecg.modules.airag.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_feishu_bot")
public class AiFeishuBot extends AbstractAiConfigEntity {
    private String botKey;
    private String name;
    private String appId;
    private String appSecretCipher;
    private String verificationTokenCipher;
    private String encryptKeyCipher;
    private String defaultChatId;
    private String entryMode;
    private Boolean commandEnabled;
    private Boolean enabled;
    private String lastTestStatus;
    private String lastTestMessage;
    private Date lastTestTime;
    private Long lastTestDurationMs;
}
