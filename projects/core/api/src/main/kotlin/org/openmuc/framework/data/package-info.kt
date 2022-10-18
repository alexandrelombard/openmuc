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
/**
 * This package contains data/value containers.
 *
 * @see org.openmuc.framework.data.Value
 *
 * @see org.openmuc.framework.data.ValueType
 */
package org.openmuc.framework.data

import java.util.HashMap
import java.lang.NumberFormatException
import java.nio.charset.Charset
import java.lang.RuntimeException
import org.openmuc.framework.dataaccess.DeviceState
import org.openmuc.framework.dataaccess.RecordListener
import org.openmuc.framework.data.FutureValue
import org.openmuc.framework.dataaccess.WriteValueContainer
import org.openmuc.framework.dataaccess.ReadRecordContainer
import java.io.IOException
import org.openmuc.framework.dataaccess.ChannelChangeListener
import org.openmuc.framework.dataaccess.LogicalDeviceChangeListener
import java.lang.Exception
import org.openmuc.framework.security.SslConfigChangeListener
