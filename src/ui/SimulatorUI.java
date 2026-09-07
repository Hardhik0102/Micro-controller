package com.team.ms51sim.ui;

import com.team.ms51sim.Assembler;
import com.team.ms51sim.CPU;
import com.team.ms51sim.Simulator;
import com.team.ms51sim.TraceFormatter;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.List;

/**
 * Minimal Swing UI for the Week 2 prototype.
 *
 * <p>Controls: <b>Load</b> (assemble the source and load it), <b>Reset</b>,
 * <b>Step</b> (one instruction through FETCH -> DECODE -> EXECUTE) and
 * <b>Run</b> (step automatically until the program terminates).</p>
 *
 * <p>Displays: the program listing with the current instruction highlighted,
 * the Program Counter, CPU registers (ACC, B, R0-R7), the PSW flags, the
 * execution status and a scrolling execution trace.</p>
 */
public class SimulatorUI extends JFrame {

    private final Assembler assembler = new Assembler();
    private final Simulator sim = new Simulator();
    private Assembler.Program program;

    private final JTextArea sourceArea = new JTextArea();
    private final DefaultListModel<String> listingModel = new DefaultListModel<>();
    private final JList<String> listingList = new JList<>(listingModel);
    private final JTextArea traceArea = new JTextArea();

    private final JLabel accLabel = valueLabel();
    private final JLabel bLabel = valueLabel();
    private final JLabel pcLabel = valueLabel();
    private final JLabel spLabel = valueLabel();
    private final JLabel pswLabel = valueLabel();
    private final JLabel[] rLabels = new JLabel[8];
    private final JLabel cyLabel = flagLabel();
    private final JLabel acLabel = flagLabel();
    private final JLabel ovLabel = flagLabel();
    private final JLabel pLabel = flagLabel();
    private final JLabel statusLabel = new JLabel("Idle - load a program to begin.");
    private final JLabel currentInstrLabel = valueLabel();

    private final JButton loadBtn = new JButton("Load");
    private final JButton resetBtn = new JButton("Reset");
    private final JButton stepBtn = new JButton("Step");
    private final JButton runBtn = new JButton("Run");
    private final JButton stopBtn = new JButton("Stop");

    private Timer runTimer;

