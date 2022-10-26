package com.filestack.android.internal

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.filestack.android.R
import com.filestack.android.Selection

class LocalFilesAdapter(
    private val interactionListener: LocalFilesInteractionListener = LocalFilesInteractionListener {}
): RecyclerView.Adapter<LocalFilesAdapter.ViewHolder>() {

    private val selections = mutableListOf<Selection>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(
            R.layout.filestack__local_files_list_item, parent, false
        )
        return ViewHolder(view, interactionListener)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(selections[position])
    }

    override fun getItemCount(): Int = selections.size

    fun addSelections(selectedFiles: List<Selection>) {
        val positionStart = selections.size
        selections.addAll(selectedFiles)
        notifyItemRangeInserted(
            positionStart,
            selectedFiles.size
        )
    }

    fun removeSelection(selection: Selection) {
        val idx = selections.indexOf(selection)
        if (idx >= 0) {
            selections.removeAt(idx)
            notifyItemRemoved(idx)
        }
    }

    class ViewHolder(
        view: View,
        listener: LocalFilesInteractionListener
    ): RecyclerView.ViewHolder(view) {
        private var selection: Selection? = null
        private val selectionName: TextView

        init {
            selectionName = view.findViewById(R.id.text_id)
            view.findViewById<View>(R.id.image_close).setOnClickListener {
                selection?.let {  listener.clearSelection(it) }
            }
        }

        fun bind(selection: Selection) {
            this.selection = selection
            selectionName.text = selection.name
        }
    }

    fun interface LocalFilesInteractionListener {
        fun clearSelection(selection: Selection)
    }
}