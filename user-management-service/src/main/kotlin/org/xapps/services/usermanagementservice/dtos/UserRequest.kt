package org.xapps.services.usermanagementservice.dtos

data class UserRequest(
  var surname: String? = null,
  var lastname: String? = null,
  var username: String? = null,
  var password: String? = null,
  var roles: List<String>? = null
)
