import net.prominic.gja_v084.Event;
import net.prominic.gja_v084.GLogger;

public class DominoMeterEventMain extends Event {
	public DominoMeter dominoMeter = null;
	public boolean m_firstRun = true;
			
	public DominoMeterEventMain(String name, long seconds, boolean fireOnStart, GLogger logger) {
		super(name, seconds, fireOnStart, logger);
	}

	@Override
	public void run() {
		if (!dominoMeter.checkConnection()) return;

		dominoMeter.loadConfig();
		dominoMeter.sendReport(false, m_firstRun);
		dominoMeter.updateVersion();
		
		m_firstRun = false;
	}
}
