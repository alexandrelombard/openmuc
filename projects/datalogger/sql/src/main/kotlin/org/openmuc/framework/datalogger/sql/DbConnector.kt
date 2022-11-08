/*
 * Copyright 2011-2022 Fraunhofer ISE
 *
 * This file is part of OpenMUC.
 * For more information visit http://www.openmuc.org
 *
 * OpenMUC is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * OpenMUC is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with OpenMUC.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package org.openmuc.framework.datalogger.sql

import org.h2.tools.Server
import org.openmuc.framework.datalogger.sql.utils.PropertyHandlerProvider
import org.openmuc.framework.datalogger.sql.utils.Settings
import org.openmuc.framework.datalogger.sql.utils.SqlValues
import org.openmuc.framework.lib.osgi.config.*
import org.osgi.framework.FrameworkUtil
import org.osgi.framework.ServiceReference
import org.osgi.service.jdbc.DataSourceFactory
import org.slf4j.LoggerFactory
import java.io.*
import java.lang.reflect.InvocationTargetException
import java.sql.*
import java.text.MessageFormat
import java.util.*
import javax.sql.DataSource

open class DbConnector {
    private val logger = LoggerFactory.getLogger(DbConnector::class.java)
    private val out = PrintWriter(System.out, true)
    private val url: String
    private var connection: Connection? = null
    private var dataSource: DataSource? = null
    private var dataSourceFactory: DataSourceFactory? = null
    private var timescaleActive = false
    private var driver: Driver? = null
    private var server: Server? = null

    init {
        url = urlFromProperties
        initConnector()
        connectionToDb
    }

    protected open val urlFromProperties: String
        get() {
            val propertyHandler = PropertyHandlerProvider.propertyHandler
            return propertyHandler!!.getString(Settings.URL)!!
        }

    protected open fun initConnector() {
        val context = FrameworkUtil.getBundle(DbConnector::class.java).bundleContext
        val reference: ServiceReference<*> = context.getServiceReference(
            DataSourceFactory::class.java
        )
        dataSourceFactory = context.getService(reference) as DataSourceFactory
    }

    val isConnected: Boolean
        get() {
            try {
                if (connection == null || connection!!.isClosed) {
                    return false
                }
            } catch (e: SQLException) {
                logger.error(e.message)
                return false
            }
            return true
        }

    /**
     * Starts up an H2 TCP server
     */
    fun startH2Server() {
        try {
            logger.info("Starting H2 Server")
            server = Server.createTcpServer("-webAllowOthers", "-tcpAllowOthers").start()
        } catch (e: SQLException) {
            logger.error(e.message)
        }
    }

    /**
     * Stops H2 TCP server
     */
    private fun stopH2Server() {
        logger.info("Stopping H2 Server")
        server!!.stop()
    }

    @Throws(SQLException::class)
    open fun createStatementWithConnection(): Statement {
        return connection!!.createStatement()
    }

    /**
     * Sets the proper dataSourceFactory, depending on the URL, using [.setDataSourceFactory] and creates a
     * dataSource with it, creates a connection to the database and in case PostgreSQL is used it checks if timescale is
     * installed with [.checkIfTimescaleInstalled] or needs to be updated with [.updateTimescale]. If a
     * H2 database is corrupted it renames it so a new one is created using [.renameCorruptedDb].
     */
    open val connectionToDb: Unit
        get() {
            try {
                logger.info("sql driver")
                if (connection == null || connection!!.isClosed) {
                    logger.debug("CONNECTING")
                    val properties = setSqlProperties()
                    logger.info(MessageFormat.format("URL is: {0}", url))
                    setDataSourceFactory()
                    dataSource = getDataSource(dataSourceFactory, properties)
                    if (logger.isTraceEnabled) {
                        dataSource!!.logWriter = out
                    }
                    connection = dataSource!!.connection
                    if (url.contains(SqlValues.POSTGRES)) {
                        checkIfTimescaleInstalled()
                    }
                    if (url.contains(SqlValues.POSTGRES) && timescaleActive) {
                        updateTimescale()
                    }
                    logger.debug("CONNECTED")
                }
            } catch (e: SQLException) {
                if (e.message!!.contains("The write format 1 is smaller than the supported format 2")) {
                    logger.error(
                        "Database is incompatible with H2 Database Engine version 2.0.206. "
                                + "To continue using it, it has to be migrated to the newer version. "
                                + "Explained here: https://www.openmuc.org/openmuc/user-guide/#_sql_logger; "
                                + "More Information: https://h2database.com/html/tutorial.html#upgrade_backup_restore "
                                + "If the Database does not contain important data, just delete the directory framework/data"
                    )
                } else {
                    logger.error(MessageFormat.format("SQLException: {0}", e.message))
                    logger.error(MessageFormat.format("SQLState:     {0}", e.sqlState))
                    logger.error(MessageFormat.format("VendorError:  {0}", e.errorCode))
                    e.printStackTrace()
                }
                if (url.contains("h2") && e.errorCode == 90030) {
                    renameCorruptedDb()
                }
            } catch (e: Exception) {
                logger.error("", e)
            }
        }

    @Synchronized
    @Throws(SQLException::class)
    private fun getDataSource(dataSourceFactory: DataSourceFactory?, properties: Properties): DataSource? {
        if (dataSource == null) {
            dataSource = dataSourceFactory!!.createDataSource(properties)
        }
        return dataSource
    }

    /**
     * returns a properties object with the attributes the datasource needs
     *
     * @return a properties object with the attributes the datasource needs
     */
    private fun setSqlProperties(): Properties {
        val propertyHandler = PropertyHandlerProvider.propertyHandler
        val properties = Properties()
        properties.setProperty("url", url)
        properties.setProperty("password", propertyHandler.getString(Settings.PASSWORD))
        properties.setProperty("user", propertyHandler.getString(Settings.USER))
        if (!url.contains("h2")) {
            if (url.contains(SqlValues.POSTGRESQL)) {
                properties.setProperty("ssl", propertyHandler.getString(Settings.SSL))
            }
            properties.setProperty("tcpKeepAlive", propertyHandler.getString(Settings.TCP_KEEP_ALIVE))
            properties.setProperty("socketTimeout", propertyHandler.getString(Settings.SOCKET_TIMEOUT))
        }
        return properties
    }

    /**
     * Iterates over the bundles in the bundleContext and creates a new instance of the PostgreSQL or MySQL
     * dataSourceFactory. The MySQL JDBC driver needs the dataSourceFactory of OPS4J Pax JDBC Generic Driver Extender,
     * which has to be instantiated with the MySQL JDBC Driver class
     */
    @Throws(
        InstantiationException::class,
        IllegalAccessException::class,
        ClassNotFoundException::class,
        InvocationTargetException::class
    )
    private fun setDataSourceFactory() {
        val bundleContext = FrameworkUtil.getBundle(
            SqlLoggerService::class.java
        ).bundleContext
        if (url.contains(SqlValues.POSTGRESQL)) {
            for (bundle in bundleContext.bundles) {
                if (bundle.symbolicName == null) {
                    continue
                }
                if (bundle.symbolicName == "org.postgresql.jdbc") {
                    dataSourceFactory = bundle.loadClass("org.postgresql.osgi.PGDataSourceFactory")
                        .declaredConstructors[0].newInstance() as DataSourceFactory
                }
            }
        }
        if (url.contains(SqlValues.MYSQL)) {
            for (bundle in bundleContext.bundles) {
                if (bundle.symbolicName == "com.mysql.cj") {
                    driver = bundle.loadClass("com.mysql.cj.jdbc.Driver").declaredConstructors[0]
                        .newInstance() as Driver
                }
                if (bundle.symbolicName == "org.ops4j.pax.jdbc") {
                    // get constructor and instantiate with MySQL driver
                    val constructors = bundle.loadClass("org.ops4j.pax.jdbc.impl.DriverDataSourceFactory")
                        .declaredConstructors
                    val constructor = constructors[0]
                    constructor.isAccessible = true
                    try {
                        dataSourceFactory = constructor.newInstance(driver) as DataSourceFactory
                    } catch (e: IllegalArgumentException) {
                        logger.error(e.message)
                    } catch (e: InvocationTargetException) {
                        logger.error(e.message)
                    }
                }
            }
        }
    }

    /**
     * Sets timescaleActive to true if timescale is installed
     */
    private fun checkIfTimescaleInstalled() {
        val sbExtensions = StringBuilder("SELECT * FROM pg_extension;")
        try {
            connection!!.createStatement().executeQuery(sbExtensions.toString()).use { resultSet ->
                while (resultSet.next()) {
                    if (resultSet.getString("extname").contains("timescale")) {
                        timescaleActive = true
                    }
                }
            }
        } catch (e: SQLException) {
            logger.error(e.message)
        }
    }

    /**
     * Updates the PostgreSQL timescale extension by executing a SQL query as a console command
     */
    private fun updateTimescale() {
        try {
            var line: String?
            val cmd = arrayOfNulls<String>(3)
            val startPoint = url.lastIndexOf('/')
            val dbName = url.substring(startPoint + 1)
            if (System.getProperty("os.name").lowercase(Locale.getDefault()).startsWith("windows")) {
                cmd[0] = "cmd.exe"
            } else {
                cmd[0] = "sh"
            }
            val propertyHandler: PropertyHandler = PropertyHandlerProvider.propertyHandler
            cmd[1] = "-c"
            cmd[2] = ("PGPASSWORD=" + propertyHandler.getString(Settings.PSQL_PASS)
                    + " psql -c 'ALTER EXTENSION timescaledb UPDATE;'  -U postgres -h localhost -d " + dbName)
            val process = Runtime.getRuntime().exec(cmd)
            val stdOutReader = BufferedReader(InputStreamReader(process.inputStream))
            while (stdOutReader.readLine().also { line = it } != null) {
                logger.info(line)
            }
            val stdErrReader = BufferedReader(InputStreamReader(process.errorStream))
            while (stdErrReader.readLine().also { line = it } != null) {
                logger.info(line)
            }
        } catch (e: Exception) {
            logger.error(MessageFormat.format("Unable to execute shell command: {0}", e.message))
        }
    }

    /**
     * Renames the corrupted database to dbName"_corrupted_"timestamp, by building a with the classpath to it and
     * calling renameTo on it
     */
    private fun renameCorruptedDb() {
        logger.error("Renaming corrupted Database so new one can be created")
        val renameTimestamp = Timestamp(System.currentTimeMillis())
        var path = ""
        val endPoint = url.indexOf(';')
        if (url.contains("file")) {
            path = url.substring(19, endPoint)
        }
        if (url.contains("tcp")) {
            path = if (url.contains("~")) {
                System.getProperty("user.home") + url.substring(30, endPoint)
            } else {
                System.getProperty("user.home") + url.substring(28, endPoint)
            }
        }
        val sqlDb = File("$path.mv.db")
        val sqlDbOld = File(path + "_corrupted_" + renameTimestamp + ".mv.db")
        val success = sqlDb.renameTo(sqlDbOld)
        if (success) {
            logger.info("Renaming successful, restarting sqlLogger")
            connectionToDb
        } else {
            logger.info("Unable to rename corrupted Database")
        }
    }

    fun closeConnection() {
        if (connection != null) {
            try {
                connection!!.close()
                if (url.contains("h2") && url.contains("tcp")) {
                    stopH2Server()
                }
            } catch (e: SQLException) {
                // ignore
            }
        }
    }
}
