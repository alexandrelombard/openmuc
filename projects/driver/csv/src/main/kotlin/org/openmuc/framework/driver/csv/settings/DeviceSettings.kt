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
package org.openmuc.framework.driver.csv.settings

import org.openmuc.framework.config.ArgumentSyntaxException
import org.openmuc.framework.driver.csv.ESamplingMode
import org.slf4j.LoggerFactory
import java.util.*

class DeviceSettings(deviceScanSettings: String?) : GenericSetting() {
    protected var samplingmode = ""
    protected var rewind = "false"
    private var samplingModeParam: ESamplingMode? = null
    private var rewindParam = false

    enum class Option(private val prefix: String, private val type: Class<*>, private val mandatory: Boolean) :
        OptionI {
        SAMPLINGMODE("samplingmode", String::class.java, true), REWIND("rewind", String::class.java, false);

        override fun prefix(): String {
            return prefix
        }

        override fun type(): Class<*> {
            return type
        }

        override fun mandatory(): Boolean {
            return mandatory
        }
    }

    init {
        val addressLength = parseFields(deviceScanSettings!!, Option::class.java)
        if (addressLength == 0) {
            logger.info("No Sampling mode given")
        }
        samplingModeParam = try {
            ESamplingMode.valueOf(samplingmode.uppercase(Locale.getDefault()))
        } catch (e: Exception) {
            throw ArgumentSyntaxException("wrong sampling mode")
        }
        rewindParam = try {
            java.lang.Boolean.parseBoolean(rewind)
        } catch (e: Exception) {
            throw ArgumentSyntaxException("wrong rewind parameter syntax")
        }
    }

    fun samplingMode(): ESamplingMode? {
        return samplingModeParam
    }

    fun rewind(): Boolean {
        return rewindParam
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DeviceSettings::class.java)
    }
}
