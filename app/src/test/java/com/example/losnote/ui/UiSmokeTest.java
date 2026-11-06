package com.example.losnote.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.os.Looper;
import android.view.View;

import com.example.losnote.MainActivity;
import com.example.losnote.R;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;

import com.example.losnote.data.DBHelper;
import com.google.android.material.bottomnavigation.BottomNavigationView;

/** 冒烟：四个 Tab 的布局都能在 Material3 主题下正常膨胀、切换不崩。 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class UiSmokeTest {

    private ActivityController<MainActivity> controller;

    @Before
    public void setUp() throws Exception {
        Field f = DBHelper.class.getDeclaredField("instance");
        f.setAccessible(true);
        f.set(null, null);
        RuntimeEnvironment.getApplication().deleteDatabase("losnote.db");
        controller = Robolectric.buildActivity(MainActivity.class).setup();
    }

    @After
    public void tearDown() {
        controller.close();
    }

    @Test
    public void allTabsInflate() {
        MainActivity activity = controller.get();
        BottomNavigationView nav = activity.findViewById(R.id.bottom_nav);
        assertNotNull(nav);

        int[] tabs = {R.id.nav_quiz, R.id.nav_daily, R.id.nav_add, R.id.nav_bank};
        for (int id : tabs) {
            nav.setSelectedItemId(id);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertEquals(id, nav.getSelectedItemId());
        }

        View container = activity.findViewById(R.id.container);
        assertNotNull(container);
        assertEquals(1, ((android.view.ViewGroup) container).getChildCount() >= 1 ? 1 : 0);
    }

    @Test
    public void bankScreenHasSearchAndChips() {
        MainActivity activity = controller.get();
        BottomNavigationView nav = activity.findViewById(R.id.bottom_nav);
        nav.setSelectedItemId(R.id.nav_bank);
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        assertNotNull(activity.findViewById(R.id.et_search));
        assertNotNull(activity.findViewById(R.id.chips_bank_categories));
        assertNotNull(activity.findViewById(R.id.recycler_bank));
    }

    @Test
    public void quizScreenHasCardAndGradeButtons() {
        MainActivity activity = controller.get();
        BottomNavigationView nav = activity.findViewById(R.id.bottom_nav);
        nav.setSelectedItemId(R.id.nav_quiz);
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        assertNotNull(activity.findViewById(R.id.card_quiz));
        assertNotNull(activity.findViewById(R.id.btn_again));
        assertNotNull(activity.findViewById(R.id.btn_good));
        assertNotNull(activity.findViewById(R.id.btn_easy));
    }
}
