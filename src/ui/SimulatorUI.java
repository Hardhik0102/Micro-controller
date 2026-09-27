package com.team.ms51sim.ui;

import com.team.ms51sim.Assembler;
import com.team.ms51sim.CPU;
import com.team.ms51sim.FifoQueue;
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
 * execution status and a scrolling execution trace. Week 3 adds live views of
 * internal RAM, the hardware stack and the FIFO queue.</p>
 */
public class SimulatorUI extends JFrame {

    private final Assembler assembler = new Assembler();
    private final Simulator sim = new Simulator();
    private Assembler.Program program;

    private final JTextArea sourceArea = new JTextArea();
    private final DefaultListModel<String> listingModel = new DefaultListModel<>();
    private final JList<String> listingList = new JList<>(listingModel);
    private final JTextArea traceArea = new JTextArea();
    private final JTextArea memoryArea = new JTextArea();
    private final JTextArea stackArea = new JTextArea();
    private final JTextArea queueArea = new JTextArea();

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

    private static final Color BANNER_BG = new Color(0x1B3A63);
    private static final Color BANNER_FG = Color.WHITE;
    private static final Color MEMORY_ACCENT = new Color(0x1B5FAE);  // blue
    private static final Color STACK_ACCENT  = new Color(0x1E7A3C);  // green
    private static final Color QUEUE_ACCENT  = new Color(0xB0530C);  // orange

