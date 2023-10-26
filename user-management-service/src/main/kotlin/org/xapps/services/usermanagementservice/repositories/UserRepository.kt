package org.xapps.services.usermanagementservice.repositories

import io.vertx.core.Future
import io.vertx.mysqlclient.MySQLClient
import io.vertx.sqlclient.Row
import io.vertx.sqlclient.SqlClient
import io.vertx.sqlclient.Tuple
import org.xapps.services.usermanagementservice.entities.User
import org.xapps.services.usermanagementservice.exceptions.DatabaseException
import org.xapps.services.usermanagementservice.exceptions.NotFoundException

class UserRepository(
  private val sqlClient: SqlClient
) {

  fun prepare(): Future<Unit> =
    sqlClient.query(
      """
      create table if not exists users
      (
          id                 int auto_increment primary key,
          surname            varchar(50)  null,
          lastname           varchar(50)  null,
          username           varchar(50)  not null,
          protected_password varchar(250) not null
      );
    """.trimIndent()
    )
      .execute()
      .map { Future.succeededFuture<Unit>() }

  fun Row.toUser(): User =
    User(
      id = getLong("id"),
      surname = getString("surname"),
      lastname = getString("lastname"),
      username = getString("username"),
      protectedPassword = getString("protected_password")
    )

  fun count(): Future<Int> =
    sqlClient.query("SELECT COUNT(id) as count FROM users;")
      .execute()
      .map { rowSet ->
        rowSet.iterator().next().getInteger("count")
      }

  fun findAll(): Future<List<User>> =
    sqlClient.query("SELECT * FROM users")
      .execute()
      .map { rowSet ->
        rowSet.map { row -> row.toUser() }
      }

  fun findById(id: Long): Future<User> =
    sqlClient.preparedQuery("SELECT * FROM users WHERE id=?")
      .execute(Tuple.of(id))
      .map { rowSet ->
        rowSet.iterator()
      }
      .map { rowIterator ->
        if (rowIterator.hasNext())
          rowIterator.next().toUser()
        else
          throw NotFoundException("User with id $id not found")
      }

  fun findByUsername(username: String): Future<User> =
    sqlClient.preparedQuery("SELECT * FROM users WHERE username=?;")
      .execute(Tuple.of(username))
      .map { rowSet ->
        rowSet.iterator()
      }
      .map { rowIterator ->
        if(rowIterator.hasNext())
          rowIterator.next().toUser()
        else
          throw NotFoundException("User with username $username not found")
      }

  fun save(user: User): Future<User> =
    if (user.id == 0L) {
      sqlClient.preparedQuery("INSERT INTO users(surname, lastname, username, protected_password) VALUES(?, ?, ?, ?);")
        .execute(Tuple.of(user.surname, user.lastname, user.username, user.protectedPassword))
        .map { rowSet ->
          if (rowSet.rowCount() == 1) {
            val id = rowSet.property(MySQLClient.LAST_INSERTED_ID)
            user.id = id
            user
          } else {
            throw DatabaseException("Error creating user entity")
          }
        }
    } else {
      sqlClient.preparedQuery("UPDATE users SET surname=?, lastname=?, username=?, protected_password=? WHERE id=?;")
        .execute(Tuple.of(user.surname, user.lastname, user.username, user.protectedPassword, user.id))
        .map { rowSet ->
          if (rowSet.rowCount() == 1) {
            user
          } else {
            throw DatabaseException("Error updating user entity")
          }
        }
    }

  fun createAll(users: List<User>): Future<List<User>> =
    sqlClient.preparedQuery("INSERT INTO users(surname, lastname, username, protected_password) VALUES(?, ?, ?, ?);")
      .executeBatch(users.map { user -> Tuple.of(user.surname, user.lastname, user.username, user.protectedPassword) })
      .map { rowSet ->
        if (rowSet.rowCount() == users.size) {
          rowSet.zip(users).map { (row, user) ->
            user.id = row.getLong(1)
            user
          }
        } else {
          throw DatabaseException("Error creating user entities")
        }
      }

  fun existsById(id: Long): Future<Boolean> =
    sqlClient.preparedQuery("SELECT COUNT(id) as count FROM users WHERE id=?;")
      .execute(Tuple.of(id))
      .map { rowSet ->
        rowSet.iterator().next().getInteger("count") == 1
      }

  fun existsByUsername(username: String): Future<Boolean> =
    sqlClient.preparedQuery("SELECT COUNT(id) as count FROM users WHERE username=?;")
      .execute(Tuple.of(username))
      .map { rowSet ->
        rowSet.iterator().next().getInteger("count") == 1
      }

  fun existsByIdNotAndUsername(id: Long, username: String): Future<Boolean> =
    sqlClient.preparedQuery("SELECT COUNT(id) as count FROM users WHERE id!=? AND username=?;")
      .execute(Tuple.of(id, username))
      .map { rowSet ->
        rowSet.iterator().next().getInteger("count") == 1
      }

  fun deleteById(id: Long): Future<Unit> =
    sqlClient.preparedQuery("DELETE FROM users WHERE id=?;")
      .execute(Tuple.of(id))
      .map { rowSet ->
        if (rowSet.rowCount() == 0) {
          throw DatabaseException("Error deleting user with id $id")
        }
      }

}
