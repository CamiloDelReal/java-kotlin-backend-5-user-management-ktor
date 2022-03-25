package org.xapps.services.services

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.xapps.services.daos.DatabaseUtils
import org.xapps.services.daos.tables.RoleEntity
import org.xapps.services.daos.tables.Roles
import org.xapps.services.models.Role
import java.io.Closeable

class RoleService(private val db: Database) : Closeable {

    companion object {
        @JvmStatic
        val instance: RoleService by lazy {
            RoleService(DatabaseUtils.databaseInstance)
        }
    }

    fun init() = transaction(db) {
        SchemaUtils.create(Roles)
    }

    fun seed() = transaction(db) {
        if(RoleEntity.count() == 0L) {
            RoleEntity.new {
                name = Role.ADMINISTRATOR
            }
            RoleEntity.new {
                name = Role.GUEST
            }
        }
    }

    fun administrator(): RoleEntity? = transaction {
        RoleEntity.find {
            Roles.name eq Role.ADMINISTRATOR
        }.singleOrNull()
    }

    fun guest(): RoleEntity? = transaction {
        RoleEntity.find {
            Roles.name eq Role.GUEST
        }.singleOrNull()
    }

    fun findByNames(names: List<String>): List<RoleEntity> = transaction {
        RoleEntity.find {
            Roles.name inList names
        }.toList()
    }

    override fun close() {}
}