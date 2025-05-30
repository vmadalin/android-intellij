/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.gradle.dcl.lang.ide

import com.android.tools.idea.gradle.dcl.lang.ide.color.DeclarativeColor
import com.android.tools.idea.gradle.dcl.lang.lexer.DeclarativeHighlightingLexer
import com.android.tools.idea.gradle.dcl.lang.parser.DeclarativeElementTypeHolder.BLOCK_COMMENT
import com.android.tools.idea.gradle.dcl.lang.parser.DeclarativeElementTypeHolder.BOOLEAN
import com.android.tools.idea.gradle.dcl.lang.parser.DeclarativeElementTypeHolder.DOUBLE_LITERAL
import com.android.tools.idea.gradle.dcl.lang.parser.DeclarativeElementTypeHolder.INTEGER_LITERAL
import com.android.tools.idea.gradle.dcl.lang.parser.DeclarativeElementTypeHolder.LINE_COMMENT
import com.android.tools.idea.gradle.dcl.lang.parser.DeclarativeElementTypeHolder.LONG_LITERAL
import com.android.tools.idea.gradle.dcl.lang.parser.DeclarativeElementTypeHolder.MULTILINE_STRING_LITERAL
import com.android.tools.idea.gradle.dcl.lang.parser.DeclarativeElementTypeHolder.NULL
import com.android.tools.idea.gradle.dcl.lang.parser.DeclarativeElementTypeHolder.ONE_LINE_STRING_LITERAL
import com.android.tools.idea.gradle.dcl.lang.parser.DeclarativeElementTypeHolder.UNSIGNED_INTEGER
import com.android.tools.idea.gradle.dcl.lang.parser.DeclarativeElementTypeHolder.UNSIGNED_LONG
import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.StringEscapesTokenTypes.INVALID_CHARACTER_ESCAPE_TOKEN
import com.intellij.psi.StringEscapesTokenTypes.INVALID_UNICODE_ESCAPE_TOKEN
import com.intellij.psi.StringEscapesTokenTypes.VALID_STRING_ESCAPE_TOKEN
import com.intellij.psi.tree.IElementType

class DeclarativeHighlighter : SyntaxHighlighterBase() {
  override fun getHighlightingLexer(): Lexer = DeclarativeHighlightingLexer()


  override fun getTokenHighlights(tokenType: IElementType): Array<out TextAttributesKey> {
    return pack(tokenMap[tokenType]?.textAttributesKey)
  }

  private val tokenMap: Map<IElementType, DeclarativeColor> = HashMap<IElementType, DeclarativeColor>().apply {
    put(LINE_COMMENT, DeclarativeColor.COMMENT)
    put(BLOCK_COMMENT, DeclarativeColor.BLOCK_COMMENT)

    put(ONE_LINE_STRING_LITERAL, DeclarativeColor.STRING)
    put(MULTILINE_STRING_LITERAL, DeclarativeColor.STRING)
    put(BOOLEAN, DeclarativeColor.BOOLEAN)
    put(NULL, DeclarativeColor.NULL)

    put(INTEGER_LITERAL, DeclarativeColor.NUMBER)
    put(LONG_LITERAL, DeclarativeColor.NUMBER)
    put(UNSIGNED_LONG, DeclarativeColor.NUMBER)
    put(UNSIGNED_INTEGER, DeclarativeColor.NUMBER)
    put(DOUBLE_LITERAL, DeclarativeColor.NUMBER)

    put(INVALID_CHARACTER_ESCAPE_TOKEN, DeclarativeColor.INVALID_STRING_ESCAPE)
    put(INVALID_UNICODE_ESCAPE_TOKEN, DeclarativeColor.INVALID_STRING_ESCAPE)
    put(VALID_STRING_ESCAPE_TOKEN, DeclarativeColor.VALID_STRING_ESCAPE)
  }
}

