import net.prominic.gja_v085.Event;
import net.prominic.gja_v085.GLogger;

public class DominoMeterEventMain extends Event {
	public DominoMeter dominoMeter = null;
	public boolean m_firstRun = true;
			
	public DominoMeterEventMain(String name, long seconds, boolean fireOnStart, GLogger logger) {
		super(name, seconds, fireOnStart, logger);
	}

	@Override
	public void run() {
		if (!dominoMeter.checkConnection()) return;

		if (!dominoMeter.updateVersion()) {
			dominoMeter.sendReport(false, m_firstRun);
		}
		
		m_firstRun = false;
	}
}
