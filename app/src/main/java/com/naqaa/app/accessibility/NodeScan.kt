package com.naqaa.app.accessibility

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Reads the smallest useful shape of one screen: identifiers, short labels, and the text
 * of an address field when the package is a known browser.
 *
 * The walk is bounded in breadth and depth, labels are cut to sixty four characters, and a
 * long text is dropped rather than kept. Nodes are not recycled: the call is deprecated and
 * has been a no-op since Android 13, so calling it would only add branches that no device
 * exercises.
 */
object NodeScan {

    data class Bounds(
        val maxNodes: Int = 300,
        val maxDepth: Int = 8,
        val maxTexts: Int = 80,
        val maxTextLength: Int = 64,
        val maxAddressLength: Int = 256
    )

    fun facts(
        root: AccessibilityNodeInfo?,
        packageName: String,
        addressIds: List<String>,
        bounds: Bounds = Bounds()
    ): ScreenFacts {
        if (root == null) return ScreenFacts(packageName, emptySet(), emptyList(), null, false)
        val ids = LinkedHashSet<String>()
        val texts = ArrayList<String>(bounds.maxTexts)
        var address: String? = null
        var editText = false

        val pending = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        pending.add(root to 0)
        var visited = 0
        while (pending.isNotEmpty() && visited < bounds.maxNodes) {
            val (node, depth) = pending.removeFirst()
            visited += 1

            val id = node.viewIdResourceName?.substringAfterLast('/')
            if (!id.isNullOrEmpty()) {
                ids.add(id)
                if (address == null && addressIds.any { id.endsWith(it) }) {
                    node.text?.toString()?.trim()?.take(bounds.maxAddressLength)?.let { text ->
                        if (text.isNotEmpty()) address = text
                    }
                }
            }
            val className = node.className?.toString().orEmpty()
            if (className.endsWith("EditText")) editText = true

            val label = (node.text ?: node.contentDescription)?.toString()?.trim()
            if (!label.isNullOrEmpty() && label.length <= bounds.maxTextLength && texts.size < bounds.maxTexts) {
                texts.add(label)
            }

            if (depth < bounds.maxDepth) {
                for (index in 0 until node.childCount) {
                    val child = node.getChild(index) ?: continue
                    pending.add(child to depth + 1)
                }
            }
        }
        return ScreenFacts(
            packageName = packageName,
            viewIds = ids,
            texts = texts,
            addressText = address,
            containsEditText = editText
        )
    }
}
