package com.afrouzi.longsmssender.ui

import android.content.BroadcastReceiver
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.ProgressBar
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.afrouzi.longsmssender.R
import com.afrouzi.longsmssender.data.model.Contact
import com.afrouzi.longsmssender.ui.BulkSendConfirmation
import com.afrouzi.longsmssender.utils.LocaleManager
import com.afrouzi.longsmssender.utils.PermissionManager
import com.afrouzi.longsmssender.utils.SmsBroadcastReceiver
import com.afrouzi.longsmssender.utils.ThemeManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.constraintlayout.widget.ConstraintLayout

class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()
    private val smsReceiver = SmsBroadcastReceiver()
    private lateinit var contactAdapter: ContactAdapter
    private var contactsDialog: AlertDialog? = null
    private var filteredContacts: List<Contact> = emptyList()
    private var selectedContacts: MutableSet<String> = mutableSetOf()

    override fun attachBaseContext(newBase: Context) {
        val language = LocaleManager.getCurrentLanguage(newBase)
        val context = LocaleManager.setLocale(newBase, language)
        super.attachBaseContext(context)
    }
    
    private fun applyLayoutDirection() {
        val language = LocaleManager.getCurrentLanguage(this)
        val isRtl = language == "fa"
        
        // Apply layout direction to window decor view
        val layoutDirection = if (isRtl) {
            android.view.View.LAYOUT_DIRECTION_RTL
        } else {
            android.view.View.LAYOUT_DIRECTION_LTR
        }
        
        window.decorView.layoutDirection = layoutDirection
        
        // Also apply text direction
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            val textDirection = if (isRtl) {
                android.view.View.TEXT_DIRECTION_RTL
            } else {
                android.view.View.TEXT_DIRECTION_LTR
            }
            window.decorView.textDirection = textDirection
        }
        
        // Force update all views in the hierarchy
        window.decorView.requestLayout()
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
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            // RECEIVER_NOT_EXPORTED = 0x00000002
            // Use reflection to call registerReceiver with flags for Android 13+
            try {
                val registerMethod = Context::class.java.getMethod(
                    "registerReceiver",
                    android.content.BroadcastReceiver::class.java,
                    IntentFilter::class.java,
                    Int::class.javaPrimitiveType
                )
                registerMethod.invoke(this, smsReceiver, filter, 0x00000002)
            } catch (e: Exception) {
                // Fallback to old method if reflection fails
                @Suppress("DEPRECATION")
                this.registerReceiver(smsReceiver, filter)
            }
        } else {
            @Suppress("DEPRECATION")
            this.registerReceiver(smsReceiver, filter)
        }

        setupUI()
        observeViewModel()
        updateThemeIcon()
        
        // Request permissions on first launch
        if (!PermissionManager.hasPermissions(this)) {
            PermissionManager.requestPermissions(this)
        }
    }

    private fun setupUI() {
        // Toolbar Actions
        updateLanguageButton()
        findViewById<Button>(R.id.btnLanguage).setOnClickListener {
            showLanguageDialog()
        }

        findViewById<ImageButton>(R.id.btnTheme).setOnClickListener {
            toggleTheme()
        }
        
        findViewById<ImageButton>(R.id.btnAbout).setOnClickListener {
            showAboutDialog()
        }
        
        findViewById<ImageButton>(R.id.btnShare).setOnClickListener {
            shareApp()
        }

        // Contacts Button
        findViewById<Button>(R.id.btnContacts).setOnClickListener {
            showContactsDialog()
        }

        // Input Area
        val edtPhone = findViewById<EditText>(R.id.edtPhone)
        val edtMessage = findViewById<EditText>(R.id.edtMessage)
        val btnSend = findViewById<Button>(R.id.btnSend)
        val btnPaste = findViewById<ImageButton>(R.id.btnPaste)

        btnSend.setOnClickListener {
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
                    // If user manually edits the field and it doesn't match selected contacts, clear selection
                    val currentSelected = viewModel.selectedIds.value.orEmpty()
                    if (currentSelected.isNotEmpty() && newText.isNotEmpty()) {
                        // Check if the new text matches the selected contacts
                        val selectedNumbers = viewModel.contacts.value
                            ?.filter { currentSelected.contains(it.id) }
                            ?.map { it.phoneNumber }
                            ?.joinToString("\n") ?: ""
                        
                        // If the new text doesn't match selected contacts, clear selection
                        if (newText.trim() != selectedNumbers.trim()) {
                            viewModel.clearSelection()
                        }
                    } else if (newText.isEmpty() && currentSelected.isNotEmpty()) {
                        // If user clears the field, clear selection
                        viewModel.clearSelection()
                    }
                    viewModel.updateRecipientsCount(newText)
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        
        // Observe selected contacts changes
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

        viewModel.contacts.observe(this) { contacts ->
            filteredContacts = contacts
            // Update dialog if it's showing
            contactsDialog?.let { dialog ->
                val recyclerView = dialog.findViewById<RecyclerView>(R.id.recyclerContactsDialog)
                recyclerView?.let {
                    contactAdapter.submitList(filteredContacts)
                }
            }
        }

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
        
        // Observe send progress for time-consuming sends
        viewModel.sendProgress.observe(this) { progress ->
            progress?.let {
                showSendProgressDialog(it)
            } ?: run {
                // Progress finished, dismiss progress dialog
                sendProgressDialog?.dismiss()
                sendProgressDialog = null
            }
        }
        
        // Observe send result for detailed status dialog
        viewModel.sendResult.observe(this) { result ->
            result?.let {
                showSendStatusDialog(it)
            }
        }
        
        viewModel.sendStatus.observe(this) { status ->
            txtStatus.text = status
        }
    }

    private var sendStatusDialog: AlertDialog? = null
    private var sendProgressDialog: AlertDialog? = null
    private var countdownTimer: android.os.CountDownTimer? = null
    
    private fun showSendProgressDialog(progress: MainViewModel.SendProgress) {
        // Show progress dialog if not already showing
        if (sendProgressDialog == null) {
            val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_send_progress, null)
            val txtTitle = dialogView.findViewById<TextView>(R.id.txtProgressTitle)
            val progressBar = dialogView.findViewById<ProgressBar>(R.id.progressBar)
            val txtStatus = dialogView.findViewById<TextView>(R.id.txtProgressStatus)
            val txtDetails = dialogView.findViewById<TextView>(R.id.txtProgressDetails)
            
            txtTitle.text = getString(R.string.sending_progress_title)
            progressBar.max = progress.totalRecipients
            txtDetails.text = getString(R.string.sending_progress_details, progress.segmentsPerMessage)
            
            sendProgressDialog = AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(false)
                .create()
            
            sendProgressDialog?.show()
        }
        
        // Update progress
        val progressBar = sendProgressDialog?.findViewById<ProgressBar>(R.id.progressBar)
        val txtStatus = sendProgressDialog?.findViewById<TextView>(R.id.txtProgressStatus)
        
        progressBar?.progress = progress.currentRecipient
        txtStatus?.text = getString(R.string.sending_progress_status, progress.currentRecipient, progress.totalRecipients)
    }
    
    private fun showSendStatusDialog(result: MainViewModel.SendResult) {
        // Cancel previous dialog and timer if exists
        sendStatusDialog?.dismiss()
        countdownTimer?.cancel()
        
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_send_status, null)
        val txtStatusMessage = dialogView.findViewById<TextView>(R.id.txtStatusMessage)
        val btnOk = dialogView.findViewById<Button>(R.id.btnOk)
        
        // Generate detailed message based on result
        val message = when {
            result.failureCount == 0 -> {
                // Full success
                getString(R.string.send_status_success, result.segmentsPerMessage, result.successCount)
            }
            result.successCount == 0 -> {
                // Full failure
                getString(R.string.send_status_error, result.lastError ?: getString(R.string.error_unknown))
            }
            else -> {
                // Partial success
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
        
        sendStatusDialog?.setOnDismissListener {
            countdownTimer?.cancel()
            // Clear result to prevent re-showing when language/theme changes
            viewModel.clearSendResult()
        }
        
        sendStatusDialog?.show()
        countdownTimer?.start()
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

        // Load contacts if not already loaded
        if (viewModel.contacts.value.isNullOrEmpty()) {
            viewModel.loadContacts()
        }

        // Create dialog layout
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_contacts, null)
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.recyclerContactsDialog)
        val progressBar = dialogView.findViewById<ProgressBar>(R.id.progressLoadingDialog)
        val emptyView = dialogView.findViewById<View>(R.id.emptyNoContactsDialog)
        val emptySearchView = dialogView.findViewById<View>(R.id.emptyNoSearchResults)
        val edtSearch = dialogView.findViewById<EditText>(R.id.edtSearch)
        val btnSelectAll = dialogView.findViewById<Button>(R.id.btnSelectAll)
        val btnClearSelection = dialogView.findViewById<Button>(R.id.btnClearSelection)
        val edtPhone = findViewById<EditText>(R.id.edtPhone)

        // Reset selection
        selectedContacts.clear()
        
        // Initialize status text
        val txtStatus = dialogView.findViewById<TextView>(R.id.txtSelectionStatusDialog)
        txtStatus?.text = getString(R.string.selected_count, 0)

        // Setup RecyclerView
        contactAdapter = ContactAdapter(
            onItemClick = { contact ->
                // Toggle selection
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
        
        // Add FastScroller for alphabet navigation
        val fastScroller = FastScroller(recyclerView, this)
        recyclerView.addItemDecoration(fastScroller)

        // Search functionality
        edtSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString().lowercase().trim()
                filteredContacts = if (query.isEmpty()) {
                    viewModel.contacts.value ?: emptyList()
                } else {
                    (viewModel.contacts.value ?: emptyList()).filter { contact ->
                        contact.name.lowercase().contains(query) || 
                        contact.phoneNumber.contains(query)
                    }
                }
                // Update alphabet indexer when contacts change (only when not searching)
                if (query.isEmpty()) {
                    contactAdapter.updateAlphabetIndexer(filteredContacts)
                }
                contactAdapter.submitList(filteredContacts)
                
                // Update empty states
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

        // Select All button
        btnSelectAll.setOnClickListener {
            selectedContacts.clear()
            filteredContacts.forEach { contact ->
                selectedContacts.add(contact.id)
            }
            contactAdapter.updateSelection(selectedContacts)
            // Update status text if exists
            val txtStatus = dialogView.findViewById<TextView>(R.id.txtSelectionStatusDialog)
            txtStatus?.text = getString(R.string.selected_count, selectedContacts.size)
        }

        // Clear Selection button
        btnClearSelection.setOnClickListener {
            selectedContacts.clear()
            contactAdapter.updateSelection(selectedContacts)
            // Update status text if exists
            val txtStatus = dialogView.findViewById<TextView>(R.id.txtSelectionStatusDialog)
            txtStatus?.text = getString(R.string.selected_count, 0)
        }

        // Observe loading state
        viewModel.isLoading.observe(this) { isLoading ->
            progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            recyclerView.visibility = if (isLoading) View.GONE else View.VISIBLE
            emptyView.visibility = View.GONE
            emptySearchView.visibility = View.GONE
        }

        // Observe contacts
        viewModel.contacts.observe(this) { contacts ->
            val query = edtSearch.text.toString().lowercase().trim()
            filteredContacts = if (query.isEmpty()) {
                contacts
            } else {
                contacts.filter { contact ->
                    contact.name.lowercase().contains(query) || 
                    contact.phoneNumber.contains(query)
                }
            }
            // Update alphabet indexer when contacts change (only when not searching)
            if (query.isEmpty()) {
                contactAdapter.updateAlphabetIndexer(filteredContacts)
            }
            contactAdapter.submitList(filteredContacts)
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

        // Create and show dialog
        contactsDialog = AlertDialog.Builder(this)
            .setTitle(R.string.select_contact)
            .setView(dialogView)
            .setPositiveButton(R.string.select) { _, _ ->
                // Apply selected contacts to phone field
                // Use a copy of filteredContacts to ensure we have the latest data
                val currentFiltered = contactAdapter.currentList
                val selectedNumbers = currentFiltered
                    .filter { selectedContacts.contains(it.id) }
                    .map { it.phoneNumber }
                    .joinToString("\n")
                
                // Update ViewModel selectedIds - set all at once to avoid multiple observer calls
                viewModel.setSelectedIds(selectedContacts)
                
                if (selectedNumbers.isNotEmpty()) {
                    edtPhone.setText(selectedNumbers)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .create()

        contactsDialog?.setOnDismissListener {
            // Clean up observers when dialog is dismissed
            viewModel.isLoading.removeObservers(this)
            viewModel.contacts.removeObservers(this)
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
        
        // Show toast message
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
        val btnTheme = findViewById<ImageButton>(R.id.btnTheme)
        val isDark = ThemeManager.isDarkMode(this)
        btnTheme.setImageResource(if (isDark) R.drawable.ic_light_mode else R.drawable.ic_dark_mode)
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
                    // Save language preference first
                    LocaleManager.setLocale(this, newLanguage)
                    // Update language button text immediately
                    updateLanguageButton()
                    // Force recreate with proper layout direction
                    dialog.dismiss()
                    // Use post to ensure dialog is dismissed before recreate
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
        
        // Set basic info
        txtAppName.text = getString(R.string.app_name)
        
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            val versionName = packageInfo.versionName
            txtVersion.text = "${getString(R.string.app_version)}: $versionName"
        } catch (e: PackageManager.NameNotFoundException) {
            txtVersion.text = getString(R.string.app_version)
        }
        
        // Set localized about content
        txtAboutTitle.text = getString(R.string.about_title)
        txtAboutDescription.text = getString(R.string.about_description)
        txtDeveloper.text = getString(R.string.developer)
        txtDeveloperName.text = getString(R.string.developer_name)
        txtDeveloperInfo.text = getString(R.string.developer_info)
        txtDeveloperDescription.text = getString(R.string.developer_description)
        txtOpenSource.text = getString(R.string.about_open_source)
        
        // Center align for Persian
        if (isRtl) {
            txtDeveloper.gravity = android.view.Gravity.CENTER
            txtDeveloperName.gravity = android.view.Gravity.CENTER
            txtDeveloperInfo.gravity = android.view.Gravity.CENTER
            txtDeveloperDescription.gravity = android.view.Gravity.CENTER
        }
        
        // Set link buttons with full URLs
        btnWebsite.text = "afrouzi.ir"
        btnGitHub.text = "github.com/mostafaafrouzi"
        btnLinkedIn.text = "linkedin.com/in/mostafaafrouzi"
        
        // Tint drawables to match text color
        val websiteDrawable = ContextCompat.getDrawable(this, R.drawable.ic_website)?.mutate()
        val githubDrawable = ContextCompat.getDrawable(this, R.drawable.ic_github)?.mutate()
        val linkedinDrawable = ContextCompat.getDrawable(this, R.drawable.ic_linkedin)?.mutate()
        
        // Use colorOnSurface for icons - tint based on theme
        val iconTintColor = if (ThemeManager.isDarkMode(this)) {
            ContextCompat.getColor(this, android.R.color.white)
        } else {
            ContextCompat.getColor(this, android.R.color.black)
        }
        
        websiteDrawable?.setTint(iconTintColor)
        githubDrawable?.setTint(iconTintColor)
        linkedinDrawable?.setTint(iconTintColor)
        
        // Set drawable based on RTL/LTR
        if (isRtl) {
            btnWebsite.setCompoundDrawablesWithIntrinsicBounds(null, null, websiteDrawable, null)
            btnGitHub.setCompoundDrawablesWithIntrinsicBounds(null, null, githubDrawable, null)
            btnLinkedIn.setCompoundDrawablesWithIntrinsicBounds(null, null, linkedinDrawable, null)
        } else {
            btnWebsite.setCompoundDrawablesWithIntrinsicBounds(websiteDrawable, null, null, null)
            btnGitHub.setCompoundDrawablesWithIntrinsicBounds(githubDrawable, null, null, null)
            btnLinkedIn.setCompoundDrawablesWithIntrinsicBounds(linkedinDrawable, null, null, null)
        }
        
        // Link button click handlers
        val websiteUrl = if (language == "fa") {
            "https://afrouzi.ir"
        } else {
            "https://afrouzi.ir/en"
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
        
        // Create and show dialog
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

    override fun onDestroy() {
        super.onDestroy()
        contactsDialog?.dismiss()
        sendStatusDialog?.dismiss()
        sendProgressDialog?.dismiss()
        countdownTimer?.cancel()
        // Remove observers to prevent leaks
        viewModel.sendResult.removeObservers(this)
        viewModel.sendProgress.removeObservers(this)
        unregisterReceiver(smsReceiver)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PermissionManager.PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                // Permissions granted, load contacts if dialog is showing
                if (contactsDialog?.isShowing == true) {
                viewModel.loadContacts()
                }
            } else {
                showSnackbar(getString(R.string.permissions_denied), Snackbar.LENGTH_LONG)
            }
        }
    }
}
