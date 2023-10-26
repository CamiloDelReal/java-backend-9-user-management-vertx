package org.xapps.services.usermanagementservice.dtos

data class LoginRequest(
  var username: String = "",
  var password: String = ""
)
