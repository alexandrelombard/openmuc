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
package org.openmuc.framework.data

import junitparams.JUnitParamsRunner
import junitparams.Parameters
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(JUnitParamsRunner::class)
class StringValueTest {
    @Test
    @Parameters(method = "params")
    @Throws(Exception::class)
    fun testBooleanConvert(value: String, expected: Boolean) {
        Assert.assertEquals(expected, StringValue(value).asBoolean())
    }

    fun params(): Any {
        return arrayOf(
            arrayOf<Any>("false", false),
            arrayOf<Any>("true", true),
            arrayOf<Any>("jhbvce", false),
            arrayOf<Any>("TRUE", true),
            arrayOf<Any>("TRuE", true)
        )
    }

    @Test(expected = TypeConversionException::class)
    @Throws(Exception::class)
    fun testException() {
        StringValue("98394kdbk").asDouble()
    }
}
