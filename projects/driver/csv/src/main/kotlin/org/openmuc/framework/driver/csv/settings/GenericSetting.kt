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
package org.openmuc.framework.driver.csv.settings

import org.openmuc.framework.config.ArgumentSyntaxException
import org.slf4j.LoggerFactory
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.net.InetAddress
import java.net.UnknownHostException
import java.text.MessageFormat
import java.util.*

abstract class GenericSetting {
    interface OptionI {
        fun prefix(): String
        fun type(): Class<*>?
        fun mandatory(): Boolean
    }

    /**
     * Example Option Enum
     */
    private enum class Option(private val prefix: String, private val type: Class<*>, private val mandatory: Boolean) :
        OptionI {
        EXAMPLE0("ex0", Int::class.java, false), EXAMPLE1("ex1", String::class.java, true);

        override fun prefix(): String {
            return prefix
        }

        override fun type(): Class<*> {
            return type
        }

        override fun mandatory(): Boolean {
            return mandatory
        }
    }

    @Synchronized
    @Throws(ArgumentSyntaxException::class)
    fun parseFields(settings: String, options: Class<out Enum<out OptionI?>>): Int {
        val enclosingClassName = options.enclosingClass.simpleName
        val enumValuesLength = options.enumConstants.size
        val prefixMethod: Method
        val typeMethod: Method
        val mandatorylMethod: Method
        val settingsArray = settings.trim { it <= ' ' }.split(SEPARATOR.toRegex()).dropLastWhile { it.isEmpty() }
            .toTypedArray()
        val settingsArrayLength = settingsArray.size
        if (settingsArrayLength >= 1 && settingsArrayLength <= enumValuesLength) {
            try {
                prefixMethod = options.getMethod(PREFIX)
                typeMethod = options.getMethod(TYPE)
                mandatorylMethod = options.getMethod(MANDATORY)
            } catch (e: NoSuchMethodException) {
                throw ArgumentSyntaxException(
                    """
    Driver implementation error, '$enclosingClassName' problem to find method in implementation. Report driver developer.
    $e
    """.trimIndent()
                )
            } catch (e: SecurityException) {
                throw ArgumentSyntaxException(
                    """
    Driver implementation error, '$enclosingClassName' problem to find method in implementation. Report driver developer.
    $e
    """.trimIndent()
                )
            }
            try {
                for (option in options.enumConstants) {
                    val prefix = prefixMethod.invoke(option) as String
                    val type = typeMethod.invoke(option) as Class<*>
                    val mandatory = mandatorylMethod.invoke(option) as Boolean
                    var noOptionsPresent = true
                    var setting = ""
                    for (singlesetting in settingsArray) {
                        setting = singlesetting.trim { it <= ' ' }
                        val pair = setting.split(PAIR_SEP.toRegex()).dropLastWhile { it.isEmpty() }
                            .toTypedArray()
                        val pairLength = pair.size
                        if (mandatory && pairLength != 2) {
                            throw ArgumentSyntaxException(
                                "Parameter in " + enclosingClassName
                                        + " is not a pair of prefix and value: <prefix>" + PAIR_SEP + "<value> "
                            )
                        }
                        if (pairLength == 2 && pair[0].trim { it <= ' ' }.equals(prefix, ignoreCase = true)) {
                            try {
                                noOptionsPresent = false
                                setField(pair[1], option.name, type, options)
                            } catch (e: NoSuchFieldException) {
                                throw ArgumentSyntaxException(
                                    """
    Driver implementation error, '$enclosingClassName' has no corresponding field for parameter $setting. Report driver developer.
    $e
    """.trimIndent()
                                )
                            } catch (e: IllegalAccessException) {
                                throw ArgumentSyntaxException(
                                    """
    Driver implementation error, '$enclosingClassName' has no corresponding field for parameter $setting. Report driver developer.
    $e
    """.trimIndent()
                                )
                            }
                        }
                    }
                    if (noOptionsPresent && mandatory) {
                        throw ArgumentSyntaxException(
                            "Mandatory parameter " + option.name + " is not present in "
                                    + this.javaClass.simpleName
                        )
                    }
                }
            } catch (e: IllegalAccessException) {
                throw ArgumentSyntaxException(
                    """
                        Driver implementation error, '${options.name.lowercase(LOCALE)}' problem to invoke method. Report driver developer.
                        $e
                        """.trimIndent()
                )
            } catch (e: IllegalArgumentException) {
                throw ArgumentSyntaxException(
                    """
                        Driver implementation error, '${options.name.lowercase(LOCALE)}' problem to invoke method. Report driver developer.
                        $e
                        """.trimIndent()
                )
            } catch (e: InvocationTargetException) {
                throw ArgumentSyntaxException(
                    """
                        Driver implementation error, '${options.name.lowercase(LOCALE)}' problem to invoke method. Report driver developer.
                        $e
                        """.trimIndent()
                )
            }
        } else if (settingsArrayLength > enumValuesLength) {
            throw ArgumentSyntaxException("Too much parameters in $enclosingClassName.")
        }
        return settingsArrayLength
    }

