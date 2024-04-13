package gossip;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.management.Notification;
import javax.management.NotificationListener;

public class Client implements NotificationListener {

	private ArrayList<Nodo> listaNodos;

	private ArrayList<Nodo> Lista_infectados;

	private int tiempo_gossip; //in ms

	public int tiempo_Limpiando; //in ms

	private Random random;

	private DatagramSocket server;

	private String mi_direccion;

	private Nodo me;

	/**
	 * Setup the client's lists, gossiping parameters, and parse the startup config file.
	 * @throws SocketException
	 * @throws InterruptedException
	 * @throws UnknownHostException
	 */
	public Client() throws SocketException, InterruptedException, UnknownHostException {

		Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
			public void run() {
				System.out.println("adios amigos :( ...");
			}
		}));

		listaNodos = new ArrayList<Nodo>();

		Lista_infectados = new ArrayList<Nodo>();

		tiempo_gossip = 100; // 1 second TODO: make configurable

		tiempo_Limpiando = 10000; // 10 seconds TODO: make configurable

		random = new Random();

		int port = 0;

		String mi_Dir_Ip = InetAddress.getLocalHost().getHostAddress();
		this.mi_direccion = mi_Dir_Ip + ":" + port;

		ArrayList<String> startupHostsList = parseStartupnodos();

		// loop over the initial hosts, and find ourselves
		for (String host : startupHostsList) {

			Nodo nodo = new Nodo(host, 0, this, tiempo_Limpiando);

			if(host.contains(mi_Dir_Ip)) {
				// save our own nodo class so we can increment our heartbeat later
				me = nodo;
				port = Integer.parseInt(host.split(":")[1]);
				this.mi_direccion = mi_Dir_Ip + ":" + port;
				System.out.println("soy " + me);
			}
			listaNodos.add(nodo);
		}

		System.out.println("Original lista de Nodost");
		System.out.println("---------------------");
		for (Nodo nodo : listaNodos) {
			System.out.println(nodo);
		}

		if(port != 0) {
			// TODO: starting the server could probably be moved to the constructor
			// of the receiver thread.
			server = new DatagramSocket(port);
		}
		else {
			// This is bad, so no need proceeding on
			System.err.println("Could not find myself in startup list");
			System.exit(-1);
		}
	}

	/**
	 * In order to have some nodoship lists at startup, we read the IP addresses
	 * and port at a newline delimited config file.
	 * @return List of <IP address:port> Strings
	 */
	private ArrayList<String> parseStartupnodos() {
		ArrayList<String> startupHostsList = new ArrayList<String>();
		File startupConfig = new File("config","startup_nodos");

		try {
			BufferedReader br = new BufferedReader(new FileReader(startupConfig));
			String line;
			while((line = br.readLine()) != null) {
				startupHostsList.add(line.trim());
			}
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return startupHostsList;
	}

	/**
	 * Performs the sending of the nodoship list, after we have
	 * incremented our own heartbeat.
	 */
	private void sendnodoshipList() {

		this.me.setinfectados(me.getinfectados() + 1);

		synchronized (this.listaNodos) {
			try {
				Nodo nodo = getRandomnodo();

				if(nodo != null) {
					ByteArrayOutputStream baos = new ByteArrayOutputStream();
					ObjectOutputStream oos = new ObjectOutputStream(baos);
					oos.writeObject(this.listaNodos);
					byte[] buf = baos.toByteArray();

					String address = nodo.getAddress();
					String host = address.split(":")[0];
					int port = Integer.parseInt(address.split(":")[1]);

					InetAddress dest;
					dest = InetAddress.getByName(host);

					System.out.println("Sending to " + dest);
					System.out.println("---------------------");
					for (Nodo m : listaNodos) {
						System.out.println(m);
					}
					System.out.println("---------------------");
					
					//simulate some packet loss ~25%
					int percentToSend = random.nextInt(100);
					if(percentToSend > 25) {
						DatagramSocket socket = new DatagramSocket();
						DatagramPacket datagramPacket = new DatagramPacket(buf, buf.length, dest, port);
						socket.send(datagramPacket);
						socket.close();
					}
				}

			} catch (IOException e1) {
				e1.printStackTrace();
			}
		}
	}

	/**
	 * Find a random peer from the local nodoship list.
	 * Ensure that we do not select ourselves, and keep
	 * trying 10 times if we do.  Therefore, in the case 
	 * where this client is the only nodo in the list, 
	 * this method will return null
	 * @return nodo random nodo if list is greater than 1, null otherwise
	 */
	private Nodo getRandomnodo() {
		Nodo nodo = null;

		if(this.listaNodos.size() > 1) {
			int tries = 10;
			do {
				int randomNeighborIndex = random.nextInt(this.listaNodos.size());
				nodo = this.listaNodos.get(randomNeighborIndex);
				if(--tries <= 0) {
					nodo = null;
					break;
				}
			} while(nodo.getAddress().equals(this.mi_direccion));
		}
		else {
			System.out.println("estoy solo :( .");
		}

		return nodo;
	}

	/**
	 * The class handles gossiping the nodoship list.
	 * This information is important to maintaining a common
	 * state among all the nodes, and is important for detecting
	 * failures.
	 */
	private class nodoshipGossiper implements Runnable {

		private AtomicBoolean keepRunning;

		public nodoshipGossiper() {
			this.keepRunning = new AtomicBoolean(true);
		}

		@Override
		public void run() {
			while(this.keepRunning.get()) {
				try {
					TimeUnit.MILLISECONDS.sleep(tiempo_gossip);
					sendnodoshipList();
				} catch (InterruptedException e) {
					// TODO: handle exception
					// This nodoship thread was interrupted externally, shutdown
					e.printStackTrace();
					keepRunning.set(false);
				}
			}

			this.keepRunning = null;
		}

	}

	/**
	 * This class handles the passive cycle, where this client
	 * has received an incoming message.  For now, this message
	 * is always the nodoship list, but if you choose to gossip
	 * additional information, you will need some logic to determine
	 * the incoming message.
	 */
	private class AsychronousReceiver implements Runnable {

		private AtomicBoolean keepRunning;

		public AsychronousReceiver() {
			keepRunning = new AtomicBoolean(true);
		}

		@SuppressWarnings("unchecked")
		@Override
		public void run() {
			while(keepRunning.get()) {
				try {
					//XXX: be mindful of this array size for later
					byte[] buf = new byte[256];
					DatagramPacket p = new DatagramPacket(buf, buf.length);
					server.receive(p);

					// extract the nodo arraylist out of the packet
					// TODO: maybe abstract this out to pass just the bytes needed
					ByteArrayInputStream bais = new ByteArrayInputStream(p.getData());
					ObjectInputStream ois = new ObjectInputStream(bais);

					Object readObject = ois.readObject();
					if(readObject instanceof ArrayList<?>) {
						ArrayList<Nodo> list = (ArrayList<Nodo>) readObject;

						System.out.println("Received nodo list:");
						for (Nodo nodo : list) {
							System.out.println(nodo);
						}
						// Merge our list with the one we just received
						mergeLists(list);
					}

				} catch (IOException e) {
					e.printStackTrace();
					keepRunning.set(false);
				} catch (ClassNotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}

		/**
		 * Merge remote list (received from peer), and our local nodo list.
		 * Simply, we must update the heartbeats that the remote list has with
		 * our list.  Also, some additional logic is needed to make sure we have 
		 * not timed out a nodo and then immediately received a list with that 
		 * nodo.
		 * @param remoteList
		 */
		private void mergeLists(ArrayList<Nodo> remoteList) {

			synchronized (Client.this.Lista_infectados) {

				synchronized (Client.this.listaNodos) {

					for (Nodo remotenodo : remoteList) {
						if(Client.this.listaNodos.contains(remotenodo)) {
							Nodo localnodo = Client.this.listaNodos.get(Client.this.listaNodos.indexOf(remotenodo));

							if(remotenodo.getinfectados() > localnodo.getinfectados()) {
								// update local list with latest heartbeat
								localnodo.setinfectados(remotenodo.getinfectados());
								// and reset the timeout of that nodo
								localnodo.resetTimeoutTimer();
							}
						}
						else {
							// the local list does not contain the remote nodo

							// the remote nodo is either brand new, or a previously declared dead nodo
							// if its dead, check the heartbeat because it may have come back from the dead

							if(Client.this.Lista_infectados.contains(remotenodo)) {
								Nodo localnodo_infectado = Client.this.Lista_infectados.get(Client.this.Lista_infectados.indexOf(remotenodo));
								if(remotenodo.getinfectados() > localnodo_infectado.getinfectados()) {
									// it's baa-aack
									Client.this.Lista_infectados.remove(localnodo_infectado);
									Nodo newLocalnodo = new Nodo(remotenodo.getAddress(), remotenodo.getinfectados(), Client.this, tiempo_Limpiando);
									Client.this.listaNodos.add(newLocalnodo);
									newLocalnodo.startTimeoutTimer();
								} // else ignore
							}
							else {
								// brand spanking new nodo - welcome
								Nodo newLocalnodo = new Nodo(remotenodo.getAddress(), remotenodo.getinfectados(), Client.this, tiempo_Limpiando);
								Client.this.listaNodos.add(newLocalnodo);
								newLocalnodo.startTimeoutTimer();
							}
						}
					}
				}
			}
		}
	}

	/**
	 * Starts the client.  Specifically, start the various cycles for this protocol.
	 * Start the gossip thread and start the receiver thread.
	 * @throws InterruptedException
	 */
	private void start() throws InterruptedException {

		// Start all timers except for me
		for (Nodo nodo : listaNodos) {
			if(nodo != me) {
				nodo.startTimeoutTimer();
			}
		}

		// Start the two worker threads
		ExecutorService executor = Executors.newCachedThreadPool();
		//  The receiver thread is a passive player that handles
		//  merging incoming nodoship lists from other neighbors.
		executor.execute(new AsychronousReceiver());
		//  The gossiper thread is an active player that 
		//  selects a neighbor to share its nodoship list
		executor.execute(new nodoshipGossiper());

		// Potentially, you could kick off more threads here
		//  that could perform additional data synching

		// keep the main thread around
		while(true) {
			TimeUnit.SECONDS.sleep(10);
		}
	}

	public static void main(String[] args) throws InterruptedException, SocketException, UnknownHostException {

		Client client = new Client();
		client.start();
	}

	/**
	 * All timers associated with a nodo will trigger this method when it goes
	 * off.  The timer will go off if we have not heard from this nodo in
	 * <code> tiempo_Limpiando </code> time.
	 */
	@Override
	public void handleNotification(Notification notification, Object handback) {

		Nodo nodo_infectado = (Nodo) notification.getUserData();

		System.out.println("nodo infectado detectado: " + nodo_infectado);

		synchronized (this.listaNodos) {
			this.listaNodos.remove(nodo_infectado);
		}

		synchronized (this.Lista_infectados) {
			this.Lista_infectados.add(nodo_infectado);
		}

	}
}
