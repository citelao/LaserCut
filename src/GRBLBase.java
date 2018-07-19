import javax.swing.*;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.CountDownLatch;

import static javax.swing.JOptionPane.*;
import static javax.swing.JOptionPane.showMessageDialog;

  /*
   *  Probe-related  commands:
   *    g32.2                       Probe toward workpiece, stop on contact, signal error if failure
   *    g32.3                       Probe toward workpiece, stop on contact
   *    g32.4                       Probe away from workpiece, stop on loss of contact, signal error if failure
   *    g32.5                       Probe away from workpiece, stop on loss of contact
   *    g38.3 f100 z-10             Start probe move to z-10 with feedrate 100, stop on probe contact, or end of move
   *    g0                          Exit probe command state
   *    $X                          Clear Alarm state
   *
   *  Probe responses:
    *   [PRB:5.000,5.000,-6.000:1]  On probe contact
    *   ALARM:4                     The probe is not in the expected initial state before starting probe cycle
   *    ALARM:5                     Probe fails to contact in within the programmed travel for G38.2 and G38.4
   *    error:9                     G-code locked out during alarm or jog state
   */

class GRBLBase {

  JMenuItem getGRBLSettingsMenu (LaserCut parent, JSSCPort jPort) {
    JMenuItem settings = new JMenuItem("Get GRBL Settings");
    settings.addActionListener(ev -> {
      if (jPort.hasSerial()) {
      String receive = sendGrbl(jPort, "$I");
      String[] rsps = receive.split("\n");
      String grblBuild = null;
      String grblVersion = null;
      String grblOptions = null;
      for (String rsp : rsps ) {
        int idx1 = rsp.indexOf("[VER:");
        int idx2 = rsp.indexOf("]");
        if (idx1 >= 0 && idx2 > 0) {
          grblVersion = rsp.substring(5, rsp.length() - 2);
          if (grblVersion.contains(":")) {
            String[] tmp = grblVersion.split(":");
            grblVersion = tmp[1];
            grblBuild = tmp[0];
          }
        }
        idx1 = rsp.indexOf("[OPT:");
        idx2 = rsp.indexOf("]");
        if (idx1 >= 0 && idx2 > 0) {
          grblOptions = rsp.substring(5, rsp.length() - 2);
        }
      }
      receive = sendGrbl(jPort, "$$");
      String[] opts = receive.split("\n");
      HashMap<String,String> sVals = new LinkedHashMap<>();
      for (String opt : opts) {
        String[] vals = opt.split("=");
        if (vals.length == 2) {
          sVals.put(vals[0], vals[1]);
        }
      }
      JPanel sPanel;
      if (grblVersion != null) {
        ParameterDialog.ParmItem[] parmSet = {
          new ParameterDialog.ParmItem("@Grbl Version",  grblVersion),
          new ParameterDialog.ParmItem("@Grbl Build", grblBuild != null ? grblBuild : "unknown"),
          new ParameterDialog.ParmItem("@Grbl Options", grblOptions != null ? grblOptions : "unknown"),
          new ParameterDialog.ParmItem("Step pulse|usec",               sVals, "$0"),
          new ParameterDialog.ParmItem("Step idle delay|msec",          sVals, "$1"),
          new ParameterDialog.ParmItem("Step port invert",              sVals, "$2", new String[] {"X", "Y", "Z"}),   // Bitfield
          new ParameterDialog.ParmItem("Direction port invert",         sVals, "$3", new String[] {"X", "Y", "Z"}),   // Bitfield
          new ParameterDialog.ParmItem("Step enable invert|boolean",    sVals, "$4"),
          new ParameterDialog.ParmItem("Limit pins invert|boolean",     sVals, "$5"),
          new ParameterDialog.ParmItem("Probe pin invert|boolean",      sVals, "$6"),
          new ParameterDialog.ParmItem("Status report|mask",            sVals, "$10"),
          new ParameterDialog.ParmItem("Junction deviation|mm",         sVals, "$11"),
          new ParameterDialog.ParmItem("Arc tolerance|mm",              sVals, "$12"),
          new ParameterDialog.ParmItem("Report inches|boolean",         sVals, "$13"),
          new ParameterDialog.ParmItem("Soft limits|boolean",           sVals, "$20"),
          new ParameterDialog.ParmItem("Hard limits|boolean",           sVals, "$21"),
          new ParameterDialog.ParmItem("Homing cycle|boolean",          sVals, "$22"),
          new ParameterDialog.ParmItem("Homing dir invert",             sVals, "$23", new String[] {"X", "Y", "Z"}),   // Bitfield
          new ParameterDialog.ParmItem("Homing feed|mm/min",            sVals, "$24"),
          new ParameterDialog.ParmItem("Homing seek|mm/min",            sVals, "$25"),
          new ParameterDialog.ParmItem("Homing debounce|msec",          sVals, "$26"),
          new ParameterDialog.ParmItem("Homing pull-off|mm",            sVals, "$27"),
          new ParameterDialog.ParmItem("Max spindle speed|RPM",         sVals, "$30"),
          new ParameterDialog.ParmItem("Min spindle speed|RPM",         sVals, "$31"),
          new ParameterDialog.ParmItem("Laser mode|boolean",            sVals, "$32"),
          new ParameterDialog.ParmItem("X Axis|steps/mm",               sVals, "$100"),
          new ParameterDialog.ParmItem("Y Axis|steps/mm",               sVals, "$101"),
          new ParameterDialog.ParmItem("Z Axis|steps/mm",               sVals, "$102"),
          new ParameterDialog.ParmItem("X Max rate|mm/min",             sVals, "$110"),
          new ParameterDialog.ParmItem("Y Max rate|mm/min",             sVals, "$111"),
          new ParameterDialog.ParmItem("Z Max rate|mm/min",             sVals, "$112"),
          new ParameterDialog.ParmItem("X Acceleration|mm/sec\u00B2",   sVals, "$120"),
          new ParameterDialog.ParmItem("Y Acceleration|mm/sec\u00B2",   sVals, "$121"),
          new ParameterDialog.ParmItem("Z Acceleration|mm/sec\u00B2",   sVals, "$122"),
          new ParameterDialog.ParmItem("X Max travel|mm",               sVals, "$130"),
          new ParameterDialog.ParmItem("Y Max travel|mm",               sVals, "$131"),
          new ParameterDialog.ParmItem("Z Max travel|mm",               sVals, "$132"),
        };
        parmSet[3].sepBefore = true;
        Properties info = parent.getProperties(parent.getResourceFile("grbl/grblparms.props"));
        ParameterDialog dialog = (new ParameterDialog(parmSet, new String[] {"Save", "Cancel"}, false, info));
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);              // Note: this call invokes dialog
        if (dialog.doAction()) {
          java.util.List<String> cmds = new ArrayList<>();
          for (ParameterDialog.ParmItem parm : parmSet) {
            String value = parm.getStringValue();
            if (!parm.readOnly & !parm.lblValue && !value.equals(sVals.get(parm.key))) {
              //System.out.println(parm.name + ": changed from " + sVals.get(parm.key) + " to " + value);
              cmds.add(parm.key + "=" + value);
            }
          }
          if (cmds.size() > 0) {
            new GRBLSender(parent, jPort, cmds.toArray(new String[0]));
          }
        } else {
          System.out.println("Cancel");
        }
      } else {
        sPanel = new JPanel(new GridLayout(sVals.size() + 5, 2, 4, 0));
        Font font = new Font("Courier", Font.PLAIN, 14);
        JLabel lbl;
        int idx1 = rsps[0].indexOf("[");
        int idx2 = rsps[0].indexOf("]");
        if (rsps.length == 2 && idx1 >= 0 && idx2 > 0) {
          grblVersion = rsps[0].substring(1, rsps[0].length() - 2);
        }
        sPanel.add(new JLabel("GRBL Version: " + (grblVersion != null ? grblVersion : "unknown")));
        sPanel.add(new JSeparator());
        for (String key : sVals.keySet()) {
          sPanel.add(lbl = new JLabel(padSpace(key + ":", 6) + sVals.get(key)));
          lbl.setFont(font);
        }
        sPanel.add(new JSeparator());
        sPanel.add(new JLabel("Note: upgrade to GRBL 1.1, or later"));
        sPanel.add(new JLabel("to enable settings editor."));
        Object[] options = {"OK"};
        showOptionDialog(parent, sPanel, "GRBL Settings", OK_CANCEL_OPTION, PLAIN_MESSAGE, null, options, options[0]);
      }
      } else {
        showMessageDialog(parent, "No Serial Port Selected", "Error", PLAIN_MESSAGE);
      }
    });
    return settings;
  }

  private String padSpace (String txt, int len) {
    while (txt.length() < len) {
      txt = txt + " ";
    }
    return txt;
  }

  static class DroPanel extends JPanel {
    static DecimalFormat  fmt = new DecimalFormat("#0.000");
    private String[]      lblTxt = {"X", "Y", "Z"};
    private JTextField[]  lbl = new JTextField[3];

    DroPanel () {
      setLayout(new GridLayout(1, 3));
      for (int ii = 0; ii < 3; ii++) {
        JPanel axis = new JPanel();
        axis.add(new JLabel(lblTxt[ii]));
        axis.add(lbl[ii] = new JTextField("0", 6));
        lbl[ii].setHorizontalAlignment(JTextField.RIGHT);
        add(axis);
      }
    }

    // Responses to "?" command
    //  <Run|MPos:0.140,0.000,0.000|FS:20,0|Pn:Z>
    //  <Idle|MPos:0.000,0.000,0.000|FS:0,0|Pn:Z>
    //  <Jog|MPos:0.000,0.000,0.000|FS:0,0|Pn:Z>
    void setPosition (String rsp) {
      int idx1 = rsp.indexOf("|MPos:");
      if (idx1 >= 0) {
        idx1 += 6;
        int idx2 = rsp.indexOf('|', idx1);
        if (idx2 > idx1) {
          String[] tmp = rsp.substring(idx1, idx2).split(",");
          if (tmp.length == 3) {
            for (int ii = 0; ii < 3; ii++) {
              lbl[ii].setText(fmt.format(Double.parseDouble(tmp[ii])));
            }
          }
        }
      }
    }
  }

  JMenuItem getGRBLJogMenu (Frame parent, JSSCPort jPort) {
    JMenuItem jogMenu = new JMenuItem("Jog Controls");
    jogMenu.addActionListener((ev) -> {
      if (jPort.hasSerial()) {
        // Build Jog Controls
        JPanel frame = new JPanel(new BorderLayout(0, 2));
        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BorderLayout(0, 2));
        DroPanel dro = new DroPanel();
        dro.setPosition(sendGrbl(jPort, "?"));                                  // Show initial position
        topPanel.add(dro, BorderLayout.NORTH);
        JSlider speed = new JSlider(10, 100, 100);
        topPanel.add(speed, BorderLayout.SOUTH);
        speed.setMajorTickSpacing(10);
        speed.setPaintTicks(true);
        speed.setPaintLabels(true);
        frame.add(topPanel, BorderLayout.NORTH);
        JPanel buttons = new JPanel(new GridLayout(3, 4, 4, 4));
        JLabel tmp;
        Font font2 = new Font("Monospaced", Font.PLAIN, 20);
        // Row 1
        buttons.add(new JogButton(new Arrow(135), jPort, speed, dro, "Y-% X-%"));    // Up Left
        buttons.add(new JogButton(new Arrow(180), jPort, speed, dro, "Y-%"));        // Up
        buttons.add(new JogButton(new Arrow(225), jPort, speed, dro, "Y-% X+%"));    // Up Right
        buttons.add(new JogButton(new Arrow(180), jPort, speed, dro, "Z+%"));        // Up
        // Row 2
        buttons.add(new JogButton(new Arrow(90), jPort, speed, dro, "X-%"));         // Left
        buttons.add(tmp = new JLabel("X/Y", JLabel.CENTER));
        tmp.setFont(font2);
        buttons.add(new JogButton(new Arrow(270), jPort, speed, dro, "X+%"));        // Right
        buttons.add(tmp = new JLabel("Z", JLabel.CENTER));
        tmp.setFont(font2);
        // Row 3
        buttons.add(new JogButton(new Arrow(45), jPort, speed, dro, "Y+% X-%"));     // Down Left
        buttons.add(new JogButton(new Arrow(0), jPort, speed, dro, "Y+%"));          // Down
        buttons.add(new JogButton(new Arrow(315), jPort, speed, dro, "Y+% X+%"));    // Down Right
        buttons.add(new JogButton(new Arrow(0), jPort, speed, dro, "Z-%"));          // Down
        frame.add(buttons, BorderLayout.CENTER);
        // Bring up Jog Controls
        Object[] options = {"Set Origin", "Cancel"};
        int res = showOptionDialog(parent, frame, "Jog Controls", OK_CANCEL_OPTION, PLAIN_MESSAGE, null, options, options[0]);
        if (res == OK_OPTION) {
          // Reset coords to new position after jog
          try {
            jPort.sendString("G92 X0 Y0 Z0\n");
          } catch (Exception ex) {
            ex.printStackTrace();
          }
        } else {
          // Return to old home position
          try {
            jPort.sendString("G00 X0 Y0 Z0\n");
          } catch (Exception ex) {
            ex.printStackTrace();
          }
        }
      } else {
        showMessageDialog(parent, "No Serial Port Selected", "Error", PLAIN_MESSAGE);
      }
    });
    return jogMenu;
  }

  static class Arrow extends ImageIcon {
    Rectangle bounds = new Rectangle(26, 26);
    private Polygon arrow;

    Arrow (double rotation) {
      arrow = new Polygon();
      arrow.addPoint(0, 11);
      arrow.addPoint(10, -7);
      arrow.addPoint(-10, -7);
      arrow.addPoint(0, 11);
      BufferedImage bImg = new BufferedImage(bounds.width, bounds.height, BufferedImage.TYPE_INT_RGB);
      Graphics2D g2 = bImg.createGraphics();
      g2.setBackground(Color.white);
      g2.clearRect(0, 0, bounds.width, bounds.height);
      g2.setColor(Color.darkGray);
      AffineTransform at = AffineTransform.getTranslateInstance(bounds.width / 2, bounds.height / 2);
      at.rotate(Math.toRadians(rotation));
      g2.fill(at.createTransformedShape(arrow));
      g2.setColor(Color.white);
      setImage(bImg);
    }
  }

  static class JogButton extends JButton implements Runnable, JSSCPort.RXEvent {
    private JSSCPort      jPort;
    private JSlider       speed;
    private DroPanel      dro;
    private StringBuilder response = new StringBuilder();
    private String        cmd, lastResponse;
    private long          step, nextStep;
    transient boolean     pressed, running;
    private final JogButton.Lock lock = new JogButton.Lock();

    private static final class Lock { }

    JogButton (Icon icon, JSSCPort jPort, JSlider speed, DroPanel dro, String cmd) {
      super(icon);
      this.jPort = jPort;
      this.speed = speed;
      this.dro = dro;
      this.cmd = cmd;
      addMouseListener(new MouseAdapter() {
        @Override
        public void mousePressed (MouseEvent e) {
          super.mousePressed(e);
          while (running) {
            try {
              Thread.sleep(50);
            } catch (InterruptedException ex) {}
          }
          pressed = true;
          running = true;
          (new Thread(JogButton.this)).start();
        }

        @Override
        public void mouseReleased (MouseEvent e) {
          super.mouseReleased(e);
          pressed = false;
        }
      });
    }

    public void run () {
      jPort.setRXHandler(JogButton.this);
      nextStep = step = 0;
      boolean firstPress = true;
      try {
        int sp = speed.getValue();
        double ratio = sp / 100.0;
        String fRate = "F" + (int) Math.max(75 * ratio, 5);
        String sDist = LaserCut.df.format(.1 * ratio);
        String jogCmd = "$J=G91 G20 " + fRate + " " + cmd + "\n";
        jogCmd = jogCmd.replaceAll("%", sDist);
        while (pressed) {
          jPort.sendString(jogCmd);
          stepWait();
          jPort.sendString("?");
          stepWait();
          dro.setPosition(lastResponse);
          // Minimum move time 50ms
          if (firstPress) {
            Thread.sleep(50);
          }
          firstPress = false;
        }
        do {
          jPort.sendString("?");
          stepWait();
          dro.setPosition(lastResponse);
        } while (lastResponse.contains("<Jog"));
        jPort.sendByte((byte) 0x85);
        Thread.sleep(500);
        running = false;
      } catch (Exception ex) {
        ex.printStackTrace(System.out);
      } finally {
        jPort.removeRXHandler(JogButton.this);
      }
    }

    private void stepWait () throws InterruptedException{
      nextStep++;
      synchronized (lock) {
        while (step < nextStep) {
          lock.wait(20);
        }
      }
    }

    public void rxChar (byte cc) {
      if (cc == '\n') {
        lastResponse = response.toString();
        response.setLength(0);
        synchronized (lock) {
          step++;
        }
      } else {
        response.append((char) cc);
      }
    }
  }

  private class GRBLRunner extends Thread implements JSSCPort.RXEvent {
    private StringBuilder   response, line = new StringBuilder();
    private CountDownLatch latch = new CountDownLatch(1);
    private JSSCPort        jPort;
    transient boolean       running = true;

    GRBLRunner (JSSCPort jPort, String cmd, StringBuilder response) {
      this.jPort = jPort;
      this.response = response;
      jPort.setRXHandler(GRBLRunner.this);
      start();
      try {
        jPort.sendString(cmd + '\n');
        latch.await();
      } catch (Exception ex) {
        ex.printStackTrace();
      }
    }

    public void rxChar (byte cc) {
      if (cc == '\n') {
        if ("ok".equalsIgnoreCase(line.toString().trim())) {
          running = false;
        }
        line.setLength(0);
        response.append('\n');
      } else if (cc != '\r'){
        line.append((char) cc);
        response.append((char) cc);
      }
    }

    public void run () {
      int timeout = 10;
      while (running) {
        try {
          Thread.sleep(100);
          if (timeout-- < 0) {
            break;
          }
        } catch (InterruptedException ex) {
          ex.printStackTrace();
        }
      }
      latch.countDown();
      jPort.removeRXHandler(GRBLRunner.this);
    }
  }

  /**
   * Send one line GRBL command and return response (terminated by "ok\n" or timeout)
   * @param jPort Open JSSC port
   * @param cmd command string
   * @return response string (excluding "ok\n")
   */
  String sendGrbl (JSSCPort jPort, String cmd) {
    StringBuilder buf = new StringBuilder();
    new GRBLRunner(jPort, cmd, buf);
    return buf.toString();
  }

  // https://github.com/gnea/grbl/wiki

  class GRBLSender extends Thread implements JSSCPort.RXEvent {
    private StringBuilder   response = new StringBuilder();
    private String          lastResponse = "";
    private String[]        cmds, abortCmds;
    private JDialog         frame;
    private JTextArea       grbl;
    private JProgressBar    progress;
    private long            step, nextStep;
    private final GRBLSender.Lock lock = new GRBLSender.Lock();
    private JSSCPort        jPort;
    private boolean         doAbort;

    final class Lock { }

    GRBLSender (Frame parent, JSSCPort jPort, String[] cmds) {
      this(parent, jPort, cmds, new String[0]);
    }

    GRBLSender (Frame parent, JSSCPort jPort, String[] cmds, String[] abortCmds) {
      this.jPort = jPort;
      this.cmds = cmds;
      this.abortCmds = abortCmds;
      frame = new JDialog(parent, "G-Code Monitor");
      frame.setLocationRelativeTo(parent);
      frame.add(progress = new JProgressBar(), BorderLayout.NORTH);
      progress.setMaximum(cmds.length);
      JScrollPane sPane = new JScrollPane(grbl = new JTextArea());
      grbl.setMargin(new Insets(3, 3, 3, 3));
      DefaultCaret caret = (DefaultCaret) grbl.getCaret();
      caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
      grbl.setEditable(false);
      frame.add(sPane, BorderLayout.CENTER);
      JButton abort = new JButton("Abort Job");
      frame.add(abort, BorderLayout.SOUTH);
      abort.addActionListener(ev -> doAbort = true);
      Rectangle loc = frame.getBounds();
      frame.setSize(600, 300);
      frame.setLocation(loc.x + loc.width / 2 - 150, loc.y + loc.height / 2 - 150);
      frame.setVisible(true);
      start();
    }

    public void rxChar (byte cc) {
      if (cc == '\n') {
        grbl.append(lastResponse = response.toString());
        grbl.append("\n");
        response.setLength(0);
        synchronized (lock) {
          step++;
        }
      } else {
        response.append((char) cc);
      }
    }

    private void stepWait () throws InterruptedException{
      nextStep++;
      synchronized (lock) {
        while (step < nextStep) {
          lock.wait(100);
        }
      }
    }

    // Responses to "?" command
    //  <Run|MPos:0.140,0.000,0.000|FS:20,0|Pn:Z>
    //  <Idle|MPos:0.000,0.000,0.000|FS:0,0|Pn:Z>

    public void run () {
      jPort.setRXHandler(GRBLSender.this);
      step = 0;
      nextStep = 0;
      try {
        for (int ii = 0; (ii < cmds.length) && !doAbort; ii++) {
          String gcode = cmds[ii];
          progress.setValue(ii);
          grbl.append(gcode + '\n');
          jPort.sendString(gcode + '\n');
          stepWait();
        }
        // Wait until all commands have been processed
        boolean waiting = true;
        while (waiting && !doAbort) {
          Thread.sleep(200);
          jPort.sendString("?");              // Set ? command to query status
          stepWait();
          if (lastResponse.contains("<Idle")) {
            waiting = false;
          }
        }
        if (doAbort) {
          //jPort.sendByte((byte) 0x18);      // Locks up GRBL (can't jog after issued)
          for (String cmd : abortCmds) {
            jPort.sendString(cmd + "\n");     // Set abort command
            stepWait();
          }
        }
      } catch (Exception ex) {
        ex.printStackTrace();
      }
      jPort.removeRXHandler(GRBLSender.this);
      frame.setVisible(false);
      frame.dispose();
    }
  }
}