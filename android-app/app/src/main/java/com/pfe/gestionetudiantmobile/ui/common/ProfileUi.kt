package com.pfe.gestionetudiantmobile.ui.common

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.pfe.gestionetudiantmobile.R
import com.pfe.gestionetudiantmobile.data.model.StudentProfile
import com.pfe.gestionetudiantmobile.data.model.TeacherProfile
import com.pfe.gestionetudiantmobile.data.model.UserSummary
import java.time.format.DateTimeFormatter
import java.util.Locale

object ProfileUi {
    private val birthDateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.FRENCH)

    fun studentRows(profile: StudentProfile, user: UserSummary?): List<UiRow> {
        val role = roleLabel(user?.role ?: "STUDENT")
        return listOf(
            UiRow(
                title = profile.fullName.clean() ?: user?.fullName.clean() ?: "Etudiant",
                subtitle = listOf(
                    "Role: $role",
                    "Identifiant: ${user?.username.clean() ?: profile.matricule}",
                    "Espace mobile GestionEtu"
                ).joinToString("\n"),
                badge = role,
                icon = initials(profile.fullName.clean() ?: user?.fullName.clean())
            ),
            UiRow(
                title = "Informations academiques",
                subtitle = listOf(
                    "Matricule: ${profile.matricule}",
                    "Classe: ${profile.classe.clean() ?: "-"}",
                    "Filiere: ${profile.filiere.clean() ?: "-"}"
                ).joinToString("\n"),
                badge = "Etudes",
                icon = "ID"
            ),
            UiRow(
                title = "Coordonnees",
                subtitle = listOf(
                    "Email: ${profile.email.clean() ?: user?.email.clean() ?: "-"}",
                    "Telephone: ${profile.telephone.clean() ?: "-"}",
                    "Adresse: ${profile.adresse.clean() ?: "-"}"
                ).joinToString("\n"),
                badge = "Contact",
                icon = "@"
            ),
            UiRow(
                title = "Details personnels",
                subtitle = listOf(
                    "Date naissance: ${profile.dateNaissance?.format(birthDateFormatter) ?: "-"}",
                    "Photo: ${if (profile.photoUrl.clean() == null) "Avatar par defaut" else "Photo disponible"}"
                ).joinToString("\n"),
                badge = "Profil",
                icon = "PRO"
            )
        )
    }

    fun teacherRows(profile: TeacherProfile, user: UserSummary?): List<UiRow> {
        val role = roleLabel(user?.role ?: "TEACHER")
        return listOf(
            UiRow(
                title = profile.fullName.clean() ?: user?.fullName.clean() ?: "Enseignant",
                subtitle = listOf(
                    "Role: $role",
                    "Identifiant: ${user?.username.clean() ?: "-"}",
                    "Espace mobile GestionEtu"
                ).joinToString("\n"),
                badge = role,
                icon = initials(profile.fullName.clean() ?: user?.fullName.clean())
            ),
            UiRow(
                title = "Informations professionnelles",
                subtitle = listOf(
                    "Grade: ${profile.grade.clean() ?: "-"}",
                    "Specialite: ${profile.specialite.clean() ?: "-"}",
                    "Bureau: ${profile.bureau.clean() ?: "-"}"
                ).joinToString("\n"),
                badge = "Enseignement",
                icon = "ENS"
            ),
            UiRow(
                title = "Coordonnees",
                subtitle = listOf(
                    "Email: ${profile.email.clean() ?: user?.email.clean() ?: "-"}",
                    "Telephone: ${profile.telephone.clean() ?: "-"}"
                ).joinToString("\n"),
                badge = "Contact",
                icon = "@"
            )
        )
    }

    fun showSessionProfileDialog(context: Context, user: UserSummary, onLogout: () -> Unit) {
        AlertDialog.Builder(context)
            .setTitle("Mon profil")
            .setView(sessionProfileView(context, user))
            .setNegativeButton("Fermer", null)
            .setPositiveButton("Deconnexion") { _, _ -> onLogout() }
            .show()
    }

    private fun sessionProfileView(context: Context, user: UserSummary): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(context.dp(28), context.dp(18), context.dp(28), context.dp(8))

            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 0, 0, context.dp(18))
                gravity = android.view.Gravity.CENTER_VERTICAL

                addView(avatar(context, user.fullName))
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(context.dp(14), 0, 0, 0)
                    addView(TextView(context).apply {
                        text = user.fullName.clean() ?: "Utilisateur"
                        textSize = 18f
                        setTypeface(typeface, Typeface.BOLD)
                        setTextColor(ContextCompat.getColor(context, R.color.textPrimary))
                    })
                    addView(TextView(context).apply {
                        text = roleLabel(user.role)
                        textSize = 13f
                        setTextColor(ContextCompat.getColor(context, R.color.primary))
                    })
                })
            })

            addInfoLine(context, "Identifiant", user.username)
            addInfoLine(context, "Email", user.email.clean() ?: "-")
            addInfoLine(context, "Role", roleLabel(user.role))
            addInfoLine(context, "Espace", "GestionEtu mobile")
        }
    }

    private fun LinearLayout.addInfoLine(context: Context, label: String, value: String) {
        addView(TextView(context).apply {
            text = label
            textSize = 12f
            setTextColor(ContextCompat.getColor(context, R.color.textSecondary))
            setPadding(0, context.dp(10), 0, context.dp(2))
        })
        addView(TextView(context).apply {
            text = value
            textSize = 15f
            setTextColor(ContextCompat.getColor(context, R.color.textPrimary))
        })
    }

    private fun avatar(context: Context, name: String): TextView {
        return TextView(context).apply {
            text = initials(name)
            gravity = android.view.Gravity.CENTER
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(context, R.color.primary))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(ContextCompat.getColor(context, R.color.primaryContainer))
            }
            layoutParams = LinearLayout.LayoutParams(context.dp(72), context.dp(72))
            contentDescription = "Avatar de profil"
        }
    }

    private fun initials(name: String?): String {
        val words = name.clean()
            ?.split(" ")
            ?.filter { it.isNotBlank() }
            .orEmpty()
        val letters = words.take(2).mapNotNull { it.firstOrNull()?.uppercaseChar() }
        return letters.joinToString("").ifBlank { "PR" }
    }

    private fun roleLabel(role: String): String {
        return when (role.trim().uppercase(Locale.ROOT)) {
            "ADMIN" -> "Administrateur"
            "CHEF_FILIERE" -> "Chef filiere"
            "TEACHER" -> "Enseignant"
            "STUDENT" -> "Etudiant"
            else -> role.ifBlank { "Utilisateur" }
        }
    }

    private fun String?.clean(): String? {
        return this
            ?.replace("\\s+".toRegex(), " ")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private fun Context.dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
