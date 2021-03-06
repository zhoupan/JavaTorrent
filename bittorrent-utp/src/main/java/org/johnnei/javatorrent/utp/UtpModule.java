package org.johnnei.javatorrent.utp;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import org.johnnei.javatorrent.TorrentClient;
import org.johnnei.javatorrent.async.LoopingRunnable;
import org.johnnei.javatorrent.internal.network.socket.ISocket;
import org.johnnei.javatorrent.internal.utp.protocol.UtpMultiplexer;
import org.johnnei.javatorrent.module.IModule;
import org.johnnei.javatorrent.module.ModuleBuildException;
import org.johnnei.javatorrent.internal.network.socket.UtpSocket;
import org.johnnei.javatorrent.torrent.peer.Peer;

/**
 * Module which allows for creating connections via uTP.
 *
 * It's advised to re-use instances of this module as each will create a dedicated thread to receive uTP messages.
 * Using more instances allows for more connections to be used as each instance is limited to 65536 (2^16) connections by protocol design.
 *
 */
public class UtpModule implements IModule {

	private LoopingRunnable multiplexerRunnable;

	private UtpMultiplexer utpMultiplexer;

	@Override
	public void configureTorrentClient(TorrentClient.Builder builder) {
		// uTP is configured on the ConnectionDegradation
	}

	@Override
	public int getRelatedBep() {
		return 29;
	}

	@Override
	public List<Class<IModule>> getDependsOn() {
		return Collections.emptyList();
	}

	@Override
	public void onPostHandshake(Peer peer) throws IOException {
		// uTP doesn't do anything special with the BitTorrent protocol.
	}

	@Override
	public void onBuild(TorrentClient torrentClient) throws ModuleBuildException {
		utpMultiplexer = createMultiplexer(torrentClient);
		multiplexerRunnable = new LoopingRunnable(utpMultiplexer);
		Thread thread = new Thread(multiplexerRunnable, "uTP Multiplexer");
		thread.setDaemon(true);
		thread.start();
	}

	UtpMultiplexer createMultiplexer(TorrentClient torrentClient) throws ModuleBuildException {
		return new UtpMultiplexer(torrentClient);
	}

	@Override
	public void onShutdown() {
		utpMultiplexer.shutdown();
		multiplexerRunnable.stop();
	}

	/**
	 * @return The internal implementation of the uTP socket facade.
	 */
	@SuppressWarnings("unchecked")
	public Class<ISocket> getUtpSocketClass() {
		// As I cannot directly cast down the UtpSocket class to ISocket class I'll take a round-about with an unchecked cast to make it happen.
		return (Class<ISocket>) ((Class<?>) UtpSocket.class);
	}

	/**
	 * @return A supplier capable of creating new {@link UtpSocket}
	 */
	public Supplier<ISocket> createSocketFactory() {
		return () -> new UtpSocket(utpMultiplexer);
	}

}
