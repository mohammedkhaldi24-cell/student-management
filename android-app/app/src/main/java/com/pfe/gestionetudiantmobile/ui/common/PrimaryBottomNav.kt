package com.pfe.gestionetudiantmobile.ui.common

import android.view.Menu
import android.view.View
import androidx.core.content.ContextCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.pfe.gestionetudiantmobile.R

object PrimaryBottomNav {

    enum class Role {
        STUDENT,
        TEACHER,
        CHEF,
        ADMIN
    }

    fun bind(
        root: View,
        role: Role,
        currentFeature: String,
        onDashboard: () -> Unit,
        onFeature: (String) -> Unit,
        onProfile: () -> Unit
    ) {
        val bottomNav = root.findViewById<BottomNavigationView>(R.id.bottomNav) ?: return
        val items = itemsFor(role)
        val normalizedFeature = primaryFeatureFor(currentFeature)

        bottomNav.menu.applyRoleItems(bottomNav, items)
        bottomNav.setOnItemSelectedListener(null)
        bottomNav.setOnItemReselectedListener(null)
        bottomNav.selectedItemId = items.firstOrNull { it.feature == normalizedFeature }?.menuId
            ?: R.id.navDashboard

        bottomNav.setOnItemSelectedListener { menuItem ->
            val item = items.firstOrNull { it.menuId == menuItem.itemId } ?: return@setOnItemSelectedListener false
            route(item, onDashboard, onFeature, onProfile)
            true
        }

        bottomNav.setOnItemReselectedListener { menuItem ->
            val item = items.firstOrNull { it.menuId == menuItem.itemId } ?: return@setOnItemReselectedListener
            route(item, onDashboard, onFeature, onProfile)
        }
    }

    private fun Menu.applyRoleItems(bottomNav: BottomNavigationView, items: List<NavItem>) {
        for (index in 0 until size()) {
            getItem(index).isVisible = false
        }
        items.forEach { item ->
            findItem(item.menuId)?.apply {
                title = item.label
                icon = ContextCompat.getDrawable(bottomNav.context, item.iconRes)
                contentDescription = "Ouvrir ${item.label}"
                isVisible = true
            }
        }
    }

    private fun route(
        item: NavItem,
        onDashboard: () -> Unit,
        onFeature: (String) -> Unit,
        onProfile: () -> Unit
    ) {
        when (item.feature) {
            FEATURE_DASHBOARD -> onDashboard()
            FEATURE_PROFILE -> onProfile()
            else -> onFeature(item.feature)
        }
    }

    private fun itemsFor(role: Role): List<NavItem> {
        return when (role) {
            Role.ADMIN -> listOf(
                NavItem(R.id.navDashboard, "Accueil", FEATURE_DASHBOARD, R.drawable.ic_dashboard),
                NavItem(R.id.navNotes, "Users", "users", R.drawable.ic_person),
                NavItem(R.id.navAbsences, "Classes", "classes", R.drawable.ic_course),
                NavItem(R.id.navCourses, "Modules", "modules", R.drawable.ic_chart),
                NavItem(R.id.navProfile, "Profile", FEATURE_PROFILE, R.drawable.ic_person)
            )
            Role.STUDENT -> listOf(
                NavItem(R.id.navDashboard, "Accueil", FEATURE_DASHBOARD, R.drawable.ic_dashboard),
                NavItem(R.id.navNotes, "Devoirs", "assignments", R.drawable.ic_upload),
                NavItem(R.id.navAbsences, "Notif.", "notifications", R.drawable.ic_email),
                NavItem(R.id.navCourses, "Cours", "courses", R.drawable.ic_course),
                NavItem(R.id.navProfile, "Profil", FEATURE_PROFILE, R.drawable.ic_person)
            )
            Role.TEACHER -> listOf(
                NavItem(R.id.navDashboard, "Accueil", FEATURE_DASHBOARD, R.drawable.ic_dashboard),
                NavItem(R.id.navNotes, "Etudiants", "students", R.drawable.ic_person),
                NavItem(R.id.navAbsences, "Appel", "absences", R.drawable.ic_calendar),
                NavItem(R.id.navCourses, "Devoirs", "assignments", R.drawable.ic_upload),
                NavItem(R.id.navProfile, "Profil", FEATURE_PROFILE, R.drawable.ic_person)
            )
            Role.CHEF -> listOf(
                NavItem(R.id.navDashboard, "Accueil", FEATURE_DASHBOARD, R.drawable.ic_dashboard),
                NavItem(R.id.navNotes, "Notes", "notes", R.drawable.ic_chart),
                NavItem(R.id.navAbsences, "Absences", "absences", R.drawable.ic_history),
                NavItem(R.id.navCourses, "Cours", "courses", R.drawable.ic_course),
                NavItem(R.id.navProfile, "Profil", FEATURE_PROFILE, R.drawable.ic_person)
            )
        }
    }

    private fun primaryFeatureFor(feature: String): String {
        return when (feature.trim().lowercase()) {
            FEATURE_DASHBOARD -> FEATURE_DASHBOARD
            "notes" -> "notes"
            "absences" -> "absences"
            "courses" -> "courses"
            "students" -> "students"
            "assignments" -> "assignments"
            "notifications" -> "notifications"
            FEATURE_PROFILE -> FEATURE_PROFILE
            else -> FEATURE_DASHBOARD
        }
    }

    private data class NavItem(
        val menuId: Int,
        val label: String,
        val feature: String,
        val iconRes: Int
    )

    private const val FEATURE_DASHBOARD = "dashboard"
    private const val FEATURE_PROFILE = "profile"
}
