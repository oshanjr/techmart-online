# TechMart Online

TechMart Online is an enterprise-level Java EE e-commerce web application featuring a modern storefront and a dedicated admin portal.

## Prerequisites
- **Java 11**
- **Maven 3.x**
- **MySQL 8.x**
- **WildFly 26.1.3.Final**

## Database Setup
1. Create a MySQL database named `techmart`.
2. Ensure you have your MySQL credentials ready. In a typical development environment:
   - **Username**: `root`
   - **Password**: `Oshan@2004`
   - **Connection URL**: `jdbc:mysql://localhost:3306/techmart`

## WildFly Server Configuration
1. **MySQL JDBC Driver**: Install the MySQL JDBC driver module in WildFly.
2. **Datasource**: Configure the `TechMartDS` datasource in your WildFly `standalone-full.xml` to point to the `techmart` database using the credentials above.
3. **JMS Queues**: Ensure ActiveMQ is enabled and configure the following JMS Queues in `standalone-full.xml`:
   - `java:/jms/queue/OrderQueue`
   - `java:/jms/queue/InventoryUpdateQueue`
   - `java:/jms/queue/NotificationQueue`

> *Note: A snippet of the required WildFly configuration is provided in `config/wildfly-standalone-snippet.xml`.*

## Build the Project
Open a terminal in the root of the project (`g:\javaee\techmart-online`) and run the following Maven command to build the Enterprise Archive (EAR):

```bash
mvn clean package -DskipTests
```

## Deployment
1. Start your WildFly server using the `standalone-full.xml` configuration (this is required because the application relies on ActiveMQ for JMS message-driven beans):
   ```cmd
   cd C:\wildfly\wildfly-26.1.3.Final\bin
   standalone.bat -c standalone-full.xml
   ```

2. Once the server is running, deploy the compiled EAR file by copying it to the WildFly deployments folder:
   ```cmd
   copy techmart-ear\target\techmart-ear-1.0.0-SNAPSHOT.ear C:\wildfly\wildfly-26.1.3.Final\standalone\deployments\
   ```

## Accessing the Application
Once the application has successfully deployed, you can access the following URLs:

- **Storefront**: [http://localhost:8080/techmart](http://localhost:8080/techmart)
- **Admin Portal**: [http://localhost:8080/techmart/admin.html](http://localhost:8080/techmart/admin.html)
- **REST API Base Path**: `http://localhost:8080/techmart/api`

## Architecture Highlights
- **RESTful Endpoints**: Built using JAX-RS for lightweight, stateless communication.
- **Business Logic**: Handled by EJBs (Stateless session beans for processing, Stateful session beans for maintaining the shopping cart).
- **Persistence**: Managed by JPA/Hibernate using the MySQL 8 dialect.
- **Asynchronous Processing**: Uses JMS (Message Driven Beans) to handle background tasks like order lifecycle transitions and notifications without blocking the user.
