package chatserver;


import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;



/**
 * A multithreaded chat room server. When a client connects the server requests
 * a screen name by sending the client the text "SUBMITNAME", and keeps
 * requesting a name until a unique one is received. After a client submits a
 * unique name, the server acknowledges with "NAMEACCEPTED". Then all messages
 * from that client will be broadcast to all other clients that have submitted a
 * unique screen name. The broadcast messages are prefixed with "MESSAGE ".
 *
 * Because this is just a teaching example to illustrate a simple chat server,
 * there are a few features that have been left out. Two are very useful and
 * belong in production code:
 *
 * 1. The protocol should be enhanced so that the client can send clean
 * disconnect messages to the server.
 *
 * 2. The server should do some logging.
 */
public class ChatServer {

	/**
	 * The port that the server listens on.
	 */
	private static final int PORT = 9001;

	/**
	 * The set of all names of clients in the chat room. Maintained so that we can
	 * check that new clients are not registering name already in use.
	 */
	private static HashSet<String> names = new HashSet<String>();

	private static HashMap<Socket, String> pointToPoint = new HashMap<Socket, String>();
	/**
	 * The set of all the print writers for all the clients. This set is kept so we
	 * can easily broadcast messages.
	 */
	private static HashSet<PrintWriter> writers = new HashSet<PrintWriter>();


	private static ArrayList<String> activeList;

