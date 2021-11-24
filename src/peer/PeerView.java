package peer;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JMenuBar;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import forks.Fork;
import logging.LogModel;
import logging.LogView;
import util.Ico;
import util.Util;
import util.swing.SwingEX;
import util.swing.SwingUtil;
import util.swing.jfuntable.JFunTableModel;
import web.AllTheBlocks;

@SuppressWarnings("serial")
public class PeerView extends JPanel {
	final List<Peer> LIST = new ArrayList<>();
	final Fork f;
	
	final PeerTableModel MODEL = new PeerTableModel();
	private final JTable TABLE = new JTable(MODEL);
	private final JScrollPane JSP = new JScrollPane(TABLE);
	private final JTextField newPeerField = new JTextField();
	private final JButton atbPeersBtn = new JButton("Get ATB Peers", Ico.ATB);
	
	private final JButton addPeers = new SwingEX.Btn("Add Peers", Ico.PLUS, () -> {
		List<String> peerList = Arrays.asList(newPeerField.getText().split("\\s+"));
		new Thread(() -> addPeers(peerList)).start();
	});
	
	private final JButton copyPeers = new SwingEX.Btn("Copy", 	Ico.CLIPBOARD,  () -> {copy();});
	private final JButton copyCLI = new SwingEX.Btn("CLI Copy", Ico.CLI,  () -> {copyCLI();});
	
	
	private final LogModel PVLOG = new LogModel();
	
	class PeerTableModel extends JFunTableModel<Peer> {
		public PeerTableModel() {
			super();
			
			addColumn("Address",   	-1,		String.class,	p->p.address);
			addColumn("Height",   	80,		String.class,	p->p.height);
			addColumn("Time",  		160,	String.class,	p->p.time);
			addColumn("Upload",   	80,		double.class, 	p->p.ul);
			addColumn("Dowload",   	80,		double.class, 	p->p.dl);
			
			onGetRowCount(() -> LIST.size());
			onGetValueAt((r, c) -> colList.get(c).apply(LIST.get(r)));
			onisCellEditable((r, c) -> false);
		}
	}
	
	public PeerView(Fork f) {
		this.f = f;
		setLayout(new BorderLayout());
		add(JSP,BorderLayout.CENTER);
		
		LogView logPanel = PVLOG.newPanelView();
		logPanel.JSP.setPreferredSize(new Dimension(300,150));
		
		add(logPanel,BorderLayout.PAGE_END);
		
		MODEL.colList.forEach(c -> c.finalize(TABLE,null));
		
		JSP.setPreferredSize(new Dimension(600,250));
		
		JMenuBar MENU = new JMenuBar();
		MENU.add(copyPeers);
		MENU.add(copyCLI);
		MENU.add(new JSeparator());
		MENU.add(addPeers);
		MENU.add(newPeerField);
		 
		atbPeersBtn.setEnabled(null != f.fd.atbPath);
		atbPeersBtn.addActionListener(al -> new Thread(() -> getATBpeers(f)).start());
		MENU.add(atbPeersBtn);
		
		addPeers.setEnabled(false);
		copyPeers.setEnabled(false);
		copyCLI.setEnabled(false);
		
		addPeers.setToolTipText("Peer format ip:port delimited by space");
		
		add(MENU,BorderLayout.PAGE_START);
		
		new Thread(() -> loadPeers()).start();
		
		TABLE.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
		    @Override
		    public void valueChanged(ListSelectionEvent event) {
		    	copyPeers.setEnabled(TABLE.getSelectedRow() > -1);
		    	copyCLI.setEnabled(TABLE.getSelectedRow() > -1);
		    }
		});
		
		newPeerField.getDocument().addDocumentListener(new DocumentListener() {

			@Override
			public void insertUpdate(DocumentEvent e) {
				addPeers.setEnabled(newPeerField.getText().length() > 10);
			}

			@Override
			public void removeUpdate(DocumentEvent e) {
				addPeers.setEnabled(newPeerField.getText().length() > 10);
			}

			@Override
			public void changedUpdate(DocumentEvent e) {
				addPeers.setEnabled(newPeerField.getText().length() > 10);
			}
		    
		});
	}
	
	public void getATBpeers(Fork f) {
		atbPeersBtn.setEnabled(false);
		String forkPath = f.fd.atbPath;
		PVLOG.add("alltheblocks.net getting peers...");
		List<String> peerList = AllTheBlocks.getPeers(forkPath);
		PVLOG.add("received " + peerList.size() + " peers");
		addPeers(peerList);
		atbPeersBtn.setEnabled(true);
	}
	
	public void loadPeers() {
		LIST.clear();
		boolean singleMode = false;
		Process p = null;
		BufferedReader br = null;
		try {
			PVLOG.add("Loading peers...");
			p = Util.startProcess(f.exePath, "show", "-c");
			br = new BufferedReader(new InputStreamReader(p.getInputStream()));
			
			String l = null;
			while ( null != (l = br.readLine())) {
				if (l.startsWith("Type") && l.contains("Hash"))
					singleMode = true;
				
				if (l.contains("FULL_NODE ")) {
					if (singleMode) {
						LIST.add(Peer.factorySingleLine(l));
	            	} else {
	            		String l2 = br.readLine();
	            		LIST.add(Peer.factoryMultiLine(l + l2));
	            	}
				}
            		
			}
			PVLOG.add("Loaded " + LIST.size() + " peers" );
		} catch (IOException e) {
			PVLOG.add("Error loading peers" + e.getStackTrace());
		}
		Util.waitForProcess(p);
		Util.closeQuietly(br);
		SwingUtilities.invokeLater(() -> {
			MODEL.fireTableDataChanged();
		});
	}
	
	private void copyCLI() {
		List<Peer> peerList = SwingUtil.getSelected(TABLE, LIST);
		StringBuilder sb = new StringBuilder();
		for (Peer p: peerList)
			if (null != p.address)
				sb.append(f.name.toLowerCase() + " show -a " + p.address + "\n");
		
		Util.copyToClip(sb.toString());
	}

	private void copy() {
		List<Peer> peerList = SwingUtil.getSelected(TABLE, LIST);
		StringBuilder sb = new StringBuilder();
		for (Peer p: peerList)
			if (null != p.address)
				sb.append(p.address + "\n");
		
		Util.copyToClip(sb.toString());
	}
	

	public void addPeers(List<String> peers) {
		PVLOG.add("Trying " + peers.size() + " peers");
		for (String p : peers) {
			String s = Util.runProcessWait(f.exePath,"show","-a", p);
			s = s.replace("\n", " ").replace("\r", " ");
			PVLOG.add(s);
		}
		PVLOG.add("Done adding peers... reloading table");
		loadPeers();
	}
}
