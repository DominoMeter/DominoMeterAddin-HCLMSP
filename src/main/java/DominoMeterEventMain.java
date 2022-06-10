import net.prominic.gja_v081.Event;
import net.prominic.gja_v081.GLogger;

public class DominoMeterEventMain extends Event {
	public DominoMeter dominoMeter = null;

	public DominoMeterEventMain(String name, long seconds, boolean fireOnStart, GLogger logger) {
		super(name, seconds, fireOnStart, logger);
	}

	@Override
	public void run() {
		if (!dominoMeter.checkConnection()) return;

		dominoMeter.loadConfig();
		dominoMeter.sendReport(false);
		dominoMeter.updateVersion();
	}
}
