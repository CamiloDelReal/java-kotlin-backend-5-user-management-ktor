package org.xapps.services.services

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.xapps.services.daos.DatabaseUtils
import org.xapps.services.daos.tables.UsersRoles
import java.io.Closeable

class UserRoleService(private val db: Database) : Closeable {

    companion object {
        @JvmStatic
        val instance: UserRoleService by lazy {
            UserRoleService(DatabaseUtils.databaseInstance)
        }
    }

    fun init() = transaction(db) {
        SchemaUtils.create(UsersRoles)
    }

    override fun close() {}
}