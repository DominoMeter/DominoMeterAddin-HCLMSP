import java.util.Calendar;
import lotus.domino.Name;
import lotus.domino.NotesException;
import net.prominic.dm.api.Config;
import net.prominic.dm.api.Log;
import net.prominic.dm.api.Ping;
import net.prominic.dm.update.UpdateRobot;
import net.prominic.gja_v084.JavaServerAddinGenesis;

public class DominoMeter extends JavaServerAddinGenesis {
	public static String	exception_last = null;
	public static long 		exception_count = 0;
	
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
		return "140";
	}

	@Override
	protected String getJavaAddinDate() {
		return "2023-11-11 12:00 (windows)";
	}

	@Override
	protected boolean runNotesAfterInitialize() {
		if (args == null) {
			String params = this.getConfigValue("runjava");
			if (params != null) {
				logMessage("Loading using config: " + this.m_javaAddinConfig);
				if (params.startsWith(this.getJavaAddinName())) {
					params = params.substring(getJavaAddinName().length()).trim();
					args = params.split(" ");
				}
			}
		}
		
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

			// new config
			m_config = new Config();
		} catch(Exception e) {
			logSevere(e);
		}

		return true;
	}

	@Override
	protected void runNotesBeforeListen() {
		try {
			DominoMeterEventMain eventMain = new DominoMeterEventMain("Main", m_interval * 60, true, this.m_logger);
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

		// unload after 20 days not able to send report
		if (failedCounter > 240) {
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
		logMessage("errors       " + String.valueOf(exception_count));
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
		exception_count++;
	}
	
	/**
	 * Return counter from child thread
	 */
	synchronized static public long getExceptionTotal() {
		return exception_count;
	}
	
	/**
	 * Exception last (child + main threads)
	 */
	synchronized static public void setExceptionLast(Exception e) {
		String message = e.getLocalizedMessage();
		if (message == null) {
			e.getMessage();
		}
		if (message == null) {
			message = "Internal Error";
		}
		
		exception_last = message;
	}
	
	/**
	 * Return last error from child thread
	 */
	synchronized static public String getExceptionLast() {
		return exception_last;
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

			// force to shut down after 5 mins hour (Domino however has a setting to shut down after 5 mins by default)
			if (counter > 3000) {
				logMessage("ReportThread: forcing to quit after 5 mins");
				return;
			}
		}

		logMessage("ReportThread: has been stopped nicely");
	}

}
