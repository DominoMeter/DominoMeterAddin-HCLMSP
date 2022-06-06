import java.io.File;
import java.io.IOException;
import java.util.Calendar;
import lotus.domino.Document;
import lotus.domino.DocumentCollection;
import lotus.domino.Name;
import lotus.domino.NotesException;
import lotus.domino.View;
import net.prominic.dm.api.Config;
import net.prominic.dm.api.Log;
import net.prominic.dm.api.Ping;
import net.prominic.dm.update.UpdateRobot;
import net.prominic.gja_v080.JavaServerAddinGenesis;
import net.prominic.install.JSONRulesStub;
import net.prominic.install.ProgramConfigStub;
import net.prominic.io.RESTClient;

public class DominoMeter extends JavaServerAddinGenesis {
	public static long 		total_exception_count = 0;

	private int				m_interval				= 120;
	private String 			m_server				= "";
	private String 			m_endpoint				= "";
	private String 			m_version				= "";
	private int				failedCounter			= 0;
	private Config			m_config				= null;
	private ReportThread 	thread					= null;

	public DominoMeter(String[] args) {
		super(args);
	}

	public DominoMeter() {
		super();
	}

	@Override
	protected String getJavaAddinVersion() {
		return "117";
	}

	@Override
	protected String getJavaAddinDate() {
		return "2022-06-06 16:50 (gja)";
	}

	@Override
	protected boolean runNotesAfterInitialize() {
		if (args == null) {
			logMessage("You must provide an endpoint to send data, see instructions below");
			showHelp();
			return false;
		}

		try {
			m_server = m_session.getServerName();
			m_version = this.getJavaAddinName() + "-" + this.getJavaAddinVersion() + ".jar";

			// endpoint
			m_endpoint = args[0];
			if ("dev".equalsIgnoreCase(m_endpoint)) {
				m_endpoint = "https://prominic-dev.dominometer.com/duca.nsf";
			}
			else if("prod".equalsIgnoreCase(m_endpoint)) {
				m_endpoint = "https://prominic.dominometer.com/duca.nsf";
			}
			else if("belsoft".equalsIgnoreCase(m_endpoint)) {
				m_endpoint = "https://belsoft.dominometer.com/duca.nsf";
			}

			if (args.length > 1) {
				setLogLevel(args[1]);
			}


			// TEMPORARY CODE:
			boolean unloadAddin = false;
			
			// TEMPORARY CODE: ONLY TO INSTALL GENESIS
			String GJA_Genesis = m_session.getEnvironmentString("GJA_Genesis", true);
			if (GJA_Genesis.isEmpty()) {
				logMessage("--------------------------------------");
				logMessage("GENESIS INSTALLATION");
				logMessage("--------------------------------------");
				boolean installed = genesis();
				if (installed) {
					unloadAddin = true;
					logMessage("> COMPLETED");
				}
			}
			
			// TEMPORARY CODE: ONLY TO RE-INSTALL DOMINOMETER
			String GJA_DominoMeter = m_session.getEnvironmentString("GJA_DominoMeter", true);
			if (GJA_DominoMeter.isEmpty()) {
				logMessage("--------------------------------------");
				logMessage("DOMINO RE-INSTALLATION");
				logMessage("--------------------------------------");
				boolean reinstalled = reconfigure();
				if (reinstalled) {
					unloadAddin = true;
					logMessage("> COMPLETED");
				}
			}
			
			// TEMPORARY CODE: UNLOAD IF ADDIN INSTALLED
			if (unloadAddin) {
				logMessage("UNLOAD ADDIN DUE TO RECONFIGURATION");
				this.restartAll(false);
				return false;
			}

			// new config
			m_config = new Config();
		} catch(Exception e) {
			logSevere(e);
		}

		return true;
	}

