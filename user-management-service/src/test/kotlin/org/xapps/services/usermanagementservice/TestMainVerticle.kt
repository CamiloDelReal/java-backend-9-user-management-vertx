package org.xapps.services.usermanagementservice

import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.core.http.HttpClient
import io.vertx.core.http.HttpClientOptions
import io.vertx.core.http.HttpMethod
import io.vertx.core.json.Json
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.xapps.services.usermanagementservice.dtos.LoginRequest
import org.xapps.services.usermanagementservice.dtos.LoginResponse


@ExtendWith(VertxExtension::class)
class TestMainVerticle {

  companion object {
    private lateinit var httpCient: HttpClient

    @JvmStatic
    @BeforeAll
    fun setUp(vertx: Vertx, testContext: VertxTestContext) {
      vertx.deployVerticle(MainVerticle(), testContext.succeeding<String> { _ -> testContext.completeNow() })
      httpCient = vertx.createHttpClient(
        HttpClientOptions()
          .setDefaultHost("localhost")
          .setDefaultPort(8080)
      )
    }
  }

  @Test
  fun login_success(vertx: Vertx, testContext: VertxTestContext) {
    httpCient.request(HttpMethod.POST, "/login")
      .flatMap { request ->
        val loginRequest = LoginRequest("root", "123456")
        request
          .send(Json.encode(loginRequest))
          .flatMap { response ->
            response.body()
              .flatMap {
                Future.succeededFuture(Json.decodeValue(it, LoginResponse::class.java))
              }
          }

      }
      .onComplete(testContext.succeeding { response ->
        testContext.verify {
          //Verify here
          assertEquals("Bearer", response.type)
          assertTrue(response.token.isNotEmpty())
          assertNotEquals(0, response.expiration)
          testContext.completeNow()
        }
      })
  }
}
