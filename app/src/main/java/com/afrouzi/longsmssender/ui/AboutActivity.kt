package com.afrouzi.longsmssender.ui

import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.afrouzi.longsmssender.R
import com.afrouzi.longsmssender.utils.LocaleManager

class AboutActivity : AppCompatActivity() {
    
    override fun attachBaseContext(newBase: Context) {
        val language = LocaleManager.getCurrentLanguage(newBase)
        super.attachBaseContext(LocaleManager.setLocale(newBase, language))
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.about_title)

        toolbar.setNavigationOnClickListener {
            finish()
        }

        val txtAppName = findViewById<TextView>(R.id.txtAppName)
        val txtVersion = findViewById<TextView>(R.id.txtVersion)
        val txtDeveloper = findViewById<TextView>(R.id.txtDeveloper)
        val txtDeveloperName = findViewById<TextView>(R.id.txtDeveloperName)
        val txtPrivacy = findViewById<TextView>(R.id.txtPrivacy)

        txtAppName.text = getString(R.string.app_name)
        
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            val versionName = packageInfo.versionName
            txtVersion.text = "${getString(R.string.app_version)}: $versionName"
        } catch (e: PackageManager.NameNotFoundException) {
            txtVersion.text = getString(R.string.app_version)
        }

        txtDeveloper.text = getString(R.string.developer)
        txtDeveloperName.text = getString(R.string.developer_name)
        txtPrivacy.text = getString(R.string.privacy_statement)
    }
}

