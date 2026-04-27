package com.afrouzi.longsmssender.ui

import android.app.Application
import android.content.Context
import android.telephony.SmsMessage
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.afrouzi.longsmssender.R
import com.afrouzi.longsmssender.data.model.Contact
import com.afrouzi.longsmssender.data.model.SmsResult
import com.afrouzi.longsmssender.data.repository.ContactRepository
import com.afrouzi.longsmssender.data.repository.SimOption
import com.afrouzi.longsmssender.data.repository.SmsRepository
import com.afrouzi.longsmssender.utils.LocaleManager
import com.afrouzi.longsmssender.utils.NumberValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

data class BulkSendConfirmation(
    val recipientCount: Int,
    val estimatedParts: Int,
    val message: String
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ContactRepository(application)
    private val smsRepository = SmsRepository(application)
    private val applicationContext: Application = application

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
    
    private val _sendResult = MutableLiveData<SendResult?>()
    val sendResult: LiveData<SendResult?> = _sendResult
    
    private val _sendProgress = MutableLiveData<SendProgress?>()
    val sendProgress: LiveData<SendProgress?> = _sendProgress
    
    private val _bulkSendConfirmation = MutableLiveData<BulkSendConfirmation?>()
    val bulkSendConfirmation: LiveData<BulkSendConfirmation?> = _bulkSendConfirmation
    
    private val _availableSims = MutableLiveData<List<SimOption>>(emptyList())
    val availableSims: LiveData<List<SimOption>> = _availableSims
    
    private val _selectedSim = MutableLiveData<SimOption?>(null)
    val selectedSim: LiveData<SimOption?> = _selectedSim
    
    private val _isQueuePaused = MutableLiveData(false)
    val isQueuePaused: LiveData<Boolean> = _isQueuePaused
    
    private var isQueueCancelled = false
    
    private var pendingRecipients: List<String> = emptyList()
    private var pendingMessage: String = ""
    
    // Debounce job for updateRecipientsCount
    private var updateRecipientsJob: Job? = null
    
    data class SendProgress(
        val currentRecipient: Int,
        val totalRecipients: Int,
        val segmentsPerMessage: Int
    )
    
    data class SendResult(
        val successCount: Int,
        val failureCount: Int,
        val totalRecipients: Int,
        val segmentsPerMessage: Int,
        val lastError: String?,
        val recipientResults: List<RecipientSendResult>
    )
    
    data class RecipientSendResult(
        val number: String,
        val success: Boolean,
        val attempts: Int,
        val error: String?
    )

    fun loadContacts() {
        _isLoading.value = true
        viewModelScope.launch {
            val list = repository.getContacts()
            _contacts.postValue(list)
            _isLoading.postValue(false)
        }
    }
    
    fun loadSimOptions(displayContext: Context) {
        _availableSims.value = smsRepository.getAvailableSimOptions(displayContext)
    }
    
    fun selectSim(option: SimOption?) {
        _selectedSim.value = option
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
    
    fun setSelectedIds(ids: Set<String>) {
        _selectedIds.value = ids
    }
    
    fun clearSendResult() {
        _sendResult.value = null
    }
    
    fun pauseQueue() {
        _isQueuePaused.value = true
    }
    
    fun resumeQueue() {
        _isQueuePaused.value = false
    }
    
    fun cancelQueue() {
        isQueueCancelled = true
        _isQueuePaused.value = false
    }
    
    // Helper function to get localized strings
    private fun getLocalizedString(resId: Int, vararg formatArgs: Any?): String {
        // Always get fresh language preference
        val currentLanguage = LocaleManager.getCurrentLanguage(getApplication())
        val localizedContext = LocaleManager.setLocale(getApplication(), currentLanguage)
        return if (formatArgs.isEmpty()) {
            localizedContext.getString(resId)
        } else {
            localizedContext.getString(resId, *formatArgs)
        }
    }
    
    fun onMessageTextChanged(text: String) {
        if (text.isEmpty()) {
            _segmentCount.value = getLocalizedString(R.string.segments_count_zero)
            return
        }
        val result = SmsMessage.calculateLength(text, false)
        _segmentCount.value = getLocalizedString(R.string.segments_count, result[0], result[2])
    }
    
    fun updateRecipientsCount(manualNumber: String) {
        // Cancel previous job if exists (debounce)
        updateRecipientsJob?.cancel()
        
        // Run in background to prevent UI freezing with debounce
        updateRecipientsJob = viewModelScope.launch {
            // Debounce: wait 300ms before processing to avoid multiple calls
            delay(300)
            
            // Use Dispatchers.Default for CPU-intensive work
            val recipients = withContext(Dispatchers.Default) {
                val recipientsSet = HashSet<String>() // Use HashSet for O(1) lookup
                
                // Parse manual numbers from multiline input
                // Filter out empty lines
                val lines = manualNumber.split("\n", "\r\n")
                lines.forEach { line ->
                    val trimmedLine = line.trim()
                    // Only process non-empty lines
                    if (trimmedLine.isNotEmpty() && NumberValidator.isValidPhoneNumber(trimmedLine)) {
                        val normalized = NumberValidator.normalizeNumber(trimmedLine)
                        if (normalized.isNotEmpty()) {
                            recipientsSet.add(normalized)
                        }
                    }
                }
                
                // Add selected contacts - optimized with HashSet lookup
                val selected = _selectedIds.value.orEmpty()
                val selectedSet = selected.toSet() // Convert to Set for O(1) lookup
                _contacts.value?.forEach { contact ->
                    if (selectedSet.contains(contact.id)) {
                        recipientsSet.add(contact.normalizedNumber)
                    }
                }
                
                recipientsSet.toList() // Convert back to list
            }
            
            // Update status based on recipient count (on main thread)
            when {
                recipients.isEmpty() -> {
                    _sendStatus.postValue(getLocalizedString(R.string.recipients_count_zero))
                }
                recipients.size == 1 -> {
                    _sendStatus.postValue(getLocalizedString(R.string.recipients_count_one))
                }
                else -> {
                    _sendStatus.postValue(getLocalizedString(R.string.recipients_count, recipients.size))
                }
            }
        }
    }
    
    fun sendSms(manualNumber: String, message: String) {
        if (message.isBlank()) {
            _sendStatus.value = getLocalizedString(R.string.message_empty)
            return
        }

        val recipients = ArrayList<String>()
        
        // Parse manual numbers from multiline input
        // Split by newlines and process each line
        val lines = manualNumber.split("\n", "\r\n")
        lines.forEach { line ->
            val trimmedLine = line.trim()
            if (trimmedLine.isNotEmpty() && NumberValidator.isValidPhoneNumber(trimmedLine)) {
                val normalized = NumberValidator.normalizeNumber(trimmedLine)
                if (normalized.isNotEmpty() && !recipients.contains(normalized)) {
                    recipients.add(normalized)
                }
            }
        }
        
        // Add selected contacts
        val selected = _selectedIds.value.orEmpty()
        _contacts.value?.filter { selected.contains(it.id) }?.forEach { 
            val normalized = it.normalizedNumber
            if (!recipients.contains(normalized)) {
                recipients.add(normalized)
            }
        }
        
        if (recipients.isEmpty()) {
            _sendStatus.value = getLocalizedString(R.string.no_valid_recipients)
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
        isQueueCancelled = false
        _isQueuePaused.value = false
        _sendStatus.value = getLocalizedString(R.string.sending_to, recipients.size)
        
        // Calculate segments per message
        val result = SmsMessage.calculateLength(message, false)
        val segmentsPerMessage = result[0]
        
        // Determine if we should show progress dialog (for time-consuming sends)
        val shouldShowProgress = recipients.size > 3 || segmentsPerMessage > 3 || (recipients.size * segmentsPerMessage) > 10
        
        viewModelScope.launch {
            var successCount = 0
            var failureCount = 0
            var lastError: String? = null
            var currentIndex = 0
            val recipientResults = mutableListOf<RecipientSendResult>()
            
            recipients.forEachIndexed { index, number ->
                if (isQueueCancelled) {
                    return@forEachIndexed
                }
                while (_isQueuePaused.value == true && !isQueueCancelled) {
                    delay(250L)
                }
                if (isQueueCancelled) {
                    return@forEachIndexed
                }
                currentIndex = index + 1
                
                // Update progress if needed
                if (shouldShowProgress) {
                    _sendProgress.postValue(SendProgress(currentIndex, recipients.size, segmentsPerMessage))
                }
                
                val (result, attempts) = sendWithRetry(number, message, _selectedSim.value?.subscriptionId)
                when (result) {
                    is SmsResult.Success -> {
                        successCount++
                        recipientResults.add(
                            RecipientSendResult(
                                number = number,
                                success = true,
                                attempts = attempts,
                                error = null
                            )
                        )
                    }
                    is SmsResult.Error -> {
                        failureCount++
                        lastError = result.message
                        recipientResults.add(
                            RecipientSendResult(
                                number = number,
                                success = false,
                                attempts = attempts,
                                error = result.message
                            )
                        )
                    }
                }
                
                // Small randomized delay improves carrier stability for bulk sends.
                if (index < recipients.lastIndex) {
                    delay(Random.nextLong(300L, 801L))
                }
            }
            
            // Clear progress
            _sendProgress.postValue(null)
            _isSending.postValue(false)
            
            // Set result for detailed dialog
            _sendResult.postValue(SendResult(
                successCount = successCount,
                failureCount = failureCount,
                totalRecipients = recipients.size,
                segmentsPerMessage = segmentsPerMessage,
                lastError = lastError,
                recipientResults = recipientResults
            ))
            
            // Keep old status for compatibility
            when {
                isQueueCancelled -> {
                    _sendStatus.postValue(getLocalizedString(R.string.queue_cancelled_status))
                }
                failureCount == 0 -> {
                    _sendStatus.postValue(getLocalizedString(R.string.sent_to_recipients, successCount))
                }
                successCount == 0 -> {
                    _sendStatus.postValue(
                        getLocalizedString(R.string.error_sending_sms, lastError ?: getLocalizedString(R.string.error_unknown))
                    )
                }
                else -> {
                    val successMsg = getLocalizedString(R.string.sent_to_recipients, successCount)
                    _sendStatus.postValue(
                        getLocalizedString(R.string.send_partial_success, successMsg, failureCount)
                    )
                }
            }
            
            clearSelection()
            _isQueuePaused.postValue(false)
            isQueueCancelled = false
        }
    }
    
    private suspend fun sendWithRetry(
        number: String,
        message: String,
        subscriptionId: Int?
    ): Pair<SmsResult, Int> {
        val maxAttempts = 2
        var lastResult: SmsResult = SmsResult.Error("Unknown error")
        var attempts = 0
        repeat(maxAttempts) { attempt ->
            attempts = attempt + 1
            lastResult = smsRepository.sendSms(number, message, subscriptionId)
            if (lastResult is SmsResult.Success) {
                return lastResult to attempts
            }
            if (attempt < maxAttempts - 1) {
                delay(600L)
            }
        }
        return lastResult to attempts
    }
}
