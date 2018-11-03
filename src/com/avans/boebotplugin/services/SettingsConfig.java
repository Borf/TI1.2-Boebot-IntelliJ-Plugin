package com.avans.boebotplugin.services;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.net.*;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

public class SettingsConfig implements SearchableConfigurable, Configurable.NoScroll {
	private final Project myProject;
	JTextField ipField;
	JList<String> list;


	/**
	 * Invoked by reflection
	 */
	public SettingsConfig (final Project project) {
		myProject = project;
	}
	@NotNull
	@Override
	public String getId() {
		return "boebot.settings";
	}

	@Nls(capitalization = Nls.Capitalization.Title)
	@Override
	public String getDisplayName() {
		return "BoeBot settings";
	}

	@Nullable
	@Override
	public JComponent createComponent() {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.PAGE_AXIS));
		panel.add(new JLabel("BoeBot IP:"));
		JPanel subPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		subPanel.add(ipField = new JTextField());
		ipField.setMinimumSize(new Dimension(400,32));
		panel.add(subPanel);
		JButton scanButton = new JButton("Scan");
		panel.add(scanButton);
		panel.add(new JScrollPane(list = new JList<String>(new DefaultListModel<String>())), BorderLayout.CENTER);

		for(Component c : panel.getComponents())
			((JComponent)c).setAlignmentX(Component.LEFT_ALIGNMENT);
		ipField.setAlignmentX(Component.LEFT_ALIGNMENT);
		list.setEnabled(false);

		scanButton.addActionListener(e ->
		{
			((DefaultListModel<String>)list.getModel()).clear();
			((DefaultListModel<String>)list.getModel()).addElement("loading...");
			new Thread(new Runnable() {
				public void run() {

					Set<String> items = discover();
					((DefaultListModel<String>)list.getModel()).clear();
					for(String element : items)
						((DefaultListModel<String>)list.getModel()).addElement(element);;
					list.setEnabled(true);
				}
			}).start();



		});

		ipField.setText(ServiceManager.getService(myProject, Settings.class).ip);
		return panel;
	}

	@Override
	public boolean isModified() {
		return !ServiceManager.getService(myProject, Settings.class).ip.equals(ipField.getText());
	}

	@Override
	public void apply() throws ConfigurationException {
		ServiceManager.getService(myProject, Settings.class).ip = ipField.getText();

	}

	public void setIpField(String ip) {
		ipField.setText(ip);
	}


	public Set<String> discover() {
		Set<String> response = new HashSet<String>();
		DatagramSocket c = null;
		try {
			c = new DatagramSocket();
			c.setBroadcast(true);

			byte[] sendData = "BOEBOT1.0_DISCOVER".getBytes();

			try {
				DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, InetAddress.getByName("255.255.255.255"), 8888);
				c.send(sendPacket);
				System.out.println(getClass().getName() + ">>> Request packet sent to: 255.255.255.255 (DEFAULT)");
			} catch (Exception e) {
				e.printStackTrace();
			}

			// Broadcast the message over all the network interfaces
			Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
			while (interfaces.hasMoreElements()) {
				NetworkInterface networkInterface = interfaces.nextElement();

				if (networkInterface.isLoopback() || !networkInterface.isUp()) {
					continue; // Don't want to broadcast to the loopback
					// interface
				}

				for (InterfaceAddress interfaceAddress : networkInterface.getInterfaceAddresses()) {
					InetAddress broadcast = interfaceAddress.getBroadcast();
					if (broadcast == null) {
						continue;
					}

					// Send the broadcast package!
					try {
						DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, broadcast, 8888);
						c.send(sendPacket);
					} catch (Exception e) {
					}

					System.out.println(getClass().getName() + ">>> Request packet sent to: " + broadcast.getHostAddress() + "; Interface: " + networkInterface.getDisplayName());
				}
			}
			System.out.println(getClass().getName() + ">>> Done looping over all network interfaces. Now waiting for a reply!");

			c.setSoTimeout(1000);
			for (int i = 0; i < 10; i++) {
				byte[] recvBuf = new byte[15000];
				DatagramPacket receivePacket = new DatagramPacket(recvBuf, recvBuf.length);
				c.receive(receivePacket);

				String message = new String(receivePacket.getData()).trim();
				// We have a response
				System.out.println(getClass().getName() + ">>> Broadcast response from server: " + receivePacket.getAddress().getHostAddress() + ", " + message);

				// Check if the message is correct
				if (message.substring(0, 13).equals("BOEBOT1.0_ACK")) {
					response.add("IP: " + receivePacket.getAddress().toString().substring(1) + "     Serial " + message.substring(14));
				}
				else
					System.out.println("not a boebot");
			}
			// Close the port!
		} catch (SocketTimeoutException e) {
			// expected
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (c != null)
				c.close();
		}
		return response;
	}
}
