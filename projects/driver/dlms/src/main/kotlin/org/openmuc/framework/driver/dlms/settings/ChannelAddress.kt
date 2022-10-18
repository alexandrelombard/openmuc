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
package org.openmuc.framework.driver.dlms.settings

import org.openmuc.framework.config.ArgumentSyntaxException
import org.openmuc.framework.driver.spi.ChannelValueContainer.value
import org.openmuc.jdlms.AttributeAddress
import org.openmuc.jdlms.ObisCode
import org.openmuc.jdlms.datatypes.DataObject
import java.util.*

class ChannelAddress(channelAddress: String?) : GenericSetting() {
    @Option(value = "a", mandatory = true, range = LOGICAL_NAME_FORMAT)
    val address: String? = null

    @Option(value = "t", range = "DataObject.Type")
    private val type: String? = null
    var attributeAddress: AttributeAddress? = null
    private var dataObjectType: DataObject.Type? = null

    init {
        val optionsNumber = parseFields(channelAddress)
        if (optionsNumber > 2) {
            throw ArgumentSyntaxException("Too many arguments given.")
        } else if (optionsNumber < 1) {
            throw ArgumentSyntaxException("Attribute address must be provided.")
        }
        attributeAddress = try {
            buildlAttributeAddress(address)
        } catch (e: NumberFormatException) {
            throw ArgumentSyntaxException("Class ID or Attribute ID is not a number.")
        } catch (e: IllegalArgumentException) {
            throw ArgumentSyntaxException(e.message)
        }
        try {
            if (type != null) {
                dataObjectType = typeFrom(type)
            }
        } catch (e: IllegalArgumentException) {
            throw ArgumentSyntaxException("Type of DataObject is unknown.")
        }
    }

    fun getType(): DataObject.Type? {
        return dataObjectType
    }

    companion object {
        private const val LOGICAL_NAME_FORMAT = "<Interface_Class_ID>/<Instance_ID>/<Object_Attribute_ID>"
        @Throws(IllegalArgumentException::class)
        private fun typeFrom(typeAsString: String): DataObject.Type {
            return DataObject.Type.valueOf(typeAsString.uppercase(Locale.getDefault()).trim { it <= ' ' })
        }

        @Throws(IllegalArgumentException::class, NumberFormatException::class)
        private fun buildlAttributeAddress(requestParameter: String?): AttributeAddress {
            val arguments = requestParameter!!.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            if (arguments.size != 3) {
                val msg = String.format("Wrong number of DLMS/COSEM address arguments. %s", LOGICAL_NAME_FORMAT)
                throw IllegalArgumentException(msg)
            }
            val classId = arguments[0].toInt()
            val instanceId = ObisCode(arguments[1])
            val attributeId = arguments[2].toInt()
            return AttributeAddress(classId, instanceId, attributeId)
        }
    }
}
