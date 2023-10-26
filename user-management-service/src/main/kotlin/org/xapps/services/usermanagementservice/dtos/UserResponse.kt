package org.xapps.services.usermanagementservice.dtos

data class UserResponse(
  var id: Long = 0,
  var surname: String = "",
  var lastname: String = "",
  var username: String = ""
)