	private boolean reconfigure() {
		try {
			// 1. install dominometer addin
			String catalog = "https://domino-1.dmytro.cloud/gc.nsf";
			StringBuffer buf = RESTClient.sendGET(catalog + "/package?openagent&id=dominometer");
			
			JSONRulesStub rules = new JSONRulesStub(m_session, m_ab, this.m_javaAddinConfig, this.m_logger);
			boolean res = rules.execute(buf.toString());

			if (res) {
				logMessage("DominoMeter installed (OK)");
				Log.sendLog(m_server, m_endpoint, "DominoMeter installed (OK)", "");
			}
			else {
				logMessage("DominoMeter FAILED");
				Log.sendLog(m_server, m_endpoint, "DominoMeter FAILED", rules.getLogBuffer().toString());
				System.out.println(rules.getLogBuffer());
			}
			
			// 2. program documents - migrate to settings
			View view = m_ab.getView("($Programs)");
			DocumentCollection col = view.getAllDocumentsByKey(m_server, true);
			Document doc = col.getFirstDocument();
			String CmdLine = doc.getItemValueString("CmdLine");
			doc.recycle();
			col.removeAll(true);
			col.recycle();

			this.setConfigValue("active", "1");
			this.setConfigValue("runjava", CmdLine);
			
			this.logMessage("DominoMeter uninstall: program documents (OK)");
			Log.sendLog(m_server, m_endpoint, "DominoMeter uninstall: program documents (OK)", "");

			// 3. notes.ini - cleanup
			String userClasses = m_session.getEnvironmentString("JAVAUSERCLASSES", true);
			String platform = m_session.getPlatform();
			String notesIniSep = platform.contains("Windows") ? ";" : ":";

			String[] userClassesArr = userClasses.split("\\" + notesIniSep);
			for (int i = 0; i < userClassesArr.length; i++) {
				if (userClassesArr[i].contains("DominoMeter")) {
					userClasses = userClasses.replace(userClassesArr[i] + notesIniSep, "");
					userClasses = userClasses.replace(userClassesArr[i], "");
					i = userClassesArr.length;
				}
			}
			m_session.setEnvironmentVar("JAVAUSERCLASSES", userClasses, true);
			this.logMessage("DominoMeter notes.ini cleanup: (OK)");
			Log.sendLog(m_server, m_endpoint, "DominoMeter notes.ini cleanup: (OK)", "");
		} catch (IOException e1) {
			e1.printStackTrace();
			this.logMessage("New DominoMeter was not installed properly [1]");
			return false;
		} catch (NotesException e) {
			e.printStackTrace();
			this.logMessage("New DominoMeter was not installed properly [2]");
			return false;
		}

		return true;
	}

	/*
	 * TODO: Must be removed in next version
	 */
	private boolean genesis() {
		try {
			// find addin in catalog
			String catalog = "https://domino-1.dmytro.cloud/gc.nsf";
			StringBuffer buf = RESTClient.sendGET(catalog + "/package?openagent&id=dominometer-genesis");

			String configPath = JAVA_ADDIN_ROOT + File.separator + "Genesis" + File.separator + CONFIG_FILE_NAME;
			JSONRulesStub rules = new JSONRulesStub(m_session, m_ab, configPath, m_logger);
			boolean res = rules.execute(buf.toString());

			if (res) {
				logMessage("Genesis installed (OK)");
				Log.sendLog(m_server, m_endpoint, "Genesis installed (OK)", "");
			}
			else {
				logMessage("Genesis FAILED");
				Log.sendLog(m_server, m_endpoint, "Genesis FAILED", rules.getLogBuffer().toString());
				System.out.println(rules.getLogBuffer());
			}
			
			ProgramConfigStub pc = new ProgramConfigStub("Genesis", this.args, m_logger);
			pc.setState(m_ab, ProgramConfigStub.LOAD);		// set program documents in LOAD state
			Log.sendLog(m_server, m_endpoint, "Genesis program documents (OK)", "");

			return res;
		} catch (IOException e) {
			logMessage("Install command failed: " + e.getMessage());
		}
		
		return false;
	}

	@Override
	protected void runNotesBeforeListen() {
		try {
			EventMain eventMain = new EventMain("Main", m_interval * 60, true, this.m_logger);
			eventMain.dominoMeter = this;
			eventsAdd(eventMain);
		} catch(Exception e) {
			logSevere(e);
		}
	}

	protected void listenAfterWhile() {
		if (thread != null && thread.isAlive()) {
			setAddinState("Report");
		}
		else {
			setAddinState("Idle");
		}
	}

	protected boolean resolveMessageQueueState(String cmd) {
		boolean flag = super.resolveMessageQueueState(cmd);
		if (flag) return true;

		if ("-u".equals(cmd) || "update".equals(cmd)) {
			m_config.load(m_endpoint, m_server);
			boolean res = updateVersion();
			if (!res) logMessage("version is up to date");
		}
		else if ("-r".equals(cmd) || "report".equals(cmd)) {
			sendReport(true);
		}
		else if ("-c".equals(cmd) || "config".equals(cmd)) {
			boolean res = loadConfig();
			logMessage(res ? "updated (OK)" : "updated (*FAILED*)");
		}
		else {
			logMessage("invalid command (use -h or help to get details)");
		}

		return true;
	}

	private void setLogLevel(String level) {
		m_logger.setLevel(Integer.parseInt(level));
	}

