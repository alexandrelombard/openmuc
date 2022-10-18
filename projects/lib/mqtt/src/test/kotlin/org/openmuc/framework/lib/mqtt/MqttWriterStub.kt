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
package org.openmuc.framework.lib.mqtt

import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish
import java.util.concurrent.CompletableFuture

/**
 * MqttWriter stub that simulates successful publishes when connection is simulated as connected
 */
class MqttWriterStub(connection: MqttConnection?) : MqttWriter(connection!!, "test") {
    override fun publish(topic: String?, message: ByteArray?): CompletableFuture<Mqtt3Publish?> {
        val future = CompletableFuture<Mqtt3Publish?>()
        future.complete(Mqtt3Publish.builder().topic("test").build())
        return future
    }
}
