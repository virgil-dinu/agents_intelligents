package poker;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;

/**
 * Point d'entree : demarre la plateforme JADE et cree exactement 2 agents.
 *
 * Agent1 demarre en tant que DEALER (initie la premiere manche).
 * Agent2 demarre en tant que PLAYER (attend la donne).
 * Les roles s'inversent a chaque manche.
 *
 * Lancement :
 *   java -cp "lib/jade.jar;bin" poker.Main
 */
public class Main {

    public static void main(String[] args) {
        System.out.println("============================================");
        System.out.println("  POKER INDIEN — Blind Man's Bluff (JADE)");
        System.out.println("============================================");
        System.out.println();

        try {
            // --- Initialisation de la plateforme JADE ---
            Runtime runtime    = Runtime.instance();
            Profile profile    = new ProfileImpl();
            profile.setParameter(Profile.GUI, "false");  // true pour activer la GUI JADE

            AgentContainer container = runtime.createMainContainer(profile);

            // --- Creation des 2 agents ---
            // Argument 0 : nom de l'adversaire
            // Argument 1 : role initial (dealer / player)
            Object[] argsAgent1 = new Object[]{
                    GameConfig.AGENT2_NAME, "dealer"
            };
            Object[] argsAgent2 = new Object[]{
                    GameConfig.AGENT1_NAME, "player"
            };

            AgentController ctrl1 = container.createNewAgent(
                    GameConfig.AGENT1_NAME,
                    "poker.PokerPlayerAgent",   // nom complet avec package
                    argsAgent1
            );
            AgentController ctrl2 = container.createNewAgent(
                    GameConfig.AGENT2_NAME,
                    "poker.PokerPlayerAgent",
                    argsAgent2
            );

            ctrl1.start();
            ctrl2.start();

            System.out.println("[MAIN] 2 agents crees et demarres.");
            System.out.println("[MAIN] La partie commence dans "
                    + (GameConfig.STARTUP_DELAY_MS / 1000) + " secondes...");
            System.out.println();

        } catch (Exception e) {
            System.err.println("[MAIN] Erreur fatale : " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
