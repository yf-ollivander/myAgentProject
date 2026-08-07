package org.jeecg.modules.airag.execution;

import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RunEngineMysqlIntegrationTest {
    @Test void migrationTransactionAndOpenInterventionUniqueKeyAreReal()throws Exception{
        String url=System.getenv("RUN_TEST_MYSQL_URL");Assumptions.assumeTrue(url!=null&&!url.isBlank(),"RUN_TEST_MYSQL_URL is not configured");
        assertTrue(url.matches("(?i).*/agent_run_test(?:\\?.*)?$"),"Integration DDL is restricted to agent_run_test");
        try(Connection connection=DriverManager.getConnection(url,System.getenv("RUN_TEST_MYSQL_USER"),System.getenv("RUN_TEST_MYSQL_PASSWORD"))){
            assertSupportedVersion(connection);dropRunTables(connection);
            String sql=Files.readString(Path.of("../../jeecg-module-system/jeecg-system-start/src/main/resources/flyway/sql/mysql/V3.9.3_5__multi_agent_run_engine.sql"));
            String ddl=sql.substring(0,sql.indexOf("INSERT INTO `sys_permission`"));for(String statement:ddl.split(";"))if(!statement.isBlank())try(Statement s=connection.createStatement()){s.execute(statement);}
            assertEquals(7,tableCount(connection));connection.setAutoCommit(false);String runId=UUID.randomUUID().toString();try(PreparedStatement p=connection.prepareStatement("INSERT INTO ai_run(id,create_time,tenant_id,del_flag,request_id,request_payload_hash,run_type,source,initiator_username,definition_hash,definition_json,input_json,workspace_context_json,status,root_run_id) VALUES(?,NOW(3),'0',0,?,'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa','PIPELINE','JEECG','tester','bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb','{}','{}','{}','CREATED',?)")){p.setString(1,runId);p.setString(2,"rollback-"+runId);p.setString(3,runId);p.executeUpdate();}connection.rollback();assertEquals(0,count(connection,"SELECT COUNT(*) FROM ai_run WHERE id='"+runId+"'"));connection.setAutoCommit(true);
            String nodeRunId=UUID.randomUUID().toString();insertIntervention(connection,UUID.randomUUID().toString(),nodeRunId);assertThrows(SQLException.class,()->insertIntervention(connection,UUID.randomUUID().toString(),nodeRunId));
        }
    }
    private int tableCount(Connection c)throws SQLException{try(PreparedStatement p=c.prepareStatement("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('ai_run','ai_node_run','ai_run_event','ai_outbox','ai_artifact','ai_run_intervention','ai_run_dependency')");ResultSet r=p.executeQuery()){r.next();return r.getInt(1);}}
    private int count(Connection c,String sql)throws SQLException{try(Statement s=c.createStatement();ResultSet r=s.executeQuery(sql)){r.next();return r.getInt(1);}}
    private void assertSupportedVersion(Connection c)throws SQLException{try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT VERSION()")){r.next();String[] parts=r.getString(1).split("[.-]");int major=Integer.parseInt(parts[0]),minor=Integer.parseInt(parts[1]);assertTrue(major>5||(major==5&&minor>=7),"MySQL 5.7+ is required");}}
    private void dropRunTables(Connection c)throws SQLException{String[] tables={"ai_run_dependency","ai_run_intervention","ai_artifact","ai_outbox","ai_run_event","ai_node_run","ai_run"};try(Statement s=c.createStatement()){for(String table:tables)s.execute("DROP TABLE IF EXISTS "+table);}}
    private void insertIntervention(Connection c,String id,String nodeRunId)throws SQLException{try(PreparedStatement p=c.prepareStatement("INSERT INTO ai_run_intervention(id,tenant_id,run_id,node_run_id,intervention_type,status,prompt,allowed_actions_json,resume_token,create_time) VALUES(?,'0',?,?, 'NEEDS_INPUT','OPEN','prompt','[\"SUPPLY_INPUT\"]',?,NOW(3))")){p.setString(1,id);p.setString(2,UUID.randomUUID().toString());p.setString(3,nodeRunId);p.setString(4,UUID.randomUUID().toString());p.executeUpdate();}}
}
