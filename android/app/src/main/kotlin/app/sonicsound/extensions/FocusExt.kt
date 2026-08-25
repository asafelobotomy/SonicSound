package app.sonicsound.extensions

import android.view.View
import android.view.ViewGroup
import android.view.ViewParent

/** Move D-pad focus into [root]'s first sensible descendant (not the sidebar). */
fun View.requestPrimaryFocus() {
    post {
        val tagged = findViewWithTag<View>("primary_focus")
        val target = tagged ?: findFirstFocusable() ?: this
        target.requestFocus()
    }
}

private fun View.findFirstFocusable(): View? {
    if (isShown && isFocusable) return this
    if (this !is ViewGroup) return null
    for (i in 0 until childCount) {
        getChildAt(i).findFirstFocusable()?.let { return it }
    }
    return null
}

/** Hide/show a sibling view (e.g. sidebar) by walking up the hierarchy. */
fun View.setSiblingVisible(siblingId: Int, visible: Boolean) {
    var p: ViewParent? = parent
    while (p is ViewGroup) {
        val sib = p.findViewById<View>(siblingId)
        if (sib != null) {
            sib.visibility = if (visible) View.VISIBLE else View.GONE
            return
        }
        p = p.parent
    }
}
