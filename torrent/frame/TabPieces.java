package torrent.frame;

import java.awt.Color;
import java.awt.Graphics;
import java.util.ArrayList;

import torrent.download.Torrent;
import torrent.download.Files;
import torrent.download.files.Piece;
import torrent.util.ISortable;
import torrent.util.Mergesort;

public class TabPieces extends TableBase {

	public static final long serialVersionUID = 1L;
	private Torrent torrent;

	public TabPieces() {
		super(20);
	}

	public void setTorrent(Torrent torrent) {
		this.torrent = torrent;
	}
	
	public Color getForegroundColor() {
		return Color.BLACK;
	}
	
	public Color getBackgroundColor() {
		return Color.WHITE;
	}
	
	protected void paintHeader(Graphics g) {
		g.drawString("Piece #", 5, getHeaderTextY());
		g.drawString("Size", 100, getHeaderTextY());
		g.drawString("Progress", 200, getHeaderTextY());
		g.drawString("Subpieces (Remaining | Requested)", 300, getHeaderTextY());
	}

	@Override
	protected void paintData(Graphics g) {
		if(torrent == null)
			return;
		if(torrent.getDownloadStatus() != Torrent.STATE_DOWNLOAD_DATA)
			return;
		ArrayList<ISortable> pieceList = new ArrayList<>();
		Files tf = torrent.getFiles();
		for(int i = 0; i < tf.getPieceCount(); i++) {
			if(tf.getPiece(i).isStarted()) {
				pieceList.add(torrent.getFiles().getPiece(i));
			}
		}
		Mergesort sortedPieces = new Mergesort(pieceList);
		sortedPieces.sort();
		for(int i = pieceList.size() - 1; i >= 0 ; i--) {
			if(isVisible()) {
				Piece p = (Piece)sortedPieces.getItem(i);
				int doneCount = p.getDoneCount();
				int progress = (int)(100 * (doneCount / (double)p.getBlockCount()));
				
				g.drawString("" + p.getIndex(), 5, getTextY());
				g.drawString("" + p.getSize(), 100, getTextY());
				g.drawString(progress + "%", 200, getTextY());
				g.drawString((p.getBlockCount() - doneCount) + " | " + p.getRequestedCount(), 300, getTextY());
			}
			advanceLine();
		}
	}
}
