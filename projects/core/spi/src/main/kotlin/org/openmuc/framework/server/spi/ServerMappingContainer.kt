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
package org.openmuc.framework.server.spi

import org.openmuc.framework.config.ServerMapping
import org.openmuc.framework.dataaccess.Channel

/**
 * Class that contains the mapping between a server-address/configuration and channel.
 *
 */
class ServerMappingContainer(
    /**
     * The mapped Channel
     *
     * @return the channel
     */
    val channel: Channel,
    /**
     * The serverMapping that the channel should be mapped to.
     *
     * @return the serverAddress
     */
    val serverMapping: ServerMapping
)
