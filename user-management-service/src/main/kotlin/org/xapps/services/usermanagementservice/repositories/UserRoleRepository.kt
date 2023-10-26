package org.xapps.services.usermanagementservice.repositories

import io.vertx.core.Future
import io.vertx.sqlclient.Row
import io.vertx.sqlclient.SqlClient
import io.vertx.sqlclient.Tuple
import org.xapps.services.usermanagementservice.entities.UserRole
import org.xapps.services.usermanagementservice.exceptions.DatabaseException

class UserRoleRepository(
  private val sqlClient: SqlClient
) {

  fun prepare(): Future<Unit> =
    sqlClient.query(
      """
      create table if not exists users_roles
      (
          user_id int not null,
          role_id int not null,
          primary key (user_id, role_id)
      );
    """.trimIndent()
    )
      .execute()
      .map { Future.succeededFuture<Unit>() }

  fun Row.toUserRole(): UserRole =
    UserRole(
      userId = getLong("user_id"),
      roleId = getLong("role_id")
    )

  fun createAll(usersRoles: List<UserRole>): Future<List<UserRole>> =
    sqlClient.preparedQuery("INSERT INTO users_roles(user_id, role_id) VALUES(?, ?);")
      .executeBatch(usersRoles.map { userRole -> Tuple.of(userRole.userId, userRole.roleId) })
      .map { rowSet ->
        usersRoles
      }

  fun deleteByUserId(userId: Long): Future<Unit> =
    sqlClient.preparedQuery("DELETE FROM users_roles WHERE user_id=?;")
      .execute(Tuple.of(userId))
      .map { rowSet ->
        if (rowSet.rowCount() == 0) {
          throw DatabaseException("Error deleting roles of user with id $userId")
        }
      }

}
