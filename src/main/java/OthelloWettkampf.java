import java.util.ArrayList;
import java.util.List;
import rahmen.OthelloArena;

public class OthelloWettkampf {

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

    String protokolldatei = "/Users/Wendo99/50-Apps-local/IntelliJ/OthelloPlayer/log/Othello.log";

    new OthelloArena(150,  // Gesamtbedenkzeit in Sekunden
        spieler,                    // Spielerliste
        protokolldatei,             // Dateiname für Ergebnisausgaben
        true                        // debug
    );
  }
}