    public SimulatorUI() {
        super("MS51FB9AE Simulator - Week 3 (CPU + Memory + Stack + FIFO Queue)");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));
        ((JComponent) getContentPane()).setBorder(new EmptyBorder(0, 8, 8, 8));

        JPanel north = new JPanel(new BorderLayout());
        north.add(buildBanner(), BorderLayout.NORTH);
        north.add(buildToolbar(), BorderLayout.SOUTH);
        add(north, BorderLayout.NORTH);
        add(buildCenter(), BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);

        wireActions();
        sourceArea.setText(DEFAULT_PROGRAM);
        setButtonsForUnloaded();
        refreshView(null);

        setSize(1200, 760);
        setLocationRelativeTo(null);
    }

    /* ------------------------------------------------------------------ */
    /*  Layout                                                           */
    /* ------------------------------------------------------------------ */

    /** Dark banner across the top so Week 3 is visually unmistakable at a glance. */
    private JComponent buildBanner() {
        JPanel banner = new JPanel(new BorderLayout());
        banner.setBackground(BANNER_BG);
        banner.setBorder(new EmptyBorder(8, 12, 8, 12));

        JLabel title = new JLabel("MS51FB9AE SIMULATOR  —  WEEK 3");
        title.setForeground(BANNER_FG);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));

        JLabel subtitle = new JLabel("New this week:  Memory read/write   ·   Stack (SP, PUSH/POP)   ·   FIFO Queue (ENQ/DEQ)");
        subtitle.setForeground(new Color(0xCFE0F5));
        subtitle.setFont(subtitle.getFont().deriveFont(Font.PLAIN, 12f));

        JPanel text = new JPanel(new GridLayout(2, 1));
        text.setOpaque(false);
        text.add(title);
        text.add(subtitle);
        banner.add(text, BorderLayout.WEST);

        JLabel chip = pillLabel("CPU + MEMORY + STACK + QUEUE");
        banner.add(chip, BorderLayout.EAST);
        return banner;
    }

    private static JLabel pillLabel(String text) {
        JLabel l = new JLabel(text);
        l.setOpaque(true);
        l.setBackground(new Color(0x2E5B96));
        l.setForeground(Color.WHITE);
        l.setFont(l.getFont().deriveFont(Font.BOLD, 11f));
        l.setBorder(new EmptyBorder(5, 10, 5, 10));
        return l;
    }

    private JComponent buildToolbar() {
        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        bar.setBackground(new Color(0xE9EEF5));
        bar.setBorder(new EmptyBorder(4, 4, 4, 4));

        Color[] accents = {
                new Color(0x2E7D32), new Color(0x8D6E00), new Color(0x1565C0),
                new Color(0x1565C0), new Color(0xB71C1C)
        };
        JButton[] buttons = {loadBtn, resetBtn, stepBtn, runBtn, stopBtn};
        for (int i = 0; i < buttons.length; i++) {
            JButton b = buttons[i];
            b.setFocusable(false);
            b.setFont(b.getFont().deriveFont(Font.BOLD));
            b.setForeground(accents[i]);
            bar.add(b);
            bar.add(Box.createHorizontalStrut(6));
        }
        bar.add(Box.createHorizontalGlue());
        JLabel chip = new JLabel("Nuvoton MS51FB9AE (8051 core)");
        chip.setFont(chip.getFont().deriveFont(Font.ITALIC));
        bar.add(chip);
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

        // right: registers + (memory/stack/queue tabs) + trace
        JPanel right = new JPanel(new BorderLayout(4, 4));
        right.add(buildRegisterPanel(), BorderLayout.NORTH);

        traceArea.setFont(mono(13));
        traceArea.setEditable(false);
        JScrollPane traceScroll = new JScrollPane(traceArea);
        traceScroll.setBorder(new TitledBorder("Execution trace  (FETCH -> DECODE -> EXECUTE)"));

        JSplitPane rightSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                buildMemStackQueuePanel(), traceScroll);
        rightSplit.setResizeWeight(0.5);
        right.add(rightSplit, BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        split.setResizeWeight(0.40);
        return split;
    }

    /**
     * Week 3's headline addition: Memory, Stack and the FIFO Queue shown
     * <b>side by side, always visible</b> (no tabs to click through) - each
     * with its own colour-coded border so the three new features stand out
     * from the Week 2 layout.
     */
    private JComponent buildMemStackQueuePanel() {
        for (JTextArea a : new JTextArea[]{memoryArea, stackArea, queueArea}) {
            a.setFont(mono(12));
            a.setEditable(false);
        }
        JComponent memPane   = coloredPane(memoryArea, "Memory (RAM)",   MEMORY_ACCENT);
        JComponent stackPane = coloredPane(stackArea,  "Stack",          STACK_ACCENT);
        JComponent queuePane = coloredPane(queueArea,  "FIFO Queue",     QUEUE_ACCENT);

        JSplitPane inner = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, stackPane, queuePane);
        inner.setResizeWeight(0.5);
        JSplitPane outer = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, memPane, inner);
        outer.setResizeWeight(0.4);
        return outer;
    }

    private static JComponent coloredPane(JTextArea area, String title, Color accent) {
        JScrollPane sp = new JScrollPane(area);
        TitledBorder tb = new TitledBorder(BorderFactory.createLineBorder(accent, 2), title);
        tb.setTitleColor(accent);
        tb.setTitleFont(sp.getFont().deriveFont(Font.BOLD, 12f));
        sp.setBorder(tb);
        return sp;
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

        memoryArea.setText(renderMemory(cpu));
        memoryArea.setCaretPosition(0);
        stackArea.setText(renderStack(cpu));
        stackArea.setCaretPosition(0);
        queueArea.setText(renderQueue(cpu));
        queueArea.setCaretPosition(0);

        // highlight the instruction the PC now points at
        int idx = indexOfAddress(cpu.pc);
        if (idx >= 0) {
            listingList.setSelectedIndex(idx);
            listingList.ensureIndexIsVisible(idx);
        } else {
            listingList.clearSelection();
        }
    }

    private static String renderMemory(CPU cpu) {
        StringBuilder sb = new StringBuilder();
        sb.append("Internal data RAM  00H - 7FH\n");
        sb.append("      +0 +1 +2 +3 +4 +5 +6 +7  +8 +9 +A +B +C +D +E +F\n");
        for (int row = 0; row < 8; row++) {
            sb.append(String.format("%02XH:  ", row * 16));
            for (int col = 0; col < 16; col++) {
                sb.append(String.format("%02X ", cpu.ram[row * 16 + col] & 0xFF));
                if (col == 7) sb.append(' ');
            }
            sb.append('\n');
        }
        sb.append("\nR0-R7 (bank ").append(cpu.currentBank()).append(") live at ")
          .append(String.format("%02XH-%02XH", cpu.currentBank() * 8, cpu.currentBank() * 8 + 7))
          .append('\n');
        sb.append("Stack grows upward from 08H (SP resets to 07H)\n");
        sb.append("Suggested scratch area for demo programs: 30H and up\n");
        return sb.toString();
    }

    private static String renderStack(CPU cpu) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("SP = %02XH%n%n", cpu.sp & 0xFF));
        if ((cpu.sp & 0xFF) < 0x08) {
            sb.append("(stack empty - nothing pushed yet)\n");
        } else {
            sb.append("addr   value\n");
            for (int a = cpu.sp & 0xFF; a >= 0x08; a--) {
                sb.append(String.format("%02XH:   %02XH%s%n",
                        a, cpu.ram[a] & 0xFF, a == (cpu.sp & 0xFF) ? "    <- top (SP)" : ""));
            }
        }
        sb.append("\nPUSH: SP = SP + 1, then RAM[SP] = value\n");
        sb.append("POP : value = RAM[SP], then SP = SP - 1\n");
        return sb.toString();
    }

    private static String renderQueue(CPU cpu) {
        FifoQueue q = cpu.queue;
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("FIFO queue  (circular, capacity %d)%n%n", q.capacity()));
        int[] s = q.snapshot();
        sb.append("front -> ");
        if (s.length == 0) {
            sb.append("(empty)");
        } else {
            for (int v : s) sb.append(String.format("[%02X] ", v & 0xFF));
        }
        sb.append("<- back\n\n");
        sb.append(String.format("size    : %d / %d%n", q.size(), q.capacity()));
        sb.append(String.format("EMPTY   : %s%n", q.isEmpty() ? "yes" : "no"));
        sb.append(String.format("FULL    : %s%n", q.isFull() ? "yes" : "no"));
        sb.append(String.format("status  : %02XH   (bit0 = empty, bit1 = full, bits 4-7 = count)%n",
                q.statusByte()));
        sb.append("\nENQ A: add ACC at the back   (CY = 1 if full)\n");
        sb.append("DEQ A: take from the front into ACC   (CY = 1 if empty)\n");
        return sb.toString();
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
            "; week3-demo.asm - exercises Memory, Stack and the FIFO Queue\n" +
            "; (MS51FB9AE / 8051 core)\n\n" +
            "        MOV  A,#7EH      ; ACC = 7EH\n" +
            "        MOV  30H,A       ; memory write: RAM[30H] = 7EH\n" +
            "        PUSH 30H         ; stack: push RAM[30H]\n" +
            "        MOV  A,#00H      ; ACC = 00H\n" +
            "        POP  A           ; stack: ACC = 7EH back off the stack\n\n" +
            "        MOV  A,#11H\n" +
            "        ENQ  A           ; queue: [11]\n" +
            "        MOV  A,#22H\n" +
            "        ENQ  A           ; queue: [11 22]\n" +
            "        MOV  A,#33H\n" +
            "        ENQ  A           ; queue: [11 22 33]\n\n" +
            "        DEQ  A           ; ACC = 11H  (FIFO - first in, first out)\n" +
            "        MOV  31H,A       ; RAM[31H] = 11H\n" +
            "        DEQ  A           ; ACC = 22H\n" +
            "        MOV  32H,A       ; RAM[32H] = 22H\n" +
            "        HLT              ; queue still holds [33]\n";

    public static void launch() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) { }
        SwingUtilities.invokeLater(() -> new SimulatorUI().setVisible(true));
    }
}
