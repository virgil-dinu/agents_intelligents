package poker;

import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.WakerBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.util.Random;

/**
 * Agent JADE pour le Poker Indien (Blind Man's Bluff).
 *
 * Chaque instance joue alternativement le role de DEALER et de PLAYER.
 * Le DEALER initie la manche (distribue), le PLAYER repond en premier.
 *
 * Protocole ACL entre les 2 agents :
 *
 *   PHASE 1 — Distribution (dealer -> player)
 *     Performatif : REQUEST
 *     Contenu : DEAL|carteVisibleParLePlayer|pot|stackDealer|stackPlayer
 *
 *   PHASE 2 — Action du player (player -> dealer)
 *     Performatif : PROPOSE
 *     Contenu : ACTION|fold/call/bet|montant|timestampEnvoi
 *
 *   PHASE 3 — Reponse du dealer (dealer -> player) si necessaire
 *     Performatif : PROPOSE
 *     Contenu : ACTION|fold/call/bet|montant
 *
 *   PHASE 4 — Resultat (dealer -> player)
 *     Performatif : INFORM
 *     Contenu : RESULT|nomGagnant|carteDealer|cartePlayer|pot|stackDealer|stackPlayer
 *
 * Apres chaque manche les roles s'inversent.
 */
public class PokerPlayerAgent extends Agent {

    // --- Synchronisation inter-agents pour l'input utilisateur ---
    private static volatile boolean waitingForInput = false;

    // --- Etat persistant ---
    private String         opponentName;
    private boolean        isDealer;
    private int            stack;
    private int            opponentStack;
    private int            roundNumber  = 0;
    private int            wins         = 0;
    private int            losses       = 0;
    private DecisionEngine brain;
    private Random         rng;

    // --- Etat de la manche en cours ---
    private int myCard;
    private int opponentCard;
    private int pot;

    // =======================================================================
    //  CYCLE DE VIE JADE
    // =======================================================================

    @Override
    protected void setup() {
        Object[] args = getArguments();
        if (args == null || args.length < 2) {
            log("Arguments manquants (opponentName, role). Arret.");
            doDelete();
            return;
        }

        opponentName  = (String) args[0];
        isDealer      = "dealer".equals(args[1]);
        stack         = GameConfig.STARTING_STACK;
        opponentStack = GameConfig.STARTING_STACK;
        brain         = new DecisionEngine();
        rng           = new Random();

        log("Pret | Stack: " + stack + " | Role initial: " + (isDealer ? "DEALER" : "PLAYER"));

        // Delai de demarrage pour que les 2 agents soient initialises
        addBehaviour(new WakerBehaviour(this, GameConfig.STARTUP_DELAY_MS) {
            @Override
            protected void onWake() {
                scheduleNextRound();
            }
        });
    }

    @Override
    protected void takeDown() {
        log("Quitte la table. Victoires=" + wins + " Defaites=" + losses + " Stack=" + stack);
    }

    // =======================================================================
    //  ORCHESTRATION DES MANCHES
    // =======================================================================

    private void scheduleNextRound() {
        if (!canContinue()) return;
        if (isDealer) {
            addBehaviour(new DealerBehaviour());
        } else {
            addBehaviour(new PlayerBehaviour());
        }
    }

    private void swapRolesAndContinue() {
        isDealer = !isDealer;

        // Un seul agent lit stdin : celui qui devient DEALER apres inversion des roles
        if (isDealer) {
            System.out.println("\n[Appuyez sur ENTREE pour la manche suivante...]");
            waitingForInput = true;
            new Thread(() -> {
                try { System.in.read(); } catch (Exception ignored) {}
                waitingForInput = false;
            }).start();
        }

        addBehaviour(new Behaviour() {
            @Override
            public void action() { if (waitingForInput) block(100); }
            @Override
            public boolean done() {
                if (!waitingForInput) { scheduleNextRound(); return true; }
                return false;
            }
        });
    }

    private boolean canContinue() {
        if (stack <= 0 || opponentStack <= 0) {
            String winner = (stack > 0) ? getLocalName() : opponentName;
            System.out.println();
            System.out.println("================================================================");
            System.out.println("  FIN DE PARTIE apres " + roundNumber + " manches");
            System.out.println("  GAGNANT : " + winner);
            System.out.println("  " + getLocalName() + " : V=" + wins + " D=" + losses
                    + " Stack=" + stack);
            System.out.println("================================================================");
            System.out.println();
            doDelete();
            return false;
        }
        return true;
    }

