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
package org.openmuc.framework.driver.iec61850

import org.openmuc.framework.config.ArgumentSyntaxException

class DeviceSettings(settings: String?) {
    var authentication: String? = null
    var tSelLocal = byteArrayOf(0, 0)
    var tSelRemote = byteArrayOf(0, 1)

    init {
        if (!settings!!.isEmpty()) {
            val args = settings.split("\\s+".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            if (args.size > 6 || args.size < 4) {
                throw ArgumentSyntaxException(
                    "Less than 4 or more than 6 arguments in the settings are not allowed."
                )
            }
            var i = 0
            while (i < args.size) {
                if (args[i] == "-a") {
                    if (args[i + 1] == "-lt") {
                        throw ArgumentSyntaxException(
                            "No authentication parameter was specified after the -a parameter"
                        )
                    }
                    authentication = args[i + 1]
                } else if (args[i] == "-lt") {
                    if (i == args.size - 1 || args[i + 1].startsWith("-")) {
                        tSelLocal = ByteArray(0)
                    } else {
                        tSelLocal = ByteArray(args[i + 1].length)
                        for (j in 0 until args[i + 1].length) {
                            tSelLocal[j] = args[i + 1][j].code.toByte()
                        }
                    }
                } else if (args[i] == "-rt") {
                    if (i == args.size - 1 || args[i + 1].startsWith("-")) {
                        tSelRemote = ByteArray(0)
                    } else {
                        tSelRemote = ByteArray(args[i + 1].length)
                        for (j in 0 until args[i + 1].length) {
                            tSelRemote[j] = args[i + 1][j].code.toByte()
                        }
                    }
                } else {
                    throw ArgumentSyntaxException("Unexpected argument: " + args[i])
                }
                i += 2
            }
        }
    }
}
