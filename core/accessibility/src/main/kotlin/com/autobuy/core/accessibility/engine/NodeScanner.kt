package com.autobuy.core.accessibility.engine

import android.view.accessibility.AccessibilityNodeInfo
import com.autobuy.core.data.model.NodeSelector
import com.autobuy.core.data.model.SelectorType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * UI 노드 탐색 엔진.
 *
 * 다중 Selector 전략을 순서대로 시도하여 타겟 노드를 탐색합니다.
 * Fallback 순서: resourceId → text → textContains → contentDesc → className → bounds
 */
@Singleton
class NodeScanner @Inject constructor() {

    /**
     * 다중 셀렉터 목록을 순서대로 시도하여 첫 번째로 매칭되는 노드를 반환합니다.
     */
    fun findNode(root: AccessibilityNodeInfo, selectors: List<NodeSelector>): AccessibilityNodeInfo? {
        for (selector in selectors) {
            val node = findNodeBySelector(root, selector)
            if (node != null) return node
        }
        return null
    }

    /**
     * 단일 셀렉터로 노드를 탐색합니다.
     */
    fun findNodeBySelector(root: AccessibilityNodeInfo, selector: NodeSelector): AccessibilityNodeInfo? {
        val candidates = when (selector.type) {
            SelectorType.RESOURCE_ID -> root.findAccessibilityNodeInfosByViewId(selector.value)
            SelectorType.TEXT -> root.findAccessibilityNodeInfosByText(selector.value)
                .filter { it.text?.toString() == selector.value }
            SelectorType.TEXT_CONTAINS -> root.findAccessibilityNodeInfosByText(selector.value)
            SelectorType.CONTENT_DESC -> root.findAccessibilityNodeInfosByText(selector.value)
                .filter { it.contentDescription?.toString()?.contains(selector.value) == true }
            SelectorType.CLASS_NAME -> findNodesByClassName(root, selector.value)
            SelectorType.XPATH_LIKE -> findNodeByXPathLike(root, selector.value)
            SelectorType.BOUNDS -> findNodeByBounds(root, selector.value)
        }

        // 부모 클래스 필터링
        val filtered = if (selector.parentClass != null) {
            candidates.filter { hasParentWithClass(it, selector.parentClass) }
        } else {
            candidates
        }

        return filtered.getOrNull(selector.index)
    }

    /**
     * 지정된 시간 안에 노드가 나타날 때까지 대기합니다.
     *
     * @param timeoutMs 최대 대기 시간 (밀리초)
     * @param pollIntervalMs 폴링 간격
     */
    suspend fun waitForNode(
        rootProvider: () -> AccessibilityNodeInfo?,
        selectors: List<NodeSelector>,
        timeoutMs: Long = 10_000L,
        pollIntervalMs: Long = 200L
    ): AccessibilityNodeInfo? = withContext(Dispatchers.Default) {
        withTimeoutOrNull(timeoutMs) {
            var found: AccessibilityNodeInfo? = null
            while (found == null) {
                val root = rootProvider() ?: run {
                    delay(pollIntervalMs)
                    return@withTimeoutOrNull null
                }
                found = findNode(root, selectors)
                if (found == null) delay(pollIntervalMs)
            }
            found
        }
    }

    /**
     * 지정된 시간 안에 노드가 사라질 때까지 대기합니다. (대기열 해소 감지)
     */
    suspend fun waitForNodeDisappear(
        rootProvider: () -> AccessibilityNodeInfo?,
        selectors: List<NodeSelector>,
        timeoutMs: Long = Long.MAX_VALUE,
        pollIntervalMs: Long = 500L
    ): Boolean = withContext(Dispatchers.Default) {
        withTimeoutOrNull(timeoutMs) {
            var found = true
            while (found) {
                val root = rootProvider()
                if (root == null) break
                found = findNode(root, selectors) != null
                if (found) delay(pollIntervalMs)
            }
            !found
        } ?: false
    }

    private fun findNodesByClassName(root: AccessibilityNodeInfo, className: String): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        traverseTree(root) { node ->
            if (node.className?.toString() == className) result.add(node)
        }
        return result
    }

    /**
     * XPath-like 경로 탐색. 형식: "LinearLayout/TextView[0]"
     */
    private fun findNodeByXPathLike(root: AccessibilityNodeInfo, path: String): List<AccessibilityNodeInfo> {
        val segments = path.split("/")
        var currentNodes = listOf(root)

        for (segment in segments) {
            val indexMatch = Regex("""(.+)\[(\d+)]""").find(segment)
            val (className, index) = if (indexMatch != null) {
                Pair(indexMatch.groupValues[1], indexMatch.groupValues[2].toInt())
            } else {
                Pair(segment, 0)
            }

            currentNodes = currentNodes.flatMap { node ->
                val children = (0 until node.childCount).mapNotNull { node.getChild(it) }
                children.filter { it.className?.toString()?.endsWith(className) == true }
            }
        }

        return currentNodes
    }

    private fun findNodeByBounds(root: AccessibilityNodeInfo, boundsStr: String): List<AccessibilityNodeInfo> {
        // 형식: "left,top,right,bottom" (예: "0,100,1080,200")
        return try {
            val parts = boundsStr.split(",").map { it.trim().toInt() }
            val targetBounds = android.graphics.Rect(parts[0], parts[1], parts[2], parts[3])
            val result = mutableListOf<AccessibilityNodeInfo>()
            traverseTree(root) { node ->
                val bounds = android.graphics.Rect()
                node.getBoundsInScreen(bounds)
                if (bounds.intersect(targetBounds)) result.add(node)
            }
            result
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun hasParentWithClass(node: AccessibilityNodeInfo, className: String): Boolean {
        var current = node.parent
        while (current != null) {
            if (current.className?.toString() == className) return true
            current = current.parent
        }
        return false
    }

    private fun traverseTree(node: AccessibilityNodeInfo, visitor: (AccessibilityNodeInfo) -> Unit) {
        visitor(node)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            traverseTree(child, visitor)
        }
    }
}
