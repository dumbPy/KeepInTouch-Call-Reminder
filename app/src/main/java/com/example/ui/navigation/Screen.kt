package com.example.ui.navigation

sealed class Screen(val route: String) {
    object Agenda : Screen("agenda")
    object Contacts : Screen("contacts")
    object Groups : Screen("groups")
    object Settings : Screen("settings")
    
    object ContactDetail : Screen("contact_detail/{contactId}") {
        fun createRoute(contactId: Long) = "contact_detail/$contactId"
    }
}
