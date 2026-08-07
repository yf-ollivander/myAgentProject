package org.jeecg.modules.airag.execution;

import org.junit.jupiter.api.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RunEngineRedisIntegrationTest {
    @Test void redis5GroupPendingClaimAndAckAreReal()throws Exception{
        String host=System.getenv("RUN_TEST_REDIS_HOST");Assumptions.assumeTrue(host!=null&&!host.isBlank(),"RUN_TEST_REDIS_HOST is not configured");int port=Integer.parseInt(Optional.ofNullable(System.getenv("RUN_TEST_REDIS_PORT")).orElse("6379"));
        try(Resp redis=new Resp(host,port)){assertTrue(String.valueOf(redis.command("INFO","server")).matches("(?s).*redis_version:5\\..*"),"Redis 5.x is required");String key="ai:test:tasks:"+UUID.randomUUID(),group="test-group";redis.command("DEL",key);assertEquals("OK",redis.command("XGROUP","CREATE",key,group,"0-0","MKSTREAM"));String id=(String)redis.command("XADD",key,"*","eventId","e1","runId","r1","nodeRunId","n1","dispatchVersion","0","traceId","t1");Object read=redis.command("XREADGROUP","GROUP",group,"c1","COUNT","1","STREAMS",key,">");assertNotNull(read);Object pending=redis.command("XPENDING",key,group);assertTrue(((List<?>)pending).get(0) instanceof Long);Object claimed=redis.command("XCLAIM",key,group,"c2","0",id);assertFalse(((List<?>)claimed).isEmpty());assertEquals(1L,redis.command("XACK",key,group,id));redis.command("DEL",key);}
    }
    private static final class Resp implements Closeable {private final Socket socket;private final InputStream in;private final OutputStream out;Resp(String host,int port)throws IOException{socket=new Socket(host,port);socket.setSoTimeout(5000);in=socket.getInputStream();out=socket.getOutputStream();}Object command(String...args)throws IOException{StringBuilder b=new StringBuilder("*").append(args.length).append("\r\n");for(String arg:args){byte[] bytes=arg.getBytes(StandardCharsets.UTF_8);b.append('$').append(bytes.length).append("\r\n").append(arg).append("\r\n");}out.write(b.toString().getBytes(StandardCharsets.UTF_8));out.flush();return read();}private Object read()throws IOException{int type=in.read();if(type<0)throw new EOFException();String line=line();return switch(type){case '+'->line;case '-'->throw new IOException(line);case ':'->Long.parseLong(line);case '$'->{int length=Integer.parseInt(line);if(length<0)yield null;byte[] value=in.readNBytes(length);in.readNBytes(2);yield new String(value,StandardCharsets.UTF_8);}case '*'->{int count=Integer.parseInt(line);if(count<0)yield null;List<Object> values=new ArrayList<>();for(int i=0;i<count;i++)values.add(read());yield values;}default->throw new IOException("Unknown RESP type");};}private String line()throws IOException{ByteArrayOutputStream b=new ByteArrayOutputStream();int previous=-1,current;while((current=in.read())>=0){if(previous=='\r'&&current=='\n')break;if(previous>=0)b.write(previous);previous=current;}return b.toString(StandardCharsets.UTF_8);}public void close()throws IOException{socket.close();}}
}
