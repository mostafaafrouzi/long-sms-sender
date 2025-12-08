package com.mostafaafrouzi.longsmssender.ui

import android.widget.SectionIndexer
import androidx.recyclerview.widget.RecyclerView
import com.mostafaafrouzi.longsmssender.data.model.Contact

abstract class SectionIndexerAdapter<T : RecyclerView.ViewHolder>(
    private val alphabetIndexer: AlphabetIndexer
) : RecyclerView.Adapter<T>(), SectionIndexer {

    override fun getSections(): Array<String> {
        return alphabetIndexer.getSections()
    }

    override fun getPositionForSection(sectionIndex: Int): Int {
        return alphabetIndexer.getPositionForSection(sectionIndex)
    }

    override fun getSectionForPosition(position: Int): Int {
        return alphabetIndexer.getSectionForPosition(position)
    }
}

