/**
 * Java. The program helps to understand: whither are funneling money
 *
 * @author Sergey Iryupin
 * @version 0.5.1 dated 10 Aug 2016
 */
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.text.*;
import javax.swing.filechooser.*;
import javax.swing.table.*;
import java.text.*;
import java.util.*;
import java.io.*;
import org.jopendocument.dom.spreadsheet.*;
import com.toedter.calendar.*; //https://www.ssec.wisc.edu/mcidas/software/v/javadoc/1.4/edu/wisc/ssec/mcidasv/data/dateChooser/JDateChooser.html
import org.jfree.chart.*;
import org.jfree.chart.labels.*;
import org.jfree.chart.plot.*;
import org.jfree.data.*;
import org.jfree.data.general.*;

public class AnalyzeCosts extends JFrame {

    final String nameOfProgram = "Analyze private costs";
    final String SETTINGS_FILE = "settings.ini";
    final String TOTAL_TITLE = "Total";
    final String BOLD_BEGIN = "<html><b>";
    final String BOLD_END = "</b></html>";
    final int WINDOW_WIDTH = 500;
    final int WINDOW_HEIGHT = 500;
    final int START_POSITION = 100;
    JFrame frame;
    JDateChooser startDate;
    JDateChooser endDate;
    JTextField fileName;
    JTabbedPane tabbedPane;
    DefaultTableModel model;
    DefaultPieDataset dataset;
    JFreeChart chart;
    ChartPanel chartPanel;

    public static void main(String[] args) {
        new AnalyzeCosts().go();
    }

