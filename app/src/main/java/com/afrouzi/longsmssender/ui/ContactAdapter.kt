package com.afrouzi.longsmssender.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SectionIndexer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.afrouzi.longsmssender.data.model.Contact
import com.afrouzi.longsmssender.databinding.ItemContactBinding

class ContactAdapter(
    private val onItemClick: (Contact) -> Unit
) : ListAdapter<Contact, ContactAdapter.ContactViewHolder>(ContactDiffCallback()), SectionIndexer {

    private var selectedIds = setOf<String>()
    private var alphabetIndexer: AlphabetIndexer? = null

    fun updateSelection(newSelection: Set<String>) {
        // Only update if selection actually changed
        if (selectedIds == newSelection) {
            return
        }
        selectedIds = newSelection
        // Notify all items that selection might have changed
        // Use notifyDataSetChanged for better performance when many items change
        notifyDataSetChanged()
    }
    
    fun updateAlphabetIndexer(contacts: List<Contact>) {
        alphabetIndexer = AlphabetIndexer(contacts)
    }
    
    override fun getSections(): Array<String> {
        return alphabetIndexer?.getSections() ?: emptyArray()
    }
    
    override fun getPositionForSection(sectionIndex: Int): Int {
        return alphabetIndexer?.getPositionForSection(sectionIndex) ?: 0
    }
    
    override fun getSectionForPosition(position: Int): Int {
        return alphabetIndexer?.getSectionForPosition(position) ?: 0
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val binding = ItemContactBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ContactViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        val contact = getItem(position)
        holder.bind(contact, selectedIds.contains(contact.id))
    }

    inner class ContactViewHolder(private val binding: ItemContactBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(contact: Contact, isSelected: Boolean) {
            binding.txtName.text = contact.name
            binding.txtNumber.text = contact.phoneNumber
            binding.checkbox.visibility = View.VISIBLE
            binding.checkbox.isChecked = isSelected
            
            binding.root.setOnClickListener {
                onItemClick(contact)
            }
            binding.checkbox.setOnClickListener { 
                onItemClick(contact)
            }
        }
    }

    class ContactDiffCallback : DiffUtil.ItemCallback<Contact>() {
        override fun areItemsTheSame(oldItem: Contact, newItem: Contact): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Contact, newItem: Contact): Boolean {
            return oldItem == newItem
        }
    }
}
