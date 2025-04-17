package com.example.oraclemigration;

import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

public class OracleDataMigration {
    
    private static String sourceUrl;
    private static String sourceUser;
    private static String sourcePassword;
    private static String targetUrl;
    private static String targetUser;
    private static String targetPassword;
    private static List<String> materializedViewNames;
    private static List<String> targetTableNames;
    
    public static void main(String[] args) {
        try {
            // Load the Oracle JDBC driver
            Class.forName("oracle.jdbc.driver.OracleDriver");
            
            // Load properties from file
            loadProperties();
            
            // Connect to both databases
            Connection sourceConn = connectToDatabase(sourceUrl, sourceUser, sourcePassword);
            Connection targetConn = connectToDatabase(targetUrl, targetUser, targetPassword);
            
            System.out.println("Connected to both databases successfully.");
            
            // Get all table names from materialized views
            List<String> allTables = new ArrayList<>();
            if (materializedViewNames != null && !materializedViewNames.isEmpty()) {
                allTables.addAll(materializedViewNames);
            } else {
                allTables = getTablesFromSourceDatabase(sourceConn);
            }
            
            // Use provided target table names or default to source names
            if (targetTableNames == null || targetTableNames.isEmpty()) {
                targetTableNames = allTables;
            }
            
            if (targetTableNames.size() != allTables.size()) {
                System.out.println("Warning: Number of target tables does not match number of source tables.");
                System.out.println("Will use as many target names as available and default to source names for the rest.");
                
                List<String> tempTargetNames = new ArrayList<>(targetTableNames);
                for (int i = targetTableNames.size(); i < allTables.size(); i++) {
                    tempTargetNames.add(allTables.get(i));
                }
                targetTableNames = tempTargetNames;
            }
            
            // Process each table
            for (int i = 0; i < allTables.size(); i++) {
                String sourceTable = allTables.get(i);
                String targetTable = targetTableNames.get(i);
                
                migrateTable(sourceConn, targetConn, sourceTable, targetTable);
                System.out.println("Migrated data from " + sourceTable + " to " + targetTable);
            }
            
            // Close connections
            sourceConn.close();
            targetConn.close();
            System.out.println("Migration completed successfully.");
            
        } catch (ClassNotFoundException e) {
            System.err.println("Oracle JDBC Driver not found: " + e.getMessage());
        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
        } catch (IOException e) {
            System.err.println("Error loading properties: " + e.getMessage());
        }
    }
    
    private static void loadProperties() throws IOException {
        Properties prop = new Properties();
        try (FileInputStream input = new FileInputStream("application.properties")) {
            prop.load(input);
            
            sourceUrl = prop.getProperty("source.db.url");
            sourceUser = prop.getProperty("source.db.username");
            sourcePassword = prop.getProperty("source.db.password");
            
            targetUrl = prop.getProperty("target.db.url");
            targetUser = prop.getProperty("target.db.username");
            targetPassword = prop.getProperty("target.db.password");
            
            String materializedViewsStr = prop.getProperty("source.materialized.views", "");
            if (!materializedViewsStr.isEmpty()) {
                materializedViewNames = Arrays.asList(materializedViewsStr.split(","));
            } else {
                materializedViewNames = new ArrayList<>();
            }
            
            String targetTablesStr = prop.getProperty("target.table.names", "");
            if (!targetTablesStr.isEmpty()) {
                targetTableNames = Arrays.asList(targetTablesStr.split(","));
            } else {
                targetTableNames = new ArrayList<>();
            }
        }
    }
    
    private static Connection connectToDatabase(String url, String user, String password) throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }
    
    private static List<String> getTablesFromSourceDatabase(Connection conn) throws SQLException {
        List<String> tables = new ArrayList<>();
        DatabaseMetaData dbMeta = conn.getMetaData();
        
        // Get materialized views - in Oracle, they are treated as tables
        try (ResultSet rs = dbMeta.getTables(null, conn.getSchema(), null, new String[]{"MATERIALIZED VIEW"})) {
            while (rs.next()) {
                tables.add(rs.getString("TABLE_NAME"));
            }
        }
        
        return tables;
    }
    
    private static void migrateTable(Connection sourceConn, Connection targetConn, 
                                  String sourceTable, String targetTable) throws SQLException {
        // Get table structure
        Statement stmt = sourceConn.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT * FROM " + sourceTable + " WHERE ROWNUM <= 1");
        ResultSetMetaData metaData = rs.getMetaData();
        
        // Create table in target database
        StringBuilder createTableSQL = new StringBuilder("CREATE TABLE " + targetTable + " (");
        
        for (int i = 1; i <= metaData.getColumnCount(); i++) {
            String columnName = metaData.getColumnName(i);
            String columnType = metaData.getColumnTypeName(i);
            int columnSize = metaData.getPrecision(i);
            int scale = metaData.getScale(i);
            
            createTableSQL.append(columnName).append(" ").append(columnType);
            
            // Add size for VARCHAR2, NUMBER, etc.
            if (columnType.equals("VARCHAR2") || columnType.contains("CHAR")) {
                createTableSQL.append("(").append(columnSize).append(")");
            } else if (columnType.equals("NUMBER") && scale > 0) {
                createTableSQL.append("(").append(columnSize).append(",").append(scale).append(")");
            } else if (columnType.equals("NUMBER") && columnSize > 0) {
                createTableSQL.append("(").append(columnSize).append(")");
            }
            
            if (i < metaData.getColumnCount()) {
                createTableSQL.append(", ");
            }
        }
        createTableSQL.append(")");
        
        // Drop table if exists
        try {
            Statement dropStmt = targetConn.createStatement();
            dropStmt.execute("DROP TABLE " + targetTable);
            dropStmt.close();
            System.out.println("Dropped existing table: " + targetTable);
        } catch (SQLException e) {
            // Table might not exist, which is fine
            System.out.println("Table doesn't exist yet: " + targetTable);
        }
        
        // Create new table
        Statement createStmt = targetConn.createStatement();
        createStmt.execute(createTableSQL.toString());
        createStmt.close();
        System.out.println("Created table: " + targetTable);
        
        // Get data from source
        rs = stmt.executeQuery("SELECT * FROM " + sourceTable);
        
        // Prepare insert statement for target
        StringBuilder insertSQL = new StringBuilder("INSERT INTO " + targetTable + " VALUES (");
        for (int i = 1; i <= metaData.getColumnCount(); i++) {
            insertSQL.append("?");
            if (i < metaData.getColumnCount()) {
                insertSQL.append(", ");
            }
        }
        insertSQL.append(")");
        
        PreparedStatement pstmt = targetConn.prepareStatement(insertSQL.toString());
        
        // Set auto-commit to false for batch processing
        targetConn.setAutoCommit(false);
        
        int batchSize = 1000;
        int count = 0;
        
        // Copy data
        while (rs.next()) {
            for (int i = 1; i <= metaData.getColumnCount(); i++) {
                pstmt.setObject(i, rs.getObject(i));
            }
            pstmt.addBatch();
            
            if (++count % batchSize == 0) {
                pstmt.executeBatch();
                targetConn.commit();
                System.out.println("Inserted " + count + " rows into " + targetTable);
            }
        }
        
        // Execute remaining batch
        pstmt.executeBatch();
        targetConn.commit();
        
        // Reset auto-commit
        targetConn.setAutoCommit(true);
        
        // Close resources
        pstmt.close();
        stmt.close();
        rs.close();
        
        System.out.println("Total " + count + " rows inserted into " + targetTable);
    }
}