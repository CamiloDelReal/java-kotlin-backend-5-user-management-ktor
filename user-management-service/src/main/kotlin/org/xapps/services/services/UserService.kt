package org.xapps.services.services

import at.favre.lib.crypto.bcrypt.BCrypt
import io.ktor.server.application.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.dao.load
import org.jetbrains.exposed.dao.with
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.xapps.services.daos.tables.RoleEntity
import org.xapps.services.daos.tables.UserEntity
import org.xapps.services.daos.tables.Users
import org.xapps.services.exceptions.EmailNotAvailable
import org.xapps.services.exceptions.InvalidCredentialsException
import org.xapps.services.exceptions.NotFoundException
import org.xapps.services.models.Login
import org.xapps.services.models.Role
import org.xapps.services.models.User
import java.io.Closeable

class UserService(
    private val dispatcher: CoroutineDispatcher,
    private val database: Database,
    private val environment: ApplicationEnvironment,
    private val roleService: RoleService
) : Closeable {
    fun init() = transaction(
        db = database
    ) {
        SchemaUtils.create(Users)
    }

    fun seed() = transaction(
        db = database
    ) {
        if (UserEntity.count() == 0L) {
            val administratorRole = runBlocking {
                roleService.administrator()
            }
            UserEntity.new {
                firstName = environment.config.property("security.defaults.root.first-name").getString()
                lastName = environment.config.property("security.defaults.root.last-name").getString()
                email = environment.config.property("security.defaults.root.email").getString()
                password = BCrypt.withDefaults().hashToString(
                    environment.config.property("security.hash-rounds").getString().toInt(),
                    environment.config.property("security.defaults.root.password").getString().toCharArray()
                )
                roles = SizedCollection(listOf(administratorRole!!))
            }
        }
    }

    suspend fun readAll(): List<User> = newSuspendedTransaction(
        context = dispatcher, db = database
    ) {
        val users = UserEntity.all().with(UserEntity::roles)
            .map {
                User(
                    id = it.id.value,
                    firstName = it.firstName,
                    lastName = it.lastName,
                    email = it.email,
                    password = null,
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

    suspend fun read(id: Long): User = newSuspendedTransaction(
        context = dispatcher, db = database
    ) {
        val user = UserEntity.findById(id)?.load(UserEntity::roles)
        user?.let {
            User(
                id = it.id.value,
                firstName = it.firstName,
                lastName = it.lastName,
                email = it.email,
                password = null,
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

    suspend fun validateLogin(login: Login): User = newSuspendedTransaction(
        context = dispatcher, db = database
    ) {
        val user = UserEntity.find { Users.email eq login.email }.with(UserEntity::roles).singleOrNull()
        if (user != null) {
            val result = BCrypt.verifyer().verify(login.password.toCharArray(), user.password)
            if (result.verified) {
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

    suspend fun create(user: User): User = newSuspendedTransaction(
        context = dispatcher, db = database
    ) {
        val emailDuplicated = Users.select { Users.email eq user.email }.singleOrNull()
        if (emailDuplicated == null) {
            var newRoles: List<RoleEntity>? = null
            if (user.roles != null && user.roles?.isNotEmpty() == true) {
                newRoles = roleService.findByNames(user.roles!!.map(Role::name))
            }
            if (newRoles == null || newRoles.isEmpty()) {
                roleService.guest()?.let {
                    newRoles = listOf(it)
                }
            }
            val newUser = UserEntity.new {
                firstName = user.firstName
                lastName = user.lastName
                email = user.email
                password = BCrypt.withDefaults().hashToString(
                    environment.config.property("security.hash-rounds").getString().toInt(),
                    user.password!!.toCharArray()
                )
                roles = SizedCollection(newRoles!!)
            }
            User(
                id = newUser.id.value,
                firstName = newUser.firstName,
                lastName = newUser.lastName,
                email = newUser.email,
                password = null,
                roles = newUser.roles.map { r -> Role(id = r.id.value, name = r.name) }.toList()
            )
        } else {
            throw EmailNotAvailable("Email ${user.email} is not available")
        }
    }

    suspend fun update(id: Long, user: User): User = newSuspendedTransaction(
        context = dispatcher, db = database
    ) {
        val emailDuplicated = Users.select { (Users.email eq user.email) and (Users.id neq id) }.singleOrNull()
        if (emailDuplicated == null) {
            val presentUser = UserEntity.findById(id)?.load(UserEntity::roles)
            if (presentUser != null) {
                presentUser.firstName = user.firstName
                presentUser.lastName = user.lastName
                presentUser.email = user.email
                user.password?.let {
                    presentUser.password = BCrypt.withDefaults().hashToString(
                        environment.config.property("security.hash-rounds").getString().toInt(),
                        it.toCharArray()
                    )
                }
                var newRoles: List<RoleEntity>? = null
                if (user.roles != null && user.roles?.isNotEmpty() == true) {
                    newRoles = roleService.findByNames(user.roles!!.map(Role::name))
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
                    password = null,
                    roles = presentUser.roles.map { r -> Role(id = r.id.value, name = r.name) }.toList()
                )
            } else {
                throw NotFoundException("No user found with id $id")
            }
        } else {
            throw EmailNotAvailable("Email ${user.email} is not available")
        }
    }

    suspend fun delete(id: Long): Boolean = newSuspendedTransaction(
        context = dispatcher, db = database
    ) {
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