	protected boolean checkConnection() {
		m_logger.info("CheckConnection");

		Ping ping = new Ping();
		boolean res = ping.check(m_endpoint, m_server);
		m_logger.info("- " + String.valueOf(res));

		if (res) {
			failedCounter = 0;
			return true;
		};

		failedCounter++;

		logMessage("connection (*FAILED*) with: " + m_endpoint);
		logMessage("> counter: " + Integer.toString(failedCounter));
		if (ping.getParsedError() != null) {
			logMessage("> " + ping.getParsedError().getMessage());
		}

		if (failedCounter > 24) {
			this.stopAddin();
		}
		return false;
	}

	protected boolean loadConfig() {
		logMessage("LoadConfig");

		boolean res = m_config.load(m_endpoint, m_server);
		logMessage("- " + String.valueOf(res));
		if (res && m_config.getInterval() > 0) {
			m_interval = m_config.getInterval();
		}

		return res;
	}

	protected boolean updateVersion() {
		setAddinState("UpdateRobot");
		logMessage("UpdateRobot");

		UpdateRobot ur = new UpdateRobot(this.m_logger);
		String newAddinFile = ur.applyNewVersion(m_session, m_server, m_endpoint, m_config.getJAR(), m_version);
		if (newAddinFile.isEmpty()) {
			if (ur.getParsedError() != null) {
				Log.sendError(m_server, m_endpoint, ur.getParsedError());
				logSevere(ur.getParsedError().getMessage());
			}

			logMessage("- " + String.valueOf(false));
			return false;
		}

		// trigger reload for each addin so we can load a fresh version
		this.restartAll(true);

		logMessage("- " + String.valueOf(true));
		return true;
	}

	protected void sendReport(boolean manual) {
		if (thread == null || !thread.isAlive()) {
			thread = new ReportThread(m_server, m_endpoint, m_version, m_logger, manual);
			thread.start();
		}
		else {
			if (manual) {
				this.logMessage("ReportThread: already running");
			}
		}
	}

	protected void showInfoExt() {
		String abbreviate;
		try {
			Name nServer = m_session.createName(m_session.getServerName());
			abbreviate = nServer.getAbbreviated();
		} catch (NotesException e) {
			abbreviate = this.m_server;
			incrementExceptionTotal();
			e.printStackTrace();
		}

		logMessage("server       " + abbreviate);
		logMessage("endpoint     " + this.m_endpoint);
		logMessage("interval     " + Integer.toString(this.m_interval) + " minutes");
		logMessage("errors       " + String.valueOf(total_exception_count));
	}

	@Override
	protected void showHelp() {
		int year = Calendar.getInstance().get(Calendar.YEAR);
		logMessage("*** Usage ***");
		AddInLogMessageText("load runjava DominoMeter <endpoint> [logLevel]");
		AddInLogMessageText("   <endpoint> - required. url to send data");
		AddInLogMessageText("   [logLevel] - optional. '0 - debug', '1- info', '2 - warning', '3 - severe'");
		AddInLogMessageText("tell DominoMeter <command>");
		AddInLogMessageText("   quit       Unload DominoMeter");
		AddInLogMessageText("   help       Show help information (or -h)");
		AddInLogMessageText("   info       Show version and more of DominoMeter");
		AddInLogMessageText("   update     Check for a new version (or -u)");
		AddInLogMessageText("   report     Send report (or -r)");
		AddInLogMessageText("   config     Reload config for addin (or -c)");
		AddInLogMessageText("Copyright (C) Prominic.NET, Inc. 2020" + (year > 2020 ? " - " + Integer.toString(year) : ""));
		AddInLogMessageText("See https://dominometer.com for more details.");
	}

	/**
	 * Exception total (child + main threads)
	 */
	synchronized static public void incrementExceptionTotal() {
		total_exception_count++;
	}

	/**
	 * Return counter from child thread
	 */
	synchronized static public long getExceptionTotal() {
		return total_exception_count;
	}

	protected void termBeforeAB() {
		terminateReportThread();
	}

	/**
	 * Terminate report thread
	 */
	private void terminateReportThread() {
		if (thread == null || !thread.isAlive()) return;

		logMessage("ReportThread: is alive, stopping...");

		long counter = 0;
		thread.interrupt();
		while(thread.isAlive()) {
			try {
				counter++;
				sleep(100);

				if (counter % 50 == 0) {
					logMessage("ReportThread: is stopping, please wait...");
				}
			} catch (InterruptedException e) {
				incrementExceptionTotal();
				logSevere(e);
				e.printStackTrace();
			}

			// 1 hour (but Domino has setting to shut down after 5 mins by default)
			if (counter > 36000) {
				logMessage("ReportThread: forcing to quit after 2 hours");
				return;
			}
		}

		logMessage("ReportThread: has been stopped nicely");
	}

}
