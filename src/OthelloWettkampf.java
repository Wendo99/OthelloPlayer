import rahmen.OthelloArena;

import java.util.ArrayList;
import java.util.List;

public class OthelloWettkampf {

    public static void main(String[] args) {
        /* Es muss ein OthelloArena-Objekt erzeugt
         * werden. Bei der Erzeugung werden die am Wettkampf
         * teilnehmenden Spieler in Form einer Liste von
         * Othello-Spielern uebergeben. Durch die Erzeugung des
         * OthelloArena-Objekts wird der Wettkampf gestartet.
         */

        //Spielerliste aufbauen
        List<String> spieler =
                new ArrayList<>();
        //Die Spieler
        spieler.add("spieler.Rumpfspieler"); //Referenzspieler in Standardeinstellung
        spieler.add("spieler.Rumpfspieler:6"); //Referenzspieler mit Zugtiefe 6

        String protokolldatei = "/Users/Wendo99/50-Apps-local/IntelliJ/OthelloPlayer/log/Othello.log";

        new OthelloArena(150,  //Gesamtbedenkzeit in Sekunden
                spieler,                //Spielerliste
                protokolldatei,         //Dateiname fuer Ergebnisausgaben
                true                      //debug
        );
    }
}