    // =======================================================================
    //  COMPORTEMENT DEALER
    //  Le dealer distribue, attend l'action du player, repond, resout.
    // =======================================================================

    private class DealerBehaviour extends Behaviour {
        private static final int STEP_WAIT_PLAYER_ACTION = 0;
        private static final int STEP_DONE               = 1;
        private int step = STEP_WAIT_PLAYER_ACTION;

        @Override
        public void onStart() {
            roundNumber++;
            myCard       = drawCard();
            opponentCard = drawCard();

            // Prelevement de l'ante
            stack         -= GameConfig.ANTE;
            opponentStack -= GameConfig.ANTE;
            pot            = GameConfig.ANTE * 2;

            printRoundHeader();

            // Le player voit MA carte (myCard) — c'est le principe du poker indien
            ACLMessage deal = new ACLMessage(ACLMessage.REQUEST);
            deal.addReceiver(opponent());
            deal.setConversationId(conversationId());
            deal.setContent(GameConfig.PREFIX_DEAL
                    + GameConfig.SEPARATOR + myCard
                    + GameConfig.SEPARATOR + pot
                    + GameConfig.SEPARATOR + stack
                    + GameConfig.SEPARATOR + opponentStack);
            send(deal);
        }

        @Override
        public void action() {
            if (step == STEP_WAIT_PLAYER_ACTION) {
                ACLMessage msg = receive(MessageTemplate.and(
                        MessageTemplate.MatchPerformative(ACLMessage.PROPOSE),
                        MessageTemplate.MatchConversationId(conversationId())));

                if (msg == null) { block(); return; }

                String[] parts   = msg.getContent().split("\\" + GameConfig.SEPARATOR);
                String   action  = parts[1];
                int      amount  = Integer.parseInt(parts[2]);
                long     sentAt  = Long.parseLong(parts[3]);
                String   speed   = classifyResponseTime(sentAt);

                log("[Dealer] " + opponentName + " joue " + action + "(" + amount + ") speed=" + speed);

                if ("fold".equals(action)) {
                    resolveRound(getLocalName(), "fold-player");
                    step = STEP_DONE;
                    return;
                }

                // Mise du player appliquee
                opponentStack -= amount;
                pot           += amount;

                // Ma decision en tant que dealer
                String myAction = brain.decide(
                        opponentCard, amount, pot, stack, speed);
                int myAmount    = brain.computeBetAmount(myAction, amount, stack);

                // Delai strategique
                try { Thread.sleep(brain.computeStrategicDelay(myAction)); }
                catch (InterruptedException ignored) {}

                log("[Dealer] Je decide : " + myAction + "(" + myAmount + ")");

                if ("fold".equals(myAction)) {
                    resolveRound(opponentName, "fold-dealer");
                } else {
                    stack -= myAmount;
                    pot   += myAmount;
                    resolveRound(whoWins(), "showdown");
                }
                step = STEP_DONE;
            }
        }

        @Override
        public boolean done() { return step == STEP_DONE; }
    }

    // =======================================================================
    //  COMPORTEMENT PLAYER
    //  Le player attend la donne, prend sa decision, attend le resultat.
    // =======================================================================

    private class PlayerBehaviour extends Behaviour {
        private static final int STEP_WAIT_DEAL   = 0;
        private static final int STEP_WAIT_RESULT = 1;
        private static final int STEP_DONE        = 2;
        private int step = STEP_WAIT_DEAL;

