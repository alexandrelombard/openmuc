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
package org.openmuc.framework.lib.ssl

import org.openmuc.framework.lib.osgi.config.GenericSettings
import org.openmuc.framework.lib.osgi.config.ServiceProperty

internal class Settings : GenericSettings() {
    init {
        properties[KEYSTORE] = ServiceProperty(
            KEYSTORE,
            "path to the keystore",
            "conf/keystore.jks",
            true
        )
        properties[KEYSTORE_PASSWORD] = ServiceProperty(
            KEYSTORE_PASSWORD,
            "keystore password",
            "changeme",
            true
        )
        properties[TRUSTSTORE] = ServiceProperty(
            TRUSTSTORE,
            "path to the truststore",
            "conf/truststore.jks",
            true
        )
        properties[TRUSTSTORE_PASSWORD] = ServiceProperty(
            TRUSTSTORE_PASSWORD,
            "truststore password",
            "changeme",
            true
        )
    }

    companion object {
        const val KEYSTORE = "keystore"
        const val KEYSTORE_PASSWORD = "keystorepassword"
        const val TRUSTSTORE = "truststore"
        const val TRUSTSTORE_PASSWORD = "truststorepassword"
    }
}
