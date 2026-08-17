/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.ide.key;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.ImageIcon;

import de.uka.ilkd.key.gui.fonticons.IconFactory;

import org.key_project.ide.key.ProofObligations.Status;

/**
 * Provides KeY's proof status icons to the IDE as image data.
 * <p>
 * The icons are covered by KeY's licence. Transferring them as bytes keeps them out of the
 * plugins, which ship neither KeY code nor KeY assets.
 */
public final class StatusIcons {

    /** The name the continue icon is served under, which is no proof state. */
    public static final String VERIFY = "VERIFY";

    private StatusIcons() {
    }

    /**
     * The icons an IDE draws, as {@code data:} URIs: one per proof state KeY has an icon
     * for, and {@link #VERIFY} for offering to prove something.
     *
     * @param size the edge length in pixels the icons are drawn at
     * @return the icons, by status name
     */
    public static Map<String, String> asDataUris(int size) {
        return asDataUris(size, false);
    }

    /**
     * The icons an IDE draws under a dark theme.
     * <p>
     * KeY draws its question icon as a dark glyph, which a dark background swallows, so that
     * one is inverted. Its keyholes carry KeY's own colours and are served as they are. Which
     * of them needs it is decided here rather than in each editor, so that a state looks the
     * same wherever it is drawn.
     *
     * @param size the edge length in pixels the icons are drawn at
     * @return the icons, by status name
     */
    public static Map<String, String> asDarkDataUris(int size) {
        return asDataUris(size, true);
    }

    private static Map<String, String> asDataUris(int size, boolean dark) {
        Map<String, String> icons = new LinkedHashMap<>();
        put(icons, Status.OPEN, IconFactory.keyHole(size, size), size, false);
        put(icons, Status.CLOSED_BUT_LEMMAS_LEFT, IconFactory.keyHoleAlmostClosed(size, size),
            size, false);
        put(icons, Status.CLOSED_BY_CACHE, IconFactory.keyCachedClosed(size, size), size, false);
        put(icons, Status.CLOSED, IconFactory.keyHoleClosed(size), size, false);

        // KeY has icons for exactly four proof states. The two states below get none of
        // them: a saved proof is neither open nor closed until it has been replayed, and an
        // unknown KeY status is not classified at all. Both use KeY's question icon, which
        // is the one drawn as a dark glyph.
        Icon unknown = fromResource("/de/uka/ilkd/key/gui/images/questionIcon.png", size);
        put(icons, Status.SAVED, unknown, size, dark);
        put(icons, Status.UNKNOWN, unknown, size, dark);

        // Not a state but an invitation: KeY's own continue button, for wherever an IDE
        // offers to start a proof. It is drawn from a font, so it can only be had by
        // asking KeY to draw it.
        String verify = encode(IconFactory.autoModeStartLogo(size), size, false);
        if (verify != null) {
            icons.put(VERIFY, verify);
        }
        return icons;
    }

    /**
     * Loads one of KeY's images, for the ones its icon factory does not expose.
     *
     * @param resource the absolute resource path, since KeY's images sit beside the
     *        package its icon factory lives in
     * @param size the edge length to scale to
     * @return the icon, or {@code null} when the image is missing
     */
    private static Icon fromResource(String resource, int size) {
        java.net.URL url = IconFactory.class.getResource(resource);
        if (url == null) {
            return null;
        }
        Image scaled = new ImageIcon(url).getImage().getScaledInstance(size, size,
            Image.SCALE_SMOOTH);
        return new ImageIcon(scaled);
    }

    private static void put(Map<String, String> icons, Status status, Icon icon, int size,
            boolean invert) {
        if (icon == null) {
            return;
        }
        String encoded = encode(icon, size, invert);
        if (encoded != null) {
            icons.put(status.name(), encoded);
        }
    }

    /**
     * Draws an icon and encodes it.
     *
     * @param icon the icon to draw
     * @param size the edge length to draw it at
     * @param invert whether to invert its colours
     * @return a {@code data:} URI, or {@code null} when the icon cannot be drawn
     */
    private static String encode(Icon icon, int size, boolean invert) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            icon.paintIcon(null, graphics, 0, 0);
        } catch (RuntimeException e) {
            return null;
        } finally {
            graphics.dispose();
        }

        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            ImageIO.write(invert ? Glyphs.inverted(image) : image, "png", bytes);
            return "data:image/png;base64,"
                + Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (IOException e) {
            return null;
        }
    }

}
