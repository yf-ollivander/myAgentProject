package org.jeecg.modules.airag.agent;

import com.lark.oapi.event.EventDispatcher;
import com.lark.oapi.ws.Client;
import org.jeecg.modules.airag.agent.config.AiAgentProperties;
import org.jeecg.modules.airag.agent.entity.AiFeishuBot;
import org.jeecg.modules.airag.agent.mapper.AiFeishuBotMapper;
import org.jeecg.modules.airag.agent.support.FeishuLongConnectionManager;
import org.jeecg.modules.airag.agent.support.FeishuMessageEventReceiver;
import org.jeecg.modules.airag.agent.support.SecretCipherService;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FeishuLongConnectionManagerTest {
    @Test
    void startsAndStopsManagedSdkClient() {
        assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {
            AiFeishuBotMapper mapper = mock(AiFeishuBotMapper.class);
            SecretCipherService cipher = mock(SecretCipherService.class);
            FeishuMessageEventReceiver receiver = mock(FeishuMessageEventReceiver.class);
            Client client = mock(Client.class);
            CountDownLatch closed = new CountDownLatch(1);
            when(cipher.decrypt("encrypted-secret")).thenReturn("plain-secret");
            doAnswer(invocation -> {
                closed.await();
                return null;
            }).when(client).start();
            doAnswer(invocation -> {
                closed.countDown();
                return null;
            }).when(client).close();

            FeishuLongConnectionManager manager = new FeishuLongConnectionManager(
                    mapper, cipher, receiver, new AiAgentProperties()) {
                @Override
                protected Client createClient(String appId, String appSecret, EventDispatcher dispatcher) {
                    assertEquals("cli_test", appId);
                    assertEquals("plain-secret", appSecret);
                    return client;
                }
            };
            AiFeishuBot bot = new AiFeishuBot();
            bot.setId("bot-id");
            bot.setBotKey("testBot");
            bot.setAppId("cli_test");
            bot.setAppSecretCipher("encrypted-secret");
            bot.setEnabled(true);

            try {
                manager.start(bot);
                waitForStatus(manager, "CONNECTED");
                verify(client).start();

                manager.stop(bot.getId());
                assertEquals("DISCONNECTED", manager.getStatus(bot.getId()));
                verify(client).close();
            } finally {
                manager.destroy();
            }
        });
    }

    private static void waitForStatus(FeishuLongConnectionManager manager, String expected)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 2_000L;
        while (System.currentTimeMillis() < deadline) {
            if (expected.equals(manager.getStatus("bot-id"))) {
                return;
            }
            Thread.sleep(20L);
        }
        assertEquals(expected, manager.getStatus("bot-id"));
    }
}