    @Synchronized
    @Throws(IllegalAccessException::class, NoSuchFieldException::class, ArgumentSyntaxException::class)
    private fun setField(
        value: String,
        enumName: String,
        type: Class<*>,
        options: Class<out Enum<out OptionI?>>
    ) {
        var value = value
        val optionName = enumName.lowercase(LOCALE)
        value = value.trim { it <= ' ' }
        when (type.simpleName) {
            "Boolean" -> options.declaringClass.getDeclaredField(optionName)
                .setBoolean(this, extractBoolean(value, enumName))

            "Short" -> options.declaringClass.getDeclaredField(optionName).setShort(this, extractShort(value, enumName))
            "Integer" -> options.declaringClass.getDeclaredField(optionName)
                .setInt(this, extractInteger(value, enumName))

            "Long" -> options.declaringClass.getDeclaredField(optionName).setLong(this, extractLong(value, enumName))
            "Float" -> options.declaringClass.getDeclaredField(optionName).setFloat(this, extractFloat(value, enumName))
            "Double" -> options.declaringClass.getDeclaredField(optionName)
                .setDouble(this, extractDouble(value, enumName))

            "String" -> options.declaringClass.getDeclaredField(optionName)[this] = value
            "InetAddress" -> options.declaringClass.getDeclaredField(optionName)[this] =
                extractInetAddress(value, enumName)

            else -> throw NoSuchFieldException(
                """
    Driver implementation error, '${enumName.lowercase(LOCALE)}' not supported data type. Report driver developer
    
    """.trimIndent()
            )
        }
    }

    @Synchronized
    @Throws(ArgumentSyntaxException::class)
    private fun extractBoolean(value: String, errorMessage: String): Boolean {
        var ret = false
        try {
            ret = java.lang.Boolean.getBoolean(value)
        } catch (e: NumberFormatException) {
            argumentSyntaxException(errorMessage, ret.javaClass.simpleName)
        }
        return ret
    }

    @Synchronized
    @Throws(ArgumentSyntaxException::class)
    private fun extractShort(value: String, errorMessage: String): Short {
        var ret: Short = 0
        try {
            ret = java.lang.Short.decode(value)
        } catch (e: NumberFormatException) {
            argumentSyntaxException(errorMessage, ret.javaClass.simpleName)
        }
        return ret
    }

    @Synchronized
    @Throws(ArgumentSyntaxException::class)
    private fun extractInteger(value: String, errorMessage: String): Int {
        var ret = 0
        try {
            ret = Integer.decode(value)
        } catch (e: NumberFormatException) {
            argumentSyntaxException(errorMessage, ret.javaClass.simpleName)
        }
        return ret
    }

    @Synchronized
    @Throws(ArgumentSyntaxException::class)
    private fun extractLong(value: String, errorMessage: String): Long {
        var ret = 0L
        try {
            ret = java.lang.Long.decode(value)
        } catch (e: NumberFormatException) {
            argumentSyntaxException(errorMessage, ret.javaClass.simpleName)
        }
        return ret
    }

