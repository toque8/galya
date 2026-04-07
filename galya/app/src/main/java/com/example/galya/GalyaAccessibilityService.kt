package com.example.galya

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class GalyaAccessibilityService : AccessibilityService() {

    companion object {
        var instance: GalyaAccessibilityService? = null
    }

    override fun onServiceConnected() {
        instance = this
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            // Убираем FLAG_REQUEST_TOUCH_EXPLORATION_MODE – он ломает тачскрин
            flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        this.serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // не обязательная обработка
    }

    override fun onInterrupt() {
        // обязательный метод
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    // ============ Методы для взаимодействия ============

    fun findNodeByText(text: String, partial: Boolean = true): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        val nodes = if (partial) {
            root.findAccessibilityNodeInfosByText(text)
        } else {
            root.findAccessibilityNodeInfosByText(".*$text.*".toRegex().pattern)
        }
        return nodes?.firstOrNull()
    }

    fun findNodeById(id: String): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        val nodes = root.findAccessibilityNodeInfosByViewId(id)
        return nodes?.firstOrNull()
    }

    fun performClick(node: AccessibilityNodeInfo): Boolean {
        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    fun inputText(node: AccessibilityNodeInfo, text: String): Boolean {
        val arguments = android.os.Bundle()
        arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }

    fun goBack(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_BACK)
    }

    fun openNotifications(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
    }

    fun launchApp(packageName: String): Boolean {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            return true
        }
        return false
    }

    fun getWindowText(): String {
        val root = rootInActiveWindow ?: return ""
        val textBuilder = StringBuilder()
        traverseNode(root, textBuilder)
        return textBuilder.toString()
    }

    fun findNodeByContentDesc(desc: String): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        return findNodeByContentDescRecursive(root, desc)
    }

    private fun findNodeByContentDescRecursive(node: AccessibilityNodeInfo, desc: String): AccessibilityNodeInfo? {
        if (node.contentDescription != null && node.contentDescription.toString().contains(desc, ignoreCase = true)) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                val found = findNodeByContentDescRecursive(child, desc)
                if (found != null) return found
                child.recycle()
            }
        }
        return null
    }

    fun findNodeByHint(hint: String): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        return findNodeByHintRecursive(root, hint)
    }

    private fun findNodeByHintRecursive(node: AccessibilityNodeInfo, hint: String): AccessibilityNodeInfo? {
        if (node.hintText != null && node.hintText.toString().contains(hint, ignoreCase = true)) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                val found = findNodeByHintRecursive(child, hint)
                if (found != null) return found
                child.recycle()
            }
        }
        return null
    }

    fun findNodeByClassName(className: String): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        return findNodeByClassNameRecursive(root, className)
    }

    private fun findNodeByClassNameRecursive(node: AccessibilityNodeInfo, className: String): AccessibilityNodeInfo? {
        if (node.className?.toString()?.contains(className, ignoreCase = true) == true) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                val found = findNodeByClassNameRecursive(child, className)
                if (found != null) return found
                child.recycle()
            }
        }
        return null
    }

    fun getFocusedNode(): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        return root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
    }

    fun pressEnter(): Boolean {
        val possibleTexts = listOf("Перейти", "Найти", "Поиск", "Go", "Search", "Enter", "Готово", "Далее")
        for (btnText in possibleTexts) {
            val node = findNodeByText(btnText)
            if (node != null && performClick(node)) return true
        }
        val iconButton = findNodeByClassName("android.widget.ImageButton")
        if (iconButton != null && performClick(iconButton)) return true
        return false
    }

    private fun traverseNode(node: AccessibilityNodeInfo, builder: StringBuilder) {
        if (node.text != null) {
            builder.append(node.text).append("\n")
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                traverseNode(child, builder)
                child.recycle()
            }
        }
    }
}