	/**
	 * The appplication main method, which just listens on a port and spawns handler
	 * threads.
	 */
	public static void main(String[] args) throws Exception {
		System.out.println("The chat server is running.");
		ServerSocket listener = new ServerSocket(PORT);
		try {
			while (true) {
				/*
				 *  wait for a client to connect
				 * when a client connects, create a new thread to handle the client
				 * and pass the socket to the thread
				 * the thread will handle the client and the server will wait for another client to connect
				 */ 
				Socket connectoinSocket = listener.accept();
				Thread handlerThread = new Thread(new Handler(connectoinSocket));
				handlerThread.start();
			}
		} finally {
			try {
				listener.close();
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	/**
	 * A handler thread class. Handlers are spawned from the listening loop and are
	 * responsible for a dealing with a single client and broadcasting its messages.
	 */
	private static class Handler implements Runnable {
		private String name;
		private Socket socket;
		private BufferedReader in;
		private PrintWriter out, writer;

		/**
		 * Constructs a handler thread, squirreling away the socket. All the interesting
		 * work is done in the run method.
		 */
		public Handler(Socket socket) {
			this.socket = socket;
		}

		/**
		 * Services this thread's client by repeatedly requesting a screen name until a
		 * unique one has been submitted, then acknowledges the name and registers the
		 * output stream for the client in a global set, then repeatedly gets inputs and
		 * broadcasts them.
		 */
		public void run() {
			try {
				//create a reader and a writer for the socket
				
				in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
				out = new PrintWriter(socket.getOutputStream(), true);

				/*
				* Request a name from this client. Keep requesting until
				* a name is submitted that is not already used. Note that
				* checking for the existence of a name and adding the name
				* must be done while locking the set of names.
				*/ 
				while (true) {
					out.println("SUBMITNAME");
					name = in.readLine();
					if (name == null) {
						return;
					}

					// Ensure the thread safety of the
					// the shared variable 'names'
					synchronized (names) {

						if (!names.contains(name)) {
							names.add(name);
							pointToPoint.put(socket, name);
							
							break;
						}
					}
				}

				// Now that a successful name has been chosen, add the
				// socket's print writer to the set of all writers so
				// this client can receive broadcast messages.
				out.println("NAMEACCEPTED");
				
				writers.add(out);
		        System.out.println("New client Conected");
			
               
				// Send the list of active clients to the client
				// who just connected.
				
		
					// Iterate through the map of sockets and names
					for (Map.Entry<Socket, String> entry : pointToPoint.entrySet()) {
						Socket oneSocket = entry.getKey();
						String mySocketName = entry.getValue();

						writer = new PrintWriter(oneSocket.getOutputStream(), true);

						activeList = new ArrayList<String>();
						
							for (String writer_name : names) {
                                //add all the names except the name of the client who just connected
								if (!mySocketName.equals(writer_name)) {
									activeList.add(writer_name);
								}
							}

						

						writer.println("ACTIVELIST " + activeList);

					}
				

				// Accept messages from this client and broadcast them.
				// Ignore other clients that cannot be broadcasted to.

				while (true) {
					String input = in.readLine();
					if (input == null) {
						return;
					}
					//point to point one receiver and one sender
					if (input.contains(">>")) {
						
                        //extract the receivers name
						String recieverName = input.substring(input.indexOf("MESSAGE ")+8, input.indexOf(">>"));
						
						
						//make this writer synchronized
				
						//loop over all other clients
							for (Map.Entry<Socket, String> entry : pointToPoint.entrySet()) {

								Socket oneSocket = entry.getKey();
								String Socketname = entry.getValue();
     
								try {
									writer = new PrintWriter(oneSocket.getOutputStream(), true);
									if (Socketname.equals(recieverName) || Socketname.equals(name)) {

										writer.println("MESSAGE " + name + ": " + input.substring(input.indexOf(">>") + 2));

									}
								} catch (Exception e) {
									// TODO Auto-generated catch block
									System.out.println("issue here");
								}
								/**
								* check whether the client is the sender or the receiver
								*if the client is the sender or the receiver, send the message to the client
								*if the client is neither the sender nor the receiver, do nothing 
								*this is to ensure that the message is  sent to the sender and the receiver
								*and the message is not sent to the other receivers
								*/
						

							}
						

					} else {
                       //check whether the client is multicasting
						if (input.startsWith("ACTIVELIST")) {

				            //list of the message receivers
							String nameList = input.substring(12, (input.indexOf("MESSAGE")) - 1);
							String message = input.substring(input.indexOf("MESSAGE") + 7);
							
							//message receivers
							String[] arrayOfWriters = nameList.split(",");
							//to ensure sender UI has only one message indicating user has messaged
                            boolean isSenderPrinted = false;
							for (String writerName : arrayOfWriters) {
								//make this writer synchronized
							
									//loop over all other clients
									for (Map.Entry<Socket, String> entry : pointToPoint.entrySet()) {

										Socket socket = entry.getKey();
										String Socketname = entry.getValue();

										//output to the corresponding socket of the client
										writer = new PrintWriter(socket.getOutputStream(), true);

										if (writerName.strip().equals(Socketname.strip())
												&& !Socketname.strip().equals(name)) {
											writer.println("MESSAGE " + name + ": " + message);
										}

										// display message to sender's UI only once
										if (Socketname.strip().equals(name) && !isSenderPrinted) {
											// display the message in the sender's UI
											isSenderPrinted = true;
											writer.println("MESSAGE " + name + ": " + message);
										}

									}
								}

							
						}
						//check whether client is broadcasting
						else if(input.startsWith("BROADCAST")) {
							//extract message from the input received from the socket
							String message = input.substring(input.indexOf("BROADCAST") + 9);
							
							//Stream out message to all the clients
							for(PrintWriter writer : writers) {
								writer.println("MESSAGE " +name+": "+ message);
							}								
							
						}

					}
					

				}

			}catch(java.net.SocketException e){
				System.out.println(e);
				System.out.println("Some one client left");
				
			} catch (Exception e) {
				System.out.println(e);
				
			} finally {
				// This client is going down! Remove its name 
			
				if (name != null) {
					names.remove(name);

				try {
					//Removing writers and update the active client list for each client
					
					 synchronized (pointToPoint) {
						 //remove socket from pointToPoint connection
						pointToPoint.remove(socket);
	
						for (Map.Entry<Socket, String> entry : pointToPoint.entrySet()) {
							//Get the socket value and socketName
							Socket oneSocket = entry.getKey();
							String mySocketName = entry.getValue();

							try {
								writer = new PrintWriter(oneSocket.getOutputStream(), true);
							} catch (Exception e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
							//create a new list of active clients
							activeList = new ArrayList<String>();

							for (String writer_name : names) {
								//Add clients to active list for each client
								if (!mySocketName.equals(writer_name)) {
									activeList.add(writer_name);
								}
							}

							//send the active list to the client 
							writer.println("ACTIVELIST " + activeList);

						}
					}
				// Also remove writer from the sets
				 if (out != null) {
						writers.remove(out);
				 }
			
					//close the socket
					socket.close();
				} catch (Exception e) {
					System.out.println(e.getMessage());
					System.out.println(name + " Good Bye");
				}
					
				}
			
			
			}
		}
		
	
	}
}