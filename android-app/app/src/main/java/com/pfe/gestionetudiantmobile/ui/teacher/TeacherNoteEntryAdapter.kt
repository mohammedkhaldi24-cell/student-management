package com.pfe.gestionetudiantmobile.ui.teacher

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.pfe.gestionetudiantmobile.data.model.NoteItem
import com.pfe.gestionetudiantmobile.data.model.StudentProfile
import com.pfe.gestionetudiantmobile.databinding.ItemTeacherNoteEntryBinding
import java.util.Locale

class TeacherNoteEntryAdapter : RecyclerView.Adapter<TeacherNoteEntryAdapter.NoteViewHolder>() {

    private val rows = mutableListOf<TeacherNoteEntry>()

    init {
        setHasStableIds(true)
    }

    fun submitEntries(entries: List<TeacherNoteEntry>) {
        rows.clear()
        rows.addAll(entries)
        notifyDataSetChanged()
    }

    fun currentEntries(): List<TeacherNoteEntry> = rows.toList()

    override fun getItemId(position: Int): Long = rows[position].student.id

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
        val binding = ItemTeacherNoteEntryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return NoteViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        holder.bind(rows[position])
    }

    override fun getItemCount(): Int = rows.size

    class NoteViewHolder(private val binding: ItemTeacherNoteEntryBinding) : RecyclerView.ViewHolder(binding.root) {
        private var ccWatcher: TextWatcher? = null
        private var examWatcher: TextWatcher? = null

        fun bind(entry: TeacherNoteEntry) {
            binding.tvStudentName.text = entry.student.fullName
            binding.tvStudentMeta.text = "${entry.student.matricule} | ${entry.student.classe ?: "-"}"
            binding.tvFinalBadge.text = finalLabel(entry)

            ccWatcher?.let { binding.inputNoteCc.removeTextChangedListener(it) }
            examWatcher?.let { binding.inputNoteExam.removeTextChangedListener(it) }

            binding.inputNoteCc.setText(entry.noteCcText)
            binding.inputNoteExam.setText(entry.noteExamText)

            ccWatcher = object : SimpleTextWatcher() {
                override fun afterTextChanged(s: Editable?) {
                    entry.noteCcText = s?.toString().orEmpty()
                    binding.tvFinalBadge.text = finalLabel(entry)
                }
            }
            examWatcher = object : SimpleTextWatcher() {
                override fun afterTextChanged(s: Editable?) {
                    entry.noteExamText = s?.toString().orEmpty()
                    binding.tvFinalBadge.text = finalLabel(entry)
                }
            }

            binding.inputNoteCc.addTextChangedListener(ccWatcher)
            binding.inputNoteExam.addTextChangedListener(examWatcher)
        }

        private fun finalLabel(entry: TeacherNoteEntry): String {
            val cc = entry.noteCcText.toNoteValue()
            val exam = entry.noteExamText.toNoteValue()
            val final = when {
                cc != null && exam != null -> (cc * 0.4) + (exam * 0.6)
                entry.note?.noteFinal != null -> entry.note.noteFinal
                else -> null
            }
            return final?.let { String.format(Locale.ROOT, "%.2f", it) } ?: "--"
        }
    }
}

data class TeacherNoteEntry(
    val student: StudentProfile,
    val note: NoteItem?,
    var noteCcText: String,
    var noteExamText: String
)

abstract class SimpleTextWatcher : TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
}

fun String.toNoteValue(): Double? {
    val value = trim().replace(',', '.')
    if (value.isBlank()) return null
    return value.toDoubleOrNull()
}
