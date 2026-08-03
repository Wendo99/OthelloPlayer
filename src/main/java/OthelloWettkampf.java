import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import rahmen.OthelloArena;

/**
 * Local test driver for your own Othello player.
 *
 * <p><strong>Origin and Rights:</strong> This class was provided as a
 * template as part of the course and does not originate from the author of this repository. It is
 * therefore
 * <em>not</em> licensed under the MIT License of this repository; the rights belong to the
 * creator of the
 * course material. The same applies to the framework archive under {@code repo/} (packages
 * {@code rahmen} and {@code spieler}), against which this project is compiled.</p>
 *
 * <p><strong>Purpose:</strong> This class creates a {@link OthelloArena} object and uses it to
 * start a local competition between the players specified in the player list. During development,
 * it was used
 * to run the user's player against a reference opponent
 * for testing purposes. The actual tournament between all participating players was
 * conducted separately in another framework; this class was not involved in that.</p>
 *
 * <p><strong>Custom changes to the template:</strong></p>
 * <ul>
 *   <li>Add your own player to the player list
 *       ({@code player.add(...)}).</li>
 *   <li>Replacing the hard-coded, absolute path of the log file with a relative
 *       path, whose directory is created if necessary (see
 *       {@link #createLogFile()}).</li>
 * </ul>
 *
 * <p>Otherwise, the template remains unchanged.</p>
 *
 */
public class OthelloWettkampf {

  /**
   * Log file, relative to the working directory.
   */
  private static final String DEFAULT_LOGFILE = "log/Othello.log";

  public static void main(String[] args) {
    /* Es muss ein OthelloArena-Objekt erzeugt
     * werden. Bei der Erzeugung werden die am Wettkampf
     * teilnehmenden Spieler in Form einer Liste von
     * Othello-Spielern übergeben. Durch die Erzeugung des
     * OthelloArena-Objekts wird der Wettkampf gestartet.
     */

    // Spielerliste aufbauen
    List<String> spieler = new ArrayList<>();

    // Die Spieler
    spieler.add("spieler.wendo.Spieler:100");  // Tiefe = Obergrenze; die Zeit begrenzt real
    spieler.add("spieler.Rumpfspieler:7");  // Referenzgegner mit Zugtiefe 7

    String protokolldatei = createLogFile();

    new OthelloArena(150,  // Gesamtbedenkzeit in Sekunden
        spieler,                    // Spielerliste
        protokolldatei,             // Dateiname für Ergebnisausgaben
        true                        // debug
    );
  }

  /**
   * Resolves {@link #DEFAULT_LOGFILE} relative to the working directory and creates the
   * parent directory if it does not yet exist (in a fresh clone,
   * {@code log/} is missing, since log files are not versioned).
   *
   * @return the absolute path of the log file
   */
  private static String createLogFile() {
    Path path = Path.of(DEFAULT_LOGFILE).toAbsolutePath();
    try {
      Files.createDirectories(path.getParent());
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot create directory for log file: " + path, e);
    }
    return path.toString();
  }
}
