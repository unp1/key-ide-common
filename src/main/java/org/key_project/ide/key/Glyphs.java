/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.ide.key;

import java.awt.image.BufferedImage;

/**
 * What a dark theme needs doing to an icon drawn as a dark glyph.
 * <p>
 * This holds no reference to KeY, which is what lets it be tested on its own: loading a class
 * that reaches KeY's user interface starts KeY's logging, and starting that before KeY's
 * resources are read leaves the logging broken for the rest of the process.
 */
public final class Glyphs {

    private Glyphs() {
    }

    /**
     * The image with its colours inverted and what it does not cover left alone.
     * <p>
     * A glyph drawn in black on nothing becomes one drawn in white on nothing, which is what
     * a dark theme needs. A transparent pixel stays transparent, so the shape is kept.
     *
     * @param image the image to invert
     * @return a new image, the original untouched
     */
    public static BufferedImage inverted(BufferedImage image) {
        BufferedImage result =
            new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                int alpha = argb >>> 24 & 0xFF;
                result.setRGB(x, y, alpha == 0 ? argb : alpha << 24 | ~argb & 0xFFFFFF);
            }
        }
        return result;
    }
}
