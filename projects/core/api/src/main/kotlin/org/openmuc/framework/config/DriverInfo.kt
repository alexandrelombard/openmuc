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
package org.openmuc.framework.config

class DriverInfo
/**
 * Constructor to set driver info
 *
 * @param id
 * driver ID
 * @param description
 * driver description
 * @param deviceAddressSyntax
 * device address syntax
 * @param settingsSyntax
 * device settings syntax
 * @param channelAddressSyntax
 * channel address syntax
 * @param deviceScanSettingsSyntax
 * device scan settings syntax
 */(
    /**
     * Returns the ID of the driver. The ID may only contain ASCII letters, digits, hyphens and underscores. By
     * convention the ID should be meaningful and all lower case letters (e.g. "mbus", "modbus").
     *
     * @return the unique ID of the driver.
     */
    val id: String, val description: String, val deviceAddressSyntax: String, val settingsSyntax: String,
    val channelAddressSyntax: String, val deviceScanSettingsSyntax: String
)
