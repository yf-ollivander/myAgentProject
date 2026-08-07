package org.jeecg.modules.airag.collaboration.notification;

import org.jeecg.modules.airag.collaboration.config.CollaborationProperties;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class FeishuNotificationGroupManager {
    private final StringRedisTemplate redis; private final CollaborationProperties properties;
    private final AtomicBoolean ready = new AtomicBoolean(); private volatile long nextAttempt;
    public FeishuNotificationGroupManager(StringRedisTemplate redis, CollaborationProperties properties) {
        this.redis=redis;this.properties=properties;
    }
    public boolean ensureGroup(){if(!properties.isEnabled())return false;if(ready.get())return true;if(System.currentTimeMillis()<nextAttempt)return false;
        synchronized(this){if(ready.get())return true;try{redis.execute((RedisCallback<Object>)c->c.execute("XGROUP",b("CREATE"),b(properties.getNotificationStream()),b(properties.getNotificationGroup()),b("0-0"),b("MKSTREAM")));ready.set(true);return true;}catch(Exception e){if(contains(e,"BUSYGROUP")){ready.set(true);return true;}nextAttempt=System.currentTimeMillis()+5000;return false;}}}
    public void failed(Throwable failure){if(contains(failure,"NOGROUP")){ready.set(false);nextAttempt=0;}}
    private boolean contains(Throwable e,String marker){for(Throwable c=e;c!=null;c=c.getCause())if(c.getMessage()!=null&&c.getMessage().toUpperCase(Locale.ROOT).contains(marker))return true;return false;}
    private byte[] b(String value){return value.getBytes(StandardCharsets.UTF_8);}
}
