package org.xapps.services.usermanagementservice.entities

data class User (
  var id: Long = 0,
  var surname: String,
  var lastname: String,
  var username: String,
  var protectedPassword: String
)
