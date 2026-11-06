package com.example.losnote;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.example.losnote.data.Note;
import com.example.losnote.ui.AddFragment;
import com.example.losnote.ui.BankFragment;
import com.example.losnote.ui.DailyFragment;
import com.example.losnote.ui.QuizFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

/** 主界面：底部导航 + Fragment 容器。默认首页为抽查。 */
public class MainActivity extends AppCompatActivity {

    private final Fragment quizFragment = new QuizFragment();
    private final Fragment dailyFragment = new DailyFragment();
    private final Fragment addFragment = new AddFragment();
    private final Fragment bankFragment = new BankFragment();
    private Fragment current = quizFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getSupportFragmentManager().beginTransaction()
                .add(R.id.container, bankFragment, "bank").hide(bankFragment)
                .add(R.id.container, addFragment, "add").hide(addFragment)
                .add(R.id.container, dailyFragment, "daily").hide(dailyFragment)
                .add(R.id.container, quizFragment, "quiz")
                .commit();

        BottomNavigationView nav = findViewById(R.id.bottom_nav);
        nav.setSelectedItemId(R.id.nav_quiz);
        nav.setOnItemSelectedListener(this::onNavItemSelected);
    }

    public void openQuizWithNote(Note note) {
        BottomNavigationView nav = findViewById(R.id.bottom_nav);
        if (nav.getSelectedItemId() != R.id.nav_quiz) {
            nav.setSelectedItemId(R.id.nav_quiz);
        } else {
            ((QuizFragment) quizFragment).refreshOnShow();
        }
        ((QuizFragment) quizFragment).showCurrentNote(note);
    }

    private boolean onNavItemSelected(@NonNull android.view.MenuItem item) {
        Fragment target;
        int id = item.getItemId();
        if (id == R.id.nav_quiz) {
            target = quizFragment;
            ((QuizFragment) quizFragment).refreshOnShow();
        } else if (id == R.id.nav_daily) {
            target = dailyFragment;
            ((DailyFragment) dailyFragment).refreshOnShow();
        } else if (id == R.id.nav_add) {
            target = addFragment;
        } else if (id == R.id.nav_bank) {
            target = bankFragment;
            ((BankFragment) bankFragment).refreshOnShow();
        } else {
            return false;
        }
        if (target != current) {
            getSupportFragmentManager().beginTransaction()
                    .hide(current).show(target).commit();
            current = target;
        }
        return true;
    }
}
