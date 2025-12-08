package com.afrouzi.longsmssender.ui

import com.afrouzi.longsmssender.data.model.Contact
import java.util.*

class AlphabetIndexer(private val contacts: List<Contact>) {
    
    private val sections = mutableListOf<String>()
    private val sectionPositions = mutableMapOf<String, Int>()
    
    init {
        buildSections()
    }
    
    private fun buildSections() {
        sections.clear()
        sectionPositions.clear()
        
        val englishSections = mutableSetOf<String>()
        val persianSections = mutableSetOf<String>()
        val numberSections = mutableSetOf<String>()
        
        contacts.forEachIndexed { index, contact ->
            val firstChar = contact.name.firstOrNull()?.uppercaseChar() ?: return@forEachIndexed
            
            when {
                firstChar in 'A'..'Z' -> {
                    val section = firstChar.toString()
                    if (!englishSections.contains(section)) {
                        englishSections.add(section)
                        sectionPositions[section] = index
                    }
                }
                firstChar in '0'..'9' -> {
                    val section = "#"
                    if (!numberSections.contains(section)) {
                        numberSections.add(section)
                        if (!sectionPositions.containsKey(section)) {
                            sectionPositions[section] = index
                        }
                    }
                }
                isPersianChar(firstChar) -> {
                    val section = getPersianSection(firstChar)
                    if (!persianSections.contains(section)) {
                        persianSections.add(section)
                        sectionPositions[section] = index
                    }
                }
                else -> {
                    val section = "#"
                    if (!numberSections.contains(section)) {
                        numberSections.add(section)
                        if (!sectionPositions.containsKey(section)) {
                            sectionPositions[section] = index
                        }
                    }
                }
            }
        }
        
        // Build final sections list: English first, then Persian, then numbers
        sections.addAll(englishSections.sorted())
        sections.addAll(persianSections.sorted())
        if (numberSections.isNotEmpty()) {
            sections.add("#")
        }
    }
    
    private fun isPersianChar(char: Char): Boolean {
        return char in '\u0600'..'\u06FF' || char in '\uFB50'..'\uFDFF' || char in '\uFE70'..'\uFEFF'
    }
    
    private fun getPersianSection(char: Char): String {
        // Persian alphabet sections
        val persianAlphabet = "آابپتثجچحخدذرزژسشصضطظعغفقکگلمنوهی"
        val index = persianAlphabet.indexOf(char.lowercaseChar())
        return if (index >= 0) {
            persianAlphabet[index].toString().uppercase()
        } else {
            char.toString().uppercase()
        }
    }
    
    fun getSections(): Array<String> {
        return sections.toTypedArray()
    }
    
    fun getPositionForSection(sectionIndex: Int): Int {
        if (sectionIndex < 0 || sectionIndex >= sections.size) {
            return 0
        }
        val section = sections[sectionIndex]
        return sectionPositions[section] ?: 0
    }
    
    fun getSectionForPosition(position: Int): Int {
        if (position < 0 || position >= contacts.size) {
            return 0
        }
        
        val contact = contacts[position]
        val firstChar = contact.name.firstOrNull()?.uppercaseChar() ?: return sections.size - 1
        
        val section = when {
            firstChar in 'A'..'Z' -> firstChar.toString()
            firstChar in '0'..'9' -> "#"
            isPersianChar(firstChar) -> getPersianSection(firstChar)
            else -> "#"
        }
        
        return sections.indexOf(section).takeIf { it >= 0 } ?: (sections.size - 1)
    }
}

