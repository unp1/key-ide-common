/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.ide.key;

import java.awt.image.BufferedImage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers what a dark theme does to KeY's dark glyphs.
 * <p>
 * KeY draws two of its states as a black question mark, which a dark background swallows.
 * Inverting it has to keep the shape, which is what the transparent pixels are.
 */
class GlyphsTest {

    @Test
    void aBlackGlyphBecomesWhite() {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0xFF000000);

        assertEquals(0xFFFFFFFF, Glyphs.inverted(image).getRGB(0, 0));
    }

    @Test
    void whatTheGlyphDoesNotCoverStaysUncovered() {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0x00000000);

        assertEquals(0x00000000, Glyphs.inverted(image).getRGB(0, 0));
    }

    @Test
    void aHalfTransparentPixelKeepsHowTransparentItIs() {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0x80336699);

        int inverted = Glyphs.inverted(image).getRGB(0, 0);

        assertEquals(0x80, inverted >>> 24 & 0xFF);
        assertEquals(0xCC9966, inverted & 0xFFFFFF);
    }

    @Test
    void theOriginalIsNotTouched() {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0xFF000000);

        Glyphs.inverted(image);

        assertEquals(0xFF000000, image.getRGB(0, 0));
    }
}
