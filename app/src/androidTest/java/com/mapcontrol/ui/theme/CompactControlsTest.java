package com.mapcontrol.ui.theme;

import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.mapcontrol.R;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class CompactControlsTest {
    private Context context() {
        return new ContextThemeWrapper(InstrumentationRegistry.getInstrumentation().getTargetContext(),
                R.style.Theme_MapControl);
    }

    private List<CompoundButton> choices(View view) {
        List<CompoundButton> found = new ArrayList<>();
        if (view instanceof CompoundButton) found.add((CompoundButton) view);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) found.addAll(choices(group.getChildAt(i)));
        }
        return found;
    }

    @Test public void toggleCanBeClearedAndVisualSyncDoesNotCommit() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Context context = context();
            LinearLayout parent = new LinearLayout(context);
            AtomicInteger commits = new AtomicInteger();
            UiStyles.BinarySegmentHandle handle = UiStyles.addBinarySegmentedControl(context, parent,
                    "Automatic start", context.getString(R.string.common_on), context.getString(R.string.common_off),
                    "Starts automatically", "Start manually", false, selected -> commits.incrementAndGet());
            List<CompoundButton> controls = choices(parent);
            assertEquals(1, controls.size());
            CompoundButton button = controls.get(0);
            View card = (View) button.getParent();
            assertTrue(card.isClickable());
            assertFalse(button.isClickable());
            assertFalse(button.isChecked());
            card.performClick();
            assertTrue(button.isChecked());
            assertTrue(handle.isLeftSelected());
            card.performClick();
            assertFalse(button.isChecked());
            assertEquals(2, commits.get());
            handle.syncVisualWithoutCommit(true);
            handle.reapplyTheme();
            assertTrue(button.isChecked());
            assertEquals(2, commits.get());
            handle.setInteractionEnabled(false);
            assertFalse(button.isEnabled());
            card.performClick();
            assertTrue(handle.isLeftSelected());
            assertEquals(2, commits.get());
        });
    }

    @Test public void rejectedPermissionRestoresTheUncheckedCircle() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Context context = context();
            LinearLayout parent = new LinearLayout(context);
            AtomicInteger commits = new AtomicInteger();
            UiStyles.BinarySegmentHandle[] handle = new UiStyles.BinarySegmentHandle[1];
            handle[0] = UiStyles.addBinarySegmentedControl(context, parent, "Overlay",
                    context.getString(R.string.common_on), context.getString(R.string.common_off),
                    "Show controls", "Hide controls", false, enabled -> {
                        commits.incrementAndGet();
                        if (enabled) handle[0].setLeftSelected(false);
                    });
            CompoundButton button = choices(parent).get(0);
            View card = (View) button.getParent();
            card.performClick();
            assertFalse(button.isChecked());
            assertFalse(handle[0].isLeftSelected());
            assertEquals(2, commits.get());
        });
    }

    @Test public void languageChoicesRemainMutuallyExclusive() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Context context = context();
            LinearLayout parent = new LinearLayout(context);
            AtomicInteger commits = new AtomicInteger();
            UiStyles.BinarySegmentHandle handle = UiStyles.addBinarySegmentedControl(context, parent, "Language",
                    context.getString(R.string.settings_language_tr), context.getString(R.string.settings_language_en),
                    "Turkish", "English", true, selected -> commits.incrementAndGet());
            List<CompoundButton> buttons = choices(parent);
            assertEquals(2, buttons.size());
            buttons.get(1).performClick();
            assertFalse(buttons.get(0).isChecked());
            assertTrue(buttons.get(1).isChecked());
            assertEquals(1, commits.get());
            handle.syncVisualWithoutCommit(true);
            assertTrue(buttons.get(0).isChecked());
            assertFalse(buttons.get(1).isChecked());
            assertEquals(1, commits.get());
        });
    }

    @Test public void upstreamTernaryLockDisablesCardsAndRadioButtonsWithoutCommitting() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Context context=context();LinearLayout parent=new LinearLayout(context);
            AtomicInteger commits=new AtomicInteger();
            UiStyles.TernarySegmentHandle handle=UiStyles.addTernarySegmentedControl(context,parent,
                    "Quality",new String[]{"Low","Medium","High"},new String[]{"Small","Balanced","Detailed"},
                    new int[]{0,1,2},0,value->commits.incrementAndGet());
            List<CompoundButton> buttons=choices(parent);assertEquals(3,buttons.size());
            handle.setInteractionEnabled(false);
            for(CompoundButton button:buttons){
                assertFalse(button.isEnabled());
                View card=(View)button.getParent();assertFalse(card.isEnabled());
                card.performClick();button.performClick();
            }
            assertEquals(0,commits.get());assertEquals(0,handle.getSelectedModeId());
            handle.syncVisualFromModeId(1);assertEquals(1,handle.getSelectedModeId());assertEquals(0,commits.get());
            handle.setInteractionEnabled(true);
            assertTrue(buttons.get(2).isEnabled());buttons.get(2).performClick();
            assertEquals(2,handle.getSelectedModeId());assertEquals(1,commits.get());
        });
    }

    private void layoutGrid(FeatureGridLayout grid, Context context, int widthDp) {
        int width = Math.round(widthDp * context.getResources().getDisplayMetrics().density);
        grid.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.AT_MOST));
        grid.layout(0, 0, width, grid.getMeasuredHeight());
        for (int i = 0; i < grid.getChildCount(); i++) {
            View child = grid.getChildAt(i);
            if (child.getVisibility() == View.GONE) continue;
            assertTrue(child.getWidth() > 0);
            assertTrue(child.getLeft() >= 0);
            assertTrue(child.getRight() <= width);
        }
    }

    @Test public void gridReflowsFromTwoColumnsToOneWithoutChangingOrder() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Context context = context();
            FeatureGridLayout grid = new FeatureGridLayout(context);
            for (int i = 0; i < 4; i++) {
                TextView card = new TextView(context);
                card.setText("Feature " + i);
                card.setMinimumHeight(100);
                grid.addCard(card);
            }
            for (int width : new int[]{1200, 400, 1200, 400, 1200}) {
                layoutGrid(grid, context, width);
                assertEquals(width >= 840 ? 2 : 1, grid.getColumnCount());
                if (width >= 840) {
                    assertEquals(grid.getChildAt(0).getTop(), grid.getChildAt(1).getTop());
                    assertTrue(grid.getChildAt(1).getLeft() > grid.getChildAt(0).getLeft());
                } else {
                    assertTrue(grid.getChildAt(1).getTop() > grid.getChildAt(0).getTop());
                }
                assertEquals("Feature 1", ((TextView) grid.getChildAt(1)).getText().toString());
            }

            // Filtering and newly added cards must also invalidate cached spans.
            grid.getChildAt(1).setVisibility(View.GONE);
            for (int width : new int[]{1200, 400, 1200}) {
                layoutGrid(grid, context, width);
                if (width >= 840) {
                    assertEquals(grid.getChildAt(0).getTop(), grid.getChildAt(2).getTop());
                    assertTrue(grid.getChildAt(2).getLeft() > grid.getChildAt(0).getLeft());
                } else {
                    assertTrue(grid.getChildAt(2).getTop() > grid.getChildAt(0).getTop());
                }
            }
            TextView added = new TextView(context);
            added.setText("Feature 4");
            added.setMinimumHeight(100);
            grid.addCard(added);
            grid.getChildAt(1).setVisibility(View.VISIBLE);
            for (int width : new int[]{400, 1200, 400}) {
                layoutGrid(grid, context, width);
                assertEquals(5, grid.getChildCount());
                assertTrue(added.getTop() > grid.getChildAt(3).getTop());
            }
            for (int i = 0; i < grid.getChildCount(); i++) grid.getChildAt(i).setVisibility(View.GONE);
            layoutGrid(grid, context, 1200);
            layoutGrid(grid, context, 400);
            for (int i = 0; i < grid.getChildCount(); i++) grid.getChildAt(i).setVisibility(View.VISIBLE);
            layoutGrid(grid, context, 1200);
            assertEquals("Feature 4", ((TextView) grid.getChildAt(4)).getText().toString());
            grid.removeView(added);
            layoutGrid(grid, context, 1200);
            assertEquals(2, grid.getRowCount());
            grid.removeAllViews();
            layoutGrid(grid, context, 400);
            assertEquals(1, grid.getRowCount());
        });
    }
}
