package com.example.losnote.ui;

import io.noties.prism4j.annotations.PrismBundle;

/**
 * Prism4j 语法定义。注解处理器会据此生成语法包。
 * 包含常见语言：clike(c/cpp 基础)、cpp、java、python、javascript 等。
 */
@PrismBundle(
        include = {"clike", "c", "cpp", "java", "python", "javascript", "json", "kotlin"},
        grammarLocatorClassName = ".GrammarLocatorImpl"
)
public class PrismGrammar {
}
