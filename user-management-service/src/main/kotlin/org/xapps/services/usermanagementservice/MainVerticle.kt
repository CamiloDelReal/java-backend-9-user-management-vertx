package org.xapps.services.usermanagementservice

import io.vertx.config.ConfigRetriever
import io.vertx.config.ConfigRetrieverOptions
import io.vertx.config.ConfigStoreOptions
import io.vertx.core.AbstractVerticle
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.http.HttpHeaders
import io.vertx.core.json.Json
import io.vertx.core.json.JsonObject
import io.vertx.ext.auth.PubSecKeyOptions
import io.vertx.ext.auth.jwt.JWTAuth
import io.vertx.ext.auth.jwt.JWTAuthOptions
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.JWTAuthHandler
import io.vertx.ext.web.handler.impl.JWTAuthHandlerImpl
import org.xapps.services.usermanagementservice.configurations.Database
import org.xapps.services.usermanagementservice.dtos.LoginRequest
import org.xapps.services.usermanagementservice.dtos.UserRequest
import org.xapps.services.usermanagementservice.dtos.UserResponse
import org.xapps.services.usermanagementservice.entities.Role
import org.xapps.services.usermanagementservice.exceptions.BadCredentialsException
import org.xapps.services.usermanagementservice.exceptions.DataException
import org.xapps.services.usermanagementservice.exceptions.DatabaseException
import org.xapps.services.usermanagementservice.exceptions.NotFoundException
import org.xapps.services.usermanagementservice.repositories.RoleRepository
import org.xapps.services.usermanagementservice.repositories.UserRepository
import org.xapps.services.usermanagementservice.repositories.UserRoleRepository
import org.xapps.services.usermanagementservice.services.UserService
import org.xapps.services.usermanagementservice.utils.logger

class MainVerticle : AbstractVerticle() {

  private lateinit var configRetriever: ConfigRetriever

  private lateinit var database: Database

  private lateinit var roleRepository: RoleRepository
  private lateinit var userRepository: UserRepository
  private lateinit var userRoleRepository: UserRoleRepository

  private lateinit var userService: UserService

  private lateinit var jwtAuthProvider: JWTAuth

  companion object {
    val logger = logger()
  }

  override fun start(startPromise: Promise<Void>) {
    val fileStore = ConfigStoreOptions()
      .setType("file")
      .setConfig(JsonObject().put("path", "configurations.json"))
    val options = ConfigRetrieverOptions()
      .addStore(fileStore)
    configRetriever = ConfigRetriever.create(vertx, options)

    configRetriever.getConfig { configResult ->
      if (configResult.succeeded()) {
        val configurations = configResult.result()
        database = Database(vertx, configurations)
        roleRepository = RoleRepository(database.client)
        userRepository = UserRepository(database.client)
        userRoleRepository = UserRoleRepository(database.client)
        jwtAuthProvider = JWTAuth.create(
          vertx, JWTAuthOptions()
            .addPubSecKey(
              PubSecKeyOptions().setAlgorithm("HS256")
                .setBuffer(configurations.getJsonObject("security").getJsonObject("jwtGeneration").getString("key"))
            )
        )
        userService = UserService(userRepository, roleRepository, userRoleRepository, jwtAuthProvider, configurations)

        Future.join(
          roleRepository.prepare(),
          userRepository.prepare(),
          userRoleRepository.prepare()
        )
          .onSuccess {
            userService.seedDatabase()
              .onComplete {
                logger.debug("Database ready with sample data")
              }
          }
          .onFailure { throwable ->
            startPromise.fail(throwable)
          }

        val router = Router.router(vertx)

        val jwtHandler = JWTAuthHandler.create(jwtAuthProvider)


        router.post("/login").handler(this::login)

        router.get("/users").handler(jwtHandler).handler { routingContext ->
          routingContext.isFresh
          checkAuthorization(routingContext = routingContext, allowedRoles = listOf(Role.ADMINISTRATOR))
        }.handler(this::readAllUsers)

        router.get("/users/:id").handler(jwtHandler).handler { routingContext ->
          val id = routingContext.request().getParam("id").toLong()
          checkAuthorization(
            routingContext = routingContext,
            allowedRoles = listOf(Role.ADMINISTRATOR),
            dataOwnerId = id
          )
        }.handler(this::readUser)

        router.post("/users").handler { routingContext ->
          (jwtHandler as JWTAuthHandlerImpl).authenticate(routingContext) {
            it.result()?.let { user ->
              routingContext.setUser(user)
            }
          }
          routingContext.next()
        }.handler(this::createUser)

        router.put("/users/:id").handler(jwtHandler).handler(this::updateUser)

        router.delete("/users/:id").handler(jwtHandler).handler { routingContext ->
          val id = routingContext.request().getParam("id").toLong()
          checkAuthorization(routingContext, listOf(Role.ADMINISTRATOR), dataOwnerId = id)
        }.handler(this::deleteUser)

        vertx
          .createHttpServer()
          .requestHandler(router)
          .listen(8080) { http ->
            if (http.succeeded()) {
              startPromise.complete()
              logger.info("HTTP server started on port 8080")
            } else {
              startPromise.fail(http.cause())
            }
          }
      } else {
        startPromise.fail(configResult.cause())
      }
    }
  }

