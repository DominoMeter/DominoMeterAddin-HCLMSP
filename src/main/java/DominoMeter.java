import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;

import lotus.domino.Name;
import lotus.domino.NotesException;
import net.prominic.dm.api.Config;
import net.prominic.dm.api.Log;
import net.prominic.dm.api.Ping;
import net.prominic.dm.update.ProgramConfig;
import net.prominic.dm.update.UpdateRobot;
import net.prominic.gja_v20220511.Event;
import net.prominic.gja_v20220511.JavaServerAddinGenesis;

public class DominoMeter extends JavaServerAddinGenesis {
	public static long 		total_exception_count = 0;

	private int				m_interval				= 60;
	private String 			m_server				= "";
	private String 			m_endpoint				= "";
	private String 			m_version				= "";
	private int				failedCounter			= 0;
	private String 			m_startDateTime			= "";
	private Config			m_config				= null;
	private ProgramConfig	m_pc					= null;
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
		return "2022-05-11 16:50 (gja)";
	}

	@Override
	protected boolean runNotesAfterInitialize() {
		if (args == null) {
			logMessage("You must provide an endpoint to send data, see instructions below");
			showHelp();
			return false;
		}

		try {
			setAddinState("Initializing");
			m_logger.info("--------------------------------------");
			m_logger.info("Initializing");

			m_server = m_session.getServerName();
			m_startDateTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Calendar.getInstance().getTime());
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

			// new config
			m_config = new Config();
			
			// adjust program documents
			m_pc = new ProgramConfig(m_server, m_endpoint, this.getJavaAddinName(), this.m_logger);
			m_pc.setState(m_ab, ProgramConfig.LOAD);		// set program documents in LOAD state
		} catch(Exception e) {
			logSevere(e);
		}

		return true;
	}

	@Override
	protected void runNotesBeforeListen() {
		try {
			// init Event
			HashMap<String, Object> paramsMain = new HashMap<String, Object>();
			paramsMain.put("dominometer", this);
			Event eventMain = new EventMain("Main", m_interval, true, paramsMain, this.m_logger);
			eventsAdd(eventMain);

			/*
			// TODO: install Genesis (must be removed after all)
			boolean installed = genesis();
			if (installed) {
				logMessage("Genesis has been installed, DominoMeter needs to be unloaded");

				ProgramConfig pc = new ProgramConfig(server, endpoint, JADDIN_NAME, fileLogger);
				pc.setState(ab, ProgramConfig.UNLOAD);		// set program documents in LOAD state

				return;
			}
			 */
		} catch(Exception e) {
			logSevere(e);
		}
	}

	/*
	private boolean genesis() {
		try {
			// check if already installed
			String GJA_Genesis = session.getEnvironmentString("GJA_Genesis", true);
			if (!GJA_Genesis.isEmpty()) {
				logMessage("Genesis - already installed (skip)");
				return false;
			}

			// find addin in catalog
			String catalog = "https://domino-1.dmytro.cloud/gc.nsf";
			StringBuffer buf = RESTClient.sendGET(catalog + "/app?openagent&name=dominometer-genesis");

			JSONRules rules = new JSONRules(session, ab);
			boolean res = rules.execute(buf.toString());

			if (res) {
				logMessage("Genesis installed (OK)");
			}
			else {
				logMessage("Genesis FAILED");
				System.out.println(rules.getLogBuffer());
			}

			return res;
		} catch (IOException e) {
			logMessage("Install command failed: " + e.getMessage());
		} catch (NotesException e) {
			logMessage("Install command failed: " + e.getMessage());
		}

		return false;
	}
	 */

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

		m_pc.setState(m_ab, ProgramConfig.UNLOAD);		// set program documents in UNLOAD state
		Log.sendLog(m_server, m_endpoint, m_version + " - will be unloaded to upgrade to a newer version: " + newAddinFile, "New version " + newAddinFile + " should start in ~20 mins");
		this.stopAddin();

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
		logMessage("log folder   " + this.m_logger.getDirectory());
		logMessage("logging      " + this.m_logger.getLevelLabel());
		logMessage("started      " + m_startDateTime);
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
