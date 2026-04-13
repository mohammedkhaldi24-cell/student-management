package com.pfe.gestionetudiantmobile.ui.common

import android.content.Context
import android.content.Intent
import com.pfe.gestionetudiantmobile.ui.home.AdminHomeActivity
import com.pfe.gestionetudiantmobile.ui.home.ChefHomeActivity
import com.pfe.gestionetudiantmobile.ui.home.StudentHomeActivity
import com.pfe.gestionetudiantmobile.ui.home.TeacherHomeActivity

object RoleHomeRouter {

    fun intentForRole(context: Context, role: String): Intent {
        val target = when (role.trim().uppercase()) {
            "ADMIN" -> AdminHomeActivity::class.java
            "CHEF_FILIERE" -> ChefHomeActivity::class.java
            "TEACHER" -> TeacherHomeActivity::class.java
            else -> StudentHomeActivity::class.java
        }
        return Intent(context, target)
    }
}
