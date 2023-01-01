package org.xapps.services.services

import kotlinx.coroutines.CoroutineDispatcher
import org.jetbrains.exposed.dao.with
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.xapps.services.daos.tables.RoleEntity
import org.xapps.services.daos.tables.Roles
import org.xapps.services.daos.tables.UserEntity
import org.xapps.services.models.Role
import org.xapps.services.models.User
import java.io.Closeable

class RoleService(
    private val dispatcher: CoroutineDispatcher,
    private val database: Database
) : Closeable {

    fun init() = transaction(
        db = database
    ) {
        SchemaUtils.create(Roles)
    }

    fun seed() = transaction(
        db = database
    ) {
        if (RoleEntity.count() == 0L) {
            RoleEntity.new {
                name = Role.ADMINISTRATOR
            }
            RoleEntity.new {
                name = Role.GUEST
            }
        }
    }

    suspend fun readAll(): List<Role> = newSuspendedTransaction(
        context = dispatcher, db = database
    ) {
        val users = RoleEntity.all()
            .map {
                Role(
                    id = it.id.value,
                    name = it.name
                )
            }
            .toList()
        users
    }
    suspend fun administrator(): RoleEntity? = newSuspendedTransaction(
        context = dispatcher, db = database
    ) {
        RoleEntity.find {
            Roles.name eq Role.ADMINISTRATOR
        }.singleOrNull()
    }

    suspend fun guest(): RoleEntity? = newSuspendedTransaction(
        context = dispatcher, db = database
    ) {
        RoleEntity.find {
            Roles.name eq Role.GUEST
        }.singleOrNull()
    }

    suspend fun findByNames(names: List<String>): List<RoleEntity> = newSuspendedTransaction(
        context = dispatcher, db = database
    ) {
        RoleEntity.find {
            Roles.name inList names
        }.toList()
    }

    override fun close() {}
}