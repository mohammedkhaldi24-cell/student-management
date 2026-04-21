package com.pfe.gestionetudiantmobile.ui.common

object AcademicNotificationCopy {
    private const val EMAIL_STATUS =
        "Les etudiants concernes verront l'evenement dans l'app et recevront un email si l'envoi est active cote backend."

    val teacherHint: String = "Notification academique: $EMAIL_STATUS"

    val studentHint: String =
        "Les evenements importants apparaissent ici. Selon la configuration de l'etablissement, ils peuvent aussi arriver par email."

    fun success(action: String): String = "$action. $EMAIL_STATUS"
}
