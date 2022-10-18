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
package org.openmuc.framework.lib.osgi.config

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.util.*

internal class PropertyHandlerTest {
    private val URL = "url"
    private val USER = "user"
    private val PASSWORD = "password"
    private val pid = "org.openmuc.framework.MyService"
    private val propertyDir = "properties/"
    private var loadDir: File? = null
    private var changedDic = hashMapOf<String, String>()
    private var defaultDic = hashMapOf<String, String>()
    private var settings: Settings? = null
    @BeforeEach
    fun initProperties() {
        System.setProperty("felix.fileinstall.dir", propertyDir)
        loadDir = File(propertyDir)
        loadDir!!.mkdir()
        settings = Settings()
    }

    @BeforeEach
    fun initChangedDic() {
        changedDic = hashMapOf()
        changedDic[URL] = "postgres"
        changedDic[USER] = "openbug"
        changedDic[PASSWORD] = "openmuc"
    }

    @BeforeEach
    fun initDefaultDic() {
        defaultDic = hashMapOf()
        defaultDic[URL] = "jdbc:h2"
        defaultDic[USER] = "openmuc"
        defaultDic[PASSWORD] = "openmuc"
    }

    @AfterEach
    fun cleanUp() {
        loadDir!!.delete()
    }

    @Test
    fun throwExceptionIfPropertyInDicIsMissing() {
        changedDic = hashMapOf()
        val propertyHandler = PropertyHandler(
            settings!!, pid
        )
        changedDic[URL] = "postgres"
        changedDic[USER] = "openbug"
        val dict = DictionaryPreprocessor(changedDic)
        Assertions.assertThrows(ServicePropertyException::class.java) { propertyHandler.processConfig(dict) }
    }

    @get:Throws(ServicePropertyException::class)
    @get:Test
    val isDefaultAfterStartWithChangedConfig_false: Unit
        get() {
            val propertyHandler = PropertyHandler(
                settings!!, pid
            )
            val config = DictionaryPreprocessor(changedDic)
            propertyHandler.processConfig(config)
            Assertions.assertFalse(propertyHandler.isDefaultConfig)
        }

    @Test
    @Throws(ServicePropertyException::class)
    fun configChangedAfterStartWithChangedConfig_true() {
        val propertyHandler = PropertyHandler(
            settings!!, pid
        )
        val config = DictionaryPreprocessor(changedDic)
        propertyHandler.processConfig(config)
        Assertions.assertTrue(propertyHandler.configChanged())
    }

    @get:Throws(ServicePropertyException::class)
    @get:Test
    val isDefaultAfterStartWithDefaultConfig_true: Unit
        get() {
            val propertyHandler = PropertyHandler(
                settings!!, pid
            )
            val config = DictionaryPreprocessor(defaultDic)
            propertyHandler.processConfig(config)
            Assertions.assertTrue(propertyHandler.isDefaultConfig)
        }

    @Test
    @Throws(ServicePropertyException::class)
    fun configChangedAfterStartWithDefaultConfig_false() {
        val propertyHandler = PropertyHandler(
            settings!!, pid
        )
        val config = DictionaryPreprocessor(defaultDic)
        propertyHandler.processConfig(config)
        Assertions.assertFalse(propertyHandler.configChanged())
    }

    @Test
    fun toStringDoesNotShowPassword() {
        val propertyHandler = PropertyHandler(
            settings!!, pid
        )
        val config = DictionaryPreprocessor(defaultDic)
        Assertions.assertTrue(!propertyHandler.toString().contains("password=openmuc"))
        Assertions.assertTrue(propertyHandler.toString().contains("password=*****"))
    }

    @Test
    fun noMoreNullPointerExceptions() {
        val propertyHandler = PropertyHandler(
            settings!!, pid
        )
        val config = DictionaryPreprocessor(defaultDic)
        Assertions.assertFalse(propertyHandler.hasValueForKey("thisDoesNotExist"))
        Assertions.assertThrows(IllegalArgumentException::class.java) { propertyHandler.getBoolean("thisDoesNotExist") }
        Assertions.assertThrows(IllegalArgumentException::class.java) { propertyHandler.getDouble("thisDoesNotExist") }
        Assertions.assertThrows(IllegalArgumentException::class.java) { propertyHandler.getInt("thisDoesNotExist") }
        Assertions.assertThrows(IllegalArgumentException::class.java) { propertyHandler.getString("thisDoesNotExist") }
    }

    internal inner class Settings : GenericSettings() {
        init {
            properties[URL] = ServiceProperty(URL, "URL of the used database", "jdbc:h2", true)
            properties[USER] = ServiceProperty(USER, "User of the used database", "openmuc", true)
            properties[PASSWORD] = ServiceProperty(PASSWORD, "Password for the database user", "openmuc", true)
        }
    }
}
