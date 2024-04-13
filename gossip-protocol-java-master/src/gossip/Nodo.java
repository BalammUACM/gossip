package gossip;

import java.io.Serializable;
import java.net.InetSocketAddress;

public class Nodo implements Serializable {

	private static final long serialVersionUID = 8387950590016941525L;

	/**
	 * The member address in the form IP:port
	 * Similar to the toString in {@link InetSocketAddress}
	 */
	private String address;

	private int infectados;

	private transient TimeoutTimer timeoutTimer;

	public Nodo(String address, int infectados, Client client, int t_cleanup) {
		this.address = address;
		this.infectados = infectados;
		this.timeoutTimer = new TimeoutTimer(t_cleanup, client, this);
	}

	public void startTimeoutTimer() {
		this.timeoutTimer.start();
	}

	public void resetTimeoutTimer() {
		this.timeoutTimer.reset();
	}

	public String getAddress() {
		return address;
	}

	public int getinfectados() {
		return infectados;
	}

	public void setinfectados(int infectados) {
		this.infectados = infectados;
	}

	@Override
	public String toString() {
		return "Nodo [Direccion=" + address + ", infectados=" + infectados + "]";
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
		+ ((address == null) ? 0 : address.hashCode());
		return result;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		Nodo other = (Nodo) obj;
		if (address == null) {
			if (other.address != null) {
				return false;
			}
		} else if (!address.equals(other.address)) {
			return false;
		}
		return true;
	}
}
