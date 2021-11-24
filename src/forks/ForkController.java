package forks;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

import ffutilities.ForkLogViewer;
import ffutilities.ManualAddView;
import ffutilities.MissingForks;
import ffutilities.PlotCalc;
import ffutilities.PortCheckerView;
import main.ForkFarmer;
import main.MainGui;
import main.Settings;
import peer.PeerView;
import types.Balance;
import types.Wallet;
import util.Ico;
import util.Util;
import util.swing.SwingEX;
import web.AllTheBlocks;
import web.Github;
import web.XchForks;

public class ForkController {
	private enum SHELL {CMD, POWERSHELL, TERMINAL}
	static boolean warnJava = false;
	static List<Fork> PENDING = new ArrayList<>();

	public static JPopupMenu getPopupMenu() {
		JPopupMenu POPUP_MENU = new JPopupMenu();
		
		final JMenuItem STAGGER_JMI = new SwingEX.JMI("Stagger", 	Ico.START,	ForkController::staggerStartDialog);
		final JMenu ACTION_SUBMENU = new SwingEX.JMIco("Action", Ico.ACTION);
		final JMenu WALLET_SUBMENU = new SwingEX.JMIco("Wallet", Ico.WALLET);
		final JMenu EXPLORE_SUBMENU = new SwingEX.JMIco("Explore", Ico.EXPLORE);
		final JMenu COPY_SUBMENU = new SwingEX.JMIco("Copy", Ico.CLIPBOARD);
		final JMenu TOOLS_SUBMENU = new SwingEX.JMIco("Tools", Ico.TOOLS);
		final JMenu COMMUNITY_SUBMENU = new SwingEX.JMIco("Community", Ico.PEOPLE);
		
		POPUP_MENU.addPopupMenuListener(new PopupMenuListener() {
			@Override public void popupMenuCanceled(PopupMenuEvent pme) {
				
			};
			@Override public void popupMenuWillBecomeInvisible(PopupMenuEvent pme) {
				
			}
			@Override public void popupMenuWillBecomeVisible(PopupMenuEvent pme) {
				List<Fork> sel = ForkView.getSelected();

				// clean sub-menus
				WALLET_SUBMENU.removeAll();
				while (COMMUNITY_SUBMENU.getItemCount() > 6)
            		COMMUNITY_SUBMENU.remove(6);
            	
				if (sel.size() < 2) {
					STAGGER_JMI.setEnabled(false);
				} else {
					//WALLET_SUBMENU.setEnabled(false);
					STAGGER_JMI.setEnabled(true);
				}
				
				if (1 == sel.size()) {
					Fork f = sel.get(0);
					populateMenu(f);
				} else if (sel.size() > 1) {
					int maxIdx = sel.stream().mapToInt(f -> f.walletList.size()).max().getAsInt();
					
					for (int i = 0; i < maxIdx; i ++) {
						final int z = i;
						WALLET_SUBMENU.add(new SwingEX.JMI("Multi Wallet Index: " + i, Ico.WALLET, () -> new Thread(() -> ForkController.multiWallet(sel,z)).start()));
					}
				}
			}
						
			private void populateMenu(Fork f) {
            	
            	ForkData fd = ForkData.MAP.get(f.symbol);
            	if (null != fd) {
	            	if (null != fd.websiteURL)
	            		COMMUNITY_SUBMENU.add(new SwingEX.JMI(f.name + " Homepage", Ico.HOME, () -> Util.openLink(fd.websiteURL)));
            		if (null != fd.discordURL)
	            		COMMUNITY_SUBMENU.add(new SwingEX.JMI(f.name + " Discord", 	Ico.DISCORD, () -> Util.openLink(fd.discordURL)));
	            	if (null != fd.gitPath)
	            		COMMUNITY_SUBMENU.add(new SwingEX.JMI(f.name + " GitHub", 	Ico.GITHUB, () -> Util.openLink(ForkData.GITHUB_URL + fd.gitPath)));
	            	if (null != fd.twitterURL)
	            		COMMUNITY_SUBMENU.add(new SwingEX.JMI(f.name + " Twitter",	Ico.TWITTER, () -> Util.openLink(fd.twitterURL)));
	            	if (null != fd.calculatorURL)
	            		COMMUNITY_SUBMENU.add(new SwingEX.JMI(f.name + " Calculator",	Ico.XCHCALC, () -> Util.openLink(fd.calculatorURL)));
            	}
            	
            	for (int i = 0; i < f.walletList.size(); i++) {
            		WALLET_SUBMENU.setEnabled(true);
            		Wallet w = f.walletList.get(i);

            		if (w.cold) {
            			JMenuItem jmi = new SwingEX.JMI("0) " + w.addr, 	Ico.WALLET_COLD, () ->  {
            				f.wallet = w;
            				f.walletAddr = w.addr;
            				AllTheBlocks.updateColdBalance(f);
            			});
            			((Component)jmi).setForeground(new Color(140,171,255));
            			WALLET_SUBMENU.add(jmi);
            		} else {
	            		WALLET_SUBMENU.add(new SwingEX.JMI(w.index + ") " + w.fingerprint + ": " + w.addr, 	Ico.WALLET, () -> {
	            			new Thread(() -> {
	            				f.balance = new Balance();
	            				f.syncStatus = "";
	            				f.wallet = w;
	            				f.walletAddr = w.addr;
	            				ForkView.update(f);
	            				f.loadWallet();
	            			}).start();
	            		}));
            		}
            	}
            	
            	if (WALLET_SUBMENU.getItemCount() > 0)
            		WALLET_SUBMENU.addSeparator();
            	WALLET_SUBMENU.add(new SwingEX.JMI("Add Cold Wallet",	Ico.WALLET_COLD,	() -> addColdWallet(f)));
            	
	
			}
		});
		
		POPUP_MENU.add(ACTION_SUBMENU);
		ACTION_SUBMENU.add(new SwingEX.JMI("Start", 		Ico.START, 	() -> new Thread(() -> ForkView.getSelected().forEach(Fork::start)).start()));
		ACTION_SUBMENU.add(STAGGER_JMI);
		ACTION_SUBMENU.add(new SwingEX.JMI("Stop",			Ico.STOP,  	() -> new Thread(() -> ForkView.getSelected().forEach(Fork::stop)).start()));
		ACTION_SUBMENU.add(new SwingEX.JMI("Custom",		Ico.CLI, 	() -> ForkStarter.newCustCMD(ForkView.getSelected())));
		ACTION_SUBMENU.addSeparator();
		if (!Util.isHostWin()) {
			ACTION_SUBMENU.add(new SwingEX.JMI("Activate",			Ico.POWER, 	() -> ForkStarter.activate(ForkView.getSelected())));
			ACTION_SUBMENU.add(new SwingEX.JMI("Activate (custom)",	Ico.POWER, 	ForkController::custom));
		}
		
		ACTION_SUBMENU.add(new SwingEX.JMI("Edit Start",	Ico.EDIT_START, 	ForkStarter::edit));
		ACTION_SUBMENU.add(new SwingEX.JMI("Set Pass File",	Ico.KEY, 			() -> new Thread(ForkController::setPassKey).start()));
		ACTION_SUBMENU.add(new SwingEX.JMI("Hide", 			Ico.HIDE,  			ForkController::removeSelected));
	
		POPUP_MENU.add(WALLET_SUBMENU);
	
		POPUP_MENU.add(EXPLORE_SUBMENU);
			EXPLORE_SUBMENU.add(new SwingEX.JMI("View Log", 	Ico.CLIPBOARD,  		() -> new ForkLogViewer(ForkView.getSelected())));
			EXPLORE_SUBMENU.add(new SwingEX.JMI("Open Config", 	Ico.CLIPBOARD,  		() -> ForkView.getSelected().forEach(f -> Util.openFile(f.configPath))));
			if (Util.isHostWin()) {
				EXPLORE_SUBMENU.add(new SwingEX.JMI("Open CMD", 		Ico.CLI,  			() -> ForkController.openShell(SHELL.CMD)));
				EXPLORE_SUBMENU.add(new SwingEX.JMI("Open Powershell",	Ico.POWERSHHELL,  	() -> ForkController.openShell(SHELL.POWERSHELL)));
			} else {
				EXPLORE_SUBMENU.add(new SwingEX.JMI("Open Terminal", 		Ico.TERMINAL,  		() -> ForkController.openShell(SHELL.TERMINAL)));
			}
		
		POPUP_MENU.add(COPY_SUBMENU);
			COPY_SUBMENU.add(new SwingEX.JMI("Copy Address", 	Ico.CLIPBOARD,  ForkController::copyAddress));
			COPY_SUBMENU.add(new SwingEX.JMI("Copy CSV", 		Ico.CLIPBOARD,  ForkController::copyCSV));
		
		POPUP_MENU.add(TOOLS_SUBMENU);
			TOOLS_SUBMENU.add(new SwingEX.JMI("Ports", 	Ico.PORTS, ForkController::runPortChecker));
			TOOLS_SUBMENU.add(new SwingEX.JMI("Missing",Ico.QUESTION, () -> ForkFarmer.newFrame("Missing Forks", Ico.QUESTION, new MissingForks())));
			JMenuItem update = new SwingEX.JMI("Force Update", 	Ico.DOLLAR,  	() -> new Thread(ForkController::webUpdateForced).start());
			update.setToolTipText("from xchforks.com / alltheblocks.net");
			TOOLS_SUBMENU.add(update);
			TOOLS_SUBMENU.add(new SwingEX.JMI("Plot Calc",Ico.EXPAND, () -> ForkFarmer.newFrame("Plot Calculator", Ico.EXPAND, new PlotCalc()).setResizable(false)));
			
		POPUP_MENU.add(COMMUNITY_SUBMENU);
			COMMUNITY_SUBMENU.add(new SwingEX.JMI("xchforks.com", 			Ico.XCHF,() -> Util.openLink("https://xchforks.com/")));
			COMMUNITY_SUBMENU.add(new SwingEX.JMI("alltheblocks.net", 		Ico.ATB, () -> Util.openLink("https://alltheblocks.net/")));
			COMMUNITY_SUBMENU.add(new SwingEX.JMI("forkschiaexchange.com", 	Ico.FCX, () -> Util.openLink("https://forkschiaexchange.com/?ref=orfinkat")));
			COMMUNITY_SUBMENU.add(new SwingEX.JMI("chiaforksblockchain.com", Ico.DOWNLOAD, () -> Util.openLink("https://chiaforksblockchain.com/")));
			COMMUNITY_SUBMENU.add(new SwingEX.JMI("casino.maize.farm", 			Ico.ROULETTE, () -> Util.openLink("https://casino.maize.farm/?ref=241")));
			COMMUNITY_SUBMENU.addSeparator();
			
		POPUP_MENU.addSeparator();
		
		POPUP_MENU.add(new SwingEX.JMI("Add Cold Wallet",	Ico.SNOW,  	() -> ForkController.addColdWallet(null)));
		POPUP_MENU.add(new SwingEX.JMI("Add Fork",			Ico.PLUS,  	ForkController::addFork));
		POPUP_MENU.add(new SwingEX.JMI("Refresh",	Ico.REFRESH,  	ForkController::refresh));
		POPUP_MENU.add(new SwingEX.JMI("Show Peers",Ico.P2P,		() -> ForkView.getSelected().forEach(f -> 
			ForkFarmer.newFrame(f.name + ": Peer Connections", f.ico, new PeerView(f)))));
		POPUP_MENU.addSeparator();
		POPUP_MENU.add(new SwingEX.JMI("Debug",			Ico.BUG,		() -> ForkView.getSelected().forEach(Fork::showLastException)));
		POPUP_MENU.add(new SwingEX.JMI("FF Logs",		Ico.CLIPBOARD,	() -> ForkFarmer.showFrame("ForkFarmer Log:", null, ForkFarmer.LOG.newFrameView())));
		
		
		return POPUP_MENU;
	}
	
