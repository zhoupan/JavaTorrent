package torrent.download;

import java.io.IOException;

import org.johnnei.utils.ThreadUtils;

import torrent.TorrentManager;
import torrent.download.peer.Peer;
import torrent.network.BitTorrentClient;
import torrent.protocol.IMessage;

public class PeersReadRunnable implements Runnable {

	private TorrentManager manager;

	public PeersReadRunnable(TorrentManager manager) {
		this.manager = manager;
	}

	@Override
	public void run() {
		while (true) {
			for (Torrent torrent : manager.getTorrents()) {
				processTorrent(torrent);
			}
			ThreadUtils.sleep(1);
		}
	}
	
	private void processTorrent(Torrent torrent) {
		for (Peer peer : torrent.getPeers()) {
			processPeer(peer);
		}
	}
	
	private void processPeer(Peer peer) {
		if (peer.closed()) {
			return;
		}
		
		BitTorrentClient socket = peer.getBitTorrentClient();
		
		try {
			if (!socket.canReadMessage()) {
				return;
			}
			
			IMessage message = socket.readMessage();
			message.process(peer);
			peer.updateLastActivity();
		} catch (IOException e) {
			peer.getLogger().severe(e.getMessage());
			peer.close();
		}
	}

}
