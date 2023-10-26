package org.xapps.services.usermanagementservice.dtos

import org.xapps.services.usermanagementservice.entities.User

fun User.toResponse(): UserResponse =
  UserResponse(
    id = id,
    surname = surname,
    lastname = lastname,
    username = username
  )
