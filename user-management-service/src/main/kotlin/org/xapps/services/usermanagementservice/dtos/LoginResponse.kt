package org.xapps.services.usermanagementservice.dtos

data class LoginResponse(
  var type: String = "",
  var token: String = "",
  var expiration: Long = 0
)
