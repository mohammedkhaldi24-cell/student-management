package com.pfe.gestionetudiantmobile.data.model

data class AcademicStatistics(
    val averageByModule: List<AcademicStatPoint>,
    val absenceRateByModule: List<AcademicStatPoint>,
    val assignmentCompletionByModule: List<AcademicStatPoint>,
    val suggestedModuleId: Long? = null,
    val suggestedModuleLabel: String? = null,
    val highlightedModuleId: Long? = null,
    val summary: String = ""
)

data class AcademicStatPoint(
    val moduleId: Long?,
    val label: String,
    val value: Double,
    val valueLabel: String,
    val detail: String
)