	static private void multiWallet(List<Fork> sel, int i) {
		sel.forEach(f -> {
			if (i < f.walletList.size()) {
				f.balance = new Balance();
				f.syncStatus = "";
				f.wallet = f.walletList.get(i);
				f.walletAddr = f.wallet.addr;
				ForkView.update(f);
				f.loadWallet();
			}
		});
	}
	
	static private void addFork() {
		ManualAddView p = new ManualAddView();
		if (ForkFarmer.showPopup("Add Fork", p)) {
			
			Fork f;
			try {
				f = p.loadFork();
				Fork.LIST.add(f);
				ForkView.update();
			} catch (FileNotFoundException e) {
				ForkFarmer.showMsg("Error", "Fork exe doesn't exist");
			}
			
		}
	}
	
	static private void custom() {
		List<Fork> fList = ForkView.getSelected();
		
		SwingEX.LIPanel scriptPanel = new SwingEX.LIPanel("Enter Script: ");
		if (true == ForkFarmer.showPopup("activate script: ", scriptPanel)) { 
			String script = scriptPanel.getText();
			ForkStarter.custom(fList, script);
		}
		
	}
	
	static private void addColdWallet(Fork f) {
		JPanel cwPanel = new JPanel(new BorderLayout());
		JTextPane jtp = new JTextPane();
		JScrollPane JSP = new JScrollPane(jtp);
		jtp.setPreferredSize(new Dimension(500,200));
		cwPanel.add(new JLabel("Enter one address per line"), BorderLayout.PAGE_START);
		cwPanel.add(JSP,BorderLayout.CENTER);
		
		if (ForkFarmer.showPopup("Cold wallet data comes from www.alltheblocks.net", cwPanel)) {
			String[] addrArray = jtp.getText().split(System.lineSeparator());
			
			new Thread(() -> {
				for (String addr : addrArray) {
					addr = addr.toLowerCase();
					if (null == f)
						Fork.newColdWallet(addr);
					else {
						if (!addr.startsWith(f.symbol.toLowerCase()))
							return;
						f.walletAddr = addr;
						f.wallet = new Wallet(addr);
						f.walletList.add(f.wallet);
						f.coldAddrList.add(addr);
					}
				}
				Util.sleep(2000);
				AllTheBlocks.updateColdForced();
			}).start();
			
		}
	}
	
