package duckdns;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.prefs.Preferences;

public class DuckClient {
    private final static String DUCKDNS_API = "https://www.duckdns.org/update";
    private final static String CHECK_IP_URL = "http://checkip.amazonaws.com";
    private final static String DuckDNSVersion = "DuckDns Updater (v1.1.0)";
    private final static String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/72.0.3626.119 Safari/537.36";
    private static String StatusMsg = "";
    private static int timercount = 0;
    private static TrayIcon processTrayIcon = null;
    private TrayIcon trayIcon;

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());

            DuckClient systemTray = new DuckClient();
            systemTray.createApplicationToSystemTray();
            systemTray.startProcess();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String getIP() {
        Document doc = null;
        String ip = "";

        try {
            doc = Jsoup.connect(CHECK_IP_URL).header("Cache-Control", "no-cache")
                    .ignoreHttpErrors(true)
                    .ignoreContentType(true)
                    .timeout(10 * 1000).get();
            ip = doc.text();
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (doc != null && doc.text().length() < 7) {
            // can't get ip address, let DuckDNS to resolve it
            ip = "";
        }
        return ip;
    }

    private static String updateDuckDNS(String domain, String token, String ip_address) {
        String url = DUCKDNS_API + "?domains=" + domain + "&token=" + token + "&ip=" + ip_address;
        Document doc = null;

        try {
            doc = Jsoup.connect(url).userAgent(UA).ignoreHttpErrors(true).ignoreContentType(true).timeout(10 * 1000).get();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return doc != null ? doc.text() : null;
    }

    private static void updateTrayIP() {
        Preferences prefs = Preferences.userNodeForPackage(DuckClient.class);

        DateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy hh:mm:ss aa");
        Calendar cal2 = Calendar.getInstance();
        cal2.add(Calendar.SECOND, ((Integer.parseInt(prefs.get("refresh", "5")) * 60) - timercount));
        processTrayIcon.setToolTip(
                DuckDNSVersion + StatusMsg + "\nNext Update: " +
                        dateFormat.format(cal2.getTime()) + "\nRefresh: " +
                        prefs.get("refresh", "5") + " minutes.");
    }

    private static void launchUrl(String urlToLaunch) {
        try {
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                JOptionPane.showMessageDialog(null,
                        "On this computer java cannot open automatically url in browser, you have to copy/paste it manually.");
                return;
            }

            Desktop desktop = Desktop.getDesktop();
            URI uri = new URI(urlToLaunch);

            desktop.browse(uri);
        } catch (URISyntaxException ex) {
            JOptionPane.showMessageDialog(null, "Url [" + urlToLaunch + "] seems to be invalid ");
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(null, "There was some error opening the url. \n Details:\n" + ex.getMessage());
        }
    }

    private void createApplicationToSystemTray() throws IOException {
        // Check the SystemTray support
        if (!SystemTray.isSupported()) {
            return;
        }

        final PopupMenu popup = new PopupMenu();
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        InputStream inputStream = classLoader.getResourceAsStream("logo.png");
        Image img = ImageIO.read(Objects.requireNonNull(inputStream));

        trayIcon = new TrayIcon(img, DuckDNSVersion);
        DuckClient.processTrayIcon = trayIcon;
        final SystemTray tray = SystemTray.getSystemTray();

        MenuItem aboutItem = new MenuItem("About");
        MenuItem settingsItem = new MenuItem("DuckDNS Settings");
        MenuItem forceupdateItem = new MenuItem("Force Update");
        MenuItem whatsmyipaddress = new MenuItem("What is my IP Address?");
        MenuItem exitItem = new MenuItem("Exit");

        popup.add(settingsItem);
        popup.add(forceupdateItem);
        popup.add(whatsmyipaddress);
        popup.addSeparator();
        popup.add(aboutItem);
        popup.add(exitItem);

        trayIcon.setPopupMenu(popup);
        trayIcon.setImageAutoSize(true);

        try {
            tray.add(trayIcon);
        } catch (AWTException e) {
            return;
        }

        trayIcon.addActionListener(e -> JOptionPane.showMessageDialog(null,
                "Right click to choose from one of the menu options!"));

        settingsItem.addActionListener(e -> {
            Preferences prefs = Preferences.userNodeForPackage(DuckClient.class);

            JPanel panel = new JPanel();
            panel.setLayout(new GridLayout(5, 1));
            JLabel domain = new JLabel("Domain");
            JLabel token = new JLabel("Token");
            JLabel sett = new JLabel("Find your settings: ");
            JLabel minute = new JLabel("Refresh Interval (minutes)");
            JLabel update = new JLabel("Show Update Notifications");
            JTextField domainField = new JTextField(30);
            JTextField tokenField = new JTextField(30);

            String[] minuteStrings = {"5", "10", "15", "30", "60"};
            JComboBox<String> minuteField = new JComboBox<>(minuteStrings);

            String[] UpdateMessages = {"YES", "NO"};
            JComboBox<String> UpdateField = new JComboBox<>(UpdateMessages);

            domainField.setText(prefs.get("domain", ""));
            tokenField.setText(prefs.get("token", ""));
            minuteField.setSelectedItem(prefs.get("refresh", "5"));
            UpdateField.setSelectedItem(prefs.get("updatemessages", "YES"));

            // html content
            JEditorPane ep2 = new JEditorPane("text/html", "<html><a href=\"https://www.duckdns.org\">https://duckdns.org</a></html>");
            JLabel label = new JLabel();

            // handle link events
            ep2.addHyperlinkListener(e1 -> {
                if (e1.getEventType().equals(HyperlinkEvent.EventType.ACTIVATED)) {
                    launchUrl(e1.getURL().toString());
                }
            });
            ep2.setEditable(false);
            ep2.setBackground(label.getBackground());

            panel.add(domain);
            panel.add(domainField);
            panel.add(token);
            panel.add(tokenField);
            panel.add(minute);
            panel.add(minuteField);
            panel.add(update);
            panel.add(UpdateField);
            panel.add(sett);
            panel.add(ep2);

            //Create a window using JFrame with title ( Two text component in JOptionPane )
            JFrame frame = new JFrame("DuckDNS Settings");
            //Set default close operation for JFrame
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            //Set JFrame size
            frame.setSize(300, 300);
            //Set JFrame locate at center
            frame.setLocationRelativeTo(null);
            //Make JFrame visible
            frame.setVisible(false);
            //Show JOptionPane that will ask user for username and password
            int a = JOptionPane.showConfirmDialog(frame, panel, "DuckDNS Settings", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);

            //Operation that will do when user click 'OK'
            if (a == JOptionPane.OK_OPTION) {
                if (domainField.getText().length() > 0 && tokenField.getText().length() > 0) {
                    prefs.put("domain", domainField.getText().replace(".duckdns.org", ""));
                    prefs.put("token", tokenField.getText());
                    prefs.put("refresh", Objects.requireNonNull(minuteField.getSelectedItem()).toString());
                    String ip = getIP();
                    //  prefs.put("oldipaddress", ip);
                    prefs.put("updatemessages", Objects.requireNonNull(UpdateField.getSelectedItem()).toString());

                    String resultofipcheck = updateDuckDNS(prefs.get("domain", ""), prefs.get("token", ""), ip);

                    if (Objects.requireNonNull(resultofipcheck).equals("KO")) {
                        JOptionPane.showMessageDialog(frame, "DuckDNS Error: Invalid domain or token!\nPlease correct your settings!", DuckDNSVersion, JOptionPane.INFORMATION_MESSAGE);
                    }

                    if (resultofipcheck.equals("OK")) {
                        JOptionPane.showMessageDialog(frame, "DuckDNS settings successfully validated and saved!", DuckDNSVersion, JOptionPane.INFORMATION_MESSAGE);
                        updateTrayIP();
                        timercount = (Integer.parseInt(prefs.get("refresh", "5")) * 60);
                    }

                    if ((!resultofipcheck.equals("OK")) && (!resultofipcheck.equals("KO"))) {
                        JOptionPane.showMessageDialog(frame, "Unable to reach DuckDNS.org!", DuckDNSVersion, JOptionPane.INFORMATION_MESSAGE);
                    }

                } else if ((domainField.getText().length() < 1) && (tokenField.getText().length() < 1)) {
                    JOptionPane.showMessageDialog(frame, "Missing DuckDNS domain & token! Please try again!", "False", JOptionPane.ERROR_MESSAGE);
                    prefs.put("domain", domainField.getText());
                    prefs.put("token", tokenField.getText());
                    prefs.put("refresh", Objects.requireNonNull(minuteField.getSelectedItem()).toString());
                } else if (domainField.getText().length() < 1) {
                    JOptionPane.showMessageDialog(frame, "Missing DuckDNS domain name! Please try again!", "False", JOptionPane.ERROR_MESSAGE);
                    prefs.put("domain", domainField.getText());
                    prefs.put("token", tokenField.getText());
                    prefs.put("refresh", Objects.requireNonNull(minuteField.getSelectedItem()).toString());

                } else if (tokenField.getText().length() < 1) {
                    JOptionPane.showMessageDialog(frame, "Missing DuckDNS token! Please try again!", "False", JOptionPane.ERROR_MESSAGE);
                    prefs.put("domain", domainField.getText());
                    prefs.put("token", tokenField.getText());
                    prefs.put("refresh", Objects.requireNonNull(minuteField.getSelectedItem()).toString());
                }
            }

            //Operation that will do when user click 'Cancel'
            else if (a == JOptionPane.CANCEL_OPTION) {
                JOptionPane.showMessageDialog(frame, "Settings were not saved!");
            }
        });

// Add listener to aboutItem.
        aboutItem.addActionListener(e -> {
            // for copying style
            JLabel label = new JLabel();

            // html content
            JEditorPane ep = new JEditorPane("text/html", DuckDNSVersion + "<br>Developed by: ETX Software Inc.<br><html><a href=\"http://www.ETX.ca\">www.ETX.ca</a></html>");

            // handle link events
            ep.addHyperlinkListener(e12 -> {
                if (e12.getEventType().equals(HyperlinkEvent.EventType.ACTIVATED)) {
                    launchUrl(e12.getURL().toString()); // roll your own link launcher or use Desktop if J6+
                }
            });
            ep.setEditable(false);
            ep.setBackground(label.getBackground());

            // show
            JOptionPane.showMessageDialog(null, ep);
        });

        // Add listener to forceupdateItem.
        forceupdateItem.addActionListener(e -> {
            Preferences prefs = Preferences.userNodeForPackage(DuckClient.class);
            timercount = (Integer.parseInt(prefs.get("refresh", "5")) * 60);
            updateTrayIP();
        });

        // Add listener to whatsmyipaddress.
        whatsmyipaddress.addActionListener(e -> {
            String ip = getIP();

            // for copying style
            JLabel label = new JLabel();
            // html content
            JEditorPane ep = new JEditorPane("text/html", "Your External IP Address: \n<html><a href=\"http://checkip.amazonaws.com\">" + ip + "</a></html>");

            // handle link events
            ep.addHyperlinkListener(e13 -> {
                if (e13.getEventType().equals(HyperlinkEvent.EventType.ACTIVATED)) {
                    launchUrl(e13.getURL().toString()); // roll your own link launcher or use Desktop if J6+
                }
            });
            ep.setEditable(false);
            ep.setBackground(label.getBackground());

            // show
            JOptionPane.showMessageDialog(null, ep);
        });

        exitItem.addActionListener(e -> {
            tray.remove(trayIcon);
            System.exit(0);
        });
    }

    private void startProcess() {
        Thread thread = new Thread(() -> {
            Preferences prefs = Preferences.userNodeForPackage(DuckClient.class);

            // first start message (on empty settings)
            if ((prefs.get("domain", "").length() < 1) || (prefs.get("token", "").length() < 1)) {
                processTrayIcon.displayMessage(DuckDNSVersion, "Right click on the tray icon to change the settings!", TrayIcon.MessageType.INFO);
            }
            Timer timer = new Timer("Repeater");
            MyTask t = new MyTask();
            timer.schedule(t, 0, 1000);
            timercount = (Integer.parseInt(prefs.get("refresh", "5")) * 60);
        });
        thread.start();
    }

    class MyTask extends TimerTask {
        @Override
        public void run() {
            Preferences prefs = Preferences.userNodeForPackage(DuckClient.class);
            timercount++;

            if (timercount > (Integer.parseInt(prefs.get("refresh", "5")) * 60)) {
                // repeat on interval
                try {
                    String resultofipcheck;
                    String ip;

                    if ((prefs.get("domain", "").length() > 0) || (prefs.get("token", "").length() > 0)) {
                        ip = getIP();
                        String oldIp = InetAddress.getByName(prefs.get("domain", "") + ".duckdns.org")
                                .toString().replace(prefs.get("domain", "") + ".duckdns.org/", "");
                        // check if IP address is different from the last one. Only update is it differs (saving calls to DuckDNS).
                        if (!ip.equals(oldIp)) {
                            resultofipcheck = updateDuckDNS(prefs.get("domain", ""), prefs.get("token", ""), ip);
                            if (Objects.requireNonNull(resultofipcheck).equals("KO")) {
                                processTrayIcon.displayMessage(DuckDNSVersion,
                                        "DuckDNS Error: Missing or invalid domain or token!\n" +
                                                "Right click for settings!", TrayIcon.MessageType.INFO);
                            }

                            if (resultofipcheck.equals("OK")) {
                                if (prefs.get("updatemessages", "YES").equals("YES")) {
                                    processTrayIcon.displayMessage(DuckDNSVersion,
                                            "DuckDNS successfully updated!\nCurrent IP Address: " +
                                                    ip + "!", TrayIcon.MessageType.INFO);
                                    StatusMsg = "\nOld IP: " + oldIp + "\nNew IP: " + ip;
                                    // prefs.put("oldipaddress", ip);
                                }
                            }

                            if ((!resultofipcheck.equals("OK")) && (!resultofipcheck.equals("KO"))) {
                                processTrayIcon.displayMessage(DuckDNSVersion,
                                        "Unable to reach DuckDNS.org!",
                                        TrayIcon.MessageType.INFO);
                            }
                        } else {
                            if (prefs.get("updatemessages", "YES").equals("YES")) {
                                processTrayIcon.displayMessage(DuckDNSVersion,
                                        "Update not necessary!\nOld IP Address: " +
                                                prefs.get("oldipaddress", "") + "!\nNew IP Address: " +
                                                ip + "!", TrayIcon.MessageType.INFO);
                                StatusMsg = "\nIP unchanged: " + oldIp + "";
                            }
                        }
                        updateTrayIP();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                //after ran on schedule reset back to 0
                timercount = 0;
            }
        }
    }
}
