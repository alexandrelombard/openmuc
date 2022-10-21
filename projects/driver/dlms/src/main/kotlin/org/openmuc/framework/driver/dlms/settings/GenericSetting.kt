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
import org.slf4j.LoggerFactory
import java.lang.reflect.Field
import java.net.InetAddress
import java.net.UnknownHostException
import java.nio.charset.StandardCharsets
import java.text.MessageFormat
import java.text.NumberFormat
import java.text.ParseException

abstract class GenericSetting {
    @Throws(ArgumentSyntaxException::class)
    protected fun parseFields(settingsStr: String): Int {
        if (settingsStr.trim { it <= ' ' }.isEmpty()) {
            return 0
        }
        val settingsClass: Class<out GenericSetting> = this.javaClass
        val setting = toMap(settingsStr)
        var setFieldCounter = 0
        for (field in settingsClass.declaredFields) {
            val option = field.getAnnotation(
                Option::class.java
            ) ?: continue
            val `val` = setting[option.value]
            if (`val` != null) {
                try {
                    setField(field, `val`, option)
                    ++setFieldCounter
                } catch (e: IllegalAccessException) {
                    logger.error("Not able to access to field $field", e)
                } catch (e: NoSuchFieldException) {
                    logger.error("No field found with name $field", e)
                }
            } else if (option.mandatory) {
                val message = MessageFormat.format(
                    "Mandatory parameter {0} is nor present in {1}.", option.value,
                    this.javaClass.simpleName
                )
                throw ArgumentSyntaxException(message)
            }
        }
        return setFieldCounter
    }

