package org.xapps.services.services

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.xapps.services.daos.tables.UsersRoles
import java.io.Closeable

class UserRoleService(
    private val database: Database
) : Closeable {

    fun init() = transaction(
        db = database
    ) {
        SchemaUtils.create(UsersRoles)
    }

    override fun close() {}
}