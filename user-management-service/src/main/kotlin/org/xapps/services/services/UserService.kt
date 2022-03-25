package org.xapps.services.services

import at.favre.lib.crypto.bcrypt.BCrypt
import org.jetbrains.exposed.dao.load
import org.jetbrains.exposed.dao.with
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.xapps.services.daos.DatabaseUtils
import org.xapps.services.daos.tables.RoleEntity
import org.xapps.services.daos.tables.UserEntity
import org.xapps.services.daos.tables.Users
import org.xapps.services.exceptions.EmailNotAvailable
import org.xapps.services.exceptions.InvalidCredentialsException
import org.xapps.services.exceptions.NotFoundException
import org.xapps.services.models.Login
import org.xapps.services.models.Role
import org.xapps.services.models.User
import org.xapps.services.utils.PropertiesProvider
import java.io.Closeable

class UserService(private val db: Database) : Closeable {

    companion object {
        @JvmStatic
        val instance: UserService by lazy {
            UserService(DatabaseUtils.databaseInstance)
        }
    }

    fun init() = transaction(db) {
        SchemaUtils.create(Users)
    }

    fun seed() = transaction(db) {
        if (UserEntity.count() == 0L) {
            val administratorRole = RoleService.instance.administrator()
            UserEntity.new {
                firstName = PropertiesProvider.instance.defaultRootFirstName
                lastName = PropertiesProvider.instance.defaultRootLastname
                email = PropertiesProvider.instance.defaultRootEmail
                password = BCrypt.withDefaults().hashToString(
                    PropertiesProvider.instance.securityHashRounds,
                    PropertiesProvider.instance.defaultRootPassword.toCharArray()
                )
                roles = SizedCollection(listOf(administratorRole!!))
            }
        }
    }

    fun readAll(): List<User> = transaction(db) {
        val users = UserEntity.all().with(UserEntity::roles)
            .map {
                User(
                    id = it.id.value,
                    firstName = it.firstName,
                    lastName = it.lastName,
                    email = it.email,
                    password = it.password,
                    roles = it.roles.map { r ->
                        Role(
                            id = r.id.value,
                            name = r.name
                        )
                    }
                )
            }
            .toList()
        users
    }

    fun read(id: Long): User = transaction(db) {
        val user = UserEntity.findById(id)?.load(UserEntity::roles)
        user?.let {
            User(
                id = it.id.value,
                firstName = it.firstName,
                lastName = it.lastName,
                email = it.email,
                password = it.password,
                roles = it.roles.map { r ->
                    Role(
                        id = r.id.value,
                        name = r.name
                    )
                }
            )
        } ?: run {
            throw NotFoundException("No user found with id $id")
        }
    }

    fun validateLogin(login: Login): User = transaction(db) {
        val user = UserEntity.find { Users.email eq login.email }.with(UserEntity::roles).singleOrNull()
        if(user != null) {
            val result = BCrypt.verifyer().verify(login.password.toCharArray(), user.password)
            if(result.verified) {
                User(
                    id = user.id.value,
                    firstName = user.firstName,
                    lastName = user.lastName,
                    email = user.email,
                    password = user.password,
                    roles = user.roles.map { r -> Role(id = r.id.value, name = r.name) }
                )
            } else {
                throw InvalidCredentialsException("Invalid credentials")
            }
        } else {
            throw InvalidCredentialsException("Invalid credentials")
        }
    }

    fun isAdministrator(user: User): Boolean {
        return user.roles?.any { r -> r.name == Role.ADMINISTRATOR } ?: false
    }

    fun hasAdministratorRole(user: User): Boolean {
        return isAdministrator(user)
    }

    fun create(user: User): User = transaction(db) {
        val emailDuplicated = Users.select { Users.email eq user.email }.singleOrNull()
        if (emailDuplicated == null) {
            var newRoles: List<RoleEntity>? = null
            if (user.roles != null && user.roles?.isNotEmpty() == true) {
                newRoles = RoleService.instance.findByNames(user.roles!!.map(Role::name))
            }
            if (newRoles == null || newRoles.isEmpty()) {
                RoleService.instance.guest()?.let {
                    newRoles = listOf(it)
                }
            }
            val newUser = UserEntity.new {
                firstName = user.firstName
                lastName = user.lastName
                email = user.email
                password = BCrypt.withDefaults().hashToString(PropertiesProvider.instance.securityHashRounds, user.password!!.toCharArray())
                roles = SizedCollection(newRoles!!)
            }
            User(
                id = newUser.id.value,
                firstName = newUser.firstName,
                lastName = newUser.lastName,
                email = newUser.email,
                password = newUser.password,
                roles = newUser.roles.map { r -> Role(id = r.id.value, name = r.name) }.toList()
            )
        } else {
            throw EmailNotAvailable("Email ${user.email} is not available")
        }
    }

    fun update(id: Long, user: User): User = transaction(db) {
        val emailDuplicated = Users.select { (Users.email eq user.email) and (Users.id neq id) }.singleOrNull()
        if (emailDuplicated == null) {
            val presentUser = UserEntity.findById(id)?.load(UserEntity::roles)
            if (presentUser != null) {
                presentUser.firstName = user.firstName
                presentUser.lastName = user.lastName
                presentUser.email = user.email
                user.password?.let {
                    presentUser.password = BCrypt.withDefaults().hashToString(PropertiesProvider.instance.securityHashRounds, it.toCharArray())
                }
                var newRoles: List<RoleEntity>? = null
                if (user.roles != null && user.roles?.isNotEmpty() == true) {
                    newRoles = RoleService.instance.findByNames(user.roles!!.map(Role::name))
                }
                if (newRoles != null && newRoles.isNotEmpty()) {
                    presentUser.roles = SizedCollection(newRoles)
                }
                presentUser.refresh(flush = true)

                User(
                    id = presentUser.id.value,
                    firstName = presentUser.firstName,
                    lastName = presentUser.lastName,
                    email = presentUser.email,
                    password = presentUser.password,
                    roles = presentUser.roles.map { r -> Role(id = r.id.value, name = r.name) }.toList()
                )
            } else {
                throw NotFoundException("No user found with id $id")
            }
        } else {
            throw EmailNotAvailable("Email ${user.email} is not available")
        }
    }

    fun delete(id: Long): Boolean = transaction(db) {
        val user = UserEntity.findById(id)
        user?.let {
            it.delete()
            true
        } ?: run {
            false
        }
    }

    override fun close() {}
}