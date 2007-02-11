/* LanguageTool, a natural language style checker 
 * Copyright (C) 2005 Daniel Naber (http://www.danielnaber.de)
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301
 * USA
 */
package de.danielnaber.languagetool.gui;

import java.awt.Container;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.Enumeration;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextPane;
import javax.swing.WindowConstants;
import javax.swing.filechooser.FileFilter;
import javax.xml.parsers.ParserConfigurationException;

import org.jdesktop.jdic.tray.SystemTray;
import org.jdesktop.jdic.tray.TrayIcon;
import org.xml.sax.SAXException;

import de.danielnaber.languagetool.JLanguageTool;
import de.danielnaber.languagetool.Language;
import de.danielnaber.languagetool.rules.Rule;
import de.danielnaber.languagetool.rules.RuleMatch;
import de.danielnaber.languagetool.server.HTTPServer;
import de.danielnaber.languagetool.tools.StringTools;

/**
 * A simple GUI to check texts with.
 * 
 * @author Daniel Naber
 */
public final class Main implements ActionListener {

  private ResourceBundle messages;
  
  private static final String HTML_FONT_START = "<font face='Arial,Helvetica'>";
  private static final String HTML_FONT_END = "</font>";
  
  private static final Icon SYSTEM_TRAY_ICON = new ImageIcon("resource"+File.separator+"TrayIcon.png");
  private static final String SYSTEM_TRAY_TOOLTIP = "LanguageTool";
  private static final String CONFIG_FILE = ".languagetool.cfg";

  private Configuration config = null;
  
  private TrayIcon trayIcon = null;
  private JFrame frame = null;
  private JTextArea textArea = null;
  private JTextPane resultArea = null;
  private JComboBox langBox = null;
  
  private HTTPServer httpServer = null;
  
  private Map<Language, ConfigurationDialog> configDialogs = new HashMap<Language, ConfigurationDialog>();

  // whether clicking on the window close button hides to system tray:
  private boolean trayMode = false;

  private Main() throws IOException {
    config = new Configuration(new File(System.getProperty("user.home")), CONFIG_FILE);
    messages = JLanguageTool.getMessageBundle();
    maybeStartServer();
  }

  private void createGUI() {
    frame = new JFrame("LanguageTool " + JLanguageTool.VERSION);
    frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
    frame.addWindowListener(new CloseListener());
    frame.setIconImage(Tools.WINDOW_ICON);
    frame.setJMenuBar(new MainMenuBar(this, messages));

    textArea = new JTextArea(messages.getString("guiDemoText"));
    // TODO: wrong line number is displayed for lines that are wrapped automatically:
    textArea.setLineWrap(true);
    textArea.setWrapStyleWord(true);
    resultArea = new JTextPane();
    resultArea.setContentType("text/html");
    resultArea.setText(HTML_FONT_START + messages.getString("resultAreaText") + HTML_FONT_END);
    resultArea.setEditable(false);
    JLabel label = new JLabel(messages.getString("enterText"));
    JButton button = new JButton(messages.getString("checkText"));
    button.setMnemonic('c'); 
    button.addActionListener(this);

    JPanel panel = new JPanel();
    panel.setLayout(new GridBagLayout());
    GridBagConstraints buttonCons = new GridBagConstraints();
    buttonCons.gridx = 0;
    buttonCons.gridy = 0;
    panel.add(button, buttonCons);
    buttonCons.gridx = 1;
    buttonCons.gridy = 0;
    panel.add(new JLabel(" " + messages.getString("textLanguage") + " "), buttonCons);
    buttonCons.gridx = 2;
    buttonCons.gridy = 0;
    langBox = new JComboBox();
    for (Language lang : Language.LANGUAGES) {
      if (lang != Language.DEMO) {
        langBox.addItem(messages.getString(lang.getShortName()));
      }
    }
    // use the system default language to preselect the language from the combo box: 
    langBox.setSelectedItem(messages.getString((Locale.getDefault().getLanguage())));
    panel.add(langBox, buttonCons);

    Container contentPane = frame.getContentPane();
    GridBagLayout gridLayout = new GridBagLayout();
    contentPane.setLayout(gridLayout);
    GridBagConstraints cons = new GridBagConstraints();
    cons.insets = new Insets(5, 5, 5, 5);
    cons.fill = GridBagConstraints.BOTH;
    cons.weightx = 10.0f;
    cons.weighty = 10.0f;
    cons.gridx = 0;
    cons.gridy = 1;
    cons.weighty = 5.0f;
    JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(textArea),
        new JScrollPane(resultArea));
    splitPane.setDividerLocation(200);
    contentPane.add(splitPane, cons);

