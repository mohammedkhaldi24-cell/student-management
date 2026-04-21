package com.pfe.gestionetudiantmobile.ui.teacher

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.pfe.gestionetudiantmobile.data.model.StudentProfile
import com.pfe.gestionetudiantmobile.databinding.ItemTeacherAbsenceEntryBinding

class TeacherAbsenceSessionAdapter : RecyclerView.Adapter<TeacherAbsenceSessionAdapter.AbsenceViewHolder>() {

    private val rows = mutableListOf<TeacherAbsenceEntry>()

    init {
        setHasStableIds(true)
    }

    fun submitEntries(entries: List<TeacherAbsenceEntry>) {
        rows.clear()
        rows.addAll(entries)
        notifyDataSetChanged()
    }

    fun currentEntries(): List<TeacherAbsenceEntry> = rows.toList()

    fun markAllPresent() {
        rows.forEach { it.absent = false }
        notifyDataSetChanged()
    }

    override fun getItemId(position: Int): Long = rows[position].student.id

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AbsenceViewHolder {
        val binding = ItemTeacherAbsenceEntryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AbsenceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AbsenceViewHolder, position: Int) {
        holder.bind(rows[position])
    }

    override fun getItemCount(): Int = rows.size

    class AbsenceViewHolder(private val binding: ItemTeacherAbsenceEntryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(entry: TeacherAbsenceEntry) {
            binding.tvStudentName.text = entry.student.fullName
            binding.tvStudentMeta.text = "${entry.student.matricule} | ${entry.student.classe ?: "-"}"
            binding.checkAbsent.setOnCheckedChangeListener(null)
            binding.checkAbsent.isChecked = entry.absent
            binding.tvAbsenceStatus.text = if (entry.absent) {
                "Absent pour cette session"
            } else {
                "Present pour cette session"
            }
            binding.checkAbsent.setOnCheckedChangeListener { _, checked ->
                entry.absent = checked
                binding.tvAbsenceStatus.text = if (checked) {
                    "Absent pour cette session"
                } else {
                    "Present pour cette session"
                }
            }
        }
    }
}

data class TeacherAbsenceEntry(
    val student: StudentProfile,
    val existingAbsenceId: Long?,
    val existingHours: Int?,
    var absent: Boolean
)
