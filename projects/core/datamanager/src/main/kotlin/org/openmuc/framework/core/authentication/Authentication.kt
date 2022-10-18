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
package org.openmuc.framework.core.authentication

import org.openmuc.framework.authentication.AuthenticationService
import org.osgi.framework.InvalidSyntaxException
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope
import org.osgi.service.useradmin.Group
import org.osgi.service.useradmin.Role
import org.osgi.service.useradmin.User
import org.osgi.service.useradmin.UserAdmin
import org.slf4j.LoggerFactory
import java.io.*
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.*

@Component(service = [AuthenticationService::class], scope = ServiceScope.SINGLETON)
class Authentication : AuthenticationService {
    private val shadow: Map<String, String> = HashMap()
    private var path: String? = null
    private var userAdmin: UserAdmin? = null
    private var userAdminInitiated = false
    @Activate
    fun activate() {
        path = initPath()
        userAdminInitiated = false
    }

    override fun register(user: String?, pw: String?, group: String?) {
        var pw = pw
        logger.info("register")
        pw += generateHash(user) // use the hash of the username as salt
        val hash = generateHash(pw)
        setUserHashPair(user, hash, group)
    }

    override fun registerNewUser(user: String?, pw: String?) {
        var pw = pw
        pw += generateHash(user) // use the hash of the username as salt
        val hash = generateHash(pw)
        setUserHashPair(user, hash, "normal")
        writeShadowToFile()
    }

    override fun login(userName: String?, pw: String?): Boolean {
        initUserAdminIfNotDone()
        // use the hash of the username as salt
        val pwToCheck = pw + generateHash(userName)
        val hash = generateHash(pwToCheck)
        val user = userAdmin!!.getUser("name", userName)
        return user.properties["password"] == hash
    }

    override fun delete(user: String?) {
        userAdmin!!.removeRole(user)
        writeShadowToFile()
    }

    override fun contains(user: String?): Boolean {
        return allUsers.contains(user)
    }

    override val allUsers: Set<String?>
        get() {
            val registeredUsers: MutableSet<String?> = HashSet()
            val allRoles = allRoleObjects
            for (role in Arrays.asList(*allRoles)) {
                val user = role as User
                val userName = user.properties["name"] as String
                if (userName != null) {
                    registeredUsers.add(userName)
                }
            }
            return registeredUsers
        }

    private fun setUserHashPair(user: String?, hash: String, group: String?) {
        var newUser = userAdmin!!.createRole(user, Role.USER) as User
        var grp = userAdmin!!.createRole(group, Role.GROUP) as Group
        if (grp == null) {
            grp = userAdmin!!.getRole(group) as Group
        }
        if (newUser == null) {
            newUser = userAdmin!!.getRole(user) as User
        }
        val properties: Dictionary<String, String?> = newUser.properties
        properties.put("name", user)
        properties.put("password", hash)
        properties.put("group", group)
        grp.addMember(newUser)
    }

    override fun writeShadowToFile() {
        val allRoles = allRoleObjects
        val textSb = prepareStringBuilder(allRoles)
        try {
            BufferedWriter(FileWriter(File(path))).use { output ->
                output.write(textSb.toString())
                output.flush()
            }
        } catch (e: IOException) {
            logger.warn("Failed to write shadow.", e)
        }
    }

    private val allRoleObjects: Array<Role>?
        private get() {
            var allUser: Array<Role>? = null
            try {
                allUser = userAdmin!!.getRoles(null)
            } catch (e: InvalidSyntaxException) {
                logger.error(e.message)
            }
            return allUser
        }

    private fun prepareStringBuilder(allUser: Array<Role>?): StringBuilder {
        val textSb = StringBuilder()
        for (role in Arrays.asList(*allUser)) {
            val user = role as User
            if (user.properties["name"] != null) {
                textSb.append(user.properties["name"].toString() + ";")
                textSb.append(user.properties["password"].toString() + ";")
                textSb.append(
                    """
    ${user.properties["group"]};
    
    """.trimIndent()
                )
            }
        }
        return textSb
    }

    private fun loadShadowFromFile() {
        try {
            BufferedReader(FileReader(path)).use { reader ->
                var line: String
                while (reader.readLine().also { line = it } != null) {
                    val temp = line.split(";".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    setUserHashPair(temp[0], temp[1], temp[2])
                }
            }
        } catch (e: IOException) {
            logger.warn("Failed to load shadow.", e)
        }
    }

    override fun isUserAdmin(userName: String?): Boolean {
        val user = userAdmin!!.getUser("name", userName)
        val loggedUser = userAdmin!!.getAuthorization(user)
        return loggedUser.hasRole("admin")
    }

    @Reference
    protected fun setUserAdmin(userAdmin: UserAdmin?) {
        this.userAdmin = userAdmin
    }

    protected fun unsetUserAdmin(userAdmin: UserAdmin?) {}
    private fun initUserAdminIfNotDone() {
        if (userAdminInitiated) {
            return
        }
        val file = File(path)
        if (!file.exists()) {
            register("admin", "admin", "adminGrp")
            writeShadowToFile()
        } else {
            loadShadowFromFile()
        }
        userAdminInitiated = true
    }

    companion object {
        private const val DEFAULT_SHADOW_FILE_LOCATION = "conf/shadow"
        private val logger = LoggerFactory.getLogger(Authentication::class.java)
        private fun initPath(): String {
            var path = System.getProperty("bundles.configuration.location")
                ?: return DEFAULT_SHADOW_FILE_LOCATION
            if (path.endsWith("/")) {
                path = path.substring(0, path.length - 1)
            }
            return "$path/shadow"
        }

        private fun generateHash(pw: String?): String {
            return try {
                val sha256 = MessageDigest.getInstance("SHA-256")
                val hashedBytes = sha256.digest(pw!!.toByteArray())
                bytesToHexString(hashedBytes)
            } catch (e: NoSuchAlgorithmException) {
                // should not occur.
                logger.error("Failed to generate hash.", e)
                ""
            }
        }

        private fun bytesToHexString(hashedBytes: ByteArray): String {
            val hash = StringBuilder()
            for (hashedByte in hashedBytes) {
                hash.append(String.format("%02x", hashedByte))
            }
            return hash.toString()
        }
    }
}
