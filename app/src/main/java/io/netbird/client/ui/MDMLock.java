package io.netbird.client.ui;

import android.content.Context;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import io.netbird.client.R;

/**
 * How a screen shows that an administrator decided a setting.
 *
 * One convention, applied everywhere, so the app cannot drift into several
 * answers to the same situation:
 * <ul>
 *   <li>a feature the policy switches off entirely disappears — there is nothing
 *       to explain about a screen the organisation does not offer;</li>
 *   <li>a single managed setting stays visible but locked, with a line under it
 *       saying who decided it. A control that silently vanishes reads as a bug;
 *       one that is there and greyed out explains itself.</li>
 * </ul>
 *
 * The locking is done in code rather than in the layouts because the same rows
 * are ordinary, editable settings on an unmanaged device, which is the common
 * case.
 */
public final class MDMLock {

    private static final float DIMMED = 0.5f;
    private static final String NOTICE_TAG = "mdm_managed_notice";

    private MDMLock() {
    }

    /**
     * Locks a settings row: the row stops responding, the controls in it are
     * disabled, and a line naming the organisation is added underneath.
     *
     * @param row      the tappable row, whose click listener is dropped
     * @param controls the switches, fields or buttons inside it
     */
    public static void lock(View row, View... controls) {
        if (row == null) {
            return;
        }
        row.setOnClickListener(null);
        row.setClickable(false);
        row.setFocusable(false);
        row.setAlpha(DIMMED);
        disable(controls);
        addNotice(row);
    }

    /**
     * Locks controls that do not sit in a row of their own — a text field with
     * its save button, say — and explains it underneath the last of them.
     */
    public static void lockControls(View... controls) {
        disable(controls);
        if (controls.length > 0) {
            addNotice(controls[controls.length - 1]);
        }
    }

    /**
     * Hides a row the policy takes away, along with the divider that follows it,
     * which the layouts include without an id of its own.
     */
    public static void hide(View row) {
        if (row == null) {
            return;
        }
        row.setVisibility(View.GONE);
        View next = nextSibling(row);
        // Only a bare View: every other row and section header is some subclass,
        // so this cannot swallow the heading of the section that comes next.
        if (next != null && next.getClass() == View.class) {
            next.setVisibility(View.GONE);
        }
    }

    private static void disable(View... controls) {
        for (View control : controls) {
            if (control != null) {
                control.setEnabled(false);
                control.setAlpha(DIMMED);
            }
        }
    }

    /**
     * Adds the "managed by your organization" line under a view.
     *
     * Only where the layout is a vertical column, which is what the settings
     * screens are; anywhere else the caption is left out rather than dropped into
     * a layout that would place it somewhere surprising.
     */
    private static void addNotice(View anchor) {
        ViewParent parent = anchor.getParent();
        if (!(parent instanceof LinearLayout)) {
            return;
        }
        LinearLayout column = (LinearLayout) parent;
        if (column.getOrientation() != LinearLayout.VERTICAL) {
            return;
        }
        int at = column.indexOfChild(anchor) + 1;
        if (at < column.getChildCount() && NOTICE_TAG.equals(column.getChildAt(at).getTag())) {
            // Already explained: the screens re-apply the policy on every resume.
            return;
        }

        Context context = column.getContext();
        TextView notice = new TextView(context);
        notice.setTag(NOTICE_TAG);
        notice.setText(R.string.mdm_managed_by_organization);
        notice.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        notice.setTextColor(ContextCompat.getColor(context, R.color.nb_txt_light));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        int side = dp(context, 20);
        params.setMargins(side, dp(context, 6), side, dp(context, 8));
        notice.setLayoutParams(params);

        column.addView(notice, at);
    }

    private static View nextSibling(View view) {
        ViewParent parent = view.getParent();
        if (!(parent instanceof ViewGroup)) {
            return null;
        }
        ViewGroup group = (ViewGroup) parent;
        int at = group.indexOfChild(view) + 1;
        return at < group.getChildCount() ? group.getChildAt(at) : null;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
