package com.mostafaafrouzi.longsmssender.ui

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.mostafaafrouzi.longsmssender.R
import com.mostafaafrouzi.longsmssender.ui.MainViewModel.BulkSendConfirmation
import com.mostafaafrouzi.longsmssender.utils.LocaleManager
import com.mostafaafrouzi.longsmssender.utils.PermissionManager
import com.mostafaafrouzi.longsmssender.utils.SmsBroadcastReceiver

class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()
    private val smsReceiver = SmsBroadcastReceiver()
    private lateinit var contactAdapter: ContactAdapter

    override fun attachBaseContext(newBase: Context) {
        val language = LocaleManager.getCurrentLanguage(newBase)
        super.attachBaseContext(LocaleManager.setLocale(newBase, language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Register Receiver
        ContextCompat.registerReceiver(
            this,
            smsReceiver,
            IntentFilter().apply {
                addAction("SMS_SENT")
                addAction("SMS_DELIVERED")
            },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        setupUI()
        observeViewModel()
    }

    private fun setupUI() {
        // Toolbar Actions
        findViewById<Button>(R.id.btnLanguage).setOnClickListener {
            showLanguageDialog()
        }
        
        findViewById<Button>(R.id.btnAbout).setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }
        
        findViewById<Button>(R.id.btnShare).setOnClickListener {
            shareApp()
        }
        
        findViewById<Button>(R.id.btnLoadContacts).setOnClickListener {
            checkAndRequestPermissions()
        }

        // RecyclerView
        val recyclerContacts = findViewById<RecyclerView>(R.id.recyclerContacts)
        contactAdapter = ContactAdapter { contact ->
            viewModel.toggleSelection(contact)
        }
        recyclerContacts.layoutManager = LinearLayoutManager(this)
        recyclerContacts.adapter = contactAdapter

        // Selection Controls
        findViewById<Button>(R.id.btnSelectAll).setOnClickListener { viewModel.selectAll() }
        findViewById<Button>(R.id.btnClearSelection).setOnClickListener { viewModel.clearSelection() }

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
    }

    private fun observeViewModel() {
        val txtStatus = findViewById<TextView>(R.id.txtStatus)
        val txtSegment = findViewById<TextView>(R.id.txtSegment)
        val txtSelectionStatus = findViewById<TextView>(R.id.txtSelectionStatus)
        val recyclerContacts = findViewById<RecyclerView>(R.id.recyclerContacts)
        val progressLoading = findViewById<ProgressBar>(R.id.progressLoading)
        val emptyNoContacts = findViewById<View>(R.id.emptyNoContacts)
        val emptyNoSelection = findViewById<View>(R.id.emptyNoSelection)
        val progressSending = findViewById<ProgressBar>(R.id.progressSending)
        val btnSend = findViewById<Button>(R.id.btnSend)

        viewModel.isLoading.observe(this) { isLoading ->
            progressLoading.visibility = if (isLoading) View.VISIBLE else View.GONE
            recyclerContacts.visibility = if (isLoading) View.GONE else View.VISIBLE
            emptyNoContacts.visibility = View.GONE
            emptyNoSelection.visibility = View.GONE
        }

        viewModel.contacts.observe(this) { contacts ->
            contactAdapter.submitList(contacts)
            updateEmptyStates(contacts, viewModel.selectedIds.value.orEmpty())
            if (contacts.isNotEmpty()) {
                txtStatus.text = getString(R.string.status_loaded, contacts.size)
                showSnackbar(getString(R.string.status_loaded, contacts.size), Snackbar.LENGTH_SHORT)
            }
        }

        viewModel.selectedIds.observe(this) { selected ->
            contactAdapter.updateSelection(selected)
            txtSelectionStatus.text = getString(R.string.selected_count, selected.size)
            updateEmptyStates(viewModel.contacts.value.orEmpty(), selected)
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
        
        viewModel.sendStatus.observe(this) { status ->
            val isSending = viewModel.isSending.value ?: false
            if (!isSending) {
                txtStatus.text = status
                // Show success/error feedback via Snackbar
                when {
                    status.contains(getString(R.string.sent_to_recipients)) && !status.contains("failed") -> {
                        showSnackbar(status, Snackbar.LENGTH_LONG)
                    }
                    status.contains(getString(R.string.error_sending_sms)) -> {
                        showSnackbar(status, Snackbar.LENGTH_LONG)
                    }
                    status.contains("failed") -> {
                        showSnackbar(status, Snackbar.LENGTH_LONG)
                    }
                }
            }
        }
    }
    
    private fun updateEmptyStates(contacts: List<Contact>, selected: Set<String>) {
        val recyclerContacts = findViewById<RecyclerView>(R.id.recyclerContacts)
        val progressLoading = findViewById<ProgressBar>(R.id.progressLoading)
        val emptyNoContacts = findViewById<View>(R.id.emptyNoContacts)
        val emptyNoSelection = findViewById<View>(R.id.emptyNoSelection)
        
        if (viewModel.isLoading.value == true) {
            return // Loading state takes priority
        }
        
        when {
            contacts.isEmpty() -> {
                recyclerContacts.visibility = View.GONE
                progressLoading.visibility = View.GONE
                emptyNoContacts.visibility = View.VISIBLE
                emptyNoSelection.visibility = View.GONE
            }
            selected.isEmpty() -> {
                recyclerContacts.visibility = View.VISIBLE
                progressLoading.visibility = View.GONE
                emptyNoContacts.visibility = View.GONE
                emptyNoSelection.visibility = View.VISIBLE
            }
            else -> {
                recyclerContacts.visibility = View.VISIBLE
                progressLoading.visibility = View.GONE
                emptyNoContacts.visibility = View.GONE
                emptyNoSelection.visibility = View.GONE
            }
        }
    }
    
    private fun showSnackbar(message: String, duration: Int) {
        val rootView = findViewById<View>(android.R.id.content)
        Snackbar.make(rootView, message, duration).show()
    }

    private fun checkAndRequestPermissions() {
        if (PermissionManager.hasPermissions(this)) {
            viewModel.loadContacts()
        } else {
            if (PermissionManager.shouldShowRationale(this)) {
                showRationaleDialog()
            } else {
                PermissionManager.requestPermissions(this)
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
                    recreate()
                }
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    private fun shareApp() {
        val shareText = getString(R.string.share_app_text, "https://cafebazaar.ir/app/com.mostafaafrouzi.longsmssender")
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
                viewModel.loadContacts()
            } else {
                showSnackbar(getString(R.string.permissions_denied), Snackbar.LENGTH_LONG)
            }
        }
    }
}
