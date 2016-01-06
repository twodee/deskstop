package org.twodee.deskstop;

import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseAdapter;
import javax.imageio.ImageIO;
import java.awt.AWTException;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Graphics;
import java.awt.GraphicsDevice.WindowTranslucency;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPopupMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.JSlider;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.filechooser.FileNameExtensionFilter;

public class Deskstop extends JFrame {
  private static final Color WINDOW_COLOR = new Color(255, 0, 0, 255);
  private static final int MINIMUM_BORDER = 10;

  private ArrayList<BufferedImage> screenshots;
  private Robot robot;
  private JLabel scrubberLabel;
  private JSlider scrubber;
  private boolean showFramesOnScrub;
  private PreviewPanel panel;

  public Deskstop() throws AWTException {
    super("Deskstop");

    screenshots = new ArrayList<BufferedImage>();

    setUndecorated(true);
    setBackground(new Color(0, 0, 0, 0));

    final JPopupMenu menu = new JPopupMenu();

    JMenuItem exportItem = new JMenuItem("Export");
    menu.add(exportItem);
    exportItem.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent event) {
        JFileChooser chooser = new JFileChooser();
        FileNameExtensionFilter filter = new FileNameExtensionFilter("GIF Images", "gif");
        chooser.setFileFilter(filter);
        int okay = chooser.showSaveDialog(Deskstop.this);
        if (okay == JFileChooser.APPROVE_OPTION) {
          String userInput = JOptionPane.showInputDialog(Deskstop.this, "How many milliseconds between frames?", "Export", JOptionPane.QUESTION_MESSAGE);
          try {
            int nMillisBetweenFrames = Integer.parseInt(userInput);
            GifSequenceWriter writer = new GifSequenceWriter(chooser.getSelectedFile().getPath(), nMillisBetweenFrames, true);
            for (BufferedImage screenshot : screenshots) {
              writer.appendFrame(screenshot);
            }
            writer.close();
          } catch (NumberFormatException e) {
            System.out.println(e);
          }
        }
      }
    });

    showFramesOnScrub = true;
    final JCheckBoxMenuItem showFramesItem = new JCheckBoxMenuItem("Show Frames on Scrub");
    showFramesItem.setSelected(showFramesOnScrub);
    menu.add(showFramesItem);
    showFramesItem.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent event) {
        showFramesOnScrub = showFramesItem.isSelected();
        repaint();
      }
    });

    menu.addSeparator();

    JMenuItem deleteItem = new JMenuItem("Delete Current Frame");
    menu.add(deleteItem);
    deleteItem.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent event) {
        if (scrubber.getValue() < screenshots.size()) {
          screenshots.remove(scrubber.getValue());
          synchronizeScrubber();
        }
      }
    });

    JMenuItem resetItem = new JMenuItem("Delete All Frames");
    menu.add(resetItem);
    resetItem.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent event) {
        screenshots.clear();
        synchronizeScrubber();
      }
    });

    menu.addSeparator();

    JMenuItem quitItem = new JMenuItem("Quit");
    menu.add(quitItem);
    quitItem.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent event) {
        dispose();
      }
    });

    SolidPanel westPanel = new SolidPanel(MINIMUM_BORDER, 0);
    SolidPanel eastPanel = new SolidPanel(MINIMUM_BORDER, 0);
    SolidPanel southPanel = new SolidPanel(0, MINIMUM_BORDER);
    SolidPanel northPanel = new SolidPanel(0, MINIMUM_BORDER);

    JButton recordButton = new JButton("Record Screenshot");
    northPanel.add(recordButton);

    JButton optionsButton = new JButton("Options");
    northPanel.add(optionsButton);
    optionsButton.addMouseListener(new MouseAdapter() {
      public void mousePressed(MouseEvent e) {
        menu.show(e.getComponent(), e.getX(), e.getY());

        // On Windows, menu is blank for some reason. Force a redraw.
        menu.repaint();
      }
    });

    scrubber = new JSlider(0, 0, 0);
    scrubber.setBackground(WINDOW_COLOR);
    southPanel.add(scrubber);

    scrubberLabel = new JLabel();
    scrubberLabel.setForeground(Color.WHITE);
    synchronizeScrubberLabel();
    southPanel.add(scrubberLabel);

    scrubber.addChangeListener(new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        synchronizeScrubberLabel();
        repaint();
      }
    });

    add(northPanel, BorderLayout.NORTH);
    add(southPanel, BorderLayout.SOUTH);
    add(westPanel, BorderLayout.WEST);
    add(eastPanel, BorderLayout.EAST);

    panel = new PreviewPanel();
    add(panel, BorderLayout.CENTER);

    northPanel.setComponentPopupMenu(menu);
    southPanel.setComponentPopupMenu(menu);
    panel.setComponentPopupMenu(menu);

    new ComponentMover(this, panel);
    new ComponentMover(this, northPanel);

    ComponentResizer cr = new ComponentResizer();
    cr.registerComponent(this);

    JRootPane root = getRootPane();
    root.putClientProperty("Window.shadow", Boolean.FALSE);
    setAlwaysOnTop(true);
    setDefaultCloseOperation(EXIT_ON_CLOSE);
    setLocationRelativeTo(null);
    pack();

    robot = new Robot();
    recordButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent event) {
        Point corner = panel.getLocationOnScreen();
        Rectangle rectangle = new Rectangle(corner.x, corner.y, panel.getWidth(), panel.getHeight());
        BufferedImage screenshot = robot.createScreenCapture(rectangle);
        screenshots.add(scrubber.getValue(), screenshot);
        synchronizeScrubber();
        scrubber.setValue(scrubber.getValue() + 1);
        repaint();
      }
    });

    addComponentListener(new ComponentAdapter() {
      public void componentResized(ComponentEvent e) {
        screenshots.clear();
        synchronizeScrubber();
      }
    });
  } 

  private void synchronizeScrubber() {
    scrubber.setMaximum(screenshots.size());
    synchronizeScrubberLabel();
  }

  private void synchronizeScrubberLabel() {
    scrubberLabel.setText((scrubber.getValue() + 1) + "/" + scrubber.getMaximum());
  }

  private class PreviewPanel extends JPanel {
    public PreviewPanel() { 
      setPreferredSize(new Dimension(300, 300));
      setSize(getPreferredSize());
    }

    protected void paintComponent(Graphics g) {
      Graphics2D g2 = (Graphics2D) g;
      if (showFramesOnScrub && scrubber.getValue() < screenshots.size()) {
        g2.drawImage(screenshots.get(scrubber.getValue()), 0, 0, new Color(0, 0, 0, 0), null);
      } else {
        g2.setBackground(new Color(0, 0, 0, 0));
        g2.clearRect(0, 0, getWidth(), getHeight());
      }
    }
  }

  private static class SolidPanel extends JPanel {
    public SolidPanel(int width, int height) { 
      setMinimumSize(new Dimension(width, height));
      setBackground(WINDOW_COLOR);
    }
  }

  public static void main(String[] args) {
    GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
    GraphicsDevice gd = ge.getDefaultScreenDevice();

    if (!gd.isWindowTranslucencySupported(WindowTranslucency.PERPIXEL_TRANSLUCENT)) {
      JOptionPane.showMessageDialog(null, "Deskstop requires transparent windows, which your computer does not support.", "Error", JOptionPane.ERROR_MESSAGE);
      System.exit(0);
    }

    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        try {
          JFrame frame = new Deskstop();
          frame.setVisible(true);
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    });
  }
}
