package com.pixel.pokecollect.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class ReflowTest {
    @Test
    fun joinsTheLinesOfAParagraph() {
        val wrapped = """
            By using, reproducing, modifying, distributing, performing or displaying any portion
            or element of Gemma, you agree to be bound by this Agreement.

            Next paragraph.
        """.trimIndent()
        assertEquals(
            "By using, reproducing, modifying, distributing, performing or displaying any portion " +
                "or element of Gemma, you agree to be bound by this Agreement.\n\nNext paragraph.",
            reflow(wrapped),
        )
    }

    @Test
    fun keepsHeadingsSeparatorsAndLists() {
        val text = """
            PokeCollect
            Copyright 2026 Kevin Desai

            Gemma
            -----
            This license is copied below, and is also available with a FAQ at:
            http://scripts.sil.org/OFL
            Model Derivatives include all of the following kinds of works, among them:
              - modifications to Gemma, and works based on Gemma, whatever their format
              - any other machine learning model created by transfer of patterns
        """.trimIndent()
        assertEquals(text, reflow(text))
    }

    @Test
    fun dropsTheIndentOfContinuationLines() {
        val apache = "      (a) You must give any other recipients of the Work or Derivative Works\n" +
            "          a copy of this License; and"
        assertEquals(
            "      (a) You must give any other recipients of the Work or Derivative Works a copy of this License; and",
            reflow(apache),
        )
    }
}
