package com.example.losnote.ui;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Looper;
import android.view.View;
import android.view.View.MeasureSpec;

import com.example.losnote.MainActivity;
import com.example.losnote.R;
import com.example.losnote.data.DBHelper;
import com.example.losnote.data.DailyLog;
import com.example.losnote.data.Note;
import com.example.losnote.data.Srs;
import com.google.android.material.bottomnavigation.BottomNavigationView;

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
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;

/**
 * 用 Robolectric 原生图形把四个页面渲染成 PNG，方便肉眼检查 UI。
 * 不参与常规断言，只产出 build/ui-shots/*.png。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class UiShotTest {

    private ActivityController<MainActivity> controller;

    @Before
    public void setUp() throws Exception {
        Field f = DBHelper.class.getDeclaredField("instance");
        f.setAccessible(true);
        f.set(null, null);
        RuntimeEnvironment.getApplication().deleteDatabase("losnote.db");

        DBHelper db = DBHelper.get(RuntimeEnvironment.getApplication());
        long now = System.currentTimeMillis();
        String[][] seed = {
                {"TCP 三次握手为什么不能是两次？", "两次握手时，服务端无法确认客户端是否收到了自己的 SYN+ACK：若客户端的 ACK 丢失，服务端会一直维持一条早已无效的连接。三次握手让双方都确认了彼此的发送与接收能力。", "计算机基础"},
                {"MVCC 是怎么做到读写不阻塞的？", "每一行保留多个版本，读操作看到的是事务开始时的快照，写操作生成新版本而不覆盖旧版本，因此读写互不阻塞。", "计算机基础"},
                {"std::move 真的移动了什么吗？", "它只是一个右值引用转换，不搬数据；真正的搬迁发生在移动构造/移动赋值里。", "C++"},
                {"为什么简历上要写量化结果？", "面试官记不住形容词，只记得住数字：把“提升了性能”换成“P99 从 320ms 降到 45ms”。", "好句与话术"},
                {"什么是 MECE？", "相互独立、完全穷尽：拆问题时不重不漏，是结构化表达的基本功。", "思维模型"},
                {"Rust 的 Option 和 C++ 的 std::optional 区别？", "Option 是枚举，可以用 match 穷尽处理；std::optional 本质是带标志位的存储，容易漏判空。", "其他技术"},
        };
        for (String[] s : seed) {
            Note n = new Note(s[0], s[1], s[2]);
            db.insert(n);
        }
        java.util.List<Note> all = db.queryAll();
        if (!all.isEmpty()) {
            Note n = all.get(0);
            n.stars += Srs.GOOD + 1;
            Srs.apply(n, Srs.GOOD);
            db.saveReview(n, Srs.GOOD);
        }
        db.insertDailyLog("把 TCP 三次握手讲给自己听了一遍，卡在“为什么不能两次”上，回头补了笔记。");
        db.insertDailyLog("刷了 20 道 C++ 八股，把 std::move 的坑记进题库。");

        controller = Robolectric.buildActivity(MainActivity.class).setup();
    }

    @After
    public void tearDown() {
        controller.close();
    }

    @Test
    public void renderAllScreens() throws Exception {
        MainActivity a = controller.get();
        capture(a, "nav_bank", "01-bank", null);
        capture(a, "nav_quiz", "02-quiz", this::showFirstNote);
        capture(a, "nav_quiz", "03-quiz-answer", () -> {
            this.showFirstNote();
            View card = controller.get().findViewById(R.id.card_quiz);
            card.performClick();
        });
        capture(a, "nav_add", "04-add", null);
        capture(a, "nav_daily", "05-daily", null);
    }

    private void showFirstNote() {
        MainActivity a = controller.get();
        java.util.List<com.example.losnote.data.Note> notes =
                DBHelper.get(a).queryAll();
        if (notes.isEmpty()) {
            return;
        }
        for (androidx.fragment.app.Fragment f : a.getSupportFragmentManager().getFragments()) {
            if (f instanceof QuizFragment) {
                ((QuizFragment) f).showCurrentNote(notes.get(0));
                return;
            }
        }
    }

    private void capture(MainActivity a, String navRes, String name, Runnable before) throws Exception {
        BottomNavigationView nav = a.findViewById(R.id.bottom_nav);
        int id = a.getResources().getIdentifier(navRes, "id", a.getPackageName());
        nav.setSelectedItemId(id);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        if (before != null) {
            before.run();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
        }

        View root = a.getWindow().getDecorView();
        int w = 1080, h = 2280;
        root.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
        root.layout(0, 0, w, h);
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        root.draw(canvas);

        File out = new File("build/ui-shots/" + name + ".png");
        File dir = out.getParentFile();
        if (dir != null && !dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("cannot create " + dir);
        }
        try (FileOutputStream fos = new FileOutputStream(out)) {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
        }
        System.out.println("[ui-shot] " + out.getAbsolutePath() + " "
                + bmp.getWidth() + "x" + bmp.getHeight());
    }
}
