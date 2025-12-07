package com.mostafaafrouzi.longsmssender.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mostafaafrouzi.longsmssender.data.model.Contact
import com.mostafaafrouzi.longsmssender.databinding.ItemContactBinding

class ContactAdapter(
    private val onItemClick: (Contact) -> Unit
) : ListAdapter<Contact, ContactAdapter.ContactViewHolder>(ContactDiffCallback()) {

    private var selectedIds = setOf<String>()

    fun updateSelection(newSelection: Set<String>) {
        val oldSelection = selectedIds
        selectedIds = newSelection
        
        // Use DiffUtil to efficiently update only changed items
        val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = currentList.size
            override fun getNewListSize(): Int = currentList.size
            
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return oldItemPosition == newItemPosition
            }
            
            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val contact = currentList[oldItemPosition]
                val wasSelected = oldSelection.contains(contact.id)
                val isSelected = newSelection.contains(contact.id)
                return wasSelected == isSelected
            }
        })
        
        diffResult.dispatchUpdatesTo(this)
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
