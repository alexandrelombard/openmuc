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
import org.slf4j.LoggerFactory
import java.io.File

class DeviceScanSettings(deviceScanSettings: String) : GenericSetting() {
    protected var path: String? = null
    private val file: File

    protected enum class Option(
        private val prefix: String,
        private val type: Class<*>,
        private val mandatory: Boolean
    ) : OptionI {
        PATH("path", String::class.java, true);

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
        if (deviceScanSettings.isEmpty()) {
            throw ArgumentSyntaxException("No scan settings specified.")
        } else {
            val addressLength = parseFields(deviceScanSettings, Option::class.java)
            if (addressLength == 0) {
                logger.info("No path given")
                throw ArgumentSyntaxException("<path> argument not found in settings.")
            }
        }
        if (path == null) {
            throw ArgumentSyntaxException("<path> argument not found in settings.")
        } else {
            if (!path.isEmpty()) {
                file = File(path)
                if (!file.isDirectory()) {
                    throw ArgumentSyntaxException("<path> argument must point to a directory.")
                }
            } else {
                throw ArgumentSyntaxException("<path> argument must point to a directory.")
            }
        }
    }

    fun path(): File? {
        return file
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DeviceScanSettings::class.java)
    }
}
