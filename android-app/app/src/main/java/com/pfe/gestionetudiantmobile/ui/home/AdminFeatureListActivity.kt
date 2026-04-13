package com.pfe.gestionetudiantmobile.ui.home

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Switch
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.pfe.gestionetudiantmobile.data.model.AdminClasseUpsertRequest
import com.pfe.gestionetudiantmobile.data.model.AdminFiliereUpsertRequest
import com.pfe.gestionetudiantmobile.data.model.AdminModuleUpsertRequest
import com.pfe.gestionetudiantmobile.data.model.AdminUserUpsertRequest
import com.pfe.gestionetudiantmobile.data.model.UserSummary
import com.pfe.gestionetudiantmobile.data.repository.AdminRepository
import com.pfe.gestionetudiantmobile.databinding.ActivityFeatureListBinding
import com.pfe.gestionetudiantmobile.ui.common.UiRow
import com.pfe.gestionetudiantmobile.ui.common.UiRowAdapter
import com.pfe.gestionetudiantmobile.util.ApiResult
import kotlinx.coroutines.launch

class AdminFeatureListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFeatureListBinding
    private val repository = AdminRepository()
    private val adapter = UiRowAdapter { row -> onRowClicked(row) }

    private var currentFeature: String = ""
    private var selectedQuery: String? = null
    private var selectedRole: String? = null
    private var selectedEnabled: Boolean? = null
    private var selectedFiliereId: Long? = null
    private var selectedTeacherId: Long? = null

    private var userRows: Map<Long, UserSummary> = emptyMap()
    private var filiereRows: Map<Long, Map<String, Any?>> = emptyMap()
    private var classeRows: Map<Long, Map<String, Any?>> = emptyMap()
    private var moduleRows: Map<Long, Map<String, Any?>> = emptyMap()

    private var filiereOptions: List<OptionItem> = emptyList()
    private var teacherOptions: List<OptionItem> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFeatureListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        currentFeature = intent.getStringExtra(EXTRA_FEATURE)?.trim()?.lowercase().orEmpty()
        binding.tvTitle.text = when (currentFeature) {
            "users" -> "Utilisateurs"
            "filieres" -> "Filieres"
            "classes" -> "Classes"
            "modules" -> "Modules"
            else -> "Liste"
        }

        binding.btnBack.setOnClickListener { finish() }
        binding.swipeLayout.setOnRefreshListener { loadFeature() }

        binding.btnFilter.visibility = View.VISIBLE
        binding.btnFilter.setOnClickListener { openFilterDialog() }

        binding.btnAction.visibility = View.VISIBLE
        binding.btnAction.text = "Nouveau"
        binding.btnAction.setOnClickListener { openCreateDialog() }

        lifecycleScope.launch {
            loadOptions()
            loadFeature()
        }
    }

    private suspend fun loadOptions() {
        when (val filieres = repository.filieres()) {
            is ApiResult.Success -> {
                filiereOptions = filieres.data.mapNotNull { row ->
                    mapLong(row, "id")?.let { OptionItem(it, mapString(row, "nom") ?: "Filiere $it") }
                }
            }
            is ApiResult.Error -> showError(filieres.message)
        }

        when (val teachers = repository.users(role = "TEACHER")) {
            is ApiResult.Success -> {
                teacherOptions = teachers.data.map { OptionItem(it.id, "${it.fullName} (${it.username})") }
            }
            is ApiResult.Error -> showError(teachers.message)
        }
    }

    private fun loadFeature() {
        lifecycleScope.launch {
            binding.swipeLayout.isRefreshing = true

            when (currentFeature) {
                "users" -> loadUsers()
                "filieres" -> loadFilieres()
                "classes" -> loadClasses()
                "modules" -> loadModules()
                else -> adapter.submitList(listOf(UiRow(title = "Feature inconnue", subtitle = "Aucune donnee")))
            }

            updateFilterSummary()
            binding.swipeLayout.isRefreshing = false
        }
    }

    private suspend fun loadUsers() {
        when (val result = repository.users(selectedRole, selectedQuery, selectedEnabled)) {
            is ApiResult.Success -> {
                userRows = result.data.associateBy { it.id }
                adapter.submitList(result.data.map {
                    UiRow(
                        id = it.id,
                        title = "${it.fullName} (${it.username})",
                        subtitle = "${it.role} | ${it.email ?: "-"}",
                        badge = it.redirectPath
                    )
                })
            }
            is ApiResult.Error -> showError(result.message)
        }
    }

    private suspend fun loadFilieres() {
        when (val result = repository.filieres(selectedQuery)) {
            is ApiResult.Success -> {
                filiereRows = result.data.mapNotNull { row -> mapLong(row, "id")?.let { it to row } }.toMap()
                adapter.submitList(result.data.map { row ->
                    UiRow(
                        id = mapLong(row, "id"),
                        title = "${mapString(row, "nom") ?: "-"} (${mapString(row, "code") ?: "-"})",
                        subtitle = "Chef: ${mapString(row, "chefFiliere") ?: "-"}",
                        badge = "ID ${mapLong(row, "id") ?: "-"}"
                    )
                })
            }
            is ApiResult.Error -> showError(result.message)
        }
    }

    private suspend fun loadClasses() {
        when (val result = repository.classes(selectedFiliereId, selectedQuery)) {
            is ApiResult.Success -> {
                classeRows = result.data.mapNotNull { row -> mapLong(row, "id")?.let { it to row } }.toMap()
                adapter.submitList(result.data.map { row ->
                    UiRow(
                        id = mapLong(row, "id"),
                        title = "${mapString(row, "nom") ?: "-"} - ${mapString(row, "niveau") ?: "-"}",
                        subtitle = "Filiere: ${mapString(row, "filiereNom") ?: "-"}",
                        badge = mapString(row, "anneeAcademique") ?: ""
                    )
                })
            }
            is ApiResult.Error -> showError(result.message)
        }
    }

    private suspend fun loadModules() {
        when (val result = repository.modules(selectedFiliereId, selectedTeacherId, selectedQuery)) {
            is ApiResult.Success -> {
                moduleRows = result.data.mapNotNull { row -> mapLong(row, "id")?.let { it to row } }.toMap()
                adapter.submitList(result.data.map { row ->
                    UiRow(
                        id = mapLong(row, "id"),
                        title = "${mapString(row, "nom") ?: "-"} (${mapString(row, "code") ?: "-"})",
                        subtitle = "Filiere: ${mapString(row, "filiereNom") ?: "-"} | Enseignant: ${mapString(row, "teacherName") ?: "-"}",
                        badge = "Coeff ${mapInt(row, "coefficient") ?: "-"}"
                    )
                })
            }
            is ApiResult.Error -> showError(result.message)
        }
    }

    private fun onRowClicked(row: UiRow) {
        when (currentFeature) {
            "users" -> onUserRowClicked(row)
            "filieres" -> onFiliereRowClicked(row)
            "classes" -> onClasseRowClicked(row)
            "modules" -> onModuleRowClicked(row)
        }
    }

    private fun updateFilterSummary() {
        val parts = mutableListOf<String>()
        if (!selectedQuery.isNullOrBlank()) parts += "Recherche: ${selectedQuery!!.trim()}"
        if (!selectedRole.isNullOrBlank()) parts += "Role: $selectedRole"
        if (selectedEnabled != null) parts += if (selectedEnabled == true) "Statut: Actif" else "Statut: Inactif"
        if (selectedFiliereId != null) {
            val f = filiereOptions.firstOrNull { it.id == selectedFiliereId }?.label
            if (!f.isNullOrBlank()) parts += "Filiere: $f"
        }
        if (selectedTeacherId != null) {
            val t = teacherOptions.firstOrNull { it.id == selectedTeacherId }?.label
            if (!t.isNullOrBlank()) parts += "Enseignant: $t"
        }
        binding.tvFilterSummary.visibility = View.VISIBLE
        binding.tvFilterSummary.text = if (parts.isEmpty()) "Aucun filtre actif" else parts.joinToString(" | ")
    }

    private fun openFilterDialog() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 24, 40, 8)
        }
        val searchInput = EditText(this).apply {
            hint = "Recherche"
            setText(selectedQuery.orEmpty())
        }
        root.addView(searchInput)

        var roleSpinner: Spinner? = null
        var statusSpinner: Spinner? = null
        var filiereSpinner: Spinner? = null
        var teacherSpinner: Spinner? = null

        if (currentFeature == "users") {
            roleSpinner = Spinner(this).apply {
                val roles = listOf("Tous", "ADMIN", "CHEF_FILIERE", "TEACHER", "STUDENT")
                adapter = ArrayAdapter(this@AdminFeatureListActivity, android.R.layout.simple_spinner_dropdown_item, roles)
                setSelection(
                    when (selectedRole) {
                        "ADMIN" -> 1
                        "CHEF_FILIERE" -> 2
                        "TEACHER" -> 3
                        "STUDENT" -> 4
                        else -> 0
                    }
                )
            }
            root.addView(roleSpinner)
            statusSpinner = Spinner(this).apply {
                val statuses = listOf("Tous statuts", "Actifs", "Inactifs")
                adapter = ArrayAdapter(this@AdminFeatureListActivity, android.R.layout.simple_spinner_dropdown_item, statuses)
                setSelection(
                    when (selectedEnabled) {
                        true -> 1
                        false -> 2
                        else -> 0
                    }
                )
            }
            root.addView(statusSpinner)
        }

        if (currentFeature == "classes" || currentFeature == "modules") {
            filiereSpinner = Spinner(this).apply {
                val options = mutableListOf("Toutes filieres") + filiereOptions.map { it.label }
                adapter = ArrayAdapter(this@AdminFeatureListActivity, android.R.layout.simple_spinner_dropdown_item, options)
                val idx = filiereOptions.indexOfFirst { it.id == selectedFiliereId }.takeIf { it >= 0 }?.plus(1) ?: 0
                setSelection(idx)
            }
            root.addView(filiereSpinner)
        }

        if (currentFeature == "modules") {
            teacherSpinner = Spinner(this).apply {
                val options = mutableListOf("Tous enseignants") + teacherOptions.map { it.label }
                adapter = ArrayAdapter(this@AdminFeatureListActivity, android.R.layout.simple_spinner_dropdown_item, options)
                val idx = teacherOptions.indexOfFirst { it.id == selectedTeacherId }.takeIf { it >= 0 }?.plus(1) ?: 0
                setSelection(idx)
            }
            root.addView(teacherSpinner)
        }

        AlertDialog.Builder(this)
            .setTitle("Filtrer")
            .setView(root)
            .setNegativeButton("Annuler", null)
            .setNeutralButton("Reinitialiser") { _, _ ->
                selectedQuery = null
                selectedRole = null
                selectedEnabled = null
                selectedFiliereId = null
                selectedTeacherId = null
                loadFeature()
            }
            .setPositiveButton("Appliquer") { _, _ ->
                selectedQuery = searchInput.text?.toString()?.trim()?.ifBlank { null }
                if (currentFeature == "users") {
                    selectedRole = when (roleSpinner?.selectedItemPosition ?: 0) {
                        1 -> "ADMIN"
                        2 -> "CHEF_FILIERE"
                        3 -> "TEACHER"
                        4 -> "STUDENT"
                        else -> null
                    }
                    selectedEnabled = when (statusSpinner?.selectedItemPosition ?: 0) {
                        1 -> true
                        2 -> false
                        else -> null
                    }
                }
                if (currentFeature == "classes" || currentFeature == "modules") {
                    val idx = filiereSpinner?.selectedItemPosition ?: 0
                    selectedFiliereId = if (idx > 0) filiereOptions[idx - 1].id else null
                }
                if (currentFeature == "modules") {
                    val idx = teacherSpinner?.selectedItemPosition ?: 0
                    selectedTeacherId = if (idx > 0) teacherOptions[idx - 1].id else null
                }
                loadFeature()
            }
            .show()
    }

    private fun openCreateDialog() {
        when (currentFeature) {
            "users" -> openUserDialog(null)
            "filieres" -> openFiliereDialog(null)
            "classes" -> openClasseDialog(null)
            "modules" -> openModuleDialog(null)
        }
    }

    private fun onUserRowClicked(row: UiRow) {
        val user = row.id?.let { userRows[it] } ?: return
        AlertDialog.Builder(this)
            .setTitle(user.fullName)
            .setItems(arrayOf("Modifier", "Changer statut", "Supprimer")) { _, which ->
                when (which) {
                    0 -> openUserDialog(user)
                    1 -> lifecycleScope.launch {
                        when (val result = repository.toggleUser(user.id)) {
                            is ApiResult.Success -> showInfo("Statut mis a jour.")
                            is ApiResult.Error -> showError(result.message)
                        }
                        loadFeature()
                    }
                    2 -> confirmDelete("Supprimer cet utilisateur ?") {
                        lifecycleScope.launch {
                            when (val result = repository.deleteUser(user.id)) {
                                is ApiResult.Success -> showInfo(result.data.message)
                                is ApiResult.Error -> showError(result.message)
                            }
                            loadFeature()
                        }
                    }
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun onFiliereRowClicked(row: UiRow) {
        val item = row.id?.let { filiereRows[it] } ?: return
        AlertDialog.Builder(this)
            .setTitle(mapString(item, "nom") ?: "Filiere")
            .setItems(arrayOf("Modifier", "Supprimer")) { _, which ->
                when (which) {
                    0 -> openFiliereDialog(item)
                    1 -> confirmDelete("Supprimer cette filiere ?") {
                        val id = mapLong(item, "id") ?: return@confirmDelete
                        lifecycleScope.launch {
                            when (val result = repository.deleteFiliere(id)) {
                                is ApiResult.Success -> showInfo(result.data.message)
                                is ApiResult.Error -> showError(result.message)
                            }
                            loadOptions(); loadFeature()
                        }
                    }
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun onClasseRowClicked(row: UiRow) {
        val item = row.id?.let { classeRows[it] } ?: return
        AlertDialog.Builder(this)
            .setTitle(mapString(item, "nom") ?: "Classe")
            .setItems(arrayOf("Modifier", "Supprimer")) { _, which ->
                when (which) {
                    0 -> openClasseDialog(item)
                    1 -> confirmDelete("Supprimer cette classe ?") {
                        val id = mapLong(item, "id") ?: return@confirmDelete
                        lifecycleScope.launch {
                            when (val result = repository.deleteClasse(id)) {
                                is ApiResult.Success -> showInfo(result.data.message)
                                is ApiResult.Error -> showError(result.message)
                            }
                            loadOptions(); loadFeature()
                        }
                    }
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun onModuleRowClicked(row: UiRow) {
        val item = row.id?.let { moduleRows[it] } ?: return
        AlertDialog.Builder(this)
            .setTitle(mapString(item, "nom") ?: "Module")
            .setItems(arrayOf("Modifier", "Supprimer")) { _, which ->
                when (which) {
                    0 -> openModuleDialog(item)
                    1 -> confirmDelete("Supprimer ce module ?") {
                        val id = mapLong(item, "id") ?: return@confirmDelete
                        lifecycleScope.launch {
                            when (val result = repository.deleteModule(id)) {
                                is ApiResult.Success -> showInfo(result.data.message)
                                is ApiResult.Error -> showError(result.message)
                            }
                            loadFeature()
                        }
                    }
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun openUserDialog(user: UserSummary?) {
        val names = splitName(user?.fullName)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 24, 40, 8) }
        val username = EditText(this).apply { hint = "Nom utilisateur"; setText(user?.username.orEmpty()) }
        val firstName = EditText(this).apply { hint = "Prenom"; setText(names.first) }
        val lastName = EditText(this).apply { hint = "Nom"; setText(names.second) }
        val email = EditText(this).apply { hint = "Email"; setText(user?.email.orEmpty()) }
        val password = EditText(this).apply { hint = if (user == null) "Mot de passe" else "Mot de passe (optionnel)" }
        val roleSpinner = Spinner(this)
        val roles = listOf("ADMIN", "CHEF_FILIERE", "TEACHER", "STUDENT")
        roleSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, roles)
        roleSpinner.setSelection(roles.indexOf(user?.role ?: "STUDENT").takeIf { it >= 0 } ?: 3)
        val enabled = Switch(this).apply { text = "Compte actif"; isChecked = true }

        root.addView(username); root.addView(firstName); root.addView(lastName); root.addView(email); root.addView(password); root.addView(roleSpinner); root.addView(enabled)

        AlertDialog.Builder(this)
            .setTitle(if (user == null) "Nouveau utilisateur" else "Modifier utilisateur")
            .setView(root)
            .setNegativeButton("Annuler", null)
            .setPositiveButton(if (user == null) "Creer" else "Mettre a jour") { _, _ ->
                val usernameValue = username.text?.toString()?.trim().orEmpty()
                val firstNameValue = firstName.text?.toString()?.trim().orEmpty()
                val lastNameValue = lastName.text?.toString()?.trim().orEmpty()
                val passwordValue = password.text?.toString()?.trim()?.ifBlank { null }

                if (usernameValue.isBlank()) {
                    showError("Le nom utilisateur est obligatoire.")
                    return@setPositiveButton
                }
                if (firstNameValue.isBlank()) {
                    showError("Le prenom est obligatoire.")
                    return@setPositiveButton
                }
                if (lastNameValue.isBlank()) {
                    showError("Le nom est obligatoire.")
                    return@setPositiveButton
                }
                if (user == null && passwordValue.isNullOrBlank()) {
                    showError("Le mot de passe est obligatoire pour la creation.")
                    return@setPositiveButton
                }

                val request = AdminUserUpsertRequest(
                    username = usernameValue,
                    password = passwordValue,
                    email = email.text?.toString()?.trim()?.ifBlank { null },
                    firstName = firstNameValue,
                    lastName = lastNameValue,
                    role = roles[roleSpinner.selectedItemPosition],
                    enabled = enabled.isChecked
                )
                lifecycleScope.launch {
                    when (val result = if (user == null) repository.createUser(request) else repository.updateUser(user.id, request)) {
                        is ApiResult.Success -> showInfo("Utilisateur enregistre.")
                        is ApiResult.Error -> showError(result.message)
                    }
                    loadFeature()
                }
            }
            .show()
    }

    private fun openFiliereDialog(row: Map<String, Any?>?) {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 24, 40, 8) }
        val nom = EditText(this).apply { hint = "Nom"; setText(mapString(row, "nom").orEmpty()) }
        val code = EditText(this).apply { hint = "Code"; setText(mapString(row, "code").orEmpty()) }
        val description = EditText(this).apply { hint = "Description"; setText(mapString(row, "description").orEmpty()) }
        root.addView(nom); root.addView(code); root.addView(description)

        AlertDialog.Builder(this)
            .setTitle(if (row == null) "Nouvelle filiere" else "Modifier filiere")
            .setView(root)
            .setNegativeButton("Annuler", null)
            .setPositiveButton(if (row == null) "Creer" else "Mettre a jour") { _, _ ->
                val nomValue = nom.text?.toString()?.trim().orEmpty()
                val codeValue = code.text?.toString()?.trim().orEmpty()
                if (nomValue.isBlank()) {
                    showError("Le nom de la filiere est obligatoire.")
                    return@setPositiveButton
                }
                if (codeValue.isBlank()) {
                    showError("Le code de la filiere est obligatoire.")
                    return@setPositiveButton
                }

                val request = AdminFiliereUpsertRequest(
                    nom = nomValue,
                    code = codeValue,
                    description = description.text?.toString()?.trim()?.ifBlank { null }
                )
                lifecycleScope.launch {
                    val result = if (row == null) repository.createFiliere(request) else repository.updateFiliere(mapLong(row, "id") ?: return@launch, request)
                    when (result) {
                        is ApiResult.Success -> showInfo("Filiere enregistree.")
                        is ApiResult.Error -> showError(result.message)
                    }
                    loadOptions(); loadFeature()
                }
            }
            .show()
    }

    private fun openClasseDialog(row: Map<String, Any?>?) {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 24, 40, 8) }
        val nom = EditText(this).apply { hint = "Nom classe"; setText(mapString(row, "nom").orEmpty()) }
        val niveau = EditText(this).apply { hint = "Niveau"; setText(mapString(row, "niveau") ?: "L1") }
        val annee = EditText(this).apply { hint = "Annee academique"; setText(mapString(row, "anneeAcademique") ?: "2025-2026") }
        val filiereSpinner = Spinner(this)
        filiereSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, filiereOptions.map { it.label })
        filiereSpinner.setSelection(filiereOptions.indexOfFirst { it.id == mapLong(row, "filiereId") }.takeIf { it >= 0 } ?: 0)
        root.addView(nom); root.addView(niveau); root.addView(annee); root.addView(filiereSpinner)

        AlertDialog.Builder(this)
            .setTitle(if (row == null) "Nouvelle classe" else "Modifier classe")
            .setView(root)
            .setNegativeButton("Annuler", null)
            .setPositiveButton(if (row == null) "Creer" else "Mettre a jour") { _, _ ->
                val nomValue = nom.text?.toString()?.trim().orEmpty()
                val niveauValue = niveau.text?.toString()?.trim().orEmpty()
                val anneeValue = annee.text?.toString()?.trim().orEmpty()

                if (nomValue.isBlank()) {
                    showError("Le nom de la classe est obligatoire.")
                    return@setPositiveButton
                }
                if (niveauValue.isBlank()) {
                    showError("Le niveau est obligatoire.")
                    return@setPositiveButton
                }
                if (anneeValue.isBlank()) {
                    showError("L'annee academique est obligatoire.")
                    return@setPositiveButton
                }
                if (filiereOptions.isEmpty()) {
                    showError("Aucune filiere disponible.")
                    return@setPositiveButton
                }
                val request = AdminClasseUpsertRequest(
                    nom = nomValue,
                    niveau = niveauValue,
                    anneeAcademique = anneeValue,
                    filiereId = filiereOptions[filiereSpinner.selectedItemPosition].id
                )
                lifecycleScope.launch {
                    val result = if (row == null) repository.createClasse(request) else repository.updateClasse(mapLong(row, "id") ?: return@launch, request)
                    when (result) {
                        is ApiResult.Success -> showInfo("Classe enregistree.")
                        is ApiResult.Error -> showError(result.message)
                    }
                    loadOptions(); loadFeature()
                }
            }
            .show()
    }

    private fun openModuleDialog(row: Map<String, Any?>?) {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 24, 40, 8) }
        val nom = EditText(this).apply { hint = "Nom module"; setText(mapString(row, "nom").orEmpty()) }
        val code = EditText(this).apply { hint = "Code module"; setText(mapString(row, "code").orEmpty()) }
        val coeff = EditText(this).apply { hint = "Coefficient"; setText((mapInt(row, "coefficient") ?: 1).toString()) }
        val volume = EditText(this).apply { hint = "Volume horaire"; setText((mapInt(row, "volumeHoraire") ?: 30).toString()) }
        val semestre = EditText(this).apply { hint = "Semestre (S1/S2)"; setText(mapString(row, "semestre") ?: "S1") }

        val filiereSpinner = Spinner(this)
        filiereSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, filiereOptions.map { it.label })
        filiereSpinner.setSelection(filiereOptions.indexOfFirst { it.id == mapLong(row, "filiereId") }.takeIf { it >= 0 } ?: 0)

        val teacherChoices = mutableListOf(OptionItem(0L, "Aucun enseignant")) + teacherOptions
        val teacherSpinner = Spinner(this)
        teacherSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, teacherChoices.map { it.label })
        teacherSpinner.setSelection(teacherChoices.indexOfFirst { it.id == (mapLong(row, "teacherId") ?: 0L) }.takeIf { it >= 0 } ?: 0)

        root.addView(nom); root.addView(code); root.addView(coeff); root.addView(volume); root.addView(semestre); root.addView(filiereSpinner); root.addView(teacherSpinner)

        AlertDialog.Builder(this)
            .setTitle(if (row == null) "Nouveau module" else "Modifier module")
            .setView(root)
            .setNegativeButton("Annuler", null)
            .setPositiveButton(if (row == null) "Creer" else "Mettre a jour") { _, _ ->
                val nomValue = nom.text?.toString()?.trim().orEmpty()
                val codeValue = code.text?.toString()?.trim().orEmpty()
                val semestreValue = semestre.text?.toString()?.trim().orEmpty()
                val coefficientValue = coeff.text?.toString()?.toIntOrNull() ?: 1

                if (nomValue.isBlank()) {
                    showError("Le nom du module est obligatoire.")
                    return@setPositiveButton
                }
                if (codeValue.isBlank()) {
                    showError("Le code du module est obligatoire.")
                    return@setPositiveButton
                }
                if (semestreValue.isBlank()) {
                    showError("Le semestre est obligatoire.")
                    return@setPositiveButton
                }
                if (coefficientValue < 1) {
                    showError("Le coefficient doit etre superieur ou egal a 1.")
                    return@setPositiveButton
                }
                if (filiereOptions.isEmpty()) {
                    showError("Aucune filiere disponible.")
                    return@setPositiveButton
                }
                val selectedTeacher = teacherChoices[teacherSpinner.selectedItemPosition].id
                val teacherForApi = when {
                    selectedTeacher > 0 -> selectedTeacher
                    row != null -> 0L
                    else -> null
                }
                val request = AdminModuleUpsertRequest(
                    nom = nomValue,
                    code = codeValue,
                    coefficient = coefficientValue,
                    volumeHoraire = volume.text?.toString()?.toIntOrNull(),
                    semestre = semestreValue,
                    filiereId = filiereOptions[filiereSpinner.selectedItemPosition].id,
                    teacherId = teacherForApi
                )
                lifecycleScope.launch {
                    val result = if (row == null) repository.createModule(request) else repository.updateModule(mapLong(row, "id") ?: return@launch, request)
                    when (result) {
                        is ApiResult.Success -> showInfo("Module enregistre.")
                        is ApiResult.Error -> showError(result.message)
                    }
                    loadFeature()
                }
            }
            .show()
    }

    private fun confirmDelete(message: String, onConfirm: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle("Confirmation")
            .setMessage(message)
            .setNegativeButton("Annuler", null)
            .setPositiveButton("Supprimer") { _, _ -> onConfirm() }
            .show()
    }

    private fun mapLong(map: Map<String, Any?>?, key: String): Long? {
        val value = map?.get(key) ?: return null
        return when (value) {
            is Number -> value.toLong()
            is String -> value.toLongOrNull()
            else -> null
        }
    }

    private fun mapInt(map: Map<String, Any?>?, key: String): Int? {
        val value = map?.get(key) ?: return null
        return when (value) {
            is Number -> value.toInt()
            is String -> value.toIntOrNull()
            else -> null
        }
    }

    private fun mapString(map: Map<String, Any?>?, key: String): String? {
        val value = map?.get(key) ?: return null
        val text = value.toString()
        return text.takeIf { it.isNotBlank() && it != "null" }
    }

    private fun splitName(fullName: String?): Pair<String, String> {
        if (fullName.isNullOrBlank()) return "" to ""
        val cleaned = fullName.trim().replace("\\s+".toRegex(), " ")
        val parts = cleaned.split(" ")
        if (parts.size == 1) return parts[0] to ""
        return parts.first() to parts.drop(1).joinToString(" ")
    }

    private fun showInfo(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    companion object {
        const val EXTRA_FEATURE = "extra_feature"
    }
}

private data class OptionItem(
    val id: Long,
    val label: String
)
