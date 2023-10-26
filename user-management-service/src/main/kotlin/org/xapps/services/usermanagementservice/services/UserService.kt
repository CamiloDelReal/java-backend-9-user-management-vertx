package org.xapps.services.usermanagementservice.services

import at.favre.lib.crypto.bcrypt.BCrypt
import io.vertx.core.Future
import io.vertx.core.json.Json
import io.vertx.core.json.JsonObject
import io.vertx.ext.auth.JWTOptions
import io.vertx.ext.auth.jwt.JWTAuth
import org.xapps.services.usermanagementservice.dtos.*
import org.xapps.services.usermanagementservice.entities.Role
import org.xapps.services.usermanagementservice.entities.User
import org.xapps.services.usermanagementservice.entities.UserRole
import org.xapps.services.usermanagementservice.exceptions.BadCredentialsException
import org.xapps.services.usermanagementservice.exceptions.DataException
import org.xapps.services.usermanagementservice.exceptions.NotFoundException
import org.xapps.services.usermanagementservice.repositories.RoleRepository
import org.xapps.services.usermanagementservice.repositories.UserRepository
import org.xapps.services.usermanagementservice.repositories.UserRoleRepository
import java.time.Instant


class UserService(
  private val userRepository: UserRepository,
  private val roleRepository: RoleRepository,
  private val userRoleRepository: UserRoleRepository,
  private val jwtAuthProvider: JWTAuth,
  private val configs: JsonObject
) {

  private val securityConfigs = configs.getJsonObject("security")

  fun seedDatabase(): Future<Unit> =
    roleRepository.count()
      .flatMap { count ->
        if (count == 0) {
          val administratorRole = Role(value = Role.ADMINISTRATOR)
          val guestRole = Role(value = Role.GUEST)
          roleRepository.createAll(listOf(administratorRole, guestRole))
            .flatMap {
              seedAdministrator(administratorRole)
            }
        } else {
          roleRepository.findByValue(Role.ADMINISTRATOR)
            .flatMap { role ->
              seedAdministrator(role)
            }
        }
      }

  private fun seedAdministrator(administratorRole: Role): Future<Unit> =
    userRepository.count()
      .flatMap { count ->
        if (count == 0) {
          val administratorUser = User(
            surname = "Root",
            lastname = "the First",
            username = "root",
            protectedPassword = BCrypt.withDefaults().hashToString(12, "123456".toCharArray())
          )
          userRepository.save(administratorUser)
            .flatMap {
              userRoleRepository.createAll(
                listOf(
                  UserRole(
                    userId = administratorUser.id,
                    roleId = administratorRole.id
                  )
                )
              )
              Future.succeededFuture()
            }
        } else {
          Future.succeededFuture()
        }
      }


  fun login(request: LoginRequest): Future<LoginResponse> =
    userRepository.findByUsername(request.username)
      .flatMap { user ->
        roleRepository.findByUserId(user.id)
          .flatMap { roles ->
            val result = BCrypt.verifyer().verify(request.password.toByteArray(), user.protectedPassword.toByteArray())
            if (result.verified) {
              val currentTimestamp = Instant.now().toEpochMilli() / 1000
              val expirationTimestamp =
                currentTimestamp + securityConfigs.getJsonObject("jwtGeneration").getLong("expiration")
              val subject = Json.encode(user.toResponse())
              val token = jwtAuthProvider.generateToken(
                JsonObject()
                  .put("sub", subject)
                  .put("iat", currentTimestamp)
                  .put("exp", expirationTimestamp)
                  .put("roles", roles.map { role -> role.value }.joinToString(",")),
                JWTOptions()
                  .setAlgorithm("HS256")
              )
              Future.succeededFuture(
                LoginResponse(
                  type = "Bearer",
                  token = token,
                  expiration = expirationTimestamp
                )
              )
            } else {
              throw BadCredentialsException("Invalid credentials")
            }
          }
      }

  fun readAll(): Future<List<UserResponse>> =
    userRepository.findAll()
      .map { users ->
        users.map { user ->
          user.toResponse()
        }
      }

  fun read(id: Long): Future<UserResponse> =
    userRepository.findById(id)
      .map { user ->
        user.toResponse()
      }

  fun create(request: UserRequest): Future<UserResponse> =
    userRepository.existsByUsername(request.username!!)
      .flatMap { exists ->
        if (exists) {
          throw DataException("Username not available")
        } else {
          val user = User(
            surname = request.surname!!,
            lastname = request.lastname ?: "",
            username = request.username!!,
            protectedPassword = BCrypt.withDefaults().hashToString(12, request.password!!.toCharArray())
          )
          userRepository.save(user)
            .flatMap { savedUser ->
              val roleResolver = if (!request.roles.isNullOrEmpty()) {
                roleRepository.findByValues(request.roles!!)
              } else {
                roleRepository.findByValue(Role.GUEST)
                  .map {
                    listOf(it)
                  }
              }
              roleResolver
                .flatMap { roles ->
                  userRoleRepository.createAll(roles.map { role ->
                    UserRole(
                      userId = savedUser.id,
                      roleId = role.id
                    )
                  })
                    .map {
                      savedUser.toResponse()
                    }
                }
            }
        }
      }

  fun update(id: Long, request: UserRequest): Future<UserResponse> =
    userRepository.findById(id)
      .flatMap { user ->
        val usernameValidationResolver = if (request.username != null) {
          userRepository.existsByIdNotAndUsername(id, request.username!!)
        } else {
          Future.succeededFuture(false)
        }
        usernameValidationResolver.flatMap { exists ->
          if (exists) {
            throw DataException("Username not available")
          } else {
            request.surname?.let { user.surname = it }
            request.lastname?.let { user.lastname = it }
            request.username?.let { user.username = it }
            request.password?.let { user.protectedPassword = BCrypt.withDefaults().hashToString(12, it.toCharArray()) }
            userRepository.save(user)
              .flatMap { savedUser ->
                if (!request.roles.isNullOrEmpty()) {
                  roleRepository.findByValues(request.roles!!)
                    .flatMap { roles ->
                      userRoleRepository.deleteByUserId(savedUser.id)
                        .flatMap {
                          userRoleRepository.createAll(roles.map { role ->
                            UserRole(
                              userId = savedUser.id,
                              roleId = role.id
                            )
                          })
                            .flatMap {
                              Future.succeededFuture(savedUser.toResponse())
                            }
                        }
                    }
                } else {
                  Future.succeededFuture(savedUser.toResponse())
                }
              }
          }
        }
      }

  fun delete(id: Long): Future<Unit> =
    userRepository.existsById(id)
      .compose { exists ->
        if (exists)
          userRepository.deleteById(id)
        else
          throw NotFoundException("User with id $id not found")
      }
      .compose {
        userRoleRepository.deleteByUserId(id)
      }

}
