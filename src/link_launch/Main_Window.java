/* Greg Rohrschach, June 25, 2021
 * V1.0
 * Link Launch
 *
 * - Launch any link in your default browser at a specified time.
 *
 */

// TODO
//   - make it minimize to system tray
//  	https://docs.oracle.com/javase/tutorial/uiswing/misc/systemtray.html
// 		https://coderanch.com/t/448545/java/minimize-window-system-tray-restore
// 		- Create a tray icon
// 		- hide the window on minimize

package link_launch;

import java.awt.*;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EventObject;
import java.util.Objects;
import javax.swing.*;
import javax.swing.JSpinner.DateEditor;
import javax.swing.table.DefaultTableColumnModel;
import javax.swing.table.DefaultTableModel;
import javafx.util.Pair;


public class Main_Window {

    private JFrame frame;
    private static final Main_Window window = new Main_Window();
    private JTextField linkField;
    private JTable table;
    private DefaultTableModel tableMod;
    private JLabel lblCountdown;
    private LocalDateTime nextOpenTime = LocalDateTime.now();
    private boolean isMinimized=false;

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e1) {
            System.err.println("Could not set look and feel of application.");
        }
        EventQueue.invokeLater(() -> {
            try {
                window.frame.setVisible(true);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        ShutdownTasks shutdownTasks = new ShutdownTasks();
        Runtime runtime = Runtime.getRuntime();
        runtime.addShutdownHook(shutdownTasks);
    }

    private static class ShutdownTasks extends Thread {
        @Override
        public void run() {
            window.saveDataOnClose();
        }
    }

    /**
     * Create the application.
     */
    public Main_Window() {
        init();
        worker.start();
        updateThread.start();
        getDataOnOpen();
    }



    //private class TableMod extends

    private void init() {

        frame = new JFrame();
        frame.setBounds(100, 100, 500, 630);
        Dimension dim = Toolkit.getDefaultToolkit().getScreenSize(); //https://stackoverflow.com/questions/2442599/how-to-set-jframe-to-appear-centered-regardless-of-monitor-resolution
        frame.setLocation(dim.width/2-frame.getSize().width/2, dim.height/2-frame.getSize().height/2);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.getContentPane().setLayout(null);

        frame.addWindowListener(wl);

        table = new JTable() {
            private static final long serialVersionUID = -4336404712355812452L;

            @Override
            public boolean editCellAt(int row, int column, EventObject e) { // prevent manual edits.
                return false;
            }
        };

        table.setRowSelectionAllowed(true);
        table.getTableHeader().setReorderingAllowed(false);
        table.setFont(new Font("Tahoma", Font.PLAIN, 14));
        JScrollPane pane = new JScrollPane(table, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        pane.setBounds(65, 150, 356, 356);
        tableMod = (DefaultTableModel) table.getModel();
        tableMod.addColumn("Link");
        tableMod.addColumn("Date");
        tableMod.addColumn("Time");
        DefaultTableColumnModel colMod = (DefaultTableColumnModel) table.getColumnModel();
        colMod.getColumn(0).setPreferredWidth(100);
        table.setRowHeight(20);
        frame.getContentPane().add(pane);


        linkField = new JTextField();
        linkField.setBounds(65, 40, 356, 35);
        frame.getContentPane().add(linkField);
        linkField.setColumns(10);

        JLabel linkBoxTitle = new JLabel("Insert Link:");
        linkBoxTitle.setFont(new Font("Tahoma", Font.BOLD, 14));
        linkBoxTitle.setHorizontalAlignment(SwingConstants.CENTER);
        linkBoxTitle.setBounds(65, 11, 356, 21);
        frame.getContentPane().add(linkBoxTitle);

        SpinnerDateModel spinnerDateModel = new SpinnerDateModel();
        JSpinner spinnerDate = new JSpinner(spinnerDateModel);
        spinnerDate.setFont(new Font("Tahoma", Font.PLAIN, 14));
        JSpinner.DateEditor spinnerDate1 = new DateEditor(spinnerDate, "yyyy-MM-dd");
        spinnerDate.setEditor(spinnerDate1);
        spinnerDate.setBounds(65, 85, 100, 25);
        frame.getContentPane().add(spinnerDate);

        SpinnerDateModel spinnerDateModel1 = new SpinnerDateModel();
        JSpinner spinnerTime = new JSpinner(spinnerDateModel1);
        spinnerTime.setFont(new Font("Tahoma", Font.PLAIN, 14));
        JSpinner.DateEditor spinnerTime1 = new DateEditor(spinnerTime, "HH:mm:ss");
        spinnerTime.setEditor(spinnerTime1);
        spinnerTime.setBounds(180, 85, 115, 25);
        frame.getContentPane().add(spinnerTime);

        JButton btnAddRow = new JButton("Create Task");
        btnAddRow.setBounds(305, 86, 116, 24);
        frame.getContentPane().add(btnAddRow);
        btnAddRow.addActionListener((e) -> {
            @SuppressWarnings("deprecation")
            LocalDate date = LocalDate.of( // get date from spinner (corrected to non epoch time)
                    spinnerDateModel.getDate().getYear() + 1900, spinnerDateModel.getDate().getMonth() + 1, spinnerDateModel.getDate().getDate()
            );
            @SuppressWarnings("deprecation")
            LocalTime time = LocalTime.of( // get time from spinner
                    spinnerDateModel1.getDate().getHours(), spinnerDateModel1.getDate().getMinutes(), spinnerDateModel1.getDate().getSeconds()
            );
            Object[] row = new Object[3]; // create the array for the rows
            URI link;
            String linkString = null;
            try {
                link = new URI(linkField.getText());
                linkString = link.toString();
                if (!uriValid(linkString)) {
                    throw new URISyntaxException(linkString," is not a valid URL.");
                }
            } catch (URISyntaxException e1) {
                showError("Invalid Link, "+linkString+" is not a valid URL.","Error",new Object[]{"Ok"},"Ok");
                return;
            }

            row[0] = link;
            row[1] = date;
            row[2] = time;

            int index=table.getRowCount();
            for(int i = 0;i<table.getRowCount();i++) { // find first element that is bigger than the time we want to add.
                if(((LocalDate) tableMod.getValueAt(i, 1)).atTime(((LocalTime) tableMod.getValueAt(i, 2))).isAfter(date.atTime(time))) {
                    index=i;
                    break;
                }
            }
            tableMod.insertRow(index,row);
            worker.interrupt();
        });

        JButton btnRemoveRow = new JButton("Remove Selected");
        btnRemoveRow.setBounds(285, 517, 135, 35);
        frame.getContentPane().add(btnRemoveRow);
        btnRemoveRow.addActionListener((e) -> {
            int row = table.getSelectedRow();
            if(row!=-1) {
                tableMod.removeRow(row);
                worker.interrupt(); // reset the cycle
            }
        });

        JButton btnStart = new JButton("Launch Now");
        btnStart.setBounds(65, 517, 135, 35);
        frame.getContentPane().add(btnStart);



        // we need a reference to lblCountdown, so we can use the setText() method from it.
        // we need something that will run every 1 or so seconds and *calculate* the time until the next link will open,
        // based on LocalDateTime and the current LocalDateTime
        // the data we need (I.E the time of the next link being opened) is not in our scope. It belongs to the worker Thread.
        // -> we need to get the data out of the worker thread
        // -> how do we do this if the worker thread is sleeping all the time except for when it gets the time of the next link
        // we need to make the worker thread access instance variables instead of local ones so that we can use them too.
        // make new method called "updateNextTime(LocalDateTime, URI)" -> called by the worker when the worker thread gets the next link it's going to wait for.

        // how do we update the label every second?
        // if we want to wait a second, we have to sleep the thread
        // this is terrible for the event dispatch thread because it means it cannot respond to events until 1 second is over.
        // can't use EDT, so we should make a new Thread

        // NEVER UPDATE UI ON A THREAD THAT IS NOT THE EDT, ALSO NEVER RUN SLOW CALCULATIONS ON THE EDT
        // SwingUtilities.invokeLater(Runnable r); -> causes the runnable to be run on the EDT sometime later (usually instantly)
        lblCountdown = new JLabel("No Current Tasks.");
        lblCountdown.setHorizontalAlignment(SwingConstants.CENTER);
        lblCountdown.setBounds(150, 555, 145, 25);
        frame.getContentPane().add(lblCountdown);
        btnStart.addActionListener((e) -> {
            int row = table.getSelectedRow();
            if(row!=-1) {
                try {
                    Desktop.getDesktop().browse((URI)tableMod.getValueAt(row, 0));
                } catch (IOException e1) {
                    e1.printStackTrace();
                }

            }
        });
    }

    Thread worker = new Thread(this::openLink);
    private void openLink() {
        while(true) {
            try {
                doWorkOrSomething();
            } catch(InterruptedException ignored) {} // this allows us to refresh the entries and restart "the cycle" at any time by interrupting worker.

        }
    }


    final Object lock = new Object();
    private void doWorkOrSomething() throws InterruptedException {
        ArrayList<Pair<URI, LocalDateTime>> data = new ArrayList<>();

        try { // lazy programmer way
            updateNextTime(LocalDateTime.now(), new URI(""));
        } catch (URISyntaxException ignored) {}

        if (tableMod.getRowCount() == 0) { // do we have any data?
            synchronized (lock) {
                lock.wait();
            }
        }

        for(int row=0; row<tableMod.getRowCount(); row++) { // put the data into a better form
            URI link = (URI) tableMod.getValueAt(row, 0);
            LocalDate date = (LocalDate) tableMod.getValueAt(row, 1);
            LocalTime time = (LocalTime) tableMod.getValueAt(row, 2);
            data.add(new Pair<>(link, date.atTime(time)));
        }

        data.sort(Comparator.comparing(Pair::getValue));

        LocalDateTime now = LocalDateTime.now();

        if (data.get(data.size() - 1).getValue().isBefore(now)){ // make sure the last entry is after the current time
            synchronized (lock) {
                lock.wait();
            }
        }

        while(data.get(0).getValue().isBefore(now)) {// remove all entries before the current time
            data.remove(0);
        }
        // our data is good, and we're ready to RUMMMMBLLLLEEE
        doWork2(data);
    }

    private void doWork2(ArrayList<Pair<URI, LocalDateTime>> data) throws InterruptedException {
        int index = 0;
        for(int i = 0;i<data.size();i++) {
            long target = convertToMillis(data.get(index).getValue());
            long current = convertToMillis(LocalDateTime.now());
            long offset = target - current; // calculate how long we have to wait for
            updateNextTime(data.get(index).getValue(), data.get(index).getKey());
            if(offset>0) // fix for multiple entries having the same time.
                Thread.sleep(offset);
            try {
                Desktop.getDesktop().browse(data.get(index).getKey()); // open the link
            } catch (IOException e) {
                System.out.println("death has become us"); // die.
            }
        }
        tableMod.removeRow(0);
    }

    private long convertToMillis(LocalDateTime prefix) {
        return prefix.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private boolean uriValid(String link) {
        try {
            URL url = new URL(link);
            URLConnection conn = url.openConnection();
            conn.connect();
        } catch (MalformedURLException e) {
            // the URL is not in a valid form
            return false;
        } catch (IOException e) {
            // the connection couldn't be established
            return false;
        }
        return true;
    }

    private void updateCountdown(Duration countdown) {
        lblCountdown.setText("Next Task in: "+toNewFormat(countdown)+".");
    }

    private String toNewFormat(Duration d) {
        String s = d.toString();
        s = s.substring(2); // remove PT

        char c = s.charAt(s.length()-1); // remove last character if it's not a number
        if(c<'0' || c>'9')
            s = s.substring(0,s.length()-1);

        char[] arr = s.toCharArray();
        for(int i =0;i<arr.length; i++) {
            c = arr[i];
            if(c<'0' || c>'9')
                arr[i]=':'; // turn letters into colons
        }
        return new String(arr);
    }
    // this is called by the worker thread when it gets a new link to wait for.
    private void updateNextTime(LocalDateTime time, URI ignoredUri) {
        nextOpenTime = time;
    }

    private void updateAndWait() { // not on the EDT
        LocalDateTime now = LocalDateTime.now();
        Duration timeToOpen = Duration.between(now, nextOpenTime);
        timeToOpen = Duration.ofSeconds(timeToOpen.getSeconds());

        final Duration newValue = timeToOpen;
        if(convertToMillis(nextOpenTime) >= System.currentTimeMillis())
            SwingUtilities.invokeLater(()->updateCountdown(newValue));
        else
            SwingUtilities.invokeLater(()->lblCountdown.setText("No Current Tasks."));
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ignored) {}
    }

    Thread updateThread = new Thread(this::updateThreadRunnable);

    private final Object updateLock = new Object();
    private void updateThreadRunnable() {
        while(true) {
            updateAndWait();
            if(isMinimized) {
                try {
                    synchronized(updateLock) {
                        updateLock.wait();
                    }
                } catch (InterruptedException ignored) {}
            }
        }
    }

    private void wakeUpdateThread() {
        synchronized (updateLock) {
            updateLock.notifyAll();
        }
    }

    private final WindowListener wl = new WindowListener() {

        @Override
        public void windowOpened(WindowEvent e) {
            isMinimized=false;
            wakeUpdateThread();
        }

        @Override
        public void windowIconified(WindowEvent e) {
            isMinimized=true;
        }

        @Override
        public void windowDeiconified(WindowEvent e) {}

        @Override
        public void windowDeactivated(WindowEvent e) {}

        @Override
        public void windowClosing(WindowEvent e) {}

        @Override
        public void windowClosed(WindowEvent e) {}

        @Override
        public void windowActivated(WindowEvent e) {
            isMinimized=false;
            wakeUpdateThread();
        }
    };

    public static void showError(Object message, String title, Object[] options, Object Default)
    {
        exclamationSound();
        System.err.println(message);
        JOptionPane.showOptionDialog(window.frame, message,
                title, JOptionPane.DEFAULT_OPTION, JOptionPane.ERROR_MESSAGE,
                null, options, Default);
    }

    public static void exclamationSound()
    {
        Toolkit.getDefaultToolkit().beep();
    }



    private void saveDataOnClose() {
        String path;
        ArrayList<Serializable> tableOut = new ArrayList<>();
        ObjectOutputStream os = null;

        try {
            path = Objects.requireNonNull(ClassLoader.getSystemClassLoader().getResource(".")).toURI().getPath();
        } catch(URISyntaxException e) {
            return;
        }

        try {
            os = new ObjectOutputStream(new BufferedOutputStream(Files.newOutputStream(Paths.get(path + "\\save.table"))));
        } catch (IOException ignored) {}

        for(int row=0; row<tableMod.getRowCount(); row++) { // put the data into a better form
            URI link = (URI)tableMod.getValueAt(row, 0);
            LocalDate date = (LocalDate) tableMod.getValueAt(row, 1);
            LocalTime time = (LocalTime) tableMod.getValueAt(row, 2);

            tableOut.add(link);
            tableOut.add(date);
            tableOut.add(time);
        }
        try {
            assert os != null;
            os.writeObject(tableOut);
        } catch (IOException ignored) {} finally {
            if (os != null) {
                try {
                    os.close();
                } catch (IOException ignored) {}
            }
        }
    }


    private void getDataOnOpen() {
        String path;
        ObjectInputStream iS = null;
        ArrayList<Serializable> tableIn;
        try {
            path = Objects.requireNonNull(ClassLoader.getSystemClassLoader().getResource(".")).toURI().getPath();

            iS = new ObjectInputStream(new BufferedInputStream(Files.newInputStream(Paths.get(path + "\\save.table"))));

            tableIn = (ArrayList<Serializable>) iS.readObject();

            for(int i =0;i<tableIn.size()/3;i++) {
                Object[] row = new Object[3];

                row[0] = tableIn.get(i*3);
                row[1] = tableIn.get(i*3+1);
                row[2] = tableIn.get(i*3+2);

                tableMod.insertRow(0,row);
            }
        } catch (ClassNotFoundException | URISyntaxException | IOException ignored) {} finally {
            if (iS != null) {
                try {
                    iS.close();
                } catch (IOException ignored) {}
            }
            worker.interrupt();
        }
    }

}
