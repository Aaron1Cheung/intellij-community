/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.ui.laf.darcula.ui;

import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.JBUI;
import sun.swing.SwingUtilities2;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicProgressBarUI;
import java.awt.*;
import java.awt.geom.*;

/**
 * @author Konstantin Bulenkov
 */
public class DarculaProgressBarUI extends BasicProgressBarUI {

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "UnusedDeclaration"})
  public static ComponentUI createUI(JComponent c) {
    c.setBorder(JBUI.Borders.empty().asUIResource());
    return new DarculaProgressBarUI();
  }

  protected volatile int offset = 0;

  @Override
  protected void installDefaults() {
    super.installDefaults();
    UIManager.put("ProgressBar.repaintInterval", new Integer(25));
    UIManager.put("ProgressBar.cycleTime", new Integer(300));
  }

  class IndeterminateProgressCache {
    private Area containingRoundRect = null;
    private Area insetArea = null;
    private Area outerInnerBorderRoundRectArea = null;
    private Area outlineArea = null;
    private Path2D path = null;

    private int barRectWidth = -1;
    private int barRectHeight = -1;
    private int w = -1;
    private int h = -1;
    private int cHeight = -1;
    private float off = 0;

    synchronized void validateAndRender(Graphics2D g,
                                        JComponent c,
                                        int barRectWidth,
                                        int barRectHeight,
                                        int w,
                                        int h,
                                        double progressOffset) {
      final float newOff = JBUI.scale(1f);
      if (path == null || barRectWidth != this.barRectWidth ||
          barRectHeight != this.barRectHeight ||
          w != this.w || h != this.h || c.getHeight() != this.cHeight ||
          off != newOff)
      {
        this.cHeight = c.getHeight();
        path = new Path2D.Double();
        int x = 0;
        while (x < Math.max(w, cHeight) + getPeriodLength()) {
          float ww = getPeriodLength() / 2f;
          path.moveTo(x, 0);
          path.lineTo(x + ww, 0);
          path.lineTo(x + ww - h / 2, h);
          path.lineTo(x - h / 2, h);
          path.lineTo(x, 0);
          path.closePath();
          x += getPeriodLength();
        }

        this.barRectWidth = barRectWidth;
        this.barRectHeight = barRectHeight;
        this.w = w;
        this.h = h;
        final float R = JBUI.scale(8f);
        final float R2 = JBUI.scale(9f);

        off = newOff;

        Area cachedInnerBorderRoundRect = new Area(new RoundRectangle2D.Float(off, off, w - 2f * off, h - 2f * off, R, R));
        containingRoundRect = new Area(new RoundRectangle2D.Float(2f * off, 2f * off, w - 4f * off, h - 4f * off, R, R));
        insetArea = new Area(cachedInnerBorderRoundRect);
        insetArea.subtract(containingRoundRect);
        outerInnerBorderRoundRectArea = new Area(new Rectangle2D.Float(0, 0, w, h));
        outerInnerBorderRoundRectArea.subtract(cachedInnerBorderRoundRect);
        outlineArea = new Area(outerInnerBorderRoundRectArea);
        outlineArea.subtract(new Area(new RoundRectangle2D.Float(0, 0, w, h, R2, R2)));
      }

      g.translate(0, (c.getHeight() - h) / 2);

      Area area = new Area(path);
      area.transform(AffineTransform.getTranslateInstance(-progressOffset, 0));
      area.intersect(containingRoundRect);
      g.setColor(new JBColor(Gray._165, Gray._88));
      g.fill(area);

      g.setColor(Gray._128);
      if (c.isOpaque()) {
        g.fill(outerInnerBorderRoundRectArea);
      }

      g.setColor(c.getParent().getBackground());
      if (c.isOpaque()) {
        g.fill(outlineArea);
      }

      g.fill(insetArea);

      g.translate(0, -(c.getHeight() - h) / 2);
    }
  }

  final private IndeterminateProgressCache indeterminateProgressCache = new IndeterminateProgressCache();

  @Override
  protected void paintIndeterminate(Graphics g2d, JComponent c) {
    if (!(g2d instanceof Graphics2D)) {
      return;
    }
    Graphics2D g = (Graphics2D)g2d;

    Insets b = progressBar.getInsets(); // area for border
    int barRectWidth = progressBar.getWidth() - (b.right + b.left);
    int barRectHeight = progressBar.getHeight() - (b.top + b.bottom);

    if (barRectWidth <= 0 || barRectHeight <= 0) {
      return;
    }
    double progressOffset = ((double)getAnimationIndex()*getPeriodLength())/getFrameCount();
    int w = c.getWidth();
    int h = c.getPreferredSize().height;
    if (!isEven(c.getHeight() - h)) h++;

    if (c.isOpaque()) {
      g.setColor(new JBColor(Gray._240, Gray._128));
      g.fillRect(0, (c.getHeight() - h)/2, w, h);
    }
    final GraphicsConfig config = GraphicsUtil.setupAAPainting(g);

    indeterminateProgressCache.validateAndRender(g, c, barRectWidth, barRectHeight, w, h, progressOffset);

    // Deal with possible text painting
    if (progressBar.isStringPainted()) {
      if (progressBar.getOrientation() == SwingConstants.HORIZONTAL) {
        paintString(g, b.left, b.top, barRectWidth, barRectHeight, boxRect.x, boxRect.width);
      }
      else {
        paintString(g, b.left, b.top, barRectWidth, barRectHeight, boxRect.y, boxRect.height);
      }
    }
    config.restore();
  }

  @Override
  protected void paintDeterminate(Graphics g, JComponent c) {
    if (!(g instanceof Graphics2D)) {
      return;
    }

    if (progressBar.getOrientation() != SwingConstants.HORIZONTAL || !c.getComponentOrientation().isLeftToRight()) {
      super.paintDeterminate(g, c);
      return;
    }
    final GraphicsConfig config = GraphicsUtil.setupAAPainting(g);
    Insets b = progressBar.getInsets(); // area for border
    int w = progressBar.getWidth();
    int h = progressBar.getPreferredSize().height;
    if (!isEven(c.getHeight() - h)) h++;

    int barRectWidth = w - (b.right + b.left);
    int barRectHeight = h - (b.top + b.bottom);

    if (barRectWidth <= 0 || barRectHeight <= 0) {
      return;
    }

    int amountFull = getAmountFull(b, barRectWidth, barRectHeight);

    g.setColor(c.getParent().getBackground());
    Graphics2D g2 = (Graphics2D)g;
    if (c.isOpaque()) {
      g.fillRect(0, 0, w, h);
    }

    final float R = JBUI.scale(8f);
    final float R2 = JBUI.scale(9f);
    final float off = JBUI.scale(1f);

    g2.translate(0, (c.getHeight() - h)/2);
    g2.setColor(progressBar.getForeground());
    g2.fill(new RoundRectangle2D.Float(0, 0, w - off, h - off, R2, R2));
    g2.setColor(c.getParent().getBackground());
    g2.fill(new RoundRectangle2D.Float(off, off, w - 2f*off - off, h - 2f*off - off, R, R));
    g2.setColor(progressBar.getForeground());
    g2.fill(new RoundRectangle2D.Float(2f*off,2f*off, amountFull - JBUI.scale(5f), h - JBUI.scale(5f), JBUI.scale(7f), JBUI.scale(7f)));
    g2.translate(0, -(c.getHeight() - h)/2);

    // Deal with possible text painting
    if (progressBar.isStringPainted()) {
      paintString(g, b.left, b.top,
                  barRectWidth, barRectHeight,
                  amountFull, b);
    }
    config.restore();
  }

  private void paintString(Graphics g, int x, int y, int w, int h, int fillStart, int amountFull) {
    if (!(g instanceof Graphics2D)) {
      return;
    }

    Graphics2D g2 = (Graphics2D)g;
    String progressString = progressBar.getString();
    g2.setFont(progressBar.getFont());
    Point renderLocation = getStringPlacement(g2, progressString,
                                              x, y, w, h);
    Rectangle oldClip = g2.getClipBounds();

    if (progressBar.getOrientation() == SwingConstants.HORIZONTAL) {
      g2.setColor(getSelectionBackground());
      SwingUtilities2.drawString(progressBar, g2, progressString,
                                 renderLocation.x, renderLocation.y);
      g2.setColor(getSelectionForeground());
      g2.clipRect(fillStart, y, amountFull, h);
      SwingUtilities2.drawString(progressBar, g2, progressString,
                                 renderLocation.x, renderLocation.y);
    } else { // VERTICAL
      g2.setColor(getSelectionBackground());
      AffineTransform rotate =
        AffineTransform.getRotateInstance(Math.PI/2);
      g2.setFont(progressBar.getFont().deriveFont(rotate));
      renderLocation = getStringPlacement(g2, progressString,
                                          x, y, w, h);
      SwingUtilities2.drawString(progressBar, g2, progressString,
                                 renderLocation.x, renderLocation.y);
      g2.setColor(getSelectionForeground());
      g2.clipRect(x, fillStart, w, amountFull);
      SwingUtilities2.drawString(progressBar, g2, progressString,
                                 renderLocation.x, renderLocation.y);
    }
    g2.setClip(oldClip);
  }

  @Override
  protected int getBoxLength(int availableLength, int otherDimension) {
    return availableLength;
  }

  protected int getPeriodLength() {
    return JBUI.scale(16);
  }

  private static boolean isEven(int value) {
    return value % 2 == 0;
  }
}
