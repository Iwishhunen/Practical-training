package gui;

import user.UserManager;
import javax.swing.*;
import java.awt.*;

/** 登录/注册对话框 */
public class LoginDialog extends JDialog {

    private final UserManager userManager;
    private boolean loginSuccessful = false;

    public LoginDialog(JFrame parent, UserManager um) {
        super(parent, "Multi-User File System - Login", true);
        this.userManager = um;
        initUI();
        pack();
        setLocationRelativeTo(parent);
        setResizable(false);
    }

    private void initUI() {
        JPanel mp = new JPanel(new BorderLayout(10, 10));
        mp.setBorder(BorderFactory.createEmptyBorder(20, 30, 20, 30));

        JLabel title = new JLabel("Multi-User File System", SwingConstants.CENTER);
        title.setFont(new Font("SansSerif", Font.BOLD, 20));
        mp.add(title, BorderLayout.NORTH);

        JPanel ip = new JPanel(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(5, 5, 5, 5); g.fill = GridBagConstraints.HORIZONTAL;
        g.gridx = 0; g.gridy = 0; ip.add(new JLabel("Username:"), g);
        g.gridx = 1; g.weightx = 1;
        JTextField uf = new JTextField(15); ip.add(uf, g);
        g.gridx = 0; g.gridy = 1; g.weightx = 0; ip.add(new JLabel("Password:"), g);
        g.gridx = 1; g.weightx = 1;
        JPasswordField pf = new JPasswordField(15); ip.add(pf, g);
        mp.add(ip, BorderLayout.CENTER);

        JPanel bp = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
        JButton loginBtn = new JButton("Login");
        JButton regBtn = new JButton("Register");
        JButton guestBtn = new JButton("Guest");
        bp.add(loginBtn); bp.add(regBtn); bp.add(guestBtn);

        JLabel status = new JLabel(" ", SwingConstants.CENTER);
        status.setForeground(Color.RED);
        JPanel sp = new JPanel(new BorderLayout());
        sp.add(bp, BorderLayout.CENTER); sp.add(status, BorderLayout.SOUTH);
        mp.add(sp, BorderLayout.SOUTH);

        loginBtn.addActionListener(e -> {
            String un = uf.getText().trim(); String pw = new String(pf.getPassword());
            if (un.isEmpty()) { status.setText("Enter username"); return; }
            String r = userManager.login(un, pw);
            if (r.startsWith("Login ok")) { loginSuccessful = true; dispose(); }
            else status.setText(r);
        });
        regBtn.addActionListener(e -> {
            String un = uf.getText().trim(); String pw = new String(pf.getPassword());
            if (un.isEmpty() || pw.isEmpty()) { status.setText("Fill all fields"); return; }
            String r = userManager.register(un, pw);
            status.setText(r);
            status.setForeground(r.startsWith("Register ok") ? new Color(0, 128, 0) : Color.RED);
        });
        guestBtn.addActionListener(e -> { loginSuccessful = false; dispose(); });
        pf.addActionListener(e -> loginBtn.doClick());
        getRootPane().setDefaultButton(loginBtn);
        add(mp);
    }

    public boolean isLoginSuccessful() { return loginSuccessful; }
}
