package com.zhixuewuli.repository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import java.sql.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
@Tag("mysql")
class KnowledgeSchemaMySqlTest {
    static String url,user,password;
    @BeforeAll static void migrate(){
        url=System.getenv("MYSQL_TEST_URL");user=System.getenv().getOrDefault("MYSQL_TEST_USER","root");
        password=System.getenv().getOrDefault("MYSQL_TEST_PASSWORD","");
        assertNotNull(url,"Set MYSQL_TEST_URL to an isolated test schema; no fallback to application database");
        Flyway.configure().dataSource(url,user,password).locations("classpath:db/migration")
                .baselineOnMigrate(true).baselineVersion("8").load().migrate();
    }
    @Test void libraryMigrationAndChineseNgramWorkOnRealMySql() throws Exception {
        try(var connection=DriverManager.getConnection(url,user,password)) {
            try(var result=connection.createStatement().executeQuery("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('knowledge_documents','knowledge_revisions','knowledge_chunks')")) {
                assertTrue(result.next());assertEquals(3,result.getInt(1));
            }
            long classId=insert(connection,"INSERT INTO classes(name,status,created_at,updated_at) VALUES(?,'ACTIVE',NOW(),NOW())","knowledge_fixture_"+UUID.randomUUID());
            Long documentId=null;
            try {
                documentId=insert(connection,"INSERT INTO knowledge_documents(class_id,owner_id,title,storage_key,media_type,content_sha256,lock_version,created_at,updated_at) VALUES(?,1,'讲义',?,'text/plain',?,0,NOW(),NOW())",classId,UUID.randomUUID()+".bin","a".repeat(64));
                long revisionId=insert(connection,"INSERT INTO knowledge_revisions(document_id,content_sha256,status,retry_count,lock_version,created_at,updated_at) VALUES(?,?,'READY',0,0,NOW(),NOW())",documentId,"a".repeat(64));
                insert(connection,"INSERT INTO knowledge_chunks(revision_id,ordinal_no,chunk_text,content_sha256,created_at,updated_at) VALUES(?,0,?,?,NOW(),NOW())",revisionId,"只有重力做功时机械能守恒","b".repeat(64));
                try(var statement=connection.prepareStatement("SELECT chunk_text FROM knowledge_chunks WHERE revision_id=? AND MATCH(chunk_text) AGAINST('机械能' IN NATURAL LANGUAGE MODE)")) {
                    statement.setLong(1,revisionId);try(var result=statement.executeQuery()){assertTrue(result.next());assertTrue(result.getString(1).contains("机械能守恒"));}
                }
                try(var statement=connection.prepareStatement("INSERT INTO knowledge_chunks(revision_id,ordinal_no,chunk_text,content_sha256,created_at,updated_at) VALUES(?,1,'orphan',?,NOW(),NOW())")) {
                    statement.setLong(1,Long.MAX_VALUE);statement.setString(2,"c".repeat(64));
                    assertThrows(SQLIntegrityConstraintViolationException.class,statement::executeUpdate);
                }
            } finally {
                if(documentId!=null)try(var statement=connection.prepareStatement("DELETE FROM knowledge_documents WHERE id=?")){statement.setLong(1,documentId);statement.executeUpdate();}
                try(var statement=connection.prepareStatement("DELETE FROM classes WHERE id=?")){statement.setLong(1,classId);statement.executeUpdate();}
            }
        }
    }

    @Test void questionKnowledgeTagsAreVersionedAndPreseededOnRealMySql() throws Exception {
        try (var connection = DriverManager.getConnection(url, user, password)) {
            try (var result = connection.createStatement().executeQuery(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('knowledge_points','question_knowledge_points')")) {
                assertTrue(result.next());
                assertEquals(2, result.getInt(1));
            }
            try (var result = connection.createStatement().executeQuery("SELECT COUNT(*) FROM knowledge_points")) {
                assertTrue(result.next());
                assertEquals(12, result.getInt(1));
            }
            try (var result = connection.createStatement().executeQuery(
                    "SELECT COUNT(*) FROM knowledge_points WHERE topic='力学' AND code LIKE 'mechanics.%'")) {
                assertTrue(result.next());
                assertEquals(12, result.getInt(1));
            }
        }
    }
    private long insert(Connection connection,String sql,Object... values)throws Exception {
        try(var statement=connection.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS)) {
            for(int i=0;i<values.length;i++)statement.setObject(i+1,values[i]);statement.executeUpdate();
            try(var keys=statement.getGeneratedKeys()){assertTrue(keys.next());return keys.getLong(1);}
        }
    }
}