	static private void copyAddress() {
		String addrs = ForkView.getSelected().stream()
				.map(f -> f.wallet.toString()).filter(Objects::nonNull)
				.collect(Collectors.joining("\n"));
		Util.copyToClip(addrs);
	}
	
	static private void copyCSV() {
		StringBuilder sb = new StringBuilder();
		sb.append("Symbol,Balance,$,ETW\n");
		for (Fork f : ForkView.getSelected()) {
			sb.append(Util.toString(ForkView.MODEL.colList.get(ForkView.MODEL.getIndex("Symbol")).getValue.apply(f)) + ",");
			sb.append(((Balance)(ForkView.MODEL.colList.get(ForkView.MODEL.getIndex("Balance")).getValue.apply(f))).amt +",");
			sb.append(((Balance)(ForkView.MODEL.colList.get(ForkView.MODEL.getIndex("$")).getValue.apply(f))).amt +",");
			sb.append(Util.toString(ForkView.MODEL.colList.get(ForkView.MODEL.getIndex("ETW")).getValue.apply(f)) + "\n");
		}
		
		Util.copyToClip(sb.toString());
	}
	
	static private void openShell(SHELL s) {
		for (Fork f : ForkView.getSelected()) {
			String path = f.exePath;
			String nativeDir = path.substring(0, path.lastIndexOf(File.separator));
			try {
				if (SHELL.POWERSHELL == s)
					Runtime.getRuntime().exec("cmd /c start powershell.exe -noexit -command " + "cd " + nativeDir);
				else if (SHELL.CMD == s)
					Runtime.getRuntime().exec("cmd /c start cmd.exe /K " + "cd " + nativeDir);
				else if (SHELL.TERMINAL == s)
					Runtime.getRuntime().exec("gnome-terminal --working-directory=" + nativeDir);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		
		}
	}
	
	static private void setPassKey() {
		List<Fork> LIST = ForkView.getSelected();
		
		JFileChooser jfc = new JFileChooser();
		if (JFileChooser.APPROVE_OPTION == jfc.showOpenDialog(ForkFarmer.FRAME)) {
			LIST.forEach(f -> {
				String path = jfc.getSelectedFile().getPath();
				f.passFile = path;
				f.hidden = false;
				f.startup();
			});
		}
	}
	
	private static void runPortChecker() {
		Fork.LIST.forEach(Fork::loadConfig);
		ForkFarmer.newFrame("Port Checker", Ico.PORTS, new PortCheckerView());
	}
	
	static private void refresh() {
		ForkView.getSelected().forEach(f -> {
			new Thread(() -> { 
				f.loadVersion();
				f.loadWallet();
			}).start();
		});
	}
	
	static private void removeSelected() {
		List<Fork> selList = ForkView.getSelected();
		for (Fork f : selList) {
			f.hidden = true;
			SwingUtilities.invokeLater(() -> {
				ForkView.MODEL.removeRow(f.getIndex().get());	
			});
			
		}
		
		ForkView.update();
		MainGui.updateNumForks();
	}
	
	static private void staggerStartDialog() {
		String delay = JOptionPane.showInputDialog(ForkFarmer.FRAME,"Enter Start Interval: (Seconds)", "60");
		
		if (null == delay)
			return;
		int delayInt = Integer.parseInt(delay);

		new Thread(() -> {
			ForkView.getSelected().stream().forEach(f -> {
				ForkStarter.start(f); Util.sleep(delayInt * 1000);
			});
		}).start();
	}
	
	static private void webUpdateForced() {
		if (!javaOld()) {
			XchForks.updatePricesForced();
			AllTheBlocks.updateColdForced();
		}
	}
	
	public static void logReader() {
		while(true) {
			for (Fork f: Fork.FULL_LIST) {
				if (false == f.hidden) {
					f.readLog();
					Util.sleep(Settings.GUI.logReaderIntraDelay);
				}
			}
				Util.sleep(Settings.GUI.logReaderExoDelay);
			
		}
	}
	
	public static boolean javaOld() {
		int jversion = Util.getJavaVersion();
		if (!warnJava && jversion < 11) {
			ForkFarmer.showMsg("Please update Java Runtime", 
					"Current java version is " + jversion + ". Java 11+ required for many features in FF\n" +
					"Please download new java release from https://www.oracle.com/java/technologies/downloads/"
			);
			warnJava = true;
			return true;
		}
		return false;
	}
	
	static private void webUpdate() {
		if (javaOld() || !Settings.GUI.autoUpdate)
			return;
		
		ForkFarmer.LOG.add("Running web update");
		
		XchForks.updatePrices();
		AllTheBlocks.updateATB();
		Github.getVersion(Fork.FULL_LIST);
		
		ForkFarmer.LOG.add("Done Running web update");
	}
	
	public static void daemonReader() {
		ExecutorService SVC = Executors.newFixedThreadPool(Settings.GUI.daemonReaderWorkers);
		
		try {
			// initial load
			SVC.submit(ForkController::webUpdate);
			Fork.FULL_LIST.forEach(f -> SVC.submit(() -> f.startup()));
				
			// main GUI refresh loop
			while(true) {
				for (Fork f: Fork.FULL_LIST) {
					if (!f.hidden)
						SVC.submit(() -> {
							f.loadFarmSummary();
							f.loadWallet();
							ForkView.update(f);
						}); 
					Util.blockUntilAvail(SVC);
					Util.sleep(Settings.GUI.daemonReaderDelay);
				}

				Util.sleep(1000);
				SVC.submit(ForkController::webUpdate);
			}
		} catch (Exception e) {
			System.out.println("Serious UNCAUGHT EXCEPTION!!!!!!!!!!!!!!!!!!!!!!");
			e.printStackTrace();
		}
	}



}