    @Synchronized
    @Throws(ArgumentSyntaxException::class)
    private fun extractFloat(value: String, errorMessage: String): Float {
        var ret = 0f
        try {
            ret = value.toFloat()
        } catch (e: NumberFormatException) {
            argumentSyntaxException(errorMessage, ret.javaClass.simpleName)
        }
        return ret
    }

    @Synchronized
    @Throws(ArgumentSyntaxException::class)
    private fun extractDouble(value: String, errorMessage: String): Double {
        var ret = 0.0
        try {
            ret = value.toDouble()
        } catch (e: NumberFormatException) {
            argumentSyntaxException(errorMessage, ret.javaClass.simpleName)
        }
        return ret
    }

    @Synchronized
    @Throws(ArgumentSyntaxException::class)
    private fun extractInetAddress(value: String, errorMessage: String): InetAddress? {
        var ret: InetAddress? = null
        try {
            ret = InetAddress.getByName(value)
        } catch (e: UnknownHostException) {
            argumentSyntaxException(errorMessage, "InetAddress")
        }
        return ret
    }

    @Synchronized
    @Throws(ArgumentSyntaxException::class)
    private fun argumentSyntaxException(errorMessage: String, returnType: String) {
        throw ArgumentSyntaxException(
            MessageFormat.format(
                "Value of {0} in {1} is not type of {2}.", errorMessage,
                this.javaClass.simpleName, returnType
            )
        )
    }

    companion object {
        private const val SEPARATOR = ";"
        private const val PAIR_SEP = "="
        private const val PREFIX = "prefix"
        private const val TYPE = "type"
        private const val MANDATORY = "mandatory"
        private val LOCALE = Locale.ENGLISH
        private val logger = LoggerFactory.getLogger(GenericSetting::class.java)
        fun syntax(genericSettings: Class<out GenericSetting?>): String {
            val options = genericSettings
                .declaredClasses[0] as Class<Enum<out OptionI>>
            val sb = StringBuilder()
            val sbNotMandetory = StringBuilder()
            if (options == null) {
                val errorMessage = ("Driver implementation error, in method syntax(). Could not find class "
                        + genericSettings.simpleName + ". Report driver developer.")
                logger.error(errorMessage)
                sb.append(errorMessage)
            } else {
                sb.append("Synopsis:")
                var first = true
                try {
                    val valueMethod = options.getMethod(PREFIX)
                    val mandatorylMethod = options.getMethod(MANDATORY)
                    for (option in options.enumConstants) {
                        val mandatory = mandatorylMethod.invoke(option) as Boolean
                        val value = valueMethod.invoke(option) as String
                        if (mandatory) {
                            if (!first) {
                                sb.append(SEPARATOR)
                            }
                            first = false
                            sb.append(' '.toString() + value + PAIR_SEP + " <" + option.name.lowercase(LOCALE) + '>')
                        } else {
                            sbNotMandetory.append(
                                " [" + SEPARATOR + value + PAIR_SEP + " <" + option.name.lowercase(LOCALE) + ">]"
                            )
                        }
                    }
                    sb.append(sbNotMandetory)
                } catch (e: IllegalArgumentException) {
                    val errorMessage =
                        "Driver implementation error, in method syntax(). Could not find method. Report driver developer."
                    logger.error(errorMessage)
                    sb.append(errorMessage)
                } catch (e: IllegalAccessException) {
                    val errorMessage =
                        "Driver implementation error, in method syntax(). Could not find method. Report driver developer."
                    logger.error(errorMessage)
                    sb.append(errorMessage)
                } catch (e: InvocationTargetException) {
                    val errorMessage =
                        "Driver implementation error, in method syntax(). Could not find method. Report driver developer."
                    logger.error(errorMessage)
                    sb.append(errorMessage)
                } catch (e: NoSuchMethodException) {
                    val errorMessage =
                        "Driver implementation error, in method syntax(). Could not find method. Report driver developer."
                    logger.error(errorMessage)
                    sb.append(errorMessage)
                } catch (e: SecurityException) {
                    val errorMessage =
                        "Driver implementation error, in method syntax(). Could not find method. Report driver developer."
                    logger.error(errorMessage)
                    sb.append(errorMessage)
                }
            }
            return sb.toString()
        }
    }
}
