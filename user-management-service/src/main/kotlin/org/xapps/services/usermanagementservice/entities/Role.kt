package org.xapps.services.usermanagementservice.entities

data class Role(
  var id: Long = 0,
  var value: String
) {

  companion object {
    const val ADMINISTRATOR = "Administrator"
    const val GUEST = "Guest"
  }

}