        @Override
        public void action() {
            switch (step) {

                case STEP_WAIT_DEAL: {
                    ACLMessage deal = receive(
                            MessageTemplate.MatchPerformative(ACLMessage.REQUEST));
                    if (deal == null) { block(); return; }

                    roundNumber++;
                    String[] parts = deal.getContent().split("\\" + GameConfig.SEPARATOR);
                    // DEAL|carteVisiblePourMoi|pot|stackDealer|monStack
                    opponentCard  = Integer.parseInt(parts[1]); // Je vois la carte du dealer
                    pot           = Integer.parseInt(parts[2]);
                    opponentStack = Integer.parseInt(parts[3]);
                    stack         = Integer.parseInt(parts[4]);

                    log("[Player] Carte adverse visible : " + opponentCard);

                    // Decision
                    String action = brain.decide(
                            opponentCard, GameConfig.ANTE, pot, stack, "normal");
                    int amount    = brain.computeBetAmount(action, GameConfig.ANTE, stack);

                    // Delai strategique
                    long delay = brain.computeStrategicDelay(action);
                    try { Thread.sleep(delay); }
                    catch (InterruptedException ignored) {}

                    log("[Player] Decision : " + action + "(" + amount + ")");

                    ACLMessage reply = new ACLMessage(ACLMessage.PROPOSE);
                    reply.addReceiver(opponent());
                    reply.setConversationId(deal.getConversationId());
                    reply.setContent(GameConfig.PREFIX_ACTION
                            + GameConfig.SEPARATOR + action
                            + GameConfig.SEPARATOR + amount
                            + GameConfig.SEPARATOR + System.currentTimeMillis());
                    send(reply);
                    step = STEP_WAIT_RESULT;
                    break;
                }

                case STEP_WAIT_RESULT: {
                    ACLMessage result = receive(
                            MessageTemplate.MatchPerformative(ACLMessage.INFORM));
                    if (result == null) { block(); return; }

                    String[] parts = result.getContent().split("\\" + GameConfig.SEPARATOR);
                    // RESULT|winner|carteDealer|cartePlayer|pot|stackDealer|stackPlayer
                    String winner     = parts[1];
                    int    dealerCard = Integer.parseInt(parts[2]);
                    int    playerCard = Integer.parseInt(parts[3]);
                    opponentStack     = Integer.parseInt(parts[5]);
                    stack             = Integer.parseInt(parts[6]);

                    if (getLocalName().equals(winner)) {
                        wins++;
                        brain.registerWin();
                    } else {
                        losses++;
                        brain.registerLoss();
                    }

                    log("[Player] Resultat recu : " + winner + " gagne | Cartes : dealer="
                            + dealerCard + " player=" + playerCard);

                    pot  = 0;
                    step = STEP_DONE;
                    swapRolesAndContinue();
                    break;
                }
            }
        }

        @Override
        public boolean done() { return step == STEP_DONE; }
    }

    // =======================================================================
    //  RESOLUTION DE MANCHE (cote dealer uniquement)
    // =======================================================================

    private void resolveRound(String winner, String reason) {
        boolean iWin = getLocalName().equals(winner);
        if (iWin) {
            stack += pot;
            wins++;
            brain.registerWin();
        } else {
            opponentStack += pot;
            losses++;
            brain.registerLoss();
        }

        System.out.println("  >>> RESULTAT manche " + roundNumber + " : " + winner
                + " gagne " + pot + " jetons (" + reason + ")");
        System.out.println("      Cartes : " + getLocalName() + "=" + myCard
                + " | " + opponentName + "=" + opponentCard);
        System.out.println("      Stacks : " + getLocalName() + "=" + stack
                + " | " + opponentName + "=" + opponentStack);

        // Informe le player
        ACLMessage resultMsg = new ACLMessage(ACLMessage.INFORM);
        resultMsg.addReceiver(opponent());
        resultMsg.setConversationId(conversationId());
        resultMsg.setContent(GameConfig.PREFIX_RESULT
                + GameConfig.SEPARATOR + winner
                + GameConfig.SEPARATOR + myCard
                + GameConfig.SEPARATOR + opponentCard
                + GameConfig.SEPARATOR + pot
                + GameConfig.SEPARATOR + stack
                + GameConfig.SEPARATOR + opponentStack);
        send(resultMsg);

        pot = 0;
        swapRolesAndContinue();
    }

    private String whoWins() {
        if (myCard > opponentCard) return getLocalName();
        if (opponentCard > myCard) return opponentName;
        return rng.nextBoolean() ? getLocalName() : opponentName;
    }

    // =======================================================================
    //  UTILITAIRES
    // =======================================================================

    private void printRoundHeader() {
        System.out.println();
        System.out.println("--- Manche " + roundNumber + " | "
                + getLocalName() + " (DEALER) vs " + opponentName
                + " | Stacks: " + stack + " vs " + opponentStack
                + " | Pot ante: " + pot + " ---");
    }

    private int drawCard() {
        return GameConfig.CARD_MIN + rng.nextInt(GameConfig.CARD_MAX - GameConfig.CARD_MIN + 1);
    }

    private AID opponent() {
        return new AID(opponentName, AID.ISLOCALNAME);
    }

    private String conversationId() {
        return "poker-round-" + roundNumber;
    }

    private String classifyResponseTime(long sentTimestamp) {
        long elapsed = System.currentTimeMillis() - sentTimestamp;
        if (elapsed < GameConfig.FAST_RESPONSE_MS)  return "fast";
        if (elapsed > GameConfig.SLOW_RESPONSE_MS)  return "slow";
        return "normal";
    }

    private void log(String message) {
        System.out.println("[" + getLocalName() + "] " + message);
    }
}