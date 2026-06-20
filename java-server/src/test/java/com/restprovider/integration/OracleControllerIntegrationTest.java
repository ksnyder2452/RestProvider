package com.restprovider.integration;

import com.restprovider.controllers.OracleController;
import com.restprovider.core.ControllerDispatcher;
import com.restprovider.core.ControllerRegistry;
import com.restprovider.domain.security.PasscodeValidator;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.logging.Logger;
import javax.sql.rowset.CachedRowSet;
import javax.sql.rowset.RowSetMetaDataImpl;
import javax.sql.rowset.RowSetProvider;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OracleControllerIntegrationTest {
    private ControllerDispatcher dispatcher;

    @BeforeEach
    void setup() throws Exception {
        DriverManager.registerDriver(new StubOracleDriver());
        PasscodeValidator validator = passCode -> "valid-passcode".equals(passCode);
        ControllerRegistry registry = new ControllerRegistry();
        registry.register(new OracleController(validator));
        registry.setControllerEnabled("Oracle", true);
        dispatcher = new ControllerDispatcher(registry);
    }

    @Test
    void shouldRejectWithoutPasscode() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/api/oracle");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(401, response.getCode());
    }

    @Test
    void shouldSupportQueryAliasWithRequestCredentials() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest(
                "GET",
                "/api/oracle/query?passCode=valid-passcode&project=oracle_proj&testcase=tc1"
                        + "&sql=select%201&connection=test-host/service&oracleUser=user&oraclePassword=pwd");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(200, response.getCode());
        Assertions.assertTrue(TestResponseUtil.body(response).contains("1 rows were retured"));
    }

    @Test
    void shouldReturnBadRequestWhenBlobFileNameMissing() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/api/oracle/blob");
        request.addHeader("passCode", "valid-passcode");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(400, response.getCode());
        Assertions.assertTrue(TestResponseUtil.body(response).contains("Missing required parameter"));
    }

    public static final class StubOracleDriver implements java.sql.Driver {
        @Override
        public Connection connect(String url, Properties info) {
            if (!acceptsURL(url)) {
                return null;
            }
            return new StubConnection();
        }

        @Override
        public boolean acceptsURL(String url) {
            return url != null && url.startsWith("jdbc:oracle:thin:@");
        }

        @Override
        public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) {
            return new DriverPropertyInfo[0];
        }

        @Override
        public int getMajorVersion() {
            return 1;
        }

        @Override
        public int getMinorVersion() {
            return 0;
        }

        @Override
        public boolean jdbcCompliant() {
            return false;
        }

        @Override
        public Logger getParentLogger() {
            return Logger.getGlobal();
        }
    }

    static final class StubConnection implements Connection {
        @Override
        public Statement createStatement() {
            return new StubStatement();
        }

        @Override public void close() {}
        @Override public boolean isClosed() { return false; }
        @Override public <T> T unwrap(Class<T> iface) { throw new UnsupportedOperationException(); }
        @Override public boolean isWrapperFor(Class<?> iface) { return false; }
        @Override public java.sql.PreparedStatement prepareStatement(String sql) { throw new UnsupportedOperationException(); }
        @Override public java.sql.CallableStatement prepareCall(String sql) { throw new UnsupportedOperationException(); }
        @Override public String nativeSQL(String sql) { throw new UnsupportedOperationException(); }
        @Override public void setAutoCommit(boolean autoCommit) {}
        @Override public boolean getAutoCommit() { return true; }
        @Override public void commit() {}
        @Override public void rollback() {}
        @Override public java.sql.DatabaseMetaData getMetaData() { throw new UnsupportedOperationException(); }
        @Override public void setReadOnly(boolean readOnly) {}
        @Override public boolean isReadOnly() { return false; }
        @Override public void setCatalog(String catalog) {}
        @Override public String getCatalog() { return null; }
        @Override public void setTransactionIsolation(int level) {}
        @Override public int getTransactionIsolation() { return Connection.TRANSACTION_NONE; }
        @Override public java.sql.SQLWarning getWarnings() { return null; }
        @Override public void clearWarnings() {}
        @Override public java.sql.Statement createStatement(int resultSetType, int resultSetConcurrency) { return createStatement(); }
        @Override public java.sql.PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency) { throw new UnsupportedOperationException(); }
        @Override public java.sql.CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) { throw new UnsupportedOperationException(); }
        @Override public java.util.Map<String, Class<?>> getTypeMap() { throw new UnsupportedOperationException(); }
        @Override public void setTypeMap(java.util.Map<String, Class<?>> map) { throw new UnsupportedOperationException(); }
        @Override public void setHoldability(int holdability) {}
        @Override public int getHoldability() { return 0; }
        @Override public java.sql.Savepoint setSavepoint() { throw new UnsupportedOperationException(); }
        @Override public java.sql.Savepoint setSavepoint(String name) { throw new UnsupportedOperationException(); }
        @Override public void rollback(java.sql.Savepoint savepoint) {}
        @Override public void releaseSavepoint(java.sql.Savepoint savepoint) {}
        @Override public java.sql.Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability) { return createStatement(); }
        @Override public java.sql.PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) { throw new UnsupportedOperationException(); }
        @Override public java.sql.CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) { throw new UnsupportedOperationException(); }
        @Override public java.sql.PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) { throw new UnsupportedOperationException(); }
        @Override public java.sql.PreparedStatement prepareStatement(String sql, int[] columnIndexes) { throw new UnsupportedOperationException(); }
        @Override public java.sql.PreparedStatement prepareStatement(String sql, String[] columnNames) { throw new UnsupportedOperationException(); }
        @Override public java.sql.Clob createClob() { throw new UnsupportedOperationException(); }
        @Override public java.sql.Blob createBlob() { throw new UnsupportedOperationException(); }
        @Override public java.sql.NClob createNClob() { throw new UnsupportedOperationException(); }
        @Override public java.sql.SQLXML createSQLXML() { throw new UnsupportedOperationException(); }
        @Override public boolean isValid(int timeout) { return true; }
        @Override public void setClientInfo(String name, String value) {}
        @Override public void setClientInfo(Properties properties) {}
        @Override public String getClientInfo(String name) { return null; }
        @Override public Properties getClientInfo() { return new Properties(); }
        @Override public java.sql.Array createArrayOf(String typeName, Object[] elements) { throw new UnsupportedOperationException(); }
        @Override public java.sql.Struct createStruct(String typeName, Object[] attributes) { throw new UnsupportedOperationException(); }
        @Override public void setSchema(String schema) {}
        @Override public String getSchema() { return null; }
        @Override public void abort(java.util.concurrent.Executor executor) {}
        @Override public void setNetworkTimeout(java.util.concurrent.Executor executor, int milliseconds) {}
        @Override public int getNetworkTimeout() { return 0; }
    }

    static final class StubStatement implements Statement {
        @Override
        public ResultSet executeQuery(String sql) {
            try {
                CachedRowSet rowSet = RowSetProvider.newFactory().createCachedRowSet();
                RowSetMetaDataImpl meta = new RowSetMetaDataImpl();
                meta.setColumnCount(1);
                meta.setColumnName(1, "value");
                meta.setColumnType(1, java.sql.Types.VARCHAR);
                rowSet.setMetaData(meta);
                rowSet.moveToInsertRow();
                rowSet.updateString(1, "value1");
                rowSet.insertRow();
                rowSet.moveToCurrentRow();
                rowSet.beforeFirst();
                return rowSet;
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public int executeUpdate(String sql) {
            return 1;
        }

        @Override public void close() {}
        @Override public int getMaxFieldSize() { return 0; }
        @Override public void setMaxFieldSize(int max) {}
        @Override public int getMaxRows() { return 0; }
        @Override public void setMaxRows(int max) {}
        @Override public void setEscapeProcessing(boolean enable) {}
        @Override public int getQueryTimeout() { return 0; }
        @Override public void setQueryTimeout(int seconds) {}
        @Override public void cancel() {}
        @Override public java.sql.SQLWarning getWarnings() { return null; }
        @Override public void clearWarnings() {}
        @Override public void setCursorName(String name) {}
        @Override public boolean execute(String sql) { return true; }
        @Override public ResultSet getResultSet() { throw new UnsupportedOperationException(); }
        @Override public int getUpdateCount() { return 0; }
        @Override public boolean getMoreResults() { return false; }
        @Override public void setFetchDirection(int direction) {}
        @Override public int getFetchDirection() { return 0; }
        @Override public void setFetchSize(int rows) {}
        @Override public int getFetchSize() { return 0; }
        @Override public int getResultSetConcurrency() { return 0; }
        @Override public int getResultSetType() { return 0; }
        @Override public void addBatch(String sql) {}
        @Override public void clearBatch() {}
        @Override public int[] executeBatch() { throw new UnsupportedOperationException(); }
        @Override public Connection getConnection() { throw new UnsupportedOperationException(); }
        @Override public boolean getMoreResults(int current) { return false; }
        @Override public ResultSet getGeneratedKeys() { throw new UnsupportedOperationException(); }
        @Override public int executeUpdate(String sql, int autoGeneratedKeys) { return 1; }
        @Override public int executeUpdate(String sql, int[] columnIndexes) { return 1; }
        @Override public int executeUpdate(String sql, String[] columnNames) { return 1; }
        @Override public boolean execute(String sql, int autoGeneratedKeys) { return true; }
        @Override public boolean execute(String sql, int[] columnIndexes) { return true; }
        @Override public boolean execute(String sql, String[] columnNames) { return true; }
        @Override public int getResultSetHoldability() { return 0; }
        @Override public boolean isClosed() { return false; }
        @Override public void setPoolable(boolean poolable) {}
        @Override public boolean isPoolable() { return false; }
        @Override public void closeOnCompletion() {}
        @Override public boolean isCloseOnCompletion() { return false; }
        @Override public <T> T unwrap(Class<T> iface) { throw new UnsupportedOperationException(); }
        @Override public boolean isWrapperFor(Class<?> iface) { return false; }
    }
}
