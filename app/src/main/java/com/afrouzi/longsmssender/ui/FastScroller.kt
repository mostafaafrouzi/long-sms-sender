package com.afrouzi.longsmssender.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import android.widget.SectionIndexer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.max
import kotlin.math.min

class FastScroller(
    private val recyclerView: RecyclerView,
    private val context: Context
) : RecyclerView.ItemDecoration() {
    
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF888888.toInt()
        textSize = 32f
        textAlign = Paint.Align.CENTER
    }
    
    private var isDragging = false
    private var lastY = 0f
    
    init {
        recyclerView.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                if (e.x > rv.width - 100) {
                    handleTouch(e)
                    return true
                }
                return false
            }
            
            override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
                if (e.x > rv.width - 100) {
                    handleTouch(e)
                }
            }
        })
    }
    
    private fun handleTouch(e: MotionEvent) {
        when (e.action) {
            MotionEvent.ACTION_DOWN -> {
                isDragging = true
                lastY = e.y
                scrollToPosition(e.y)
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    lastY = e.y
                    scrollToPosition(e.y)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
            }
        }
    }
    
    private fun scrollToPosition(y: Float) {
        val adapter = recyclerView.adapter as? SectionIndexer ?: return
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
        
        val sections = adapter.sections
        if (sections.isEmpty()) return
        
        val thumbHeight = recyclerView.height / sections.size.toFloat()
        val sectionIndex = (y / thumbHeight).toInt().coerceIn(0, sections.size - 1)
        
        val position = adapter.getPositionForSection(sectionIndex)
        layoutManager.scrollToPositionWithOffset(position, 0)
    }
    
    override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        super.onDrawOver(c, parent, state)
        
        val adapter = parent.adapter as? SectionIndexer ?: return
        val sections = adapter.sections
        if (sections.isEmpty()) return
        
        val width = parent.width
        val height = parent.height
        val sectionHeight = height / sections.size.toFloat()
        
        sections.forEachIndexed { index, section ->
            val sectionStr = section as? String ?: return@forEachIndexed
            val y = sectionHeight * index + sectionHeight / 2
            val textBounds = Rect()
            paint.getTextBounds(sectionStr, 0, sectionStr.length, textBounds)
            
            c.drawText(
                sectionStr,
                width - 50f,
                y + textBounds.height() / 2f,
                paint
            )
        }
    }
}

