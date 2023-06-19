import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.util.List;
import org.snmp4j.CommunityTarget;
import org.snmp4j.Snmp;
import org.snmp4j.TransportMapping;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.event.ResponseListener;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.smi.Address;
import org.snmp4j.smi.GenericAddress;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.VariableBinding;
import org.snmp4j.transport.DefaultUdpTransportMapping;
import org.snmp4j.util.DefaultPDUFactory;
import org.snmp4j.util.TreeEvent;
import org.snmp4j.util.TreeListener;
import org.snmp4j.util.TreeUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;



public class SnmpMonitoringGUI extends JFrame {

    private JTextField ipAddressField;
    private JTextField communityField;
    private JTextField oidField;
    private JButton startButton;
    private JTextArea outputArea;
    private JsonNode rfc1213JsonRoot;

    public SnmpMonitoringGUI() {
        setTitle("SNMP Monitoring");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // Create input panel
        JPanel inputPanel = new JPanel(new FlowLayout());
        JLabel ipAddressLabel = new JLabel("IP Address:");
        ipAddressField = new JTextField(15);
        JLabel communityLabel = new JLabel("Community:");
        communityField = new JTextField(15);
        JLabel oidLabel = new JLabel("OID:");
        oidField = new JTextField(15);
        startButton = new JButton("Start");
        inputPanel.add(ipAddressLabel);
        inputPanel.add(ipAddressField);
        inputPanel.add(communityLabel);
        inputPanel.add(communityField);
        inputPanel.add(oidLabel);
        inputPanel.add(oidField);
        inputPanel.add(startButton);

        // Create output panel
        JPanel outputPanel = new JPanel(new BorderLayout());
        JLabel outputLabel = new JLabel("Output:");
        outputArea = new JTextArea(10, 40);
        outputArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(outputArea);
        outputPanel.add(outputLabel, BorderLayout.NORTH);
        outputPanel.add(scrollPane, BorderLayout.CENTER);

        // Add panels to the frame
        add(inputPanel, BorderLayout.NORTH);
        add(outputPanel, BorderLayout.CENTER);

        // Register action listener for the start button
        startButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                startMonitoring();
            }
        });

        // Register mouse listener for the output area
        outputArea.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                int pos = outputArea.viewToModel(evt.getPoint());
                try {
                    int lineStart = javax.swing.text.Utilities.getRowStart(outputArea, pos);
                    int lineEnd = javax.swing.text.Utilities.getRowEnd(outputArea, pos);
                    String selectedLine = outputArea.getText().substring(lineStart, lineEnd);
                    if (selectedLine.contains("=")) {
                        String oid = selectedLine.split("=")[0].trim();
                        displayOIDInformation(oid);
                    }
                } catch (javax.swing.text.BadLocationException ex) {
                    ex.printStackTrace();
                }
            }
        });

        // Load the RFC1213 MIB JSON file
        rfc1213JsonRoot = loadRFC1213JsonFile("E:\\proj_1\\rfc1213.json");
    }
    private JsonNode loadRFC1213JsonFile(String filePath) {
        ObjectMapper objMapper = new ObjectMapper();
        JsonNode jsonNode = null;

        try {
            jsonNode = objMapper.readTree(new File(filePath));
        } catch (IOException e) {
            e.printStackTrace();
        }

        return jsonNode;
    }


    private void startMonitoring() {
        String ipAddress = ipAddressField.getText();
        String community = communityField.getText();
        String oid = oidField.getText();

        try {
            // Create a new SNMP session
            TransportMapping transport = new DefaultUdpTransportMapping();
            Snmp snmp = new Snmp(transport);
            transport.listen();

            // Create the target for SNMP operation
            Address targetAddress = GenericAddress.parse("udp:" + ipAddress + "/161");
            CommunityTarget target = new CommunityTarget();
            target.setCommunity(new OctetString(community));
            target.setAddress(targetAddress);
            target.setRetries(2);
            target.setTimeout(1500);
            target.setVersion(SnmpConstants.version2c);

            // Create a TreeUtils instance for walking the SNMP tree
            TreeUtils treeUtils = new TreeUtils(snmp, new DefaultPDUFactory());

            // Define the OID subtree to walk
            OID subtree = new OID(oid);

            // Perform SNMP walk operation
            List<TreeEvent> events = treeUtils.getSubtree(target, subtree);

            // Process the SNMP walk results
            for (TreeEvent event : events) {
                if (event != null) {
                    if (event.isError()) {
                        outputArea.append("SNMP walk operation failed: " + event.getErrorMessage() + "\n");
                    } else {
                        VariableBinding[] varBindings = event.getVariableBindings();
                        if (varBindings != null) {
                            for (VariableBinding varBinding : varBindings) {
                                String oidStr = varBinding.getOid().toString();
                                String oidName = getOIDName(oidStr);
                                String oidDescription = getOIDDescription(oidStr);

                                outputArea.append("OID: " + oidStr + "\n");
                                outputArea.append("Name: " + oidName + "\n");
                                outputArea.append("Description: " + oidDescription + "\n\n");
                            }
                        }
                    }
                }
            }

            // Close the SNMP session
            snmp.close();
        } catch (IOException e) {
            outputArea.append("Error: " + e.getMessage() + "\n");
        }
    }

    private String getOIDName(String oidStr) {
        JsonNode oidNode = rfc1213JsonRoot.get(oidStr);
        if (oidNode != null) {
            JsonNode nameNode = oidNode.get("name");
            if (nameNode != null) {
                return nameNode.asText();
            }
        }
        return "";
    }

    private String getOIDDescription(String oidStr) {
        JsonNode oidNode = rfc1213JsonRoot.get(oidStr);
        if (oidNode != null) {
            JsonNode descriptionNode = oidNode.get("description");
            if (descriptionNode != null) {
                return descriptionNode.asText();
            }
        }
        return "";
    }

    private void displayOIDInformation(String oid) {
        try {
            String oidName = getOIDName(oid);
            String oidDescription = getOIDDescription(oid);
            
            String message = "Selected OID: " + oid + "\n";
            message += "Name: " + oidName + "\n";
            message += "Description: " + oidDescription + "\n";

            JOptionPane.showMessageDialog(this, message, "OID Information", JOptionPane.INFORMATION_MESSAGE);
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Invalid OID: " + oid, "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                SnmpMonitoringGUI gui = new SnmpMonitoringGUI();
                gui.pack();
                gui.setVisible(true);
            }
        });
    }
}
