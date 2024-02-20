package org.mineacademy.fo.database;

import lombok.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mineacademy.fo.*;
import org.mineacademy.fo.collection.SerializedMap;
import org.mineacademy.fo.collection.StrictMap;
import org.mineacademy.fo.debug.Debugger;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Represents a simple MySQL database
 * <p>
 * Before running queries make sure to call connect() methods.
 * <p>
 * You can also override onConnected() to run your code after the
 * connection has been established.
 * <p>
 * To use this class you must know the MySQL command syntax!
 *
 * @author kangarko
 * @author Rubix327
 * @since 6.2.5.6
 */
@SuppressWarnings({"unused", "SameParameterValue"})
public class SimpleDatabaseManager {

    @Getter
    private SimpleDatabaseConnector connector;
    private Connection connection;
    @Getter(AccessLevel.PACKAGE)
    private Consumer<SimpleDatabaseManager> afterConnected;

    /**
     * Map of variables you can use with the {} syntax in SQL
     */
    private final StrictMap<String, String> sqlVariables = new StrictMap<>();

    void setConnector(SimpleDatabaseConnector connector) {
        this.connector = connector;
        this.connection = connector.getConnection();
    }

    protected void onConnected() {}

    private Connection getConnection() {
        return connector.getConnection();
    }

    private String getUrl() {
        return connector.getUrl();
    }

    /**
     * Set a runnable to be executed after {@link #onConnected()}
     * @param afterConnected the consumer
     */
    public final SimpleDatabaseManager setAfterConnected(Consumer<SimpleDatabaseManager> afterConnected) {
        this.afterConnected = afterConnected;
        return this;
    }

    // --------------------------------------------------------------------
    // Querying
    // --------------------------------------------------------------------

    /**
     * Creates a database table with an empty callback
     */
    protected final void createTable(TableCreator creator){
        createTable(creator, new EmptyCallback<>());
    }

    /**
     * Creates a database table, to be used in onConnected
     */
    protected final void createTable(TableCreator creator, @NotNull Callback<Void> callback) {
        StringBuilder columns = new StringBuilder();

        for (final TableRow column : creator.getColumns()) {
            columns.append((columns.length() == 0) ? "" : ", ").append("`").append(column.getName()).append("` ").append(column.getDataType());

            if (column.getAutoIncrement() != null && column.getAutoIncrement())
                columns.append(" NOT NULL AUTO_INCREMENT");

            else if (column.getNotNull() != null && column.getNotNull())
                columns.append(" NOT NULL");

            if (column.getDefaultValue() != null)
                columns.append(" DEFAULT ").append(column.getDefaultValue());
        }

        if (creator.getPrimaryColumn() != null)
            columns.append(", PRIMARY KEY (`").append(creator.getPrimaryColumn()).append("`)");

        try {
            final boolean isSQLite = getUrl() != null && getUrl().startsWith("jdbc:sqlite");

            this.update("CREATE TABLE IF NOT EXISTS `" + creator.getName() + "` (" + columns + ")" + (isSQLite ? "" : " DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_520_ci") + ";", callback);

        } catch (final Throwable t) {
            if (t.toString().contains("Unknown collation")) {
                Common.log("You need to update your database driver to support utf8mb4_unicode_520_ci collation. We switched to support unicode using 4 bits length because the previous system only supported 3 bits.");
                Common.log("Some characters such as smiley or Chinese are stored in 4 bits so they would crash the 3-bit database leading to more problems. Most hosting providers have now widely adopted the utf8mb4_unicode_520_ci encoding you seem lacking. Disable database connection or update your driver to fix this.");
            } else {
                callback.onFail(t);
                throw t;
            }
        }
    }

    /**
     * Insert the given column-values pairs into the given table
     */
    protected final void insert(String table, @NonNull SerializedMap columnsAndValues, @NotNull Callback<Void> callback) {
        final String columns = Common.join(columnsAndValues.keySet());
        final String values = Common.join(columnsAndValues.values(), ", ", this::parseValue);
        final String duplicateUpdate = Common.join(columnsAndValues.entrySet(), ", ", entry -> entry.getKey() + "=VALUES(" + entry.getKey() + ")");

        this.update("INSERT INTO " + this.replaceVariables(table) + " (" + columns + ") VALUES (" + values + ") ON DUPLICATE KEY UPDATE " + duplicateUpdate + ";", callback);
    }