  private fun globalFailureHandler(exception: Throwable, routingContext: RoutingContext) {
    val errorType = when (exception) {
      is NotFoundException -> 404
      is BadCredentialsException -> 401
      is DataException -> 400
      is DatabaseException -> 500
      else -> 500
    }
    routingContext.response()
      .setStatusCode(errorType)
      .setStatusMessage(exception.message)
      .end()
  }

  private fun checkAuthorization(
    routingContext: RoutingContext,
    allowedRoles: List<String> = listOf(Role.ADMINISTRATOR),
    dataOwnerId: Long? = null,
    request: UserRequest? = null
  ) {
    val user = routingContext.user()
    user?.principal()?.let { principal ->
      val roles = principal.getString("roles").split(",")
      if (roles.any { allowedRoles.contains(it) }) {
        routingContext.next()
      } else if (dataOwnerId != null) {
        val tokenOwnerId = Json.decodeValue(principal.getString("sub"), UserResponse::class.java).id
        if (dataOwnerId == tokenOwnerId) {
          if (request != null && request.roles?.contains(Role.ADMINISTRATOR) != true) {
            routingContext.next()
          } else {
            routingContext.response()
              .setStatusCode(403)
              .end()
          }
        } else {
          routingContext.response()
            .setStatusCode(403)
            .end()
        }
      } else {
        routingContext.response()
          .setStatusCode(403)
          .end()
      }
    } ?: run {
      if (request == null || request.roles?.contains(Role.ADMINISTRATOR) != true) {
        routingContext.next()
      } else {
        routingContext.response()
          .setStatusCode(403)
          .end()
      }
    }
  }

  private fun checkAuthorization(
    principal: JsonObject? = null,
    allowedRoles: List<String> = listOf(Role.ADMINISTRATOR),
    dataOwnerId: Long? = null,
    request: UserRequest? = null
  ): Boolean {
    return principal?.let {
      val roles = principal.getString("roles").split(",")
      if (roles.any { allowedRoles.contains(it) }) {
        true
      } else if (dataOwnerId != null) {
        val tokenOwnerId = Json.decodeValue(principal.getString("sub"), UserResponse::class.java).id
        if (dataOwnerId == tokenOwnerId) {
          if (request != null && request.roles?.contains(Role.ADMINISTRATOR) != true) {
            true
          } else {
            false
          }
        } else {
          false
        }
      } else {
        false
      }
    } ?: run {
      request == null || request.roles?.contains(Role.ADMINISTRATOR) != true
    }
  }

  private fun login(routingContext: RoutingContext) {
    routingContext.request().body()
      .onSuccess {
        val request = Json.decodeValue(it, LoginRequest::class.java)
        userService.login(request)
          .onSuccess { response ->
            routingContext.response()
              .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
              .setStatusCode(200)
              .end(Json.encode(response))
          }
          .onFailure { throwable -> globalFailureHandler(throwable, routingContext) }
      }
      .onFailure { throwable -> globalFailureHandler(throwable, routingContext) }
  }

  private fun readAllUsers(routingContext: RoutingContext) {
    userService.readAll()
      .onSuccess { response ->
        routingContext.response()
          .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
          .setStatusCode(200)
          .end(Json.encode(response))
      }
      .onFailure { throwable -> globalFailureHandler(throwable, routingContext) }
  }

  private fun readUser(routingContext: RoutingContext) {
    val id = routingContext.request().getParam("id").toLong()
    userService.read(id)
      .onSuccess { response ->
        routingContext.response()
          .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
          .setStatusCode(200)
          .end(Json.encode(response))
      }
      .onFailure { throwable -> globalFailureHandler(throwable, routingContext) }
  }

  private fun createUser(routingContext: RoutingContext) {
    routingContext.request().body()
      .onSuccess {
        val request = Json.decodeValue(it, UserRequest::class.java)
        if (checkAuthorization(
            principal = routingContext.user()?.principal(),
            allowedRoles = listOf(Role.ADMINISTRATOR),
            request = request
          )
        ) {
          userService.create(request)
            .onSuccess { response ->
              routingContext.response()
                .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .setStatusCode(201)
                .end(Json.encode(response))
            }
            .onFailure { throwable -> globalFailureHandler(throwable, routingContext) }
        } else {
          routingContext.response()
            .setStatusCode(403)
            .end()
        }
      }
      .onFailure { throwable -> globalFailureHandler(throwable, routingContext) }
  }

  private fun updateUser(routingContext: RoutingContext) {
    val id = routingContext.request().getParam("id").toLong()
    routingContext.request().body()
      .onSuccess {
        val request = Json.decodeValue(it, UserRequest::class.java)
        if (checkAuthorization(
            principal = routingContext.user()?.principal(),
            allowedRoles = listOf(Role.ADMINISTRATOR),
            dataOwnerId = id,
            request = request
          )
        ) {
          userService.update(id, request)
            .onSuccess { response ->
              routingContext.response()
                .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .setStatusCode(200)
                .end(Json.encode(response))
            }
            .onFailure { throwable -> globalFailureHandler(throwable, routingContext) }
        } else {
          routingContext.response()
            .setStatusCode(403)
            .end()
        }
      }
      .onFailure { throwable -> globalFailureHandler(throwable, routingContext) }
  }

  private fun deleteUser(routingContext: RoutingContext) {
    val id = routingContext.request().getParam("id").toLong()
    userService.delete(id)
      .onSuccess {
        routingContext.response()
          .setStatusCode(200)
          .end()
      }
      .onFailure { throwable -> globalFailureHandler(throwable, routingContext) }
  }

}
