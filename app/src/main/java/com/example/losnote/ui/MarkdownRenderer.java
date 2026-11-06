package com.example.losnote.ui;

import android.content.Context;

import io.noties.markwon.Markwon;
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin;
import io.noties.markwon.ext.tables.TablePlugin;
import io.noties.markwon.syntax.Prism4jThemeDarkula;
import io.noties.markwon.syntax.Prism4jThemeDefault;
import io.noties.markwon.syntax.SyntaxHighlightPlugin;
import io.noties.prism4j.Prism4j;

/**
 * 统一构建带插件的 Markwon 实例：
 * - 代码语法高亮（Prism4j，支持 c/cpp/java/python 等）
 * - 表格
 * - 删除线
 *
 * 抽查页与题库页共用，保证渲染风格一致。
 */
public final class MarkdownRenderer {

    private MarkdownRenderer() {
    }

    public static Markwon create(Context context) {
        // GrammarLocatorImpl 由 prism4j-bundler 注解处理器在编译期生成
        Prism4j prism4j = new Prism4j(new GrammarLocatorImpl());
        // 根据系统当前是否深色模式选主题
        boolean night = (context.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;

        SyntaxHighlightPlugin syntaxPlugin = SyntaxHighlightPlugin.create(
                prism4j,
                night ? Prism4jThemeDarkula.create() : Prism4jThemeDefault.create());

        return Markwon.builder(context)
                .usePlugin(syntaxPlugin)
                .usePlugin(TablePlugin.create(context))
                .usePlugin(StrikethroughPlugin.create())
                .build();
    }
}