    /**
     * Insert the batch map into the database
     */
    protected final void insertBatch(String table, @NonNull List<SerializedMap> maps, @NotNull Callback<Void> callback) {
        final List<String> sqls = new ArrayList<>();

        for (final SerializedMap map : maps) {
            final String columns = Common.join(map.keySet());
            final String values = Common.join(map.values(), ", ", this::parseValue);
            final String duplicateUpdate = Common.join(map.entrySet(), ", ", entry -> entry.getKey() + "=VALUES(" + entry.getKey() + ")");

            sqls.add("INSERT INTO " + table + " (" + columns + ") VALUES (" + values + ") ON DUPLICATE KEY UPDATE " + duplicateUpdate + ";");
        }

        this.batchUpdate(sqls, callback);
    }

    /**
     * A helper method to insert compatible value to db
     */
    private String parseValue(Object value) {
        return value == null || value.equals("NULL") ? "NULL" : value instanceof Boolean ? serializeObject(value) : "'" + serializeObject(value) + "'";
    }

    private String serializeObject(Object value){
        return SerializeUtil.serialize(getMode(), value).toString();
    }

    /**
     * Attempts to execute a new update query
     * <p>
     * Make sure you called connect() first otherwise an error will be thrown
     */
    protected final void update(String sql, @NotNull Callback<Void> callback) {
        if (!this.connector.isConnecting()){
            Valid.checkAsync("Updating database must be done async! Call: " + sql);
        }

        this.connector.checkEstablished();

        if (!this.connector.isConnected()){
            this.connector.connectUsingLastCredentials();
        }

        sql = this.replaceVariables(sql);
        Debugger.debug("mysql", "Updating database with: " + sql);

        try (Statement statement = this.connection.createStatement()) {
            statement.executeUpdate(sql);
            callback.onSuccess(null);
        }
        catch (SQLException e) {
            callback.onFail(e);
            this.handleError(e, "Error on updating database with: " + sql);
        }
    }

    /**
     * Lists all columns from all rows in the given table.
     * See {@link #select(String, String, Callback)} for more detailed info.
     */
    protected final void selectAll(String table, @NotNull Callback<ResultSet> callback) {
        this.select(table, "*", callback);
    }

    /**
     * Lists all columns from all rows in the given table with the given "where" clause
     * See {@link #select(String, String, Callback)} for more detailed info.
     */
    protected final void selectAll(String table, String where, @NotNull Callback<ResultSet> callback) {
        this.select(table, "*", where, callback);
    }

    /**
     * @see #select(String, String, String, Callback)
     */
    protected final void select(@NotNull String table, @NotNull String columns, @NotNull Callback<ResultSet> callback) {
        this.select(table, columns, null, callback);
    }

    /**
     * Lists all given columns from all rows in the given table.<br><br>
     * Here you should go over all rows yourself (while resultSet.next()).<br>
     * To use a ready solution for this please see {@link #selectForEach(String, String, String, Callback)} and {@link #forEachRow(ResultSet, Callback)}.<br><br>
     * Do not forget to close the connection when done in your consumer.
     */
    protected final void select(@NotNull String table, @NotNull String columns, @Nullable String where, @NotNull Callback<ResultSet> callback){
        if (!this.connector.isLoaded()){
            return;
        }

        this.query("SELECT " + columns + " FROM " + table + (where == null ? "" : " WHERE " + where), callback);
    }

    /**
     * Go over every row in the given resultSet
     * @param resultSet the resultSet
     * @param callbackForEachRow the callback applied to each row
     */
    protected final void forEachRow(ResultSet resultSet, @NotNull Callback<ResultSet> callbackForEachRow){
        try {
            while (resultSet != null && resultSet.next()){
                try {
                    callbackForEachRow.onSuccess(resultSet);
                }
                catch (final Throwable t) {
                    callbackForEachRow.onFail(t);
                    break;
                }
            }
        } catch (SQLException ex){
            callbackForEachRow.onFail(ex);
        }
    }

