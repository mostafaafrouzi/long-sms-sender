package com.afrouzi.longsmssender.data.repository

import android.content.Context
import android.provider.ContactsContract
import com.afrouzi.longsmssender.data.model.Contact
import com.afrouzi.longsmssender.utils.NumberValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ContactRepository(private val context: Context) {

    suspend fun getContacts(): List<Contact> = withContext(Dispatchers.IO) {
        val contactsList = ArrayList<Contact>()
        val uniqueNumbers = HashSet<String>()

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null,
            null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )

        cursor?.use {
            val idIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

            while (it.moveToNext()) {
                val id = it.getString(idIndex) ?: ""
                val name = it.getString(nameIndex) ?: "Unknown"
                val rawNumber = it.getString(numberIndex) ?: ""
                
                val normalizedNumber = NumberValidator.normalizeNumber(rawNumber)

                if (normalizedNumber.isNotEmpty() && !uniqueNumbers.contains(normalizedNumber)) {
                    uniqueNumbers.add(normalizedNumber)
                    contactsList.add(Contact(id, name, rawNumber, normalizedNumber))
                }
            }
        }
        return@withContext contactsList
    }
}
