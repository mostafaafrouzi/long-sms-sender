package com.mostafaafrouzi.longsmssender.ui

import android.app.Application
import android.content.Context
import android.telephony.SmsMessage
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.mostafaafrouzi.longsmssender.R
import com.mostafaafrouzi.longsmssender.data.model.Contact
import com.mostafaafrouzi.longsmssender.data.model.SmsResult
import com.mostafaafrouzi.longsmssender.data.repository.ContactRepository
import com.mostafaafrouzi.longsmssender.data.repository.SmsRepository
import com.mostafaafrouzi.longsmssender.utils.NumberValidator
import kotlinx.coroutines.launch

data class BulkSendConfirmation(
    val recipientCount: Int,
    val estimatedParts: Int,
    val message: String
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ContactRepository(application)
    private val smsRepository = SmsRepository(application)
    private val context: Context = application

    private val _contacts = MutableLiveData<List<Contact>>()
    val contacts: LiveData<List<Contact>> = _contacts

    private val _selectedIds = MutableLiveData<Set<String>>(emptySet())
    val selectedIds: LiveData<Set<String>> = _selectedIds

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    
    private val _isSending = MutableLiveData<Boolean>()
    val isSending: LiveData<Boolean> = _isSending
    
    private val _segmentCount = MutableLiveData<String>()
    val segmentCount: LiveData<String> = _segmentCount
    
    private val _sendStatus = MutableLiveData<String>()
    val sendStatus: LiveData<String> = _sendStatus
    
    private val _bulkSendConfirmation = MutableLiveData<BulkSendConfirmation?>()
    val bulkSendConfirmation: LiveData<BulkSendConfirmation?> = _bulkSendConfirmation
    
    private var pendingRecipients: List<String> = emptyList()
    private var pendingMessage: String = ""

    fun loadContacts() {
        _isLoading.value = true
        viewModelScope.launch {
            val list = repository.getContacts()
            _contacts.postValue(list)
            _isLoading.postValue(false)
        }
    }

    fun toggleSelection(contact: Contact) {
        val current = _selectedIds.value.orEmpty().toMutableSet()
        if (current.contains(contact.id)) {
            current.remove(contact.id)
        } else {
            current.add(contact.id)
        }
        _selectedIds.value = current
    }

    fun selectAll() {
        val allIds = _contacts.value?.map { it.id }?.toSet() ?: emptySet()
        _selectedIds.value = allIds
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }
    
    fun onMessageTextChanged(text: String) {
        if (text.isEmpty()) {
            _segmentCount.value = context.getString(R.string.segments_count_zero)
            return
        }
        val result = SmsMessage.calculateLength(text, false)
        _segmentCount.value = context.getString(R.string.segments_count, result[0], result[2])
    }
    
    fun sendSms(manualNumber: String, message: String) {
        if (message.isBlank()) {
            _sendStatus.value = context.getString(R.string.message_empty)
            return
        }

        val recipients = ArrayList<String>()
        
        // Add manual number if valid
        if (NumberValidator.isValidPhoneNumber(manualNumber)) {
            recipients.add(NumberValidator.normalizeNumber(manualNumber))
        }
        
        // Add selected contacts
        val selected = _selectedIds.value.orEmpty()
        _contacts.value?.filter { selected.contains(it.id) }?.forEach { 
            recipients.add(it.normalizedNumber)
        }
        
        if (recipients.isEmpty()) {
            _sendStatus.value = context.getString(R.string.no_valid_recipients)
            return
        }
        
        // Calculate estimated SMS parts
        val result = SmsMessage.calculateLength(message, false)
        val estimatedParts = result[0]
        val totalParts = recipients.size * estimatedParts
        
        // If more than one recipient, show confirmation dialog
        if (recipients.size > 1) {
            pendingRecipients = recipients
            pendingMessage = message
            _bulkSendConfirmation.value = BulkSendConfirmation(
                recipientCount = recipients.size,
                estimatedParts = totalParts,
                message = message
            )
        } else {
            // Single recipient, send directly
            performSendSms(recipients, message)
        }
    }
    
    fun confirmBulkSend() {
        if (pendingRecipients.isNotEmpty() && pendingMessage.isNotBlank()) {
            performSendSms(pendingRecipients, pendingMessage)
            _bulkSendConfirmation.value = null
        }
    }
    
    fun cancelBulkSend() {
        _bulkSendConfirmation.value = null
        pendingRecipients = emptyList()
        pendingMessage = ""
    }
    
    private fun performSendSms(recipients: List<String>, message: String) {
        _isSending.value = true
        _sendStatus.value = context.getString(R.string.sending_to, recipients.size)
        
        viewModelScope.launch {
            var successCount = 0
            var failureCount = 0
            var lastError: String? = null
            
            recipients.forEach { number ->
                when (val result = smsRepository.sendSms(number, message)) {
                    is SmsResult.Success -> {
                        successCount++
                    }
                    is SmsResult.Error -> {
                        failureCount++
                        lastError = result.message
                    }
                }
            }
            
            _isSending.postValue(false)
            
            when {
                failureCount == 0 -> {
                    _sendStatus.postValue(context.getString(R.string.sent_to_recipients, successCount))
                }
                successCount == 0 -> {
                    _sendStatus.postValue(
                        context.getString(R.string.error_sending_sms, lastError ?: context.getString(R.string.error_unknown))
                    )
                }
                else -> {
                    val successMsg = context.getString(R.string.sent_to_recipients, successCount)
                    _sendStatus.postValue(
                        context.getString(R.string.send_partial_success, successMsg, failureCount)
                    )
                }
            }
            
            clearSelection()
        }
    }
}
