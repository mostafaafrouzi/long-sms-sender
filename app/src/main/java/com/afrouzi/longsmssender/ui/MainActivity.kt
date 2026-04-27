package com.afrouzi.longsmssender.ui

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.telephony.SmsMessage
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.content.res.Configuration
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.afrouzi.longsmssender.R
import com.afrouzi.longsmssender.data.model.Contact
import com.afrouzi.longsmssender.data.repository.SimOption
import com.afrouzi.longsmssender.utils.AppVersionTracker
import com.afrouzi.longsmssender.utils.LocaleManager
import com.afrouzi.longsmssender.utils.NumberValidator
import com.afrouzi.longsmssender.utils.PermissionManager
import com.afrouzi.longsmssender.utils.PreparedMessage
import com.afrouzi.longsmssender.utils.PreparedMessageStore
import com.afrouzi.longsmssender.utils.RecentContactsStore
import com.afrouzi.longsmssender.utils.RecipientGroup
import com.afrouzi.longsmssender.utils.RecipientGroupStore
import com.afrouzi.longsmssender.utils.ScheduledMessageStore
import com.afrouzi.longsmssender.utils.ScheduledSmsJob
import com.afrouzi.longsmssender.utils.ScheduledSmsJobStore
import com.afrouzi.longsmssender.utils.ScheduledSmsReceiver
import com.afrouzi.longsmssender.utils.ScheduledSmsWorker
import com.afrouzi.longsmssender.utils.SmsBroadcastReceiver
import com.afrouzi.longsmssender.utils.ThemeManager
import com.google.android.material.snackbar.Snackbar
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    companion object {
        private const val REQUEST_PHONE_STATE_FOR_SIM = 2001
    }
    
    private data class PendingSchedulePayload(
        val recipientsRaw: String,
        val message: String
    )
    
    private data class PendingScheduleDraft(
        val dueAtMillis: Long,
        val recipientsRaw: String,
        val message: String,
        val label: String
    )

    private val viewModel: MainViewModel by viewModels()
    private val smsReceiver = SmsBroadcastReceiver()
    private val groupStore by lazy { RecipientGroupStore(this) }
    private val recentContactsStore by lazy { RecentContactsStore(this) }
    private val scheduledJobStore by lazy { ScheduledSmsJobStore(this) }
    private val preparedMessageStore by lazy { PreparedMessageStore(this) }
    private lateinit var contactAdapter: ContactAdapter
    private var contactsDialog: AlertDialog? = null
    private var sendStatusDialog: AlertDialog? = null
    private var sendProgressDialog: AlertDialog? = null
    private var countdownTimer: android.os.CountDownTimer? = null
    private var filteredContacts: List<Contact> = emptyList()
    private var selectedContacts: MutableSet<String> = mutableSetOf()
    private var isSmsReceiverRegistered = false
    private var currentSimOption: SimOption? = null
    private var pendingSchedulePayload: PendingSchedulePayload? = null
    private var pendingScheduleDraft: PendingScheduleDraft? = null
    private var reopenScheduleAfterBatteryRequest = false
    private var scheduledTimeLabel: String? = null
    
    private val batteryOptimizationLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (reopenScheduleAfterBatteryRequest) {
            reopenScheduleAfterBatteryRequest = false
            if (isIgnoringBatteryOptimizations()) {
                pendingSchedulePayload?.let { payload ->
                    showScheduleDialog(payload.recipientsRaw, payload.message)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadSimOptions(this)
        refreshLocalizedUiStrings()
        refreshScheduledJobsUi()
    }

    override fun attachBaseContext(newBase: Context) {
        val language = LocaleManager.getCurrentLanguage(newBase)
        val context = LocaleManager.setLocale(newBase, language)
        super.attachBaseContext(context)
    }
    
    /** Refresh strings that live in ViewModel LiveData after language change (activity is recreated). */
    private fun refreshLocalizedUiStrings() {
        val edtMessage = findViewById<EditText>(R.id.edtMessage)
        val edtPhone = findViewById<EditText>(R.id.edtPhone)
        viewModel.onMessageTextChanged(edtMessage.text.toString())
        viewModel.updateRecipientsCount(edtPhone.text.toString())
    }

    /** Dialogs pick up theme + locale correctly (Activity resources alone can lag behind in-app locale). */
    private fun schedulePickerContext(): Context {
        val lang = LocaleManager.getCurrentLanguage(this)
        val locale = Locale.forLanguageTag(if (lang == "fa") "fa" else "en")
        val config = Configuration(resources.configuration)
        config.setLocale(locale)
        val localized = createConfigurationContext(config)
        return ContextThemeWrapper(localized, R.style.Theme_LongSmsSender)
    }

    private fun applyLayoutDirection() {
        window.decorView.layoutDirection = if (LocaleManager.getCurrentLanguage(this) == "fa") {
            View.LAYOUT_DIRECTION_RTL
        } else {
            View.LAYOUT_DIRECTION_LTR
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Apply layout direction based on current language AFTER setContentView
        applyLayoutDirection()

        // Register Receiver
        val filter = IntentFilter().apply {
            addAction("SMS_SENT")
            addAction("SMS_DELIVERED")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                val registerMethod = Context::class.java.getMethod(
                    "registerReceiver",
                    android.content.BroadcastReceiver::class.java,
                    IntentFilter::class.java,
                    Int::class.javaPrimitiveType
                )
                registerMethod.invoke(this, smsReceiver, filter, 0x00000002)
                isSmsReceiverRegistered = true
            } catch (e: Exception) {
                @Suppress("DEPRECATION")
                this.registerReceiver(smsReceiver, filter)
                isSmsReceiverRegistered = true
            }
        } else {
            @Suppress("DEPRECATION")
            this.registerReceiver(smsReceiver, filter)
            isSmsReceiverRegistered = true
        }

        setupUI()
        observeViewModel()
        viewModel.loadSimOptions(this)
        refreshLocalizedUiStrings()
        updateThemeIcon()
        refreshScheduledJobsUi()
        handleIncomingIntent(intent)

        if (!PermissionManager.hasPermissions(this)) {
            PermissionManager.requestPermissions(this)
        }
    }

    private fun setupUI() {
        updateLanguageButton()
        findViewById<View>(R.id.btnLanguage)?.setOnClickListener {
            showLanguageDialog()
        }

        findViewById<View>(R.id.btnTheme)?.setOnClickListener {
            toggleTheme()
        }
        
        findViewById<View>(R.id.btnAbout)?.setOnClickListener {
            showAboutDialog()
        }
        
        findViewById<View>(R.id.btnShare)?.setOnClickListener {
            shareApp()
        }

        findViewById<View>(R.id.btnContacts)?.setOnClickListener {
            showContactsDialog()
        }
        findViewById<Button>(R.id.btnSim)?.setOnClickListener { showSimPickerDialog() }
        findViewById<Button>(R.id.btnGroups)?.setOnClickListener { showGroupsDialog() }
        findViewById<Button>(R.id.btnSchedule)?.setOnClickListener { showScheduleDialog() }
        findViewById<ImageButton>(R.id.btnScheduledJobs)?.setOnClickListener { showScheduledJobsDialog() }
        findViewById<ImageButton>(R.id.btnPreparedMessages)?.setOnClickListener {
            showPreparedMessagesRootDialog()
        }

        val edtPhone = findViewById<EditText>(R.id.edtPhone)
        val edtMessage = findViewById<EditText>(R.id.edtMessage)
        val btnSend = findViewById<Button>(R.id.btnSend)
        val btnPaste = findViewById<ImageButton>(R.id.btnPaste)

        btnSend.setOnClickListener {
            val draft = pendingScheduleDraft
            if (draft != null) {
                showScheduledSendConfirmationDialog(draft)
                return@setOnClickListener
            }
            val phone = edtPhone.text.toString()
            val message = edtMessage.text.toString()
            viewModel.sendSms(phone, message)
        }

        btnPaste.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            if (clipboard.hasPrimaryClip()) {
                val item = clipboard.primaryClip?.getItemAt(0)
                val text = item?.text
                if (!text.isNullOrEmpty()) {
                    val start = Math.max(edtMessage.selectionStart, 0)
                    val end = Math.max(edtMessage.selectionEnd, 0)
                    edtMessage.text.replace(Math.min(start, end), Math.max(start, end), text)
                }
            }
        }

        edtMessage.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!s.isNullOrBlank()) {
                    clearScheduledUiState()
                }
                viewModel.onMessageTextChanged(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        
        edtPhone.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val isSending = viewModel.isSending.value ?: false
                if (!isSending) {
                    val newText = s.toString()
                    if (newText.isNotBlank()) {
                        clearScheduledUiState()
                    }
                    val currentSelected = viewModel.selectedIds.value.orEmpty()
                    if (currentSelected.isNotEmpty() && newText.isNotEmpty()) {
                        val selectedNumbers = viewModel.contacts.value
                            ?.filter { currentSelected.contains(it.id) }
                            ?.map { it.phoneNumber }
                            ?.joinToString("\n") ?: ""
                        if (newText.trim() != selectedNumbers.trim()) {
                            viewModel.clearSelection()
                        }
                    } else if (newText.isEmpty() && currentSelected.isNotEmpty()) {
                        viewModel.clearSelection()
                    }
                    viewModel.updateRecipientsCount(newText)
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        
        viewModel.selectedIds.observe(this) {
            val isSending = viewModel.isSending.value ?: false
            if (!isSending) {
                val phone = edtPhone.text.toString()
                viewModel.updateRecipientsCount(phone)
            }
        }
    }

    private fun observeViewModel() {
        val txtStatus = findViewById<TextView>(R.id.txtStatus)
        val txtSegment = findViewById<TextView>(R.id.txtSegment)
        val progressSending = findViewById<ProgressBar>(R.id.progressSending)
        val btnSend = findViewById<Button>(R.id.btnSend)

        viewModel.bulkSendConfirmation.observe(this) { confirmation ->
            confirmation?.let {
                showBulkSendConfirmationDialog(it)
            }
        }

        viewModel.segmentCount.observe(this) { count ->
            txtSegment.text = count
        }
        
        viewModel.isSending.observe(this) { isSending ->
            progressSending.visibility = if (isSending) View.VISIBLE else View.GONE
            btnSend.isEnabled = !isSending
            if (isSending) {
                txtStatus.text = getString(R.string.sending_sms)
            }
        }
        
        viewModel.sendProgress.observe(this) { progress ->
            progress?.let {
                showSendProgressDialog(it)
            } ?: run {
                sendProgressDialog?.dismiss()
                sendProgressDialog = null
            }
        }
        
        viewModel.sendResult.observe(this) { result ->
            result?.let {
                showSendStatusDialog(it)
            }
        }
        
        viewModel.sendStatus.observe(this) { status ->
            txtStatus.text = status
        }
        
        viewModel.isQueuePaused.observe(this) { paused ->
            sendProgressDialog?.findViewById<Button>(R.id.btnPauseResumeQueue)?.text =
                getString(if (paused) R.string.resume_queue else R.string.pause_queue)
            if (paused) {
                txtStatus.text = getString(R.string.queue_paused_status)
            }
        }
        
        viewModel.availableSims.observe(this) { options ->
            if (currentSimOption == null && options.isNotEmpty()) {
                currentSimOption = options.first()
                viewModel.selectSim(currentSimOption)
                updateSimButtonText()
            } else {
                val stillValid = options.firstOrNull {
                    it.subscriptionId == currentSimOption?.subscriptionId
                }
                currentSimOption = stillValid ?: options.firstOrNull()
                viewModel.selectSim(currentSimOption)
                updateSimButtonText()
            }
        }
    }

    private fun showSendProgressDialog(progress: MainViewModel.SendProgress) {
        if (sendProgressDialog == null) {
            val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_send_progress, null)
            val txtTitle = dialogView.findViewById<TextView>(R.id.txtProgressTitle)
            val progressBar = dialogView.findViewById<ProgressBar>(R.id.progressBar)
            val txtDetails = dialogView.findViewById<TextView>(R.id.txtProgressDetails)
            val btnPauseResume = dialogView.findViewById<Button>(R.id.btnPauseResumeQueue)
            val btnCancelQueue = dialogView.findViewById<Button>(R.id.btnCancelQueue)
            
            txtTitle.text = getString(R.string.sending_progress_title)
            progressBar.max = progress.totalRecipients
            txtDetails.text = getString(R.string.sending_progress_details, progress.segmentsPerMessage)
            btnPauseResume.text = getString(R.string.pause_queue)
            
            btnPauseResume.setOnClickListener {
                val isPaused = viewModel.isQueuePaused.value == true
                if (isPaused) {
                    viewModel.resumeQueue()
                } else {
                    viewModel.pauseQueue()
                }
            }
            btnCancelQueue.setOnClickListener {
                viewModel.cancelQueue()
                showSnackbar(getString(R.string.queue_cancelled_status), Snackbar.LENGTH_LONG)
            }
            
            sendProgressDialog = AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(false)
                .create()
            
            sendProgressDialog?.show()
        }
        
        val progressBar = sendProgressDialog?.findViewById<ProgressBar>(R.id.progressBar)
        val txtStatus = sendProgressDialog?.findViewById<TextView>(R.id.txtProgressStatus)
        
        progressBar?.progress = progress.currentRecipient
        txtStatus?.text = getString(R.string.sending_progress_status, progress.currentRecipient, progress.totalRecipients)
    }
    
    private fun showSendStatusDialog(result: MainViewModel.SendResult) {
        sendStatusDialog?.dismiss()
        countdownTimer?.cancel()
        
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_send_status, null)
        val txtStatusMessage = dialogView.findViewById<TextView>(R.id.txtStatusMessage)
        val btnOk = dialogView.findViewById<Button>(R.id.btnOk)
        val btnShareReport = dialogView.findViewById<Button>(R.id.btnShareReport)
        
        val message = when {
            result.failureCount == 0 -> {
                getString(R.string.send_status_success, result.segmentsPerMessage, result.successCount)
            }
            result.successCount == 0 -> {
                getString(R.string.send_status_error, result.lastError ?: getString(R.string.error_unknown))
            }
            else -> {
                getString(R.string.send_status_partial, result.segmentsPerMessage, result.successCount, result.failureCount)
            }
        }
        
        txtStatusMessage.text = message
        
        var countdown = 7
        btnOk.text = getString(R.string.ok_with_countdown, countdown)
        
        countdownTimer = object : android.os.CountDownTimer(7000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                countdown = (millisUntilFinished / 1000).toInt()
                btnOk.text = getString(R.string.ok_with_countdown, countdown)
            }
            
            override fun onFinish() {
                sendStatusDialog?.dismiss()
            }
        }
        
        sendStatusDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()
        
        btnOk.setOnClickListener {
            countdownTimer?.cancel()
            sendStatusDialog?.dismiss()
        }
        btnShareReport.setOnClickListener {
            shareSendReport(result)
        }
        
        sendStatusDialog?.setOnDismissListener {
            countdownTimer?.cancel()
            viewModel.clearSendResult()
        }
        
        sendStatusDialog?.show()
        countdownTimer?.start()
    }
    
    private fun shareSendReport(result: MainViewModel.SendResult) {
        val reportText = buildString {
            appendLine(getString(R.string.app_name))
            appendLine("------")
            appendLine("time: ${SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.US).format(Date())}")
            appendLine("totalRecipients: ${result.totalRecipients}")
            appendLine("success: ${result.successCount}")
            appendLine("failed: ${result.failureCount}")
            appendLine("segmentsPerMessage: ${result.segmentsPerMessage}")
            if (!result.lastError.isNullOrBlank()) {
                appendLine("lastError: ${result.lastError}")
            }
            if (!scheduledTimeLabel.isNullOrBlank()) {
                appendLine(getString(R.string.report_scheduled_time, scheduledTimeLabel ?: ""))
            } else {
                appendLine(getString(R.string.report_not_scheduled))
            }
            appendLine("------")
            appendLine("recipientDetails:")
            result.recipientResults.forEach { recipient ->
                val status = if (recipient.success) "SUCCESS" else "FAILED"
                append("- ${recipient.number} | $status | attempts=${recipient.attempts}")
                if (!recipient.error.isNullOrBlank()) {
                    append(" | error=${recipient.error}")
                }
                appendLine()
            }
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, reportText)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share_report)))
    }
    
    private fun showSnackbar(message: String, duration: Int) {
        val rootView = findViewById<View>(android.R.id.content)
        Snackbar.make(rootView, message, duration).show()
    }

    private fun showContactsDialog() {
        if (!PermissionManager.hasPermissions(this)) {
            if (PermissionManager.shouldShowRationale(this)) {
                showRationaleDialog()
            } else {
                PermissionManager.requestPermissions(this)
            }
            return
        }

        if (viewModel.contacts.value.isNullOrEmpty()) {
            viewModel.loadContacts()
        }

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_contacts, null)
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.recyclerContactsDialog)
        val progressBar = dialogView.findViewById<ProgressBar>(R.id.progressLoadingDialog)
        val emptyView = dialogView.findViewById<View>(R.id.emptyNoContactsDialog)
        val emptySearchView = dialogView.findViewById<View>(R.id.emptyNoSearchResults)
        val edtSearch = dialogView.findViewById<EditText>(R.id.edtSearch)
        val btnSelectAll = dialogView.findViewById<Button>(R.id.btnSelectAll)
        val btnClearSelection = dialogView.findViewById<Button>(R.id.btnClearSelection)
        val edtPhone = findViewById<EditText>(R.id.edtPhone)

        selectedContacts.clear()
        selectedContacts.addAll(viewModel.selectedIds.value.orEmpty())
        
        val txtStatus = dialogView.findViewById<TextView>(R.id.txtSelectionStatusDialog)
        txtStatus?.text = getString(R.string.selected_count, selectedContacts.size)

        contactAdapter = ContactAdapter(
            onItemClick = { contact ->
                if (selectedContacts.contains(contact.id)) {
                    selectedContacts.remove(contact.id)
                } else {
                    selectedContacts.add(contact.id)
                }
                contactAdapter.updateSelection(selectedContacts)
                // Update status text
                txtStatus?.text = getString(R.string.selected_count, selectedContacts.size)
            }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = contactAdapter
        
        val fastScroller = FastScroller(recyclerView, this)
        recyclerView.addItemDecoration(fastScroller)

        edtSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString().lowercase().trim()
                filteredContacts = if (query.isEmpty()) {
                    sortContactsByRecent(viewModel.contacts.value ?: emptyList())
                } else {
                    (viewModel.contacts.value ?: emptyList()).filter { contact ->
                        contact.name.lowercase().contains(query) || 
                        contact.phoneNumber.contains(query)
                    }
                }
                if (query.isEmpty()) {
                    contactAdapter.updateAlphabetIndexer(filteredContacts)
                }
                contactAdapter.submitList(filteredContacts)
                if (filteredContacts.isEmpty() && query.isNotEmpty()) {
                    recyclerView.visibility = View.GONE
                    emptyView.visibility = View.GONE
                    emptySearchView.visibility = View.VISIBLE
                } else if (filteredContacts.isEmpty()) {
                    recyclerView.visibility = View.GONE
                    emptyView.visibility = View.VISIBLE
                    emptySearchView.visibility = View.GONE
                } else {
                    recyclerView.visibility = View.VISIBLE
                    emptyView.visibility = View.GONE
                    emptySearchView.visibility = View.GONE
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnSelectAll.setOnClickListener {
            selectedContacts.clear()
            filteredContacts.forEach { contact ->
                selectedContacts.add(contact.id)
            }
            contactAdapter.updateSelection(selectedContacts)
            txtStatus?.text = getString(R.string.selected_count, selectedContacts.size)
        }

        btnClearSelection.setOnClickListener {
            selectedContacts.clear()
            contactAdapter.updateSelection(selectedContacts)
            txtStatus?.text = getString(R.string.selected_count, 0)
        }

        val loadingObserver = Observer<Boolean> { isLoading ->
            progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            recyclerView.visibility = if (isLoading) View.GONE else View.VISIBLE
            emptyView.visibility = View.GONE
            emptySearchView.visibility = View.GONE
        }

        val contactsObserver = Observer<List<Contact>> { contacts ->
            val query = edtSearch.text.toString().lowercase().trim()
            filteredContacts = if (query.isEmpty()) {
                sortContactsByRecent(contacts)
            } else {
                contacts.filter { contact ->
                    contact.name.lowercase().contains(query) || 
                    contact.phoneNumber.contains(query)
                }
            }
            if (query.isEmpty()) {
                contactAdapter.updateAlphabetIndexer(filteredContacts)
            }
            contactAdapter.submitList(filteredContacts)
            contactAdapter.updateSelection(selectedContacts)
            if (filteredContacts.isEmpty() && query.isEmpty() && viewModel.isLoading.value != true) {
                recyclerView.visibility = View.GONE
                progressBar.visibility = View.GONE
                emptyView.visibility = View.VISIBLE
                emptySearchView.visibility = View.GONE
            } else if (filteredContacts.isEmpty() && query.isNotEmpty()) {
                recyclerView.visibility = View.GONE
                progressBar.visibility = View.GONE
                emptyView.visibility = View.GONE
                emptySearchView.visibility = View.VISIBLE
            } else {
                recyclerView.visibility = View.VISIBLE
                progressBar.visibility = View.GONE
                emptyView.visibility = View.GONE
                emptySearchView.visibility = View.GONE
            }
        }
        viewModel.isLoading.observe(this, loadingObserver)
        viewModel.contacts.observe(this, contactsObserver)

        contactsDialog = AlertDialog.Builder(this)
            .setTitle(R.string.select_contact)
            .setView(dialogView)
            .setPositiveButton(R.string.select) { _, _ ->
                val allContacts = viewModel.contacts.value.orEmpty()
                val selectedNumbers = allContacts
                    .filter { selectedContacts.contains(it.id) }
                    .map { it.phoneNumber }
                    .joinToString("\n")
                
                viewModel.setSelectedIds(selectedContacts)
                recentContactsStore.saveRecentSelection(selectedContacts)
                
                if (selectedNumbers.isNotEmpty()) {
                    edtPhone.setText(selectedNumbers)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .create()

        contactsDialog?.setOnDismissListener {
            viewModel.isLoading.removeObserver(loadingObserver)
            viewModel.contacts.removeObserver(contactsObserver)
        }

        contactsDialog?.show()
    }

    private fun toggleTheme() {
        val currentMode = ThemeManager.getThemeMode(this)
        val newMode = when (currentMode) {
            ThemeManager.THEME_LIGHT -> ThemeManager.THEME_DARK
            ThemeManager.THEME_DARK -> ThemeManager.THEME_SYSTEM
            else -> ThemeManager.THEME_LIGHT
        }
        ThemeManager.setThemeMode(this, newMode)
        
        val language = LocaleManager.getCurrentLanguage(this)
        val themeMessage = when (newMode) {
            ThemeManager.THEME_LIGHT -> {
                if (language == "fa") "حالت روز فعال شد" else "Light mode enabled"
            }
            ThemeManager.THEME_DARK -> {
                if (language == "fa") "حالت شب فعال شد" else "Dark mode enabled"
            }
            else -> {
                if (language == "fa") "حالت پیروی از سیستم فعال شد" else "System default mode enabled"
            }
        }
        android.widget.Toast.makeText(this, themeMessage, android.widget.Toast.LENGTH_SHORT).show()
        
        updateThemeIcon()
        recreate()
    }
    
    private fun updateLanguageButton() {
        val btnLanguage = findViewById<Button>(R.id.btnLanguage)
        val currentLanguage = LocaleManager.getCurrentLanguage(this)
        btnLanguage.text = if (currentLanguage == "fa") "EN" else "فا"
    }

    private fun updateThemeIcon() {
        val isDark = ThemeManager.isDarkMode(this)
        when (val themeView = findViewById<View>(R.id.btnTheme)) {
            is ImageButton -> {
                themeView.setImageResource(
                    if (isDark) R.drawable.ic_light_mode else R.drawable.ic_dark_mode
                )
            }
            is Button -> {
                themeView.text = getString(R.string.theme)
            }
        }
    }

    private fun showRationaleDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.permission_rationale_title)
            .setMessage(R.string.permission_rationale_message)
            .setPositiveButton(R.string.grant) { _, _ ->
                PermissionManager.requestPermissions(this)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    private fun showBulkSendConfirmationDialog(confirmation: BulkSendConfirmation) {
        AlertDialog.Builder(this)
            .setTitle(R.string.bulk_send_title)
            .setMessage(getString(R.string.bulk_send_message, confirmation.recipientCount, confirmation.estimatedParts))
            .setPositiveButton(R.string.bulk_send_confirm) { _, _ ->
                viewModel.confirmBulkSend()
            }
            .setNegativeButton(R.string.bulk_send_cancel) { _, _ ->
                viewModel.cancelBulkSend()
            }
            .setCancelable(false)
            .show()
    }
    
    private fun showLanguageDialog() {
        val currentLanguage = LocaleManager.getCurrentLanguage(this)
        val languages = arrayOf(
            getString(R.string.language_english),
            getString(R.string.language_persian)
        )
        val currentIndex = if (currentLanguage == "fa") 1 else 0
        
        AlertDialog.Builder(this)
            .setTitle(R.string.change_language)
            .setSingleChoiceItems(languages, currentIndex) { dialog, which ->
                val newLanguage = if (which == 1) "fa" else "en"
                if (newLanguage != currentLanguage) {
                    LocaleManager.setLocale(this, newLanguage)
                    updateLanguageButton()
                    dialog.dismiss()
                    window.decorView.post {
                        recreate()
                    }
                } else {
                    dialog.dismiss()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    private fun showAboutDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_about, null)
        val txtAppName = dialogView.findViewById<TextView>(R.id.txtAppName)
        val txtVersion = dialogView.findViewById<TextView>(R.id.txtVersion)
        val txtAboutTitle = dialogView.findViewById<TextView>(R.id.txtAboutTitle)
        val txtAboutDescription = dialogView.findViewById<TextView>(R.id.txtAboutDescription)
        val txtDeveloper = dialogView.findViewById<TextView>(R.id.txtDeveloper)
        val txtDeveloperName = dialogView.findViewById<TextView>(R.id.txtDeveloperName)
        val txtDeveloperInfo = dialogView.findViewById<TextView>(R.id.txtDeveloperInfo)
        val txtDeveloperDescription = dialogView.findViewById<TextView>(R.id.txtDeveloperDescription)
        val txtOpenSource = dialogView.findViewById<TextView>(R.id.txtOpenSource)
        val btnWebsite = dialogView.findViewById<Button>(R.id.btnWebsite)
        val btnGitHub = dialogView.findViewById<Button>(R.id.btnGitHub)
        val btnLinkedIn = dialogView.findViewById<Button>(R.id.btnLinkedIn)
        
        val language = LocaleManager.getCurrentLanguage(this)
        val isRtl = language == "fa"
        
        txtAppName.text = getString(R.string.app_name)
        
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            val versionName = packageInfo.versionName
            txtVersion.text = "${getString(R.string.app_version)}: $versionName"
        } catch (e: PackageManager.NameNotFoundException) {
            txtVersion.text = getString(R.string.app_version)
        }
        
        txtAboutTitle.text = getString(R.string.about_title)
        txtAboutDescription.text = getString(R.string.about_description)
        txtDeveloper.text = getString(R.string.developer)
        txtDeveloperName.text = getString(R.string.developer_name)
        txtDeveloperInfo.text = getString(R.string.developer_info)
        txtDeveloperDescription.text = getString(R.string.developer_description)
        txtOpenSource.text = getString(R.string.about_open_source)
        
        if (isRtl) {
            txtDeveloper.gravity = android.view.Gravity.CENTER
            txtDeveloperName.gravity = android.view.Gravity.CENTER
            txtDeveloperInfo.gravity = android.view.Gravity.CENTER
            txtDeveloperDescription.gravity = android.view.Gravity.CENTER
        }
        
        btnWebsite.text = "afrouzi.ir"
        btnGitHub.text = "github.com/mostafaafrouzi"
        btnLinkedIn.text = "linkedin.com/in/mostafaafrouzi"
        
        val websiteDrawable = ContextCompat.getDrawable(this, R.drawable.ic_website)?.mutate()
        val githubDrawable = ContextCompat.getDrawable(this, R.drawable.ic_github)?.mutate()
        val linkedinDrawable = ContextCompat.getDrawable(this, R.drawable.ic_linkedin)?.mutate()
        
        val iconTintColor = if (ThemeManager.isDarkMode(this)) {
            ContextCompat.getColor(this, android.R.color.white)
        } else {
            ContextCompat.getColor(this, android.R.color.black)
        }
        
        websiteDrawable?.setTint(iconTintColor)
        githubDrawable?.setTint(iconTintColor)
        linkedinDrawable?.setTint(iconTintColor)
        
        if (isRtl) {
            btnWebsite.setCompoundDrawablesWithIntrinsicBounds(null, null, websiteDrawable, null)
            btnGitHub.setCompoundDrawablesWithIntrinsicBounds(null, null, githubDrawable, null)
            btnLinkedIn.setCompoundDrawablesWithIntrinsicBounds(null, null, linkedinDrawable, null)
        } else {
            btnWebsite.setCompoundDrawablesWithIntrinsicBounds(websiteDrawable, null, null, null)
            btnGitHub.setCompoundDrawablesWithIntrinsicBounds(githubDrawable, null, null, null)
            btnLinkedIn.setCompoundDrawablesWithIntrinsicBounds(linkedinDrawable, null, null, null)
        }
        
        val websiteUrl = if (language == "fa") {
            "https://afrouzi.ir/?utm_source=com.afrouzi.longsmssender&utm_medium=application&utm_campaign=portfolio"
        } else {
            "https://afrouzi.ir/en/?utm_source=com.afrouzi.longsmssender&utm_medium=application&utm_campaign=portfolio"
        }
        
        btnWebsite.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(websiteUrl))
            startActivity(intent)
        }
        
        btnGitHub.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/mostafaafrouzi"))
            startActivity(intent)
        }
        
        btnLinkedIn.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://linkedin.com/in/mostafaafrouzi"))
            startActivity(intent)
        }
        
        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton(R.string.close, null)
            .show()
    }
    
    private fun shareApp() {
        val language = LocaleManager.getCurrentLanguage(this)
        val cafeBazaarUrl = "https://cafebazaar.ir/app/com.afrouzi.longsmssender"
        val githubUrl = if (language == "fa") {
            "https://github.com/mostafaafrouzi/long-sms-sender/blob/main/README.fa.md"
        } else {
            "https://github.com/mostafaafrouzi/long-sms-sender"
        }
        
        val shareText = getString(R.string.share_app_text, cafeBazaarUrl, githubUrl)
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, shareText)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, getString(R.string.share_app))
        startActivity(shareIntent)
    }
    
    private fun showSimPickerDialog() {
        if (!hasPhoneStatePermission()) {
            showSimPermissionDialog()
            return
        }
        viewModel.loadSimOptions(this)
        val options = viewModel.availableSims.value.orEmpty()
        if (options.isEmpty()) return
        val labels = options.map { it.displayName }.toTypedArray()
        val selectedIndex = options.indexOfFirst {
            it.subscriptionId == currentSimOption?.subscriptionId
        }.coerceAtLeast(0)
        
        AlertDialog.Builder(this)
            .setTitle(R.string.select_sim)
            .setSingleChoiceItems(labels, selectedIndex) { dialog, which ->
                currentSimOption = options[which]
                viewModel.selectSim(currentSimOption)
                updateSimButtonText()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    private fun updateSimButtonText() {
        findViewById<Button>(R.id.btnSim)?.text = currentSimOption?.displayName ?: getString(R.string.sim_default)
    }
    
    private fun showGroupsDialog() {
        val actions = arrayOf(
            getString(R.string.save_group),
            getString(R.string.load_group),
            getString(R.string.delete_group)
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.groups)
            .setItems(actions) { _, which ->
                when (which) {
                    0 -> promptSaveGroup()
                    1 -> promptLoadGroup()
                    2 -> promptDeleteGroup()
                }
            }
            .show()
    }
    
    private fun promptSaveGroup() {
        val edtPhone = findViewById<EditText>(R.id.edtPhone)
        val recipients = edtPhone.text.toString()
            .split("\n", "\r\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        if (recipients.isEmpty()) {
            showSnackbar(getString(R.string.no_valid_recipients), Snackbar.LENGTH_LONG)
            return
        }
        
        val input = EditText(this).apply {
            hint = getString(R.string.group_name_hint)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.save_group)
            .setView(input)
            .setPositiveButton(R.string.save_group) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    groupStore.saveGroup(RecipientGroup(name, recipients))
                    showSnackbar(getString(R.string.group_saved), Snackbar.LENGTH_SHORT)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    private fun promptLoadGroup() {
        val groups = groupStore.loadGroups()
        if (groups.isEmpty()) {
            showSnackbar(getString(R.string.group_not_found), Snackbar.LENGTH_LONG)
            return
        }
        val names = groups.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.load_group)
            .setItems(names) { _, which ->
                val target = groups[which]
                findViewById<EditText>(R.id.edtPhone).setText(target.recipients.joinToString("\n"))
                viewModel.clearSelection()
                viewModel.updateRecipientsCount(target.recipients.joinToString("\n"))
            }
            .show()
    }
    
    private fun promptDeleteGroup() {
        val groups = groupStore.loadGroups()
        if (groups.isEmpty()) {
            showSnackbar(getString(R.string.group_not_found), Snackbar.LENGTH_LONG)
            return
        }
        val names = groups.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_group)
            .setItems(names) { _, which ->
                groupStore.deleteGroup(groups[which].name)
                showSnackbar(getString(R.string.group_deleted), Snackbar.LENGTH_SHORT)
            }
            .show()
    }
    
    private fun showScheduleDialog() {
        val merged = buildRecipientsMultilineForSchedule()
        val message = findViewById<EditText>(R.id.edtMessage).text.toString().trim()
        val noRecipients = merged == null
        val noMessage = message.isBlank()
        val scheduleErrorRes = when {
            noRecipients && noMessage -> R.string.schedule_error_recipients_and_message
            noRecipients -> R.string.schedule_error_no_recipients
            noMessage -> R.string.schedule_error_no_message
            else -> null
        }
        if (scheduleErrorRes != null) {
            showSnackbar(getString(scheduleErrorRes), Snackbar.LENGTH_LONG)
            return
        }
        showScheduleDialog(merged!!, message)
    }
    
    private fun showScheduleDialog(recipientsRaw: String, message: String) {
        if (recipientsRaw.isBlank() || message.isBlank()) {
            showSnackbar(getString(R.string.no_valid_recipients), Snackbar.LENGTH_LONG)
            return
        }
        pendingSchedulePayload = PendingSchedulePayload(recipientsRaw, message)
        if (!isIgnoringBatteryOptimizations()) {
            showBatteryOptimizationPromptForScheduling()
            return
        }
        
        val calendar = Calendar.getInstance()
        val pickerCtx = schedulePickerContext()
        android.app.DatePickerDialog(
            pickerCtx,
            { _, year, month, day ->
                calendar.set(Calendar.YEAR, year)
                calendar.set(Calendar.MONTH, month)
                calendar.set(Calendar.DAY_OF_MONTH, day)
                TimePickerDialog(
                    pickerCtx,
                    { _, hour, minute ->
                        calendar.set(Calendar.HOUR_OF_DAY, hour)
                        calendar.set(Calendar.MINUTE, minute)
                        calendar.set(Calendar.SECOND, 0)
                        calendar.set(Calendar.MILLISECOND, 0)
                        val recipientsCount = recipientsRaw
                            .split("\n")
                            .map { it.trim() }
                            .count { it.isNotEmpty() }
                        val scheduledLabel = formatScheduleTime(calendar.timeInMillis)
                        AlertDialog.Builder(this)
                            .setTitle(R.string.schedule_title)
                            .setMessage(
                                getString(
                                    R.string.schedule_confirm_message,
                                    scheduledLabel,
                                    String.format(Locale.US, "%d", recipientsCount)
                                )
                            )
                            .setPositiveButton(R.string.schedule_set) { _, _ ->
                                prepareScheduledDraft(
                                    dueAtMillis = calendar.timeInMillis,
                                    recipientsRaw = recipientsRaw,
                                    message = message,
                                    scheduledLabel = scheduledLabel
                                )
                            }
                            .setNegativeButton(R.string.cancel, null)
                            .show()
                    },
                    calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE),
                    true
                ).show()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }
    
    private fun prepareScheduledDraft(
        dueAtMillis: Long,
        recipientsRaw: String,
        message: String,
        scheduledLabel: String
    ) {
        if (dueAtMillis <= System.currentTimeMillis()) {
            showSnackbar(getString(R.string.schedule_invalid_time), Snackbar.LENGTH_LONG)
            return
        }
        pendingScheduleDraft = PendingScheduleDraft(
            dueAtMillis = dueAtMillis,
            recipientsRaw = recipientsRaw,
            message = message,
            label = scheduledLabel
        )
        scheduledTimeLabel = scheduledLabel
        findViewById<TextView>(R.id.txtScheduledTime)?.apply {
            visibility = View.VISIBLE
            text = getString(R.string.scheduled_time_footer, scheduledLabel)
        }
        updateSendButtonForScheduledState()
        showSnackbar(getString(R.string.scheduled_draft_ready, scheduledLabel), Snackbar.LENGTH_LONG)
    }
    
    private fun scheduleSms(
        dueAtMillis: Long,
        recipientsRaw: String,
        message: String,
        scheduledLabel: String
    ) {
        if (dueAtMillis <= System.currentTimeMillis()) {
            showSnackbar(getString(R.string.schedule_invalid_time), Snackbar.LENGTH_LONG)
            return
        }
        val scheduleToken = UUID.randomUUID().toString()
        val dueAt = dueAtMillis
        val subId = currentSimOption?.subscriptionId
        Log.d("MainActivity", "Scheduling token=$scheduleToken dueAt=$dueAt label=$scheduledLabel")
        ScheduledMessageStore.register(this, scheduleToken, dueAt)
        val pendingIntent = createScheduledPendingIntent(
            scheduleToken,
            message,
            recipientsRaw,
            subId
        )
        val alarmManager = getSystemService(AlarmManager::class.java)
        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                dueAt,
                pendingIntent
            )
            Log.d("MainActivity", "AlarmManager exact schedule set token=$scheduleToken")
        } catch (_: SecurityException) {
            Log.w("MainActivity", "Exact alarm blocked, fallback to WorkManager token=$scheduleToken")
            val delayMillis = (dueAt - System.currentTimeMillis()).coerceAtLeast(0L)
            val workData = Data.Builder()
                .putString(ScheduledSmsWorker.KEY_TOKEN, scheduleToken)
                .putString(ScheduledSmsWorker.KEY_MESSAGE, message)
                .putString(ScheduledSmsWorker.KEY_RECIPIENTS, recipientsRaw)
                .apply {
                    currentSimOption?.subscriptionId?.let {
                        putInt(ScheduledSmsWorker.KEY_SUBSCRIPTION_ID, it)
                    }
                }
                .build()
            val workRequest = OneTimeWorkRequestBuilder<ScheduledSmsWorker>()
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .setInputData(workData)
                .build()
            WorkManager.getInstance(this)
                .enqueueUniqueWork("scheduled_sms_$scheduleToken", ExistingWorkPolicy.REPLACE, workRequest)
        }

        scheduledJobStore.upsertJob(
            ScheduledSmsJob(
                token = scheduleToken,
                dueAtMillis = dueAtMillis,
                recipientsRaw = recipientsRaw,
                message = message,
                subscriptionId = subId
            )
        )
        refreshScheduledJobsUi()

        showSnackbar(getString(R.string.schedule_set_with_time, scheduledLabel), Snackbar.LENGTH_LONG)
    }
    
    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        val edtPhone = findViewById<EditText>(R.id.edtPhone)
        val edtMessage = findViewById<EditText>(R.id.edtMessage)
        
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
                if (text.isNotBlank()) {
                    edtMessage.setText(text)
                }
            }
            Intent.ACTION_SENDTO -> {
                val data = intent.data
                val recipientFromData = extractRecipientFromUri(data)
                val body = intent.getStringExtra("sms_body").orEmpty()
                if (recipientFromData.isNotBlank()) edtPhone.setText(recipientFromData)
                if (body.isNotBlank()) edtMessage.setText(body)
            }
        }
    }
    
    private fun extractRecipientFromUri(uri: Uri?): String {
        if (uri == null) return ""
        val value = uri.schemeSpecificPart.orEmpty()
        return value.substringBefore("?").replace(";", "\n")
    }
    
    private fun showWhatsNewOnFirstLaunchAfterUpdate() {
        if (!AppVersionTracker.shouldShowWhatsNew(this)) {
            AppVersionTracker.markCurrentVersionSeen(this)
            return
        }
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        val versionName = packageInfo.versionName ?: ""
        AlertDialog.Builder(this)
            .setTitle(R.string.whats_new_title)
            .setMessage(getString(R.string.whats_new_message, versionName))
            .setPositiveButton(R.string.ok, null)
            .show()
        AppVersionTracker.markCurrentVersionSeen(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        contactsDialog?.dismiss()
        sendStatusDialog?.dismiss()
        sendProgressDialog?.dismiss()
        countdownTimer?.cancel()
        // Remove observers to prevent leaks
        viewModel.sendResult.removeObservers(this)
        viewModel.sendProgress.removeObservers(this)
        if (isSmsReceiverRegistered) {
            unregisterReceiver(smsReceiver)
            isSmsReceiverRegistered = false
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PermissionManager.PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                viewModel.loadContacts()
            } else {
                showSnackbar(getString(R.string.permissions_denied), Snackbar.LENGTH_LONG)
            }
        } else if (requestCode == REQUEST_PHONE_STATE_FOR_SIM) {
            val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            if (granted) {
                viewModel.loadSimOptions(this)
                showSimPickerDialog()
            } else {
                showSnackbar(getString(R.string.sim_permission_denied), Snackbar.LENGTH_LONG)
            }
        }
    }

    private fun hasPhoneStatePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun showSimPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.sim_permission_title)
            .setMessage(R.string.sim_permission_message)
            .setPositiveButton(R.string.grant) { _, _ ->
                requestPermissions(
                    arrayOf(Manifest.permission.READ_PHONE_STATE),
                    REQUEST_PHONE_STATE_FOR_SIM
                )
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showBatteryOptimizationPromptForScheduling() {
        AlertDialog.Builder(this)
            .setTitle(R.string.battery_optimization_title)
            .setMessage(R.string.battery_optimization_message)
            .setPositiveButton(R.string.battery_optimization_allow) { _, _ ->
                reopenScheduleAfterBatteryRequest = true
                val allowIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                batteryOptimizationLauncher.launch(allowIntent)
            }
            .setNegativeButton(R.string.cancel) { _, _ ->
                reopenScheduleAfterBatteryRequest = false
            }
            .show()
    }
    
    private fun isIgnoringBatteryOptimizations(): Boolean {
        val powerManager = getSystemService(PowerManager::class.java)
        return powerManager.isIgnoringBatteryOptimizations(packageName)
    }
    
    private fun formatScheduleTime(timeInMillis: Long): String {
        val locale = if (LocaleManager.getCurrentLanguage(this) == "fa") Locale("fa") else Locale.ENGLISH
        val formatter = SimpleDateFormat("yyyy/MM/dd HH:mm", locale)
        return formatter.format(Date(timeInMillis))
    }

    /** Always Western (0–9) digits — used where locale would show Persian/Arabic numerals. */
    private fun formatScheduleTimeWesternDigits(timeInMillis: Long): String =
        SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US).format(Date(timeInMillis))

    private fun updateSendButtonForScheduledState() {
        findViewById<Button>(R.id.btnSend).text = if (scheduledTimeLabel.isNullOrBlank()) {
            getString(R.string.send)
        } else {
            getString(R.string.schedule_send_button_text)
        }
    }
    
    private fun clearScheduledUiState() {
        pendingScheduleDraft = null
        scheduledTimeLabel = null
        findViewById<TextView>(R.id.txtScheduledTime)?.visibility = View.GONE
        updateSendButtonForScheduledState()
    }

    private fun refreshScheduledJobsUi() {
        val visible = scheduledJobStore.loadJobs().isNotEmpty()
        findViewById<ImageButton>(R.id.btnScheduledJobs)?.visibility =
            if (visible) View.VISIBLE else View.GONE
    }

    private fun buildRecipientsMultilineForSchedule(): String? {
        val manual = findViewById<EditText>(R.id.edtPhone).text.toString()
        val recipients = LinkedHashSet<String>()
        manual.split("\n", "\r\n").forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isNotEmpty() && NumberValidator.isValidPhoneNumber(trimmed)) {
                val normalized = NumberValidator.normalizeNumber(trimmed)
                if (normalized.isNotEmpty()) recipients.add(normalized)
            }
        }
        viewModel.selectedIds.value.orEmpty().forEach { id ->
            viewModel.contacts.value?.find { it.id == id }?.normalizedNumber?.let {
                recipients.add(it)
            }
        }
        if (recipients.isEmpty()) return null
        return recipients.joinToString("\n")
    }

    private fun buildScheduledSmsIntent(
        token: String,
        message: String,
        recipientsRaw: String,
        subscriptionId: Int?
    ): Intent = Intent(this, ScheduledSmsReceiver::class.java).apply {
        action = "com.afrouzi.longsmssender.SCHEDULED_SEND.$token"
        data = Uri.parse("longsmssender://scheduled/$token")
        putExtra(ScheduledSmsReceiver.EXTRA_MESSAGE, message)
        putExtra(ScheduledSmsReceiver.EXTRA_RECIPIENTS, recipientsRaw)
        putExtra(ScheduledSmsReceiver.EXTRA_SCHEDULE_TOKEN, token)
        subscriptionId?.let {
            putExtra(ScheduledSmsReceiver.EXTRA_SIM_SUBSCRIPTION_ID, it)
        }
    }

    private fun createScheduledPendingIntent(
        token: String,
        message: String,
        recipientsRaw: String,
        subscriptionId: Int?
    ): PendingIntent {
        val intent = buildScheduledSmsIntent(token, message, recipientsRaw, subscriptionId)
        return PendingIntent.getBroadcast(
            this,
            token.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun cancelScheduledJob(job: ScheduledSmsJob) {
        ScheduledMessageStore.clear(this, job.token)
        scheduledJobStore.removeJob(job.token)
        val alarmManager = getSystemService(AlarmManager::class.java)
        val pi = createScheduledPendingIntent(
            job.token,
            job.message,
            job.recipientsRaw,
            job.subscriptionId
        )
        alarmManager.cancel(pi)
        pi.cancel()
        WorkManager.getInstance(this).cancelUniqueWork("scheduled_sms_${job.token}")
        refreshScheduledJobsUi()
    }

    private fun showScheduledSendConfirmationDialog(draft: PendingScheduleDraft) {
        val mergedRecipients = buildRecipientsMultilineForSchedule()
        val messageBody = findViewById<EditText>(R.id.edtMessage).text.toString()
        if (mergedRecipients == null || messageBody.isBlank()) {
            showSnackbar(getString(R.string.no_valid_recipients), Snackbar.LENGTH_LONG)
            return
        }
        val segmentsPerMessage = SmsMessage.calculateLength(messageBody, false)[0]
        val recipientCount = mergedRecipients.split("\n")
            .map { it.trim() }
            .count { it.isNotEmpty() }
        val totalParts = recipientCount * segmentsPerMessage
        val countStr = String.format(Locale.US, "%d", recipientCount)
        val partsStr = String.format(Locale.US, "%d", totalParts)
        val timeWestern = formatScheduleTimeWesternDigits(draft.dueAtMillis)
        AlertDialog.Builder(this)
            .setTitle(R.string.schedule_send_confirm_title)
            .setMessage(
                getString(
                    R.string.schedule_send_confirm_message,
                    countStr,
                    partsStr,
                    timeWestern,
                    getString(R.string.schedule_send_confirm_management_hint)
                )
            )
            .setPositiveButton(R.string.schedule_send_confirm_button) { _, _ ->
                scheduleSms(
                    draft.dueAtMillis,
                    mergedRecipients,
                    messageBody,
                    draft.label
                )
                clearScheduledUiState()
            }
            .setNegativeButton(R.string.cancel, null)
            .setCancelable(false)
            .show()
    }

    private fun showScheduledJobsDialog() {
        val jobs = scheduledJobStore.loadJobs()
        if (jobs.isEmpty()) {
            showSnackbar(getString(R.string.scheduled_jobs_empty), Snackbar.LENGTH_SHORT)
            return
        }
        val labels = jobs.map { job ->
            val preview = job.message.trim().replace("\n", " ").take(48)
            "${formatScheduleTime(job.dueAtMillis)} · $preview"
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.scheduled_jobs_title)
            .setItems(labels) { _, which ->
                showScheduledJobActionsDialog(jobs[which])
            }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    private fun showScheduledJobActionsDialog(job: ScheduledSmsJob) {
        val actions = arrayOf(
            getString(R.string.scheduled_action_send_now),
            getString(R.string.scheduled_action_edit),
            getString(R.string.scheduled_action_cancel)
        )
        AlertDialog.Builder(this)
            .setTitle(formatScheduleTime(job.dueAtMillis))
            .setItems(actions) { _, which ->
                when (which) {
                    0 -> sendScheduledJobImmediately(job)
                    1 -> beginEditScheduledJob(job)
                    2 -> {
                        cancelScheduledJob(job)
                        showSnackbar(getString(R.string.scheduled_job_cancelled), Snackbar.LENGTH_SHORT)
                    }
                }
            }
            .show()
    }

    private fun sendScheduledJobImmediately(job: ScheduledSmsJob) {
        cancelScheduledJob(job)
        viewModel.sendSms(job.recipientsRaw, job.message)
    }

    private fun beginEditScheduledJob(job: ScheduledSmsJob) {
        cancelScheduledJob(job)
        findViewById<EditText>(R.id.edtPhone).setText(job.recipientsRaw)
        findViewById<EditText>(R.id.edtMessage).setText(job.message)
        job.subscriptionId?.let { subId ->
            val option = viewModel.availableSims.value?.firstOrNull { it.subscriptionId == subId }
            if (option != null) {
                currentSimOption = option
                viewModel.selectSim(option)
                updateSimButtonText()
            }
        }
        viewModel.clearSelection()
        viewModel.updateRecipientsCount(job.recipientsRaw)
        viewModel.onMessageTextChanged(job.message)
        showScheduleDialog(job.recipientsRaw, job.message)
        showSnackbar(getString(R.string.scheduled_edit_pick_time), Snackbar.LENGTH_LONG)
    }

    private fun showPreparedMessagesRootDialog() {
        val actions = arrayOf(
            getString(R.string.prepared_insert_title),
            getString(R.string.prepared_save_current_title),
            getString(R.string.prepared_manage_title)
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.prepared_messages)
            .setItems(actions) { _, which ->
                when (which) {
                    0 -> {
                        val list = preparedMessageStore.loadAll()
                        if (list.isEmpty()) {
                            showSnackbar(getString(R.string.prepared_none_saved), Snackbar.LENGTH_SHORT)
                        } else {
                            showPickPreparedTemplateDialog(list)
                        }
                    }
                    1 -> showSavePreparedTemplateDialog()
                    2 -> showManagePreparedTemplatesDialog()
                }
            }
            .show()
    }

    private fun showPickPreparedTemplateDialog(templates: List<PreparedMessage>) {
        val labels = templates.map { it.title }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.prepared_pick_title)
            .setItems(labels) { _, which ->
                val body = templates[which].body
                findViewById<EditText>(R.id.edtMessage).setText(body)
                viewModel.onMessageTextChanged(body)
            }
            .show()
    }

    private fun showSavePreparedTemplateDialog() {
        val body = findViewById<EditText>(R.id.edtMessage).text.toString().trim()
        if (body.isBlank()) {
            showSnackbar(getString(R.string.message_empty), Snackbar.LENGTH_SHORT)
            return
        }
        val input = EditText(this).apply {
            hint = getString(R.string.prepared_title_hint)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.prepared_save_current_title)
            .setView(input)
            .setPositiveButton(R.string.save_group) { _, _ ->
                val title = input.text.toString().trim()
                if (title.isNotEmpty()) {
                    preparedMessageStore.save(PreparedMessage(title, body))
                    showSnackbar(getString(R.string.prepared_saved), Snackbar.LENGTH_SHORT)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showManagePreparedTemplatesDialog() {
        val templates = preparedMessageStore.loadAll()
        if (templates.isEmpty()) {
            showSnackbar(getString(R.string.prepared_none_saved), Snackbar.LENGTH_SHORT)
            return
        }
        val labels = templates.map { it.title }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.prepared_manage_title)
            .setItems(labels) { _, which ->
                showPreparedTemplateItemActions(templates[which])
            }
            .show()
    }

    private fun showPreparedTemplateItemActions(msg: PreparedMessage) {
        val actions = arrayOf(
            getString(R.string.prepared_use_now),
            getString(R.string.prepared_edit_entry),
            getString(R.string.prepared_delete_entry)
        )
        AlertDialog.Builder(this)
            .setTitle(msg.title)
            .setItems(actions) { _, which ->
                when (which) {
                    0 -> {
                        findViewById<EditText>(R.id.edtMessage).setText(msg.body)
                        viewModel.onMessageTextChanged(msg.body)
                    }
                    1 -> showEditPreparedTemplateDialog(msg)
                    2 -> {
                        preparedMessageStore.delete(msg.title)
                        showSnackbar(getString(R.string.prepared_deleted), Snackbar.LENGTH_SHORT)
                    }
                }
            }
            .show()
    }

    private fun showEditPreparedTemplateDialog(msg: PreparedMessage) {
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            val pad = (resources.displayMetrics.density * 24f).toInt()
            setPadding(pad, pad / 2, pad, 0)
        }
        val titleInput = EditText(this).apply {
            setText(msg.title)
            hint = getString(R.string.prepared_title_hint)
        }
        val bodyInput = EditText(this).apply {
            setText(msg.body)
            minLines = 4
            hint = getString(R.string.message_hint)
        }
        container.addView(titleInput)
        container.addView(bodyInput)
        AlertDialog.Builder(this)
            .setTitle(R.string.prepared_edit_entry)
            .setView(container)
            .setPositiveButton(R.string.save_group) { _, _ ->
                val newTitle = titleInput.text.toString().trim()
                val newBody = bodyInput.text.toString()
                if (newTitle.isEmpty()) return@setPositiveButton
                preparedMessageStore.delete(msg.title)
                preparedMessageStore.save(PreparedMessage(newTitle, newBody))
                showSnackbar(getString(R.string.prepared_saved), Snackbar.LENGTH_SHORT)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    private fun sortContactsByRecent(contacts: List<Contact>): List<Contact> {
        if (contacts.isEmpty()) return contacts
        val recentOrder = recentContactsStore.loadRecentIds()
            .withIndex()
            .associate { it.value to it.index }
        return contacts.sortedWith(
            compareBy<Contact> { recentOrder[it.id] ?: Int.MAX_VALUE }
                .thenBy { it.name.lowercase() }
        )
    }
}
