package org.johnnei.javatorrent.internal.utp.protocol;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.HashMap;
import java.util.Map;

import org.johnnei.javatorrent.TorrentClient;
import org.johnnei.javatorrent.async.LoopingRunnable;
import org.johnnei.javatorrent.internal.network.UtpPeerConnectionAcceptor;
import org.johnnei.javatorrent.internal.network.socket.UtpSocketImpl;
import org.johnnei.javatorrent.internal.utp.protocol.payload.UtpPayloadFactory;
import org.johnnei.javatorrent.module.ModuleBuildException;
import org.johnnei.javatorrent.network.InStream;
import org.johnnei.javatorrent.network.socket.UtpSocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UtpMultiplexer implements Runnable {

	private static final Logger LOGGER = LoggerFactory.getLogger(UtpMultiplexer.class);

	private final Object socketListLock = new Object();

	private UtpPeerConnectionAcceptor connectionAcceptor;

	private LoopingRunnable connectionAcceptorRunnable;

	private UtpSocketImpl.Builder utpSocketFactory;

	/**
	 * The Factory to create the packet instances<br/>
	 * If JavaTorrent will need to update the protocol then we can use multiple factory's to create the correct version of the packet
	 */
	private UtpPayloadFactory packetFactory;

	/**
	 * The socket on which the udp packet will be received and send
	 */
	DatagramSocket multiplexerSocket;

	/**
	 * All {@link UtpSocket}s which have registered to listen for packets
	 */
	private Map<Short, UtpSocketImpl> utpSockets;

	public UtpMultiplexer(TorrentClient torrentClient) throws ModuleBuildException {
		utpSockets = new HashMap<>();
		utpSocketFactory = new UtpSocketImpl.Builder()
				.setUtpMultiplexer(this);
		packetFactory = new UtpPayloadFactory();
		connectionAcceptor = new UtpPeerConnectionAcceptor(torrentClient);
		connectionAcceptorRunnable = new LoopingRunnable(connectionAcceptor);
		startMultiplexer(torrentClient.getDownloadPort());
	}

	void startMultiplexer(int port) throws ModuleBuildException {
		try {
			multiplexerSocket = new DatagramSocket(port);
		} catch (IOException e) {
			throw new ModuleBuildException("Failed to bind to socket for uTP connections.", e);
		}

		Thread thread = new Thread(connectionAcceptorRunnable, "uTP Connection Acceptor");
		thread.setDaemon(true);
		thread.start();
	}

	/**
	 * Attempts to register the given socket.
	 * @param socket The socket to register.
	 * @return <code>true</code> when the socket is registered. <code>false</code> when the connection id conflicts with an existing socket.
	 */
	public boolean registerSocket(UtpSocketImpl socket) {
		synchronized (socketListLock) {
			if (utpSockets.containsKey(socket.getReceivingConnectionId())) {
				return false;
			}

			utpSockets.put(socket.getReceivingConnectionId(), socket);
		}
		return true;
	}

	public void send(DatagramPacket datagramPacket) throws IOException {
		multiplexerSocket.send(datagramPacket);
	}

	@Override
	public void run() {
		try {
			boolean newSocket = false;
			byte[] dataBuffer = new byte[25600]; //25kB buffer
			DatagramPacket packet = new DatagramPacket(dataBuffer, dataBuffer.length);
			multiplexerSocket.receive(packet);
			try {
				InStream inStream = new InStream(packet.getData(), packet.getOffset(), packet.getLength());
				UtpPacket utpPacket = new UtpPacket();
				utpPacket.read(inStream, packetFactory);
				UtpSocketImpl socket = utpSockets.get(utpPacket.getConnectionId());

				if (socket == null) {
					LOGGER.debug("Received connection from {}", packet.getSocketAddress());
					utpSocketFactory.setSocketAddress(packet.getSocketAddress());
					socket = utpSocketFactory.build(utpPacket.getConnectionId());
					registerSocket(socket);
					newSocket = true;
				}

				socket.process(utpPacket);

				if (newSocket) {
					connectionAcceptor.onReceivedConnection(new UtpSocket(this, socket));
				}
			} catch (IllegalArgumentException e) {
				LOGGER.debug("Invalid Packet of {} bytes ({}:{}).", packet.getLength(), packet.getAddress(), packet.getPort(), e);
			}
		} catch (IOException e) {
			LOGGER.warn("Failed to process uTP packet", e);
		}
	}

	/**
	 * Closes the socket.
	 */
	public void shutdown() {
		multiplexerSocket.close();
		connectionAcceptorRunnable.stop();
	}
}
