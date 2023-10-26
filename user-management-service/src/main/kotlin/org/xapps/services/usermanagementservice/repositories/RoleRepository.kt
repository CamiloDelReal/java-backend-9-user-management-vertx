package org.xapps.services.usermanagementservice.repositories

import io.vertx.core.Future
import io.vertx.mysqlclient.MySQLClient
import io.vertx.sqlclient.Row
import io.vertx.sqlclient.SqlClient
import io.vertx.sqlclient.Tuple
import org.xapps.services.usermanagementservice.entities.Role
import org.xapps.services.usermanagementservice.exceptions.DatabaseException
import org.xapps.services.usermanagementservice.exceptions.NotFoundException

class RoleRepository(
  private val sqlClient: SqlClient
) {

  fun prepare(): Future<Unit> =
    sqlClient.query(
      """
      create table if not exists roles
      (
          id                 int auto_increment primary key,
          value              varchar(50)  null
      );
    """.trimIndent()
    )
      .execute()
      .map { Future.succeededFuture<Unit>() }

  fun Row.toRole(): Role =
    Role(
      id = getLong("id"),
      value = getString("value")
    )

  fun count(): Future<Int> =
    sqlClient.query("SELECT COUNT(id) as count FROM roles;")
      .execute()
      .map { rowSet ->
        rowSet.iterator().next().getInteger("count")
      }

  fun findAll(): Future<List<Role>> =
    sqlClient.query("SELECT * FROM roles")
      .execute()
      .map { rowSet ->
        rowSet.map { row -> row.toRole() }
      }

  fun findById(id: Long): Future<Role> =
    sqlClient.preparedQuery("SELECT * FROM roles WHERE id=?")
      .execute(Tuple.of(id))
      .map { rowSet ->
        rowSet.iterator()
      }
      .map { rowIterator ->
        if (rowIterator.hasNext())
          rowIterator.next().toRole()
        else
          throw NotFoundException("Role with id $id not found")
      }

  fun findByUserId(userId: Long): Future<List<Role>> =
    sqlClient.preparedQuery("SELECT roles.* FROM roles, users_roles WHERE users_roles.user_id=? AND roles.id=users_roles.role_id")
      .execute(Tuple.of(userId))
      .map { rowSet ->
        if (rowSet.count() > 0) {
          rowSet.map { it.toRole() }
        } else {
          throw NotFoundException("Roles for user with id $userId not found")
        }
      }

  fun findByValue(value: String): Future<Role> =
    sqlClient.preparedQuery("SELECT * FROM roles WHERE value=?")
      .execute(Tuple.of(value))
      .map { rowSet ->
        rowSet.iterator()
      }
      .map { rowIterator ->
        if (rowIterator.hasNext())
          rowIterator.next().toRole()
        else
          throw NotFoundException("Role with value $value not found")
      }

  fun findByValues(values: List<String>): Future<List<Role>> =
    sqlClient.preparedQuery("SELECT * FROM roles WHERE value IN (?)")
      .execute(Tuple.of(values.joinToString(",")))
      .map { rowSet ->
        rowSet.map { row -> row.toRole() }
      }

  fun save(role: Role): Future<Role> =
    if (role.id == 0L) {
      sqlClient.preparedQuery("INSERT INTO roles(value) VALUES(?);")
        .execute(Tuple.of(role.value))
        .map { rowSet ->
          if (rowSet.rowCount() == 1) {
            val id = rowSet.property(MySQLClient.LAST_INSERTED_ID)
            role.id = id
            role
          } else {
            throw DatabaseException("Error creating role entity")
          }
        }
    } else {
      sqlClient.preparedQuery("UPDATE roles SET value=? WHERE id=?;")
        .execute(Tuple.of(role.value, role.id))
        .map { rowSet ->
          if (rowSet.rowCount() == 1) {
            role
          } else {
            throw DatabaseException("Error updating role entity")
          }
        }
    }

  fun createAll(roles: List<Role>): Future<List<Role>> =
    sqlClient.preparedQuery("INSERT INTO roles(value) VALUES(?);")
      .executeBatch(roles.map { role -> Tuple.of(role.value) })
      .map { rowSet ->
        if (rowSet.rowCount() == roles.size) {
          rowSet.zip(roles).map { (row, role) ->
            role.id = row.getLong(1)
            role
          }
        } else {
          throw DatabaseException("Error creating role entities")
        }
      }

}