    protected final void selectAllForEach(@NotNull String table, @NotNull Callback<ResultSet> callbackForEachElement) {
        this.selectForEach(table, "*", null, callbackForEachElement);
    }

    protected final void selectAllForEach(@NotNull String table, @Nullable String where, @NotNull Callback<ResultSet> callbackForEachElement){
        this.selectForEach(table, "*", where, callbackForEachElement);
    }

    /**
     * Select rows from the given table (only specified columns) and run through each found row using the callback
     * @param table the table name
     * @param columns the columns
     * @param callbackForEachElement the callback applied to each found row
     */
    protected final void selectForEach(@NotNull String table, @NotNull String columns, @Nullable String where, @NotNull Callback<ResultSet> callbackForEachElement){
        this.select(table, columns, where, new Callback<ResultSet>() {
            @Override
            public void onSuccess(ResultSet object) {
                forEachRow(object, callbackForEachElement);
            }

            @Override
            public void onFail(Throwable t) {
                if (t instanceof SQLException){
                    Common.error(t, "Error selecting rows from table " + table + " with param '" + columns + "'");
                    callbackForEachElement.onFail(t);
                    return;
                }
                Common.log("Error reading a row from table " + table + " with columns '" + columns + "', aborting...");
                callbackForEachElement.onFail(t);
                t.printStackTrace();
            }
        });
    }

    /**
     * Get the amount of rows from the given table per the key-value conditions.
     * <br><br>
     * Example conditions: count("MyTable", "Player", "kangarko", "Status", "PENDING")
     * This example will return all rows where column Player is equal to kangarko and Status column equals PENDING.
     */
    protected final void count(String table, @NotNull Callback<Integer> callback, Object... array) {
        this.count(table, SerializedMap.ofArray(array), callback);
    }

    /**
     * Get the amount of rows from the given table per the conditions,
     * <br><br>
     * Example conditions: SerializedMap.ofArray("Player", "kangarko", "Status", "PENDING")
     * This example will return all rows where column Player is equal to kangarko and Status column equals PENDING.
     */
    protected final void count(String table, SerializedMap conditions, @NotNull Callback<Integer> callback) {

        // Convert conditions into SQL syntax
        final Set<String> conditionsList = Common.convertSet(conditions.entrySet(), entry -> entry.getKey() + " = '" + SerializeUtil.serialize(getMode(), entry.getValue()) + "'");

        // Run the query
        final String sql = "SELECT * FROM " + table + (conditionsList.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditionsList)) + ";";

