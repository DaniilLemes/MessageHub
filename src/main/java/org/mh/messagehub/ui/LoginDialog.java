package org.mh.messagehub.ui;


import javax.swing.*;
import java.awt.*;

import static org.mh.messagehub.ui.MessageHub.*;

public class LoginDialog extends JDialog {
    private JTextField tfNickname;
    private JComboBox<String> cbRooms;
    private boolean succeeded;

    public LoginDialog(Frame parent) {
        super(parent, "Dołącz do MessageHub", true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(350, 200);
        setLocationRelativeTo(parent);

        String[] existingRooms = { "General", "Tech", "Muzyka", "Gry" };

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(COLOR_BACKGROUND);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 5, 5, 5);

        JLabel lbNickname = new JLabel("Nick:");
        lbNickname.setForeground(COLOR_WHITE);
        lbNickname.setFont(new Font("Arial", Font.PLAIN, 14));
        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(lbNickname, gbc);

        tfNickname = new JTextField(20);
        tfNickname.setBackground(COLOR_DARK_GRAY);
        tfNickname.setForeground(COLOR_WHITE);
        tfNickname.setCaretColor(COLOR_WHITE);
        gbc.gridx = 1;
        gbc.gridy = 0;
        panel.add(tfNickname, gbc);

        JLabel lbRoom = new JLabel("Pokój:");
        lbRoom.setForeground(COLOR_WHITE);
        lbRoom.setFont(new Font("Arial", Font.PLAIN, 14));
        gbc.gridx = 0;
        gbc.gridy = 1;
        panel.add(lbRoom, gbc);

        cbRooms = new JComboBox<>(existingRooms);
        cbRooms.setEditable(true);
        cbRooms.setBackground(COLOR_DARK_GRAY);
        cbRooms.setForeground(COLOR_WHITE);
        JTextField editor = (JTextField) cbRooms.getEditor().getEditorComponent();
        editor.setBackground(COLOR_DARK_GRAY);
        editor.setForeground(COLOR_WHITE);
        editor.setCaretColor(COLOR_WHITE);
        gbc.gridx = 1;
        gbc.gridy = 1;
        panel.add(cbRooms, gbc);

        JButton btnJoin = new JButton("Dołącz");
        btnJoin.setBackground(COLOR_ORANGE);
        btnJoin.setForeground(COLOR_BLACK);
        btnJoin.setFocusPainted(false);
        btnJoin.setFont(new Font("Arial", Font.BOLD, 14));
        gbc.gridx = 1;
        gbc.gridy = 2;
        gbc.anchor = GridBagConstraints.CENTER;
        panel.add(btnJoin, gbc);

        btnJoin.addActionListener(e -> {
            String nick = tfNickname.getText().trim();
            String room = ((JTextField) cbRooms.getEditor().getEditorComponent()).getText().trim();
            if (nick.isEmpty()) {
                JOptionPane.showMessageDialog(LoginDialog.this,
                        "Musisz podać nick.", "Błąd", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (room.isEmpty()) {
                JOptionPane.showMessageDialog(LoginDialog.this,
                        "Musisz podać nazwę pokoju.", "Błąd", JOptionPane.ERROR_MESSAGE);
                return;
            }
            succeeded = true;
            dispose();
        });

        getContentPane().add(panel);
    }

    public String getNickname() {
        return tfNickname.getText().trim();
    }

    public String getRoom() {
        return ((JTextField) cbRooms.getEditor().getEditorComponent()).getText().trim();
    }

    public boolean isSucceeded() {
        return succeeded;
    }
}