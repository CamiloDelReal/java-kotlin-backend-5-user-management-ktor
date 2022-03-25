package org.xapps.services.daos

import org.jetbrains.exposed.sql.Database
import org.xapps.services.utils.PropertiesProvider

class DatabaseUtils {
    companion object {
        @JvmStatic
        val databaseInstance: Database by lazy {
            Database.connect(
                url = PropertiesProvider.instance.databaseUrl,
                driver = PropertiesProvider.instance.databaseDriver,
                user = PropertiesProvider.instance.databaseUser,
                password = PropertiesProvider.instance.databasePassword
            )
        }
    }
}