        this.query(sql, new Callback<ResultSet>() {
            @Override
            public void onSuccess(ResultSet resultSet) {
                try{
                    int count = 0;

                    while (resultSet != null && resultSet.next()){
                        count++;
                    }

                    callback.onSuccess(count);
                } catch (SQLException ex){
                    callback.onFail(ex);
                    Common.throwError(ex,
                            "Unable to count rows!",
                            "Table: " + replaceVariables(table),
                            "Conditions: " + conditions,
                            "Query: " + sql);
                }
            }

            @Override
            public void onFail(Throwable t) {
                callback.onFail(t);
            }
        });
    }

    /**
     * Attempts to execute a new query
     * <p>
     * Make sure you called connect() first otherwise an error will be thrown
     */
    protected final void query(String sql, @NotNull Callback<ResultSet> callback) {
        Valid.checkAsync("Sending database query must be called async, command: " + sql);

        this.connector.checkEstablished();

        if (!this.connector.isConnected()){
            this.connector.connectUsingLastCredentials();
        }

        sql = this.replaceVariables(sql);

        Debugger.debug("mysql", "Querying database with: " + sql);

        try {
            final Statement statement = this.connection.createStatement();
            final ResultSet resultSet = statement.executeQuery(sql);

            callback.onSuccess(resultSet);
        }
        catch (final SQLException ex) {
            if (ex instanceof SQLSyntaxErrorException && ex.getMessage().startsWith("Table") && ex.getMessage().endsWith("doesn't exist")){
                callback.onSuccess(new DummyResultSet());
                return;
            }

            callback.onFail(ex);
            this.handleError(ex, "Error on querying database with: " + sql);
        }
    }

    /**
     * Executes a massive batch update
     */
    protected final void batchUpdate(@NonNull List<String> sqls, @NotNull Callback<Void> callback) {
        if (sqls.isEmpty())
            return;

        this.connector.checkEstablished();

        if (!this.connector.isConnected()){
            this.connector.connectUsingLastCredentials();
        }

        try (Statement batchStatement = this.getConnection().createStatement(ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_UPDATABLE)) {
            final int processedCount = sqls.size();

            for (final String sql : sqls)
                batchStatement.addBatch(this.replaceVariables(sql));

            if (processedCount > 10_000)
                Common.log("Updating your database (" + processedCount + " entries)... PLEASE BE PATIENT THIS WILL TAKE "
                        + (processedCount > 50_000 ? "10-20 MINUTES" : "5-10 MINUTES") + " - If server will print a crash report, ignore it, update will proceed.");

            // Prevent automatically sending db instructions
            this.getConnection().setAutoCommit(false);

            try {
                // Execute
                batchStatement.executeBatch();

                // This will block the thread
                this.getConnection().commit();
                callback.onSuccess(null);
            }
            catch (final Throwable t) {
                // Cancel the task but handle the error upstream
                throw t;
            }

        } catch (final Throwable t) {
            final List<String> errorLog = new ArrayList<>();

            errorLog.add(Common.consoleLine());
            errorLog.add(" [" + TimeUtil.getFormattedDateShort() + "] Failed to save batch sql, please contact the plugin author with this file content: " + t);
            errorLog.add(Common.consoleLine());

            for (final String statement : sqls)
                errorLog.add(this.replaceVariables(statement));

            FileUtil.write("sql-error.log", errorLog);

            t.printStackTrace();
            callback.onFail(t);

        } finally {
            try {
                this.connection.setAutoCommit(true);

            } catch (final SQLException ex) {
                ex.printStackTrace();
                callback.onFail(ex);
            }
        }
    }

    /**
     * Attempts to return a prepared statement
     * <p>
     * Make sure you called connect() first otherwise an error will be thrown
     */
    protected final java.sql.PreparedStatement prepareStatement(String sql) throws SQLException {
        this.connector.checkEstablished();

        if (!this.connector.isConnected()){
            this.connector.connectUsingLastCredentials();
        }

        sql = this.replaceVariables(sql);

        Debugger.debug("mysql", "Preparing statement: " + sql);
        return this.connection.prepareStatement(sql);
    }

    /**
     * Attempts to return a prepared statement
     * <p>
     * Make sure you called connect() first otherwise an error will be thrown
     */
    protected final java.sql.PreparedStatement prepareStatement(String sql, int type, int concurrency) throws SQLException {
        this.connector.checkEstablished();

        if (!this.connector.isConnected()){
            this.connector.connectUsingLastCredentials();
        }

        sql = this.replaceVariables(sql);

        Debugger.debug("mysql", "Preparing statement: " + sql);
        return this.connection.prepareStatement(sql, type, concurrency);
    }

    // --------------------------------------------------------------------
    // Variables
    // --------------------------------------------------------------------

    /**
     * Check if the developer called {@link #addVariable(String, String)} early enough
     * to be registered
     *
     * @param key the key of the variable
     * @return true if called
     */
    final boolean hasVariable(String key) {
        return this.sqlVariables.containsKey(key);
    }

    /**
     * Adds a new variable you can then use in your queries.
     * The variable name will be added {} brackets automatically.
     *
     * @param name the name of the variable
     * @param value the value
     */
    protected final void addVariable(final String name, final String value) {
        this.sqlVariables.put(name, value);
    }

    /**
     * Replace the {@link #sqlVariables} in the sql query
     *
     * @param sql the query
     * @return the variables-replaced query
     */
    protected final String replaceVariables(String sql) {

        for (final Map.Entry<String, String> entry : this.sqlVariables.entrySet()){
            sql = sql.replace("{" + entry.getKey() + "}", entry.getValue());
        }

        return sql;
    }

    // --------------------------------------------------------------------
    // Other
    // --------------------------------------------------------------------

    /**
     * Check if there's a collation-related error and prints warning message for the user to
     * update his database.
     */
    private void handleError(Throwable t, String fallbackMessage) {
        if (t.toString().contains("Unknown collation")) {
            Common.log("You need to update your database provider driver. We switched to support unicode using 4 bits length because the previous system only supported 3 bits.");
            Common.log("Some characters such as smiley or Chinese are stored in 4 bits so they would crash the 3-bit database leading to more problems. Most hosting providers have now widely adopted the utf8mb4_unicode_520_ci encoding you seem lacking. Disable database connection or update your driver to fix this.");
        }

        else if (t.toString().contains("Incorrect string value")) {
            Common.log("Attempted to save unicode letters (e.g. coors) to your database with invalid encoding, see https://stackoverflow.com/a/10959780 and adjust it. MariaDB may cause issues, use MySQL 8.0 for best results.");

            t.printStackTrace();

        } else {
            Common.throwError(t, fallbackMessage);
        }
    }

    protected SerializeUtil.Mode getMode(){
        return SerializeUtil.Mode.YAML;
    }

    // --------------------------------------------------------------------
    // Classes
    // --------------------------------------------------------------------

    /**
     * Helps to create new database tables preventing SQL syntax errors
     */
    @Getter
    @RequiredArgsConstructor
    protected final static class TableCreator {

        /**
         * The table name
         */
        private final String name;

        /**
         * The table columns
         */
        private final List<TableRow> columns = new ArrayList<>();

        /**
         * The primary column
         */
        private String primaryColumn;

        /**
         * Add a new column of the given name and data type
         */
        public TableCreator add(String name, String dataType) {
            this.columns.add(TableRow.builder().name(name).dataType(dataType).build());

            return this;
        }

        /**
         * Add a new column of the given name and data type that is "NOT NULL"
         */
        public TableCreator addNotNull(String name, String dataType) {
            this.columns.add(TableRow.builder().name(name).dataType(dataType).notNull(true).build());

            return this;
        }

        /**
         * Add a new column of the given name and data type that is "NOT NULL AUTO_INCREMENT"
         */
        public TableCreator addAutoIncrement(String name, String dataType) {
            this.columns.add(TableRow.builder().name(name).dataType(dataType).autoIncrement(true).build());

            return this;
        }

        /**
         * Add a new column of the given name and data type that has a default value
         */
        public TableCreator addDefault(String name, String dataType, String def) {
            this.columns.add(TableRow.builder().name(name).dataType(dataType).defaultValue(def).build());

            return this;
        }

        /**
         * Marks which column is the primary key
         */
        public TableCreator setPrimaryColumn(String primaryColumn) {
            this.primaryColumn = primaryColumn;

            return this;
        }

        /**
         * Create a new table
         */
        public static TableCreator of(String name) {
            return new TableCreator(name);
        }
    }

    /*
     * Internal helper to create table rows
     */
    @Data
    @Builder
    private final static class TableRow {

        /**
         * The table row name
         */
        private final String name;

        /**
         * The data type
         */
        private final String dataType;

        /**
         * Is this row NOT NULL?
         */
        private final Boolean notNull;

        /**
         * Does this row have a default value?
         */
        private final String defaultValue;

        /**
         * Is this row NOT NULL AUTO_INCREMENT?
         */
        private final Boolean autoIncrement;
    }

    /**
     * A helper class to read results set. (We cannot use a simple Consumer since it does not
     * catch exceptions automatically.)
     */
    protected interface ResultReader {

        /**
         * Reads and process the given results set, we handle exceptions for you
         */
        void accept(ResultSet set) throws SQLException;
    }

    public interface Callback<T>{
        /**
         * Called when the execution of a query has finished successfully
         * @param object the object
         */
        void onSuccess(T object);

        /**
         * Called if the execution of a query has failed
         * @param t the throwable that occurred during the execution
         */
        void onFail(Throwable t);
    }

    /**
     * An empty callback, does nothing, use it as a filler
     */
    public static class EmptyCallback<T> implements Callback<T>{

        @Override
        public void onSuccess(T object) {}

        @Override
        public void onFail(Throwable t) {}
    }

}