    public SimulatorUI() {
        super("MS51FB9AE Simulator - Week 2 Prototype");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));
        ((JComponent) getContentPane()).setBorder(new EmptyBorder(8, 8, 8, 8));

        add(buildToolbar(), BorderLayout.NORTH);
        add(buildCenter(), BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);

        wireActions();
        sourceArea.setText(DEFAULT_PROGRAM);
        setButtonsForUnloaded();
        refreshView(null);

        setSize(1024, 720);
        setLocationRelativeTo(null);
    }

    /* ------------------------------------------------------------------ */
    /*  Layout                                                           */
    /* ------------------------------------------------------------------ */

    private JComponent buildToolbar() {
        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        for (JButton b : new JButton[]{loadBtn, resetBtn, stepBtn, runBtn, stopBtn}) {
            b.setFocusable(false);
            bar.add(b);
            bar.add(Box.createHorizontalStrut(4));
        }
        bar.add(Box.createHorizontalGlue());
        bar.add(new JLabel("Nuvoton MS51FB9AE (8051 core)  "));
        return bar;
    }

    private JComponent buildCenter() {
        // left: source editor + assembled listing
        JPanel left = new JPanel(new BorderLayout(4, 4));

        sourceArea.setFont(mono(13));
        JScrollPane srcScroll = new JScrollPane(sourceArea);
        srcScroll.setBorder(new TitledBorder("Program source (.asm)"));
        srcScroll.setPreferredSize(new Dimension(430, 260));

        listingList.setFont(mono(13));
        listingList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        listingList.setCellRenderer(new ListingRenderer());
        JScrollPane listScroll = new JScrollPane(listingList);
        listScroll.setBorder(new TitledBorder("Assembled program  (>> = next instruction)"));

        JSplitPane leftSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, srcScroll, listScroll);
        leftSplit.setResizeWeight(0.45);
        left.add(leftSplit, BorderLayout.CENTER);

        // right: registers + trace
        JPanel right = new JPanel(new BorderLayout(4, 4));
        right.add(buildRegisterPanel(), BorderLayout.NORTH);

        traceArea.setFont(mono(13));
        traceArea.setEditable(false);
        JScrollPane traceScroll = new JScrollPane(traceArea);
        traceScroll.setBorder(new TitledBorder("Execution trace  (FETCH -> DECODE -> EXECUTE)"));
        right.add(traceScroll, BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        split.setResizeWeight(0.42);
        return split;
    }

    private JComponent buildRegisterPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(new TitledBorder("CPU state"));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(2, 6, 2, 6);
        c.anchor = GridBagConstraints.WEST;

        int row = 0;
        addPair(p, c, row++, "Current instruction", currentInstrLabel);
        addPair(p, c, row++, "ACC (A)", accLabel);
        addPair(p, c, row++, "B", bLabel);
        addPair(p, c, row++, "PC", pcLabel);
        addPair(p, c, row++, "SP", spLabel);
        addPair(p, c, row++, "PSW", pswLabel);

        for (int i = 0; i < 8; i++) {
            rLabels[i] = valueLabel();
            addPair(p, c, row++, "R" + i, rLabels[i]);
        }

        // flags row
        c.gridx = 0; c.gridy = row; c.gridwidth = 1;
        p.add(new JLabel("Flags"), c);
        JPanel flags = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        flags.add(taggedFlag("CY", cyLabel));
        flags.add(taggedFlag("AC", acLabel));
        flags.add(taggedFlag("OV", ovLabel));
        flags.add(taggedFlag("P", pLabel));
        c.gridx = 1; c.gridy = row;
        p.add(flags, c);

        return p;
    }

    private JComponent buildStatusBar() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(new EmptyBorder(4, 4, 0, 4));
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN));
        p.add(statusLabel, BorderLayout.WEST);
        return p;
    }

    /* ------------------------------------------------------------------ */
    /*  Actions                                                          */
    /* ------------------------------------------------------------------ */

    private void wireActions() {
        loadBtn.addActionListener(e -> doLoad());
        resetBtn.addActionListener(e -> doReset());
        stepBtn.addActionListener(e -> doStep());
        runBtn.addActionListener(e -> doRun());
        stopBtn.addActionListener(e -> stopRun());
    }

    private void doLoad() {
        try {
            program = assembler.assemble(sourceArea.getText());
            sim.load(program.code);
            listingModel.clear();
            for (Assembler.Line ln : program.listing) {
                listingModel.addElement(String.format("%04X:  %-8s  %s",
                        ln.address, ln.bytesHex(), ln.source));
            }
            traceArea.setText("");
            append(String.format("Loaded %d bytes (%d instructions). Ready.%n%n",
                    program.code.length, program.listing.size()));
            statusLabel.setText("Loaded. PC = 0000H. Press Step or Run.");
            setButtonsForLoaded();
            refreshView(null);
        } catch (Assembler.AssemblyException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(),
                    "Assembly error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void doReset() {
        stopRun();
        sim.reset();
        traceArea.setText("");
        append("-- CPU reset. PC = 0000H --\n\n");
        statusLabel.setText("Reset. PC = 0000H.");
        setButtonsForLoaded();
        refreshView(null);
    }

    private void doStep() {
        if (program == null) return;
        Simulator.StepResult r = sim.step();
        append(TraceFormatter.format(r));
        refreshView(r);

        if (r.invalid) {
            statusLabel.setText("Halted: " + r.error);
            stepBtn.setEnabled(false);
            runBtn.setEnabled(false);
        } else if (r.halted) {
            statusLabel.setText("Program terminated (HLT) after PC reached " + hex4(r.pcAfter) + ".");
            stepBtn.setEnabled(false);
            runBtn.setEnabled(false);
        } else if (sim.finished()) {
            statusLabel.setText("Reached end of program.");
            stepBtn.setEnabled(false);
            runBtn.setEnabled(false);
        } else {
            statusLabel.setText("Stepped. Next PC = " + hex4(sim.cpu().pc) + ".");
        }
    }

    private void doRun() {
        if (program == null || sim.finished()) return;
        runBtn.setEnabled(false);
        stepBtn.setEnabled(false);
        loadBtn.setEnabled(false);
        resetBtn.setEnabled(false);
        stopBtn.setEnabled(true);
        statusLabel.setText("Running...");

        runTimer = new Timer(180, ev -> {
            if (sim.finished()) { stopRun(); return; }
            Simulator.StepResult r = sim.step();
            append(TraceFormatter.format(r));
            refreshView(r);
            if (r.invalid || r.halted || sim.finished()) {
                stopRun();
                statusLabel.setText(r.halted ? "Program terminated (HLT)."
                        : r.invalid ? ("Halted: " + r.error)
                        : "Reached end of program.");
            }
        });
        runTimer.start();
    }

    private void stopRun() {
        if (runTimer != null) { runTimer.stop(); runTimer = null; }
        stopBtn.setEnabled(false);
        loadBtn.setEnabled(true);
        boolean canStep = program != null && !sim.finished();
        stepBtn.setEnabled(canStep);
        runBtn.setEnabled(canStep);
        resetBtn.setEnabled(program != null);
    }

    /* ------------------------------------------------------------------ */
    /*  View refresh                                                     */
    /* ------------------------------------------------------------------ */

    private void refreshView(Simulator.StepResult last) {
        CPU cpu = sim.cpu();
        accLabel.setText(hex2(cpu.acc));
        bLabel.setText(hex2(cpu.b));
        pcLabel.setText(hex4(cpu.pc));
        spLabel.setText(hex2(cpu.sp));
        pswLabel.setText(hex2(cpu.psw) + "  (bank " + cpu.currentBank() + ")");
        for (int i = 0; i < 8; i++) rLabels[i].setText(hex2(cpu.getR(i)));

        setFlag(cyLabel, cpu.carry());
        setFlag(acLabel, cpu.aux());
        setFlag(ovLabel, cpu.overflow());
        setFlag(pLabel, cpu.parity());

        currentInstrLabel.setText(last != null && last.fetched ? last.mnemonic : "-");

        // highlight the instruction the PC now points at
        int idx = indexOfAddress(cpu.pc);
        if (idx >= 0) {
            listingList.setSelectedIndex(idx);
            listingList.ensureIndexIsVisible(idx);
        } else {
            listingList.clearSelection();
        }
    }

    private int indexOfAddress(int address) {
        if (program == null) return -1;
        for (int i = 0; i < program.listing.size(); i++) {
            if (program.listing.get(i).address == address) return i;
        }
        return -1;
    }

    /* ------------------------------------------------------------------ */
    /*  Small helpers                                                    */
    /* ------------------------------------------------------------------ */

    private void append(String s) {
        traceArea.append(s);
        traceArea.setCaretPosition(traceArea.getDocument().getLength());
    }

    private void setButtonsForUnloaded() {
        loadBtn.setEnabled(true);
        resetBtn.setEnabled(false);
        stepBtn.setEnabled(false);
        runBtn.setEnabled(false);
        stopBtn.setEnabled(false);
    }

    private void setButtonsForLoaded() {
        loadBtn.setEnabled(true);
        resetBtn.setEnabled(true);
        boolean canStep = !sim.finished();
        stepBtn.setEnabled(canStep);
        runBtn.setEnabled(canStep);
        stopBtn.setEnabled(false);
    }

    private static Font mono(int size) {
        return new Font(Font.MONOSPACED, Font.PLAIN, size);
    }

    private static JLabel valueLabel() {
        JLabel l = new JLabel("-");
        l.setFont(mono(13));
        return l;
    }

    private static JLabel flagLabel() {
        JLabel l = new JLabel("0");
        l.setFont(mono(13).deriveFont(Font.BOLD));
        l.setOpaque(true);
        l.setBorder(new EmptyBorder(1, 6, 1, 6));
        return l;
    }

    private static JComponent taggedFlag(String tag, JLabel value) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        p.add(new JLabel(tag + "="));
        p.add(value);
        return p;
    }

    private static void setFlag(JLabel l, boolean on) {
        l.setText(on ? "1" : "0");
        l.setBackground(on ? new Color(0xFFE08A) : new Color(0xEDEDED));
    }

    private static void addPair(JPanel p, GridBagConstraints c, int row, String name, JComponent value) {
        c.gridx = 0; c.gridy = row; c.gridwidth = 1; c.weightx = 0;
        p.add(new JLabel(name), c);
        c.gridx = 1; c.gridy = row; c.weightx = 1;
        p.add(value, c);
    }

    private static String hex2(int v) { return String.format("%02XH", v & 0xFF); }
    private static String hex4(int v) { return String.format("%04XH", v & 0xFFFF); }

    /* ------------------------------------------------------------------ */

    /** Renders a ">>" gutter marker on the currently-selected (next) line. */
    private class ListingRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean selected, boolean focus) {
            JLabel l = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focus);
            l.setText((selected ? " >> " : "    ") + value);
            l.setFont(mono(13));
            if (selected) {
                l.setBackground(new Color(0xCDE8FF));
                l.setForeground(Color.BLACK);
            }
            return l;
        }
    }

    private static final String DEFAULT_PROGRAM =
            "; demo1 - Week 2 demonstration program (MS51FB9AE / 8051)\n" +
            "; add 3 five times via a DJNZ loop, then SUBB, ANL, INC, HLT.\n\n" +
            "        MOV  A,#3\n" +
            "        MOV  R2,A\n" +
            "        MOV  A,#5\n" +
            "        MOV  R1,A\n" +
            "        MOV  A,#0\n" +
            "loop:   ADD  A,R2\n" +
            "        DJNZ R1,loop\n" +
            "        SUBB A,#1\n" +
            "        ANL  A,#0CH\n" +
            "        INC  A\n" +
            "        HLT\n";

    public static void launch() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) { }
        SwingUtilities.invokeLater(() -> new SimulatorUI().setVisible(true));
    }
}