    @Throws(ArgumentSyntaxException::class)
    private fun toMap(settingsStr: String?): Map<String, String?> {
        val settings = settingsStr!!.trim { it <= ' ' }.split(SEPARATOR.toRegex()).dropLastWhile { it.isEmpty() }
            .toTypedArray()
        val settingMap: MutableMap<String, String?> = HashMap(settings.size)
        for (setting in settings) {
            val s = setting.split(PAIR_SEP.toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray()
            if (s.size != 2) {
                val message = MessageFormat.format("Illegal setting ''{0}''.", setting)
                throw ArgumentSyntaxException(message)
            }
            val key = s[0].trim { it <= ' ' }
            if (settingMap.put(key, s[1].trim { it <= ' ' }) != null) {
                val message = MessageFormat.format("''{0}'' has been set twice.", key)
                throw ArgumentSyntaxException(message)
            }
        }
        return settingMap
    }

    @Throws(IllegalAccessException::class, NoSuchFieldException::class, ArgumentSyntaxException::class)
    private fun setField(field: Field, value: String, option: Option) {
        val newVal = extracted(field, value)
        field[this] = newVal
    }

    @Throws(ArgumentSyntaxException::class, IllegalAccessException::class, NoSuchFieldException::class)
    private fun extracted(field: Field, value: String): Any {
        val trimmed = value.trim { it <= ' ' }
        field.isAccessible = true
        val type = field.type
        return if (type.isAssignableFrom(Boolean::class.javaPrimitiveType) || type.isAssignableFrom(Boolean::class.java)) {
            extractBoolean(trimmed)
        } else if (type.isAssignableFrom(Byte::class.javaPrimitiveType) || type.isAssignableFrom(Byte::class.java)) {
            extractByte(trimmed)
        } else if (type.isAssignableFrom(Short::class.javaPrimitiveType) || type.isAssignableFrom(Short::class.java)) {
            extractShort(trimmed)
        } else if (type.isAssignableFrom(Int::class.javaPrimitiveType) || type.isAssignableFrom(Int::class.java)) {
            extractInteger(trimmed)
        } else if (type.isAssignableFrom(Long::class.javaPrimitiveType) || type.isAssignableFrom(Long::class.java)) {
            extractLong(trimmed)
        } else if (type.isAssignableFrom(Float::class.javaPrimitiveType) || type.isAssignableFrom(Float::class.java)) {
            extractFloat(trimmed)
        } else if (type.isAssignableFrom(Double::class.javaPrimitiveType) || type.isAssignableFrom(Double::class.java)) {
            extractDouble(trimmed)
        } else if (type.isAssignableFrom(String::class.java)) {
            value
        } else if (type.isAssignableFrom(ByteArray::class.java)) {
            extractByteArray(trimmed)
        } else if (type.isAssignableFrom(InetAddress::class.java)) {
            extractInetAddress(trimmed)
        } else {
            throw NoSuchFieldException(
                "$type  Driver implementation error not supported data type. Report driver developer\n"
            )
        }
    }

    @Throws(ArgumentSyntaxException::class)
    private fun extractBoolean(value: String): Boolean {
        return try {
            java.lang.Boolean.parseBoolean(value)
        } catch (e: NumberFormatException) {
            throw argumentSyntaxException(Boolean::class.javaPrimitiveType!!.simpleName)
        }
    }

    @Throws(ArgumentSyntaxException::class)
    private fun extractByte(value: String): Byte {
        return try {
            parseNumber(value).toByte()
        } catch (e: NumberFormatException) {
            throw argumentSyntaxException(Short::class.java.simpleName)
        } catch (e: ParseException) {
            throw argumentSyntaxException(Short::class.java.simpleName)
        }
    }

    @Throws(ArgumentSyntaxException::class)
    private fun extractShort(value: String): Short {
        return try {
            parseNumber(value).toShort()
        } catch (e: NumberFormatException) {
            throw argumentSyntaxException(Short::class.java.simpleName)
        } catch (e: ParseException) {
            throw argumentSyntaxException(Short::class.java.simpleName)
        }
    }

    @Throws(ArgumentSyntaxException::class)
    private fun extractInteger(value: String): Int {
        return try {
            parseNumber(value).toInt()
        } catch (e: NumberFormatException) {
            throw argumentSyntaxException(Int::class.java.simpleName)
        } catch (e: ParseException) {
            throw argumentSyntaxException(Int::class.java.simpleName)
        }
    }

    @Throws(ArgumentSyntaxException::class)
    private fun extractLong(value: String): Long {
        return try {
            parseNumber(value).toLong()
        } catch (e: NumberFormatException) {
            throw argumentSyntaxException(Long::class.java.simpleName)
        } catch (e: ParseException) {
            throw argumentSyntaxException(Long::class.java.simpleName)
        }
    }

    @Throws(ArgumentSyntaxException::class)
    private fun extractFloat(value: String): Float {
        return try {
            parseNumber(value).toFloat()
        } catch (e: NumberFormatException) {
            throw argumentSyntaxException(Float::class.java.simpleName)
        } catch (e: ParseException) {
            throw argumentSyntaxException(Float::class.java.simpleName)
        }
    }

    @Throws(ArgumentSyntaxException::class)
    private fun extractDouble(value: String): Double {
        return try {
            parseNumber(value).toDouble()
        } catch (e: NumberFormatException) {
            throw argumentSyntaxException(Double::class.java.simpleName)
        } catch (e: ParseException) {
            throw argumentSyntaxException(Double::class.java.simpleName)
        }
    }

    @Throws(ArgumentSyntaxException::class)
    private fun extractByteArray(value: String): ByteArray {
        return if (!value.startsWith("0x")) {
            value.toByteArray(StandardCharsets.US_ASCII)
        } else try {
            hexToBytes(value.substring(2).trim { it <= ' ' })
        } catch (e: IllegalArgumentException) {
            throw argumentSyntaxException(ByteArray::class.java.simpleName)
        }
    }

    private fun hexToBytes(s: String): ByteArray {
        val b = ByteArray(s.length / 2)
        var index: Int
        for (i in b.indices) {
            index = i * 2
            b[i] = s.substring(index, index + 2).toInt(16).toByte()
        }
        return b
    }

    @Throws(ArgumentSyntaxException::class)
    private fun extractInetAddress(value: String): InetAddress {
        return try {
            InetAddress.getByName(value)
        } catch (e: UnknownHostException) {
            throw argumentSyntaxException(InetAddress::class.java.simpleName)
        }
    }

    private fun argumentSyntaxException(returnType: String): ArgumentSyntaxException {
        return ArgumentSyntaxException(
            MessageFormat.format(
                "Value of {0} in {1} is not type of {2}.", "error",
                this.javaClass.simpleName, returnType
            )
        )
    }

    @MustBeDocumented
    @kotlin.annotation.Retention(AnnotationRetention.RUNTIME)
    @Target(AnnotationTarget.FIELD)
    annotation class Option(val value: String, val mandatory: Boolean = false, val range: String = "")
    companion object {
        private val logger = LoggerFactory.getLogger(GenericSetting::class.java)
        private const val SEPARATOR = ";"
        private const val PAIR_SEP = "="
        @JvmStatic
        fun <T : GenericSetting?> strSyntaxFor(settings: Class<T>): String {
            val sbOptinal = StringBuilder()
            val sb = StringBuilder().append("SYNOPSIS: ")
            var first = true
            for (field in settings.declaredFields) {
                val option = field.getAnnotation(
                    Option::class.java
                ) ?: continue
                val str = strFor(option, first)
                sbOptinal.append(str)
                first = false
            }
            sb.append(sbOptinal)
            return sb.toString().trim { it <= ' ' }
        }

        private fun strFor(option: Option, first: Boolean): String {
            val sb = StringBuilder()
            val value: String = option.value
            val mandatory: Boolean = option.mandatory
            if (!mandatory) {
                sb.append('[')
            }
            if (!first) {
                sb.append(SEPARATOR)
            }
            val range: String
            if (option.range.isEmpty()) {
                range = option.value
            } else {
                range = option.range
            }
            sb.append(MessageFormat.format("{0}{1}<{2}>", value, PAIR_SEP, range))
            if (!mandatory) {
                sb.append(']')
            }
            return sb.append(' ').toString()
        }

        @Throws(ParseException::class)
        private fun parseNumber(value: String): Number {
            return NumberFormat.getNumberInstance().parse(value)
        }
    }
}
