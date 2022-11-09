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

import org.h2.Driver
import org.h2.util.OsgiDataSourceFactory
import java.lang.reflect.InvocationTargetException
import java.sql.*
import java.util.*

object TestConnectionHelper {
    const val DB_DRIVER = "org.h2.Driver"
    const val DB_CONNECTION = "jdbc:h2:mem:;DB_CLOSE_DELAY=-1;" + "MODE=MYSQL"

    /**
     * Creates a in-memory database for testing
     *
     * @return Connection to the database
     * @throws SQLException
     */
    @get:Throws(SQLException::class)
    val connection: Connection
        get() {
            val properties = Properties()
            properties.setProperty("url", DB_CONNECTION)
            properties.setProperty("password", "")
            properties.setProperty("user", "")
            var dataSourceFactory: OsgiDataSourceFactory? = null
            try {
                dataSourceFactory = OsgiDataSourceFactory(
                    Class.forName(DB_DRIVER).getDeclaredConstructor().newInstance() as Driver
                )
            } catch (e: InstantiationException) {
                e.printStackTrace()
            } catch (e: IllegalAccessException) {
                e.printStackTrace()
            } catch (e: InvocationTargetException) {
                e.printStackTrace()
            } catch (e: NoSuchMethodException) {
                e.printStackTrace()
            } catch (e: ClassNotFoundException) {
                e.printStackTrace()
            }
            assert(dataSourceFactory != null)
            val dataSource = dataSourceFactory!!.createDataSource(properties)
            return dataSource.connection
        }

    /**
     * Executes the sql statement on the connection
     *
     * @param connection
     * @param sql
     * @throws SQLException
     */
    @Throws(SQLException::class)
    fun executeSQL(connection: Connection?, sql: String?) {
        val statement = connection!!.createStatement()
        statement.execute(sql)
    }

    /**
     * Executes the query on the connection
     *
     * @param connection
     * @param sql
     * @return
     * @throws SQLException
     */
    @Throws(SQLException::class)
    fun executeQuery(connection: Connection?, sql: String?): ResultSet {
        val statement = connection!!.createStatement()
        return statement.executeQuery(sql)
    }
}
