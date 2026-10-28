package com.freelancerheaven.timetracker;

import android.content.Context;
import android.os.Bundle;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.LinearLayout;

/** Clickable project surface. Child buttons retain their own click targets. */
final class ProjectCard extends LinearLayout {
    private boolean expandable;
    private boolean expanded;

    ProjectCard(Context context) {
        super(context);
        setClickable(true);
        setFocusable(true);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    /** The supplied description is localized and includes the project and action. */
    void setExpansionState(boolean canExpand, boolean isExpanded, CharSequence description) {
        expandable = canExpand;
        expanded = isExpanded;
        setContentDescription(description);
    }

    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName("android.widget.Button");
        if (expandable) {
            info.addAction(expanded ? AccessibilityNodeInfo.AccessibilityAction.ACTION_COLLAPSE
                    : AccessibilityNodeInfo.AccessibilityAction.ACTION_EXPAND);
        }
    }

    @Override public boolean performAccessibilityAction(int action, Bundle args) {
        if (expandable && ((!expanded && action == AccessibilityNodeInfo.ACTION_EXPAND)
                || (expanded && action == AccessibilityNodeInfo.ACTION_COLLAPSE))) {
            return performClick();
        }
        return super.performAccessibilityAction(action, args);
    }
}
