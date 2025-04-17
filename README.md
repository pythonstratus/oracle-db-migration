# Oracle Materialized View Migration Tool

A Java-based utility for migrating data from Oracle materialized views to tables in a target Oracle database.

## Overview

This tool allows you to:
- Connect to source and target Oracle databases
- Read from multiple materialized views in the source database
- Create corresponding tables in the target database
- Efficiently transfer all data using batch processing
- Customize target table names

## Project Structure

```
oracle-migration/
├── src/
│   └── main/
│       ├── java/
│       │   └── com/
│       │       └── example/
│       │           └── oraclemigration/
│       │               └── OracleDataMigration.java
│       └── resources/
│           └── application.properties
├── pom.xml
└── README.md
```

## Prerequisites

- Java 17 or higher
- Maven 3.6 or higher
- Access to Oracle source and target databases
- Oracle JDBC driver

## Configuration

Edit the `src/main/resources/application.properties` file:

```properties
# Source database connection
source.db.url=jdbc:oracle:thin:@//sourcehost:1521/sourceservice
source.db.username=source_user
source.db.password=source_password

# Target database connection
target.db.url=jdbc:oracle:thin:@//targethost:1521/targetservice
target.db.username=target_user
target.db.password=target_password

# Source materialized view names (comma-separated)
source.materialized.views=MY_MATERIALIZED_VIEW1,MY_MATERIALIZED_VIEW2,MY_MATERIALIZED_VIEW3

# Target table names (comma-separated)
# If not specified, source table names will be used
target.table.names=TARGET_TABLE1,TARGET_TABLE2,TARGET_TABLE3
```

## Building the Project

```bash
# Clone the repository
git clone https://github.com/yourusername/oracle-migration.git
cd oracle-migration

# Build the project
mvn clean package
```

This generates two JAR files in the `target` directory:
- `oracle-migration-1.0-SNAPSHOT.jar`: Basic JAR
- `oracle-migration-1.0-SNAPSHOT-jar-with-dependencies.jar`: Executable JAR with all dependencies included

## Running the Application

### Option 1: Using the built-in properties file

```bash
java -jar target/oracle-migration-1.0-SNAPSHOT-jar-with-dependencies.jar
```

### Option 2: Using an external properties file

```bash
java -Dconfig.file=/path/to/external/application.properties -jar target/oracle-migration-1.0-SNAPSHOT-jar-with-dependencies.jar
```

## How It Works

1. The application connects to both source and target Oracle databases
2. Reads the structure of each materialized view from the source database
3. Creates corresponding tables in the target database
4. Efficiently copies all data using batch processing

## Troubleshooting

- **Oracle JDBC Driver Issues**: Make sure you have the Oracle JDBC driver in your classpath or in your Maven repository
- **Connection Problems**: Verify that database connection details are correct and that your network allows connections
- **Permissions Issues**: Ensure your database users have the necessary permissions to read/write tables

## Best Practices

- Run a test with a small subset of data first
- Use a dedicated database user with only the necessary permissions
- For large migrations, consider running during off-peak hours
- Back up your target database before running the migration

## Contributing

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## License

This project is licensed under the MIT License - see the LICENSE file for details.
