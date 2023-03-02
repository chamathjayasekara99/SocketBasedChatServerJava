package chatserver;


import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;

import javax.swing.DefaultListModel;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;

/**
 * A simple Swing-based client for the chat server. Graphically it is a frame
 * with a text field for entering messages and a textarea to see the whole
 * dialog.
 *
 * The client follows the Chat Protocol which is as follows. When the server
 * sends "SUBMITNAME" the client replies with the desired screen name. The
 * server will keep sending "SUBMITNAME" requests as long as the client submits
 * screen names that are already in use. When the server sends a line beginning
 * with "NAMEACCEPTED" the client is now allowed to start sending the server
 * arbitrary strings to be broadcast to all chatters connected to the server.
 * When the server sends a line beginning with "MESSAGE " then all characters
 * following this string should be displayed in its message area.
 */
public class ChatClient {

	private BufferedReader in;
	private  PrintWriter out;
	private JFrame frame = new JFrame("Chatter");
	private JTextField textField = new JTextField(40);
	private JTextArea messageArea = new JTextArea(8, 40);
	private DefaultListModel model = new DefaultListModel();
	private ArrayList<String> arrayList = new ArrayList<String>();
	private String receiver="";
	private ArrayList<String> receiverList = new ArrayList<String>();
	private JList list = new JList(model);
	private JCheckBox checkBox = new JCheckBox("Broadcast");

	/**
	 * Constructs the client by laying out the GUI and registering a listener with
	 * the textfield so that pressing Return in the listener sends the textfield
	 * contents to the server. Note however that the textfield is initially NOT
	 * editable, and only becomes editable AFTER the client receives the
	 * NAMEACCEPTED message from the server.
	 */
	public ChatClient() {

		// Layout GUI
		textField.setEditable(false);
		messageArea.setEditable(false);

		frame.getContentPane().add(textField, "North");
		frame.getContentPane().add(new JScrollPane(messageArea), "Center");
		frame.getContentPane().add(new JScrollPane(list), "West");

		frame.getContentPane().add(checkBox, "South");
		frame.pack();


		
		//Enabling multiple selection in the list
		list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		//set chekbox as selected
		checkBox.setSelected(true);

		textField.addActionListener(new ActionListener() {
			/**
			 * Responds to pressing the enter key in the textfield by sending the contents
			 * of the text field to the server. Then clear the text area in preparation for
			 * the next message.
			 */
			@Override
			public void actionPerformed(ActionEvent e) {
				
				/**
				 * Check whether client is broadcasting
                 * Ensure client select at least one user before doing peer to peer or to or multicasting
				 * Stream out the the message with receiver or receiver list
				 */
				if (!checkBox.isSelected()) {
					if (list.isSelectionEmpty()) {
                        JOptionPane.showMessageDialog(frame, "Please select at least one user to send a private message.");
                        return;
                    }
					int selectedIndices[] = list.getSelectedIndices();
					//Check the number of receivers selected for peer to peer or multicasting

					if(selectedIndices.length > 1) {
						//Add each receiver to the receiverList using writer_index and list model list
						for (int writer_index : selectedIndices) {
							receiverList.add((String) list.getModel().getElementAt(writer_index));

						}
						//Stream out the message with receiverList
						out.println("ACTIVELIST " + receiverList + "MESSAGE " + textField.getText());
						//empty receiverList
						receiverList.clear();
						
					}else {
						//get the selected index
						int selectedIndex = list.getSelectedIndex();
						
						/*
						 * check if the selected index is valid
						 * if not, display a message and return
						*/
					    if (selectedIndex < 0) {
					        JOptionPane.showMessageDialog(frame, "Please select a user to send a private message.");
					        return;
					    }
						//get the selected receiver
						receiver = (String) list.getModel().getElementAt(selectedIndex);
						//Stream out the message with receiver
						out.println("ACTIVELIST MESSAGE " + receiver + ">>" + textField.getText());
						//empty receiver
					    receiver = "";
						
					}						
				
				}
				//Check if client is broadcasting
				else if(checkBox.isSelected()){
					//Stream out the message with BROADCAST
					String msg = "BROADCAST " + textField.getText();
					out.println(msg);
					
				}
			    //empty the text field
				textField.setText("");
				//clear the selection
				list.clearSelection();
			}
		});

	}

	/**
	 * Prompt for and return the address of the server.
	 */
	private String getServerAddress() {
		return JOptionPane.showInputDialog(frame, "Enter IP Address of the Server:", "Welcome to the Chatter",
				JOptionPane.QUESTION_MESSAGE);
	}

	/**
	 * Prompt for and return the desired screen name.
	 */
	private String getName() {
		return JOptionPane.showInputDialog(frame, "Choose a screen name:", "Screen name selection",
				JOptionPane.PLAIN_MESSAGE);
	}

	/**
	 * Connects to the server then enters the processing loop.
	 */
	private void run() throws IOException {

		// Make connection and initialize streams
		String serverAddress = getServerAddress();
		Socket socket = new Socket(serverAddress, 9001);
		try {
			
		
				in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
				out = new PrintWriter(socket.getOutputStream(), true);
					// Process all messages from server, according to the protocol.
			   while (true) {
			   //read the message from server
				String line = in.readLine();
			      
				//check whether the message contains SUBMITNAME string
				//if yes, get the client name and stream out the name to the server
			   	if (line.startsWith("SUBMITNAME")) {
					String clientName = getName();
					out.println(clientName);
					frame.setTitle(clientName);
				//check whether the message contains NAMEACCEPTED string
				//if yes, set the textfield to editable
				} else if (line.startsWith("NAMEACCEPTED")) {
					textField.setEditable(true);
					/*
					 *check whether the message contains MESSAGE string
					 *if yes, append the message to the message area 
					 */
				} else if (line.startsWith("MESSAGE")) {
					messageArea.append(line.substring(8) + "\n");
				}
				/*
				* check whether the message contains ACTIVELIST string
				* if yes, remove all elements from the list model
				* then split the message to get the list of active users
				* add each user to the list model
				*/
				else if (line.substring(line.indexOf("ACTIVELIST")) != null) {
			        
					model.removeAllElements();
					
					line = line.substring(12, line.length() - 1);
					String[] arrayOfWriters = line.split(",");
					int i = 0;
					
					for (String writer : arrayOfWriters) {
						model.add(i, writer.strip());
						i++;
					}
				}else {
					
				}
			}
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			socket.close();
		}
		
	
	}

	/**
	 * Runs the client as an application with a closeable frame.
	 */
	public static void main(String[] args) throws Exception {
		ChatClient client = new ChatClient();

		client.frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		client.frame.setVisible(true);
		client.run();
	}
}