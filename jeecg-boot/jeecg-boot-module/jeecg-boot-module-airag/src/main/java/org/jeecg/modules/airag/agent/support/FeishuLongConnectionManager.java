package org.jeecg.modules.airag.agent.support;

import com.lark.oapi.event.EventDispatcher;
import com.lark.oapi.event.cardcallback.P2CardActionTriggerHandler;
import com.lark.oapi.event.cardcallback.model.P2CardActionTrigger;
import com.lark.oapi.event.cardcallback.model.P2CardActionTriggerResponse;
import com.lark.oapi.service.im.ImService;
import com.lark.oapi.service.im.v1.model.P2MessageReceiveV1;
import com.lark.oapi.ws.Client;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.modules.airag.agent.config.AiAgentProperties;
import org.jeecg.modules.airag.agent.entity.AiFeishuBot;
import org.jeecg.modules.airag.agent.mapper.AiFeishuBotMapper;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Slf4j
@Component
public class FeishuLongConnectionManager {
    public static final String CONNECTION_MODE = "LONG_CONNECTION";

    private static final long READY_TIMEOUT_MS = 15_000L;

    private final AiFeishuBotMapper botMapper;
    private final SecretCipherService secretCipherService;
    private final FeishuMessageEventReceiver eventReceiver;
    private final FeishuCardActionEventReceiver cardReceiver;
    private final AiAgentProperties properties;
    private final ExecutorService executor = Executors.newCachedThreadPool(
            new CustomizableThreadFactory("feishu-long-connection-"));
    private final Map<String, ConnectionHandle> connections = new ConcurrentHashMap<>();
    private final Map<String, String> statuses = new ConcurrentHashMap<>();

    @Autowired
    public FeishuLongConnectionManager(AiFeishuBotMapper botMapper,
                                       SecretCipherService secretCipherService,
                                       FeishuMessageEventReceiver eventReceiver,
                                       FeishuCardActionEventReceiver cardReceiver,
                                       AiAgentProperties properties) {
        this.botMapper = botMapper;
        this.secretCipherService = secretCipherService;
        this.eventReceiver = eventReceiver;
        this.cardReceiver = cardReceiver;
        this.properties = properties;
    }

    public FeishuLongConnectionManager(AiFeishuBotMapper botMapper,
                                       SecretCipherService secretCipherService,
                                       FeishuMessageEventReceiver eventReceiver,
                                       AiAgentProperties properties) {
        this(botMapper, secretCipherService, eventReceiver, null, properties);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void restoreEnabledConnections() {
        // Application startup is not associated with one tenant, so restore every enabled tenant Bot explicitly.
        botMapper.selectSystemEnabled().forEach(this::start);
    }

    public synchronized void start(AiFeishuBot bot) {
        if (bot == null || bot.getId() == null || !Boolean.TRUE.equals(bot.getEnabled())) {
            return;
        }
        stopInternal(bot.getId(), false);
        try {
            String appSecret = secretCipherService.decrypt(bot.getAppSecretCipher());
            EventDispatcher.Builder dispatcherBuilder = EventDispatcher.newBuilder("", "")
                    .onP2MessageReceiveV1(new ImService.P2MessageReceiveV1Handler() {
                        @Override
                        public void handle(P2MessageReceiveV1 event) {
                            eventReceiver.accept(bot, event);
                        }
                    });
            if (cardReceiver != null) dispatcherBuilder.onP2CardActionTrigger(new P2CardActionTriggerHandler() {
                        @Override
                        public P2CardActionTriggerResponse handle(P2CardActionTrigger event) {
                            return cardReceiver.accept(bot, event);
                        }
                    });
            EventDispatcher dispatcher = dispatcherBuilder.build();
            Client client = createClient(bot.getAppId(), appSecret, dispatcher);
            ConnectionHandle handle = new ConnectionHandle(client);
            connections.put(bot.getId(), handle);
            statuses.put(bot.getId(), "STARTING");

            handle.startFuture = executor.submit(() -> runClient(bot, handle));
            executor.execute(() -> awaitReady(bot, handle));
        } catch (Exception e) {
            statuses.put(bot.getId(), "FAILED");
            log.error("Failed to initialize Feishu long connection: botKey={}, reason={}",
                    bot.getBotKey(), e.getClass().getSimpleName());
        }
    }

    public synchronized void stop(String botId) {
        stopInternal(botId, true);
    }

    public String getStatus(String botId) {
        return statuses.getOrDefault(botId, "DISCONNECTED");
    }

    protected Client createClient(String appId, String appSecret, EventDispatcher dispatcher) {
        Client.Builder builder = new Client.Builder(appId, appSecret)
                .eventHandler(dispatcher)
                .autoReconnect(true)
                .onReconnecting(() -> log.info("Feishu long connection is reconnecting"))
                .onReconnected(() -> log.info("Feishu long connection reconnected"));
        if (properties.getFeishuApiBaseUrl() != null && !properties.getFeishuApiBaseUrl().isBlank()) {
            builder.domain(properties.getFeishuApiBaseUrl());
        }
        return builder.build();
    }

    private void runClient(AiFeishuBot bot, ConnectionHandle handle) {
        try {
            handle.client.start();
            if (!handle.stopping) {
                statuses.put(bot.getId(), "FAILED");
                log.warn("Feishu long connection stopped unexpectedly: botKey={}", bot.getBotKey());
            }
        } catch (Exception e) {
            if (!handle.stopping) {
                statuses.put(bot.getId(), "FAILED");
                log.error("Feishu long connection failed: botKey={}, reason={}",
                        bot.getBotKey(), e.getClass().getSimpleName());
            }
        } finally {
            connections.remove(bot.getId(), handle);
        }
    }

    private void awaitReady(AiFeishuBot bot, ConnectionHandle handle) {
        try {
            handle.client.awaitReady(READY_TIMEOUT_MS);
            if (connections.get(bot.getId()) == handle && !handle.stopping) {
                statuses.put(bot.getId(), "CONNECTED");
                log.info("Feishu long connection established: botKey={}", bot.getBotKey());
            }
        } catch (Exception e) {
            if (!handle.stopping && connections.remove(bot.getId(), handle)) {
                statuses.put(bot.getId(), "FAILED");
                handle.client.close();
                log.error("Feishu long connection handshake failed: botKey={}, reason={}",
                        bot.getBotKey(), e.getClass().getSimpleName());
            }
        }
    }

    private void stopInternal(String botId, boolean updateStatus) {
        ConnectionHandle handle = connections.remove(botId);
        if (handle != null) {
            handle.stopping = true;
            handle.client.close();
            if (handle.startFuture != null) {
                handle.startFuture.cancel(true);
            }
        }
        if (updateStatus) {
            statuses.put(botId, "DISCONNECTED");
        }
    }

    @PreDestroy
    public void destroy() {
        for (String botId : new ArrayList<>(connections.keySet())) {
            stopInternal(botId, true);
        }
        executor.shutdownNow();
    }

    private static final class ConnectionHandle {
        private final Client client;
        private volatile Future<?> startFuture;
        private volatile boolean stopping;

        private ConnectionHandle(Client client) {
            this.client = client;
        }
    }
}