    void go() {
        frame = new JFrame(nameOfProgram);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(WINDOW_WIDTH, WINDOW_HEIGHT);
        frame.setLocation(START_POSITION, START_POSITION);
        frame.setResizable(true);

        startDate = new JDateChooser(new Date());
        endDate = new JDateChooser(new Date());

        fileName = new JTextField("Click me...", 22);
        fileName.setToolTipText("Click to select the data file *.ods");
        fileName.setEditable(false);
        fileName.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                JFileChooser open = new JFileChooser();
                open.setFileSelectionMode(JFileChooser.FILES_ONLY);
                open.setFileFilter(new FileNameExtensionFilter("Spreadsheet files (*.ods)", "ods"));
                int result = open.showOpenDialog(frame);
                if (result == JFileChooser.APPROVE_OPTION) {
                    fileName.setText(open.getSelectedFile().getAbsolutePath());
                }
            }
        });

        ImageIcon icon = new ImageIcon(AnalyzeCosts.class.getResource("go.png"));
        JButton getFileName = new JButton();
        getFileName.setPreferredSize(new Dimension(45, 30));
        getFileName.setIcon(icon);
        getFileName.setToolTipText("Click to calculate the results");
        getFileName.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                readAndAnalize();
                saveSettings();
            }
        });

        JPanel upPanel = new JPanel();
        upPanel.add(startDate);
        upPanel.add(endDate);
        upPanel.add(fileName);
        upPanel.add(getFileName);

        model = new DefaultTableModel() {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
            @Override
            public Class getColumnClass(int col) {
              if (col == 1 || col == 2)
                 return Integer.class;
              else
                 return String.class;
            }
        };
        model.addColumn("Cost type");
        model.addColumn("Cost sum");
        model.addColumn("%");
        JTable table = new JTable(model);

        dataset = new DefaultPieDataset();
        chart = ChartFactory.createPieChart(
            "",        // chart title
            dataset,   // data
            false,     // include legend
            true,
            false);
        chartPanel = new ChartPanel(chart);

        tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Total", new JScrollPane(table));
        tabbedPane.addTab("Chart", chartPanel);

        frame.getContentPane().add(BorderLayout.NORTH, upPanel);
        frame.getContentPane().add(BorderLayout.CENTER, tabbedPane);
        readSettings();
        frame.setVisible(true);
    }

    void readAndAnalize() {
        SpreadSheet spreadSheet;
        Sheet sheet;
        MutableCell<SpreadSheet> cell;
        Date date = new Date();

        Map<String, Float> tm = new TreeMap<String, Float>();
        String[] arrayWords;
        float total = 0;

        File file = new File(fileName.getText());
        try {
            spreadSheet = SpreadSheet.createFromFile(file);
            for (int i=0; i<spreadSheet.getSheetCount(); i++) {
                sheet = spreadSheet.getSheet(i);
                int row = 1;
                while (true) {

                    // date
                    try {
                        cell = sheet.getCellAt(0, row);
                        if (!cell.isEmpty()) {
                            date = (Date) cell.getValue();
                        }
                    } catch (IllegalArgumentException e) {
                        //System.out.println("Bad address");
                    }

                    // operation
                    MutableCell<SpreadSheet> operation = sheet.getCellAt(1, row);

                    // sum
                    MutableCell<SpreadSheet> sum = sheet.getCellAt(2, row);

                    // check EOD on the page
                    if (operation.isEmpty() && (!sum.isEmpty())) {
                        break;
                    }

                    // next row
                    row++;

                    // date range check
                    if (date.before(startDate.getDate())) {
                        continue;
                    }
                    if (date.after(endDate.getDate())) {
                        break;
                    }

                    // collect by type of costs
                    if ((!operation.isEmpty()) && (!sum.isEmpty())) {
                        arrayWords = operation.getTextValue().split("[: ]");
                        //System.out.println(arrayWords[0] + ": " + sum.getValue());
                        float add = Float.parseFloat(sum.getValue().toString());
                        float value = 0;
                        try {
                            value = tm.get(arrayWords[0]);
                        } catch(NullPointerException e) { }
                        tm.put(arrayWords[0], value + add);
                        total += add;
                    }
                }
            }
            tm.put(TOTAL_TITLE, total);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // sort by values
        ArrayList<Map.Entry<String, Float>> list = new ArrayList<Map.Entry<String, Float>>(tm.entrySet());
        Collections.sort(list, new Comparator<Map.Entry<String, Float>>() {
            public int compare(Map.Entry<String, Float> o1, Map.Entry<String, Float> o2) {
                Float v1 = o1.getValue();
                Float v2 = o2.getValue();
                return v1.compareTo(v2);
            }
        });

        // clear table
        model.setRowCount(0);

        // show results
        //Iterator i = tm.entrySet().iterator(); // sort by name
        Iterator<Map.Entry<String, Float>> i = list.iterator();
        while (i.hasNext()) {
            Map.Entry<String, Float> obj = i.next();
            String key = obj.getKey();
            float value = (float) obj.getValue();
            // for Total panel
            String type = key;
            String strValue = String.format("%,.2f", value);
            String strPercent = String.format("%.2f%%", value/total*100);
            if (key == TOTAL_TITLE) {
                type = BOLD_BEGIN + key + BOLD_END;
                strValue = BOLD_BEGIN + strValue + BOLD_END;
                strPercent = "";
            }
            String[] row = {type, strValue, strPercent};
            model.addRow(row);
            // for Chart panel
            if (key != TOTAL_TITLE) {
                dataset.setValue(key, new Double(value));
            }
        }

        // draw Chart
        final PiePlot plot = (PiePlot) chart.getPlot();
        plot.setBackgroundPaint(Color.white);
        //plot.setInteriorGap(0.0);
        //plot.setLabelGenerator(null);
        plot.setLabelGenerator(new StandardPieSectionLabelGenerator("{0} {2}"));
        //plot.setCircular(true);
    }

    void readSettings() {
        Object[] ini = new Object[3];
        try {
            FileInputStream fileIn = new FileInputStream(new File(SETTINGS_FILE));
            ObjectInputStream is = new ObjectInputStream(fileIn);
            ini = (Object[]) is.readObject();
            startDate.setDate((Date)ini[0]);
            endDate.setDate((Date)ini[1]);
            fileName.setText((String)ini[2]);
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    void saveSettings() {
        Object[] ini = {startDate.getDate(), endDate.getDate(), fileName.getText()};
        try {
            FileOutputStream fileStream = new FileOutputStream(new File(SETTINGS_FILE));
            ObjectOutputStream os = new ObjectOutputStream(fileStream);
            os.writeObject(ini);
        } catch(Exception e) {
            e.printStackTrace();
        }
    }
}