    cons.fill = GridBagConstraints.NONE;
    cons.gridx = 0;
    cons.gridy = 2;
    cons.weighty = 0.0f;
    cons.insets = new Insets(3,3,3,3);
    //cons.fill = GridBagConstraints.NONE;
    contentPane.add(label, cons);
    cons.gridy = 3;
    contentPane.add(panel, cons);
    
    frame.pack();
    frame.setSize(600, 550);
  }

  private void showGUI() {
    frame.setVisible(true);
  }
  
  public void actionPerformed(final ActionEvent e) {
    try {
      if (e.getActionCommand().equals(messages.getString("checkText"))) {
        JLanguageTool langTool = getCurrentLanguageTool();
        checkTextAndDisplayResults(langTool, getCurrentLanguage());
      } else {
        throw new IllegalArgumentException("Unknown action " + e);
      }
    } catch (Exception exc) {
      showError(exc);
    }
  }

  void loadFile() {
    JFileChooser jfc = new JFileChooser();
    jfc.setFileFilter(new PlainTextFilter());
    jfc.showOpenDialog(frame);
    try {
      File file = jfc.getSelectedFile();
      if (file == null)   // user cancelled
        return;
      String fileContents = StringTools.readFile(file.getAbsolutePath());
      textArea.setText(fileContents);
      JLanguageTool langTool = getCurrentLanguageTool();
      checkTextAndDisplayResults(langTool, getCurrentLanguage());
    } catch (IOException e) {
      showError(e);
    }
  }
  
  private static void showError(final Exception e) {
    String msg = de.danielnaber.languagetool.tools.Tools.getFullStackTrace(e);
    JOptionPane.showMessageDialog(null, msg, "Error", JOptionPane.ERROR_MESSAGE);
    e.printStackTrace();
  }

  void hideToTray() {
    if (trayIcon == null) {
      try {
        trayIcon = new TrayIcon(SYSTEM_TRAY_ICON);
      } catch (NoClassDefFoundError e) {
        JOptionPane.showMessageDialog(null, "TrayIcon class not found. Please unzip " +
            "'standalone-libs.zip' in your LanguageTool installation directory.", "Error",
            JOptionPane.ERROR_MESSAGE);
        return;
      }
      SystemTray tray = SystemTray.getDefaultSystemTray();
      trayIcon.addActionListener(new TrayActionListener());
      trayIcon.setToolTip(SYSTEM_TRAY_TOOLTIP);
      /*
      FIXME: this menu disappears immediately for me *unless* the main 
      Window is open:
      JPopupMenu popupMenu = new JPopupMenu("LanguageTool");
      popupMenu.add(new JMenuItem("Check text from clipboard"));
      popupMenu.add(new JMenuItem("Quit"));
      trayIcon.setPopupMenu(popupMenu);*/
      tray.addTrayIcon(trayIcon);
    }
    frame.setVisible(false);
  }
  
  void showOptions() {
    JLanguageTool langTool = getCurrentLanguageTool();
    List<Rule> rules = langTool.getAllRules();
    ConfigurationDialog configDialog = getCurrentConfigDialog();
    configDialog.show(rules);   // this blocks until OK/Cancel is clicked in the dialog
    config.setDisabledRuleIds(configDialog.getDisabledRuleIds());
    config.setMotherTongue(configDialog.getMotherTongue());
    config.setRunServer(configDialog.getRunServer());
    config.setServerPort(configDialog.getServerPort());
    // Stop server, start new server if requested:
    stopServer();
    maybeStartServer();
  }
  
  private void restoreFromTray() {
    String s = getClipboardText();
    // show GUI and check the text from clipboard/selection:
    frame.setVisible(true);
    textArea.setText(s);
    JLanguageTool langTool = getCurrentLanguageTool();
    checkTextAndDisplayResults(langTool, getCurrentLanguage());
  }

  void checkClipboardText() {
    String s = getClipboardText();
    textArea.setText(s);
    JLanguageTool langTool = getCurrentLanguageTool();
    checkTextAndDisplayResults(langTool, getCurrentLanguage());
  }
  
  private String getClipboardText() {
    // get text from clipboard or selection:
    Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemSelection();
    if (clipboard == null) {    // on Windows
      clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
    }
    String s = null;
    Transferable data = clipboard.getContents(this);
    try {
      DataFlavor df = DataFlavor.getTextPlainUnicodeFlavor();
      Reader sr = df.getReaderForText(data);
      s = StringTools.readerToString(sr);
    } catch (Exception ex) {
      ex.printStackTrace();
      s = data.toString();
    }
    return s;
  }

  void quitOrHide() {
    if (trayMode)
      hideToTray();
    else
      quit();
  }
  
  void quit() {
    stopServer();
    try {
      config.saveConfiguration();
    } catch (IOException e) {
      showError(e);
    }
    if (trayIcon != null) {
      SystemTray tray = SystemTray.getDefaultSystemTray();
      tray.removeTrayIcon(trayIcon);
    }
    frame.setVisible(false);
    System.exit(0);
  }

  private void maybeStartServer() {
    if (config.getRunServer()) {
      httpServer = new HTTPServer(config.getServerPort());
      httpServer.run();
    }
  }

  private void stopServer() {
    if (httpServer != null) {
      httpServer.stop();
      httpServer = null;
    }
  }

  private Language getCurrentLanguage() {
    String langName = langBox.getSelectedItem().toString();
    String lang = langName;
    for (Enumeration<String> e = messages.getKeys(); e.hasMoreElements();) {
      String elem = e.nextElement().toString();
      if (messages.getString(elem).equals(langName)) {
        lang = elem;
        break;
      }
    }
    return Language.getLanguageForShortName(lang);
  }
  
  private ConfigurationDialog getCurrentConfigDialog() {
    Language language = getCurrentLanguage();
    ConfigurationDialog configDialog = null;
    if (configDialogs.containsKey(language)) {
      configDialog = (ConfigurationDialog)configDialogs.get(language);
    } else {
      configDialog = new ConfigurationDialog(frame, false);
      configDialog.setMotherTongue(config.getMotherTongue());
      configDialog.setDisabledRules(config.getDisabledRuleIds());
      configDialog.setRunServer(config.getRunServer());
      configDialog.setServerPort(config.getServerPort());
      configDialogs.put(language, configDialog);
    }
    return configDialog;
  }
  
  private JLanguageTool getCurrentLanguageTool() {
    JLanguageTool langTool;
    try {
      ConfigurationDialog configDialog = getCurrentConfigDialog();
      langTool = new JLanguageTool(getCurrentLanguage(), configDialog.getMotherTongue());
      langTool.activateDefaultPatternRules();
      langTool.activateDefaultFalseFriendRules();
      Set<String> disabledRules = configDialog.getDisabledRuleIds();
      if (disabledRules != null) {
        for (String ruleId : disabledRules) {
          langTool.disableRule(ruleId);
        }
      }
    } catch (IOException ioe) {
      throw new RuntimeException(ioe);
    } catch (ParserConfigurationException ex) {
      throw new RuntimeException(ex);
    } catch (SAXException ex) {
      throw new RuntimeException(ex);
    }
    return langTool;
  }

  private void checkTextAndDisplayResults(final JLanguageTool langTool, final Language lang) {
    if (textArea.getText().trim().equals("")) {
      textArea.setText("enterText2");
    } else {
      StringBuilder sb = new StringBuilder();
      String startChecktext = Tools.makeTexti18n(messages, "startChecking", 
          new Object[] { messages.getString(lang.getShortName()) });
      resultArea.setText(HTML_FONT_START + startChecktext +"<br>\n" + HTML_FONT_END);
      resultArea.repaint(); // FIXME: why doesn't this work?
      //TODO: resultArea.setCursor(new Cursor(Cursor.WAIT_CURSOR)); 
      sb.append(startChecktext+"...<br>\n");
      int matches = 0;
      try {
        matches = checkText(langTool, textArea.getText(), sb);
      } catch (Exception ex) {
        sb.append("<br><br><b><font color=\"red\">" + ex.toString() + "<br>");
        StackTraceElement[] elements = ex.getStackTrace();
        for (StackTraceElement element : elements) {
          sb.append(element + "<br>");
        }
        sb.append("</font></b><br>");
        ex.printStackTrace();
      }
      String checkDone = Tools.makeTexti18n(messages, "checkDone", new Object[] { new Integer(matches) });
      sb.append(checkDone + "<br>\n");
      resultArea.setText(HTML_FONT_START + sb.toString() + HTML_FONT_END);
      resultArea.setCaretPosition(0);
    }
  }

  private int checkText(final JLanguageTool langTool, final String text, final StringBuilder sb) throws IOException {
    long startTime = System.currentTimeMillis();
    List<RuleMatch> ruleMatches = langTool.check(text);
    long startTimeMatching = System.currentTimeMillis();
    int i = 0;
    for (RuleMatch match : ruleMatches) {
      String output = Tools.makeTexti18n(messages, "result1", new Object[] {
          new Integer(i+1), new Integer(match.getLine()+1), new Integer(match.getColumn())
      });
      sb.append(output);
      String msg = match.getMessage();
      msg = msg.replaceAll("<suggestion>", "<b>");
      msg = msg.replaceAll("</suggestion>", "</b>");
      msg = msg.replaceAll("<old>", "<b>");
      msg = msg.replaceAll("</old>", "</b>");
      sb.append("<b>" +messages.getString("errorMessage")+ "</b> " + msg + "<br>\n");
      if (match.getSuggestedReplacements().size() > 0) {
        String repl = StringTools.listToString(match.getSuggestedReplacements(), "; ");
        sb.append("<b>" +messages.getString("correctionMessage")+ "</b> " + repl + "<br>\n");
      }
      String context = Tools.getContext(match.getFromPos(), match.getToPos(), text);
      sb.append("<b>" +messages.getString("errorContext")+ "</b> " + context);
      sb.append("<br>\n");
      i++;
    }
    long endTime = System.currentTimeMillis();
    sb.append(Tools.makeTexti18n(messages, "resultTime", new Object[] {
       new Long(endTime - startTime), new Long(endTime - startTimeMatching)
    }));
    return ruleMatches.size();
  }

  private void setTrayMode(boolean trayMode) {
    this.trayMode = trayMode;
  }

  public static void main(final String[] args) {
    try {
      final Main prg = new Main();
      if (args.length == 1 && (args[0].equals("-t") || args[0].equals("--tray"))) {
        // dock to systray on startup
        javax.swing.SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            try {
              prg.createGUI();
              prg.setTrayMode(true);
              prg.hideToTray();
            } catch (Exception e) {
              showError(e);
            }
          }
        });
      } else if (args.length >= 1) {
        System.out.println("Usage: java de.danielnaber.languagetool.gui.Main [-t|--tray]");
        System.out.println("  -t, --tray: dock LanguageTool to system tray on startup");
      } else {
        javax.swing.SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            try {
              prg.createGUI();
              prg.showGUI();
            } catch (Exception e) {
              showError(e);
            }
          }
        });
      }
    } catch (Exception e) {
      showError(e);
    }
  }

  //
  // The System Tray stuff
  //
  
  class TrayActionListener implements ActionListener {

    public void actionPerformed(@SuppressWarnings("unused") ActionEvent e) {
      if (frame.isVisible() && frame.isActive()) {
        frame.setVisible(false);
      } else if (frame.isVisible() && !frame.isActive()) {
        frame.toFront();
        restoreFromTray();
      } else {
        restoreFromTray();
      }
    }
    
  }

  class CloseListener implements WindowListener {

    public void windowClosing(@SuppressWarnings("unused") WindowEvent e) {
      quitOrHide();
    }

    public void windowActivated(@SuppressWarnings("unused")WindowEvent e) {}
    public void windowClosed(@SuppressWarnings("unused")WindowEvent e) {}
    public void windowDeactivated(@SuppressWarnings("unused")WindowEvent e) {}
    public void windowDeiconified(@SuppressWarnings("unused")WindowEvent e) {}
    public void windowIconified(@SuppressWarnings("unused")WindowEvent e) {}
    public void windowOpened(@SuppressWarnings("unused")WindowEvent e) {}
    
  }
  
  class PlainTextFilter extends FileFilter {

    public boolean accept(final File f) {
      if (f.getName().toLowerCase().endsWith(".txt"))
        return true;
      return false;
    }

    public String getDescription() {
      return "*.txt";
    }
    
  }
  
}
