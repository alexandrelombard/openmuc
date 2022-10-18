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
package org.openmuc.framework.lib.parser.openmuc

import org.openmuc.framework.parser.spi.ParserService
import org.osgi.framework.BundleContext
import org.osgi.framework.ServiceRegistration
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Deactivate
import java.util.*

@Component
class OpenMucParserComponent {
    private var registration: ServiceRegistration<*>? = null
    @Activate
    fun activate(context: BundleContext) {
        val properties: Dictionary<String, Any> = Hashtable()
        properties.put("parserID", "openmuc")
        val serviceName = ParserService::class.java.name
        registration = context.registerService(serviceName, OpenmucParserServiceImpl(), properties)
    }

    @Deactivate
    fun deactivate() {
        registration!!.unregister()
    }
}
