package org.xapps.services.usermanagementservice.configurations

import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.mysqlclient.MySQLConnectOptions
import io.vertx.mysqlclient.MySQLPool
import io.vertx.sqlclient.PoolOptions


class Database(
  vertx: Vertx,
  configs: JsonObject
) {

  private val datasourceConfigs = configs.getJsonObject("datasource")

  private val connectOptions = MySQLConnectOptions()
    .setPort(datasourceConfigs.getInteger("port"))
    .setHost(datasourceConfigs.getString("host"))
    .setDatabase(datasourceConfigs.getString("database"))
    .setUser(datasourceConfigs.getString("user"))
    .setPassword(datasourceConfigs.getString("password"))

  private val poolOptions = PoolOptions()
    .setMaxSize(datasourceConfigs.getInteger("poolSize"))

  var client = MySQLPool.client(vertx, connectOptions, poolOptions)

}
