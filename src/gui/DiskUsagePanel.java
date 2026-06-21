package gui;

import disk.Disk;
import disk.DiskConstants;
import fs.FileSystem;
import javax.swing.*;
import java.awt.*;

/** 磁盘使用情况可视化（128 块网格） */
public class DiskUsagePanel extends JDialog {

    public DiskUsagePanel(JFrame parent, FileSystem fs, Disk disk) {
        super(parent, "Disk Usage", true);
        JPanel mp = new JPanel(new BorderLayout(10, 10));
        mp.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        mp.add(new JLabel("Disk Block Map (128 x 64B = 8KB)", SwingConstants.CENTER) {{
            setFont(new Font("SansSerif", Font.BOLD, 14));
        }}, BorderLayout.NORTH);

        JPanel grid = new JPanel(new GridLayout(8, 16, 2, 2));
        int[] fat = fs.getFATSnapshot();
        for (int i = 0; i < 128; i++) {
            JPanel cell = new JPanel();
            cell.setPreferredSize(new Dimension(45, 30));
            if (i <= 1) cell.setBackground(new Color(255, 200, 100));
            else if (i == 2) cell.setBackground(new Color(100, 200, 255));
            else if (fat[i] == 0) cell.setBackground(new Color(200, 255, 200));
            else if (fat[i] == 0xFF) cell.setBackground(new Color(255, 180, 180));
            else cell.setBackground(new Color(255, 220, 150));
            cell.setBorder(BorderFactory.createLineBorder(Color.GRAY));
            cell.add(new JLabel(String.valueOf(i), SwingConstants.CENTER));
            cell.setToolTipText("Block " + i + ": FAT=" + fat[i]);
            grid.add(cell);
        }
        mp.add(new JScrollPane(grid), BorderLayout.CENTER);

        JPanel legend = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 5));
        legend.add(lg("FAT", new Color(255, 200, 100)));
        legend.add(lg("Root", new Color(100, 200, 255)));
        legend.add(lg("Free", new Color(200, 255, 200)));
        legend.add(lg("Used", new Color(255, 180, 180)));
        legend.add(lg("Link", new Color(255, 220, 150)));
        mp.add(legend, BorderLayout.SOUTH);

        JPanel stats = new JPanel();
        int free = disk.getFreeBlockCount();
        stats.add(new JLabel("Free: " + free + " blocks (" + free * 64 + "B) | "));
        stats.add(new JLabel("Used: " + (128 - free - 2) + " blocks"));
        mp.add(stats, BorderLayout.SOUTH);

        JButton close = new JButton("Close");
        close.addActionListener(e -> dispose());
        JPanel bp = new JPanel(); bp.add(close); mp.add(bp, BorderLayout.SOUTH);

        add(mp); pack(); setLocationRelativeTo(parent);
    }

    private JPanel lg(String t, Color c) {
        JPanel p = new JPanel(new BorderLayout(5,0));
        JPanel b = new JPanel(); b.setPreferredSize(new Dimension(16, 16));
        b.setBackground(c); b.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        p.add(b, BorderLayout.WEST); p.add(new JLabel(t), BorderLayout.CENTER); return p;
    }
}
