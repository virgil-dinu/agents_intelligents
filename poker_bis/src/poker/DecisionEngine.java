package poker;

import java.util.Random;

/**
 * Moteur de decision pour le Poker Indien.
 *
 * Implemente la politique de decision conforme au tableau ai-raison.com.
 * Chaque methode publique peut etre remplacee par un appel HTTP a
 * AiRaisonClient sans modifier le reste du code.
 *
 * Priorite d'evaluation : du scenario le plus specifique (3 elements)
 * au plus general (1 element), puis defaut aleatoire.
 */
public class DecisionEngine {

    private final Random rng = new Random();

    // --- Statistiques pour strategie adaptative ---
    private int bluffAttempts  = 0;
    private int bluffSuccesses = 0;
    private int totalRounds    = 0;
    private int recentLosses   = 0;

    /**
     * Point d'entree principal. Construit le contexte et retourne une decision.
     *
     * @param opponentCardVisible  carte adverse visible par cet agent
     * @param opponentBetAmount    montant de la mise adverse (0 si premier a parler)
     * @param currentPot           pot actuel
     * @param myStack              mon stack
     * @param responseSpeed        "fast", "slow" ou "normal"
     * @return "fold", "call" ou "bet"
     */
    public String decide(int opponentCardVisible, int opponentBetAmount,
                         int currentPot, int myStack, String responseSpeed) {

        boolean highCard    = opponentCardVisible >= GameConfig.HIGH_CARD_THRESHOLD;
        boolean strongBet   = opponentBetAmount   >= GameConfig.STRONG_BET_THRESHOLD;
        boolean bigPot      = currentPot           >= GameConfig.BIG_POT_THRESHOLD;
        boolean lowStack    = myStack              <= GameConfig.LOW_STACK_THRESHOLD;
        boolean fastResp    = "fast".equals(responseSpeed);
        boolean slowResp    = "slow".equals(responseSpeed);

        totalRounds++;
        String decision = applyPolicy(highCard, strongBet, bigPot, lowStack, fastResp, slowResp);

        // --- Couche de bluff par-dessus la decision brute ---
        decision = applyBluffLayer(decision, highCard, lowStack, myStack);

        return decision;
    }

    /**
     * Politique de decision deterministe (tableau raison.com).
     * Ordre : 3 elements -> 2 elements -> 1 element -> defaut.
     */
    private String applyPolicy(boolean highCard, boolean strongBet, boolean bigPot,
                                boolean lowStack, boolean fastResp, boolean slowResp) {

        // ===== 3 elements =====

        // low_opponent_card + strong_opponent_bet + adv_fast_response => call
        if (!highCard && strongBet && fastResp) return "call";

        // low_opponent_card + strong_opponent_bet + big_pot => call
        if (!highCard && strongBet && bigPot) return "call";

        // low_opponent_card + low_opponent_bet + big_pot => bet
        if (!highCard && !strongBet && bigPot) return "bet";

        // high_opponent_card + low_opponent_bet + adv_slow_response => fold
        if (highCard && !strongBet && slowResp) return "fold";

        // ===== 2 elements =====

        // high_opponent_card + strong_opponent_bet => fold
        if (highCard && strongBet) return "fold";

        // low_opponent_card + strong_opponent_bet => call
        if (!highCard && strongBet) return "call";

        // low_opponent_card + low_opponent_bet => bet
        if (!highCard && !strongBet) return "bet";

        // ===== 1 element =====

        // low_stack => fold ou bet (desespere)
        if (lowStack) return rng.nextDouble() < 0.4 ? "bet" : "fold";

        // high_opponent_card seul => fold
        if (highCard) return "fold";

        // low_opponent_card seul => bet/call
        if (!highCard) return rng.nextBoolean() ? "bet" : "call";

        // ===== Defaut =====
        double r = rng.nextDouble();
        if (r < 0.3) return "fold";
        if (r < 0.65) return "call";
        return "bet";
    }

    /**
     * Couche de bluff appliquee par-dessus la decision de base.
     * Implemente les 4 politiques du document :
     *   1. Kamikaze      — grosse mise sur carte adverse forte
     *   2. Leurre        — petite mise sur carte adverse faible
     *   3. Desespere     — all-in quand stack critique
     *   4. Bluff debutant — ne bluff jamais les 10 premiers tours
     */
    private String applyBluffLayer(String baseDecision, boolean highCard,
                                    boolean lowStack, int myStack) {

        // Politique 4 : pas de bluff les 10 premiers tours
        if (totalRounds <= 10) return baseDecision;

        // Politique 3 : desespere (stack <= 5) => bet max
        if (myStack <= 5) {
            bluffAttempts++;
            return "bet";
        }

        // Politique 1 : kamikaze — la decision dit fold (carte adverse haute)
        //   mais on bet quand meme avec une probabilite variable
        if ("fold".equals(baseDecision) && highCard) {
            double kamikazeRate = computeBluffRate();
            if (rng.nextDouble() < kamikazeRate) {
                bluffAttempts++;
                return "bet";
            }
        }

        // Politique 2 : leurre de faiblesse — carte adverse faible, on call
        //   au lieu de bet pour attirer l'adversaire
        if ("bet".equals(baseDecision) && !highCard && rng.nextDouble() < 0.2) {
            return "call";
        }

        return baseDecision;
    }

    /**
     * Taux de bluff adaptatif.
     * Monte si : beaucoup de pertes recentes, ou bon taux de reussite en bluff.
     */
    private double computeBluffRate() {
        double base = 0.15;

        // Augmente si beaucoup perdu recemment
        if (recentLosses > 3) base += 0.10;

        // Augmente si le bluff fonctionne souvent
        if (bluffAttempts > 0) {
            double successRate = (double) bluffSuccesses / bluffAttempts;
            if (successRate > 0.5) base += 0.10;
        }

        return Math.min(base, 0.40); // plafond a 40%
    }

    // --- Mise a jour des stats (appele depuis l'agent) ---

    public void registerBluffResult(boolean success) {
        if (success) bluffSuccesses++;
    }

    public void registerLoss() {
        recentLosses++;
        // Fenetre glissante : reset apres 10 pertes
        if (recentLosses > 10) recentLosses = 5;
    }

    public void registerWin() {
        recentLosses = Math.max(0, recentLosses - 1);
    }

    /**
     * Calcule le montant de mise en fonction de l'action decidee.
     */
    public int computeBetAmount(String action, int currentBet, int myStack) {
        if ("fold".equals(action)) return 0;
        if ("call".equals(action)) return Math.min(currentBet, myStack);

        // bet : entre 2x ante et 30% du stack
        int minBet = GameConfig.ANTE * 2;
        int maxBet = Math.max(minBet, (int) (myStack * 0.3));
        int range  = Math.max(1, maxBet - minBet + 1);
        return Math.min(minBet + rng.nextInt(range), myStack);
    }

    /**
     * Simule un delai de reponse strategique (section "temps comme signal").
     * @return delai en ms a attendre avant d'envoyer la reponse
     */
    public long computeStrategicDelay(String action) {
        // Intimidateur : reponse rapide + mise elevee
        if ("bet".equals(action) && rng.nextDouble() < 0.3) {
            return 100 + rng.nextInt(200);
        }
        // Hesitation : attente longue pour inciter l'adversaire
        if ("call".equals(action) && rng.nextDouble() < 0.25) {
            return 1500 + rng.nextInt(1000);
        }
        // Normal
        return 300 + rng.nextInt(700);
    